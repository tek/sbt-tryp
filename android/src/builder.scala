package tryp

import reflect.macros.Context

import sbt._
import Keys._
import android.Keys._
import Types._

object Aar
{
  lazy val settings = android.Plugin.buildAar ++ Export.settings
}

trait Proguard {
  lazy val settings = Seq(
    useProguard in Android := true,
    proguardScala in Android := true,
    proguardCache in Android ++= cache,
    proguardOptions in Android ++= options,
    packagingOptions in Android := PackagingOptions(excludes, Seq(), merges)
  )

  lazy val cache: Seq[String] = Seq()

  lazy val options: Seq[String] = Seq()

  lazy val excludes: Seq[String] = Seq()

  lazy val merges: Seq[String] = Seq()
}

trait Placeholders {
  lazy val defaults: Map[String, String] = Map()

  def apply(name: String) = {
    defaults ++ specific.get(name).getOrElse(Map[String, String]())
  }

  lazy val specific: Map[String, Map[String, String]] = Map()
}

object Tests {
  def settings = Seq(
    testOptions in Test += sbt.Tests.Argument("-oF"),
    exportJars in Test := false,
    fork in Test := true,
    javaOptions in Test ++= Seq("-XX:+CMSClassUnloadingEnabled", "-noverify"),
    unmanagedClasspath in Test ++= (bootClasspath in Android).value,
    test in Test <<= test in Test dependsOn TrypAndroidKeys.symlinkLibs,
    testOnly in Test <<= testOnly in Test dependsOn TrypAndroidKeys.symlinkLibs
  )
}

class AndroidTrypId(plainId: DepSpec, path: String, sub: Seq[String],
  dev: Boolean)
extends TrypId(plainId, path, sub, dev)
{
  def aRefs = super.projects
}

object AndroidDeps
{
  def adImpl(c: Context)(id: c.Expr[ModuleID], path: c.Expr[String],
    sub: c.Expr[String]*) =
  {
    import c.universe._
    c.Expr[AndroidTrypId] {
      q"""new tryp.AndroidTrypId(
        libraryDependencies += android.Keys.aar($id), $path, Seq(..$sub), true
      )
      """
    }
  }
}

trait AndroidDeps
extends Deps
{
  override def resolvers = Map(
    "unit" → Seq(
    "RoboTest releases" at
      "https://github.com/zbsz/mvn-repo/raw/master/releases/"
    )
  )

  override def unit = super.unit ++ ids(
    "org.apache.maven" % "maven-ant-tasks" % "2.1.3",
    "junit" % "junit" % "4.+",
    "com.geteit" %% "robotest" % "+"
  )

  override def integration = ids(
    "junit" % "junit" % "4.+",
    "com.jayway.android.robotium" % "robotium-solo" % "5.+"
  )

  def ad(id: ModuleID, path: String, sub: String*) = macro AndroidDeps.adImpl

  def aRefs(name: String) = {
    (common ++ deps.get(name).toSeq.flatten).collect {
      case id: AndroidTrypId ⇒ id.aRefs
      case _ ⇒ List()
    }.flatten
  }
}

object Multidex
{
  def settings(main: Seq[String]) = Seq(
    dexMainClasses in Android := main ++ Seq(
      "android/support/multidex/BuildConfig.class",
      "android/support/multidex/MultiDex$V14.class",
      "android/support/multidex/MultiDex$V19.class",
      "android/support/multidex/MultiDex$V4.class",
      "android/support/multidex/MultiDex.class",
      "android/support/multidex/MultiDexApplication.class",
      "android/support/multidex/MultiDexExtractor$1.class",
      "android/support/multidex/MultiDexExtractor.class",
      "android/support/multidex/ZipUtil$CentralDirectory.class",
      "android/support/multidex/ZipUtil.class"
    ),
    dexMulti in Android := true,
    dexMinimizeMain in Android := false
  )

  def deps = Seq(
    libraryDependencies ++= Seq(
      aar("com.android.support" % "multidex" % "1.+")
    )
  )
}

case class AndroidParams(transitive: Boolean, target: String, aar: Boolean,
  settings: Setts)

class AndroidProjectBuilder(name: String, deps: AndroidDeps, prog: Proguard,
  placeholders: Placeholders, params: ProjectParams, aparams: AndroidParams)
extends ProjectBuilder[AndroidProjectBuilder](name, deps, params)
{
  import ProjectBuilder._

  def copy(newParams: ProjectParams) =
    new AndroidProjectBuilder(name, deps, prog, placeholders, newParams,
      aparams)

  def acopy(newParams: AndroidParams) =
    new AndroidProjectBuilder(name, deps, prog, placeholders, params,
      newParams)

  def asettings(extra: Setts) = {
    acopy(aparams.copy(settings = extra ++ aparams.settings))
  }

  def asettingsV(extra: Setting[_]*) = asettings(extra)

  def androidTest = {
    settings(Tests.settings)
  }

  def aar = {
    acopy(aparams.copy(aar = true))
  }

  def proguard = {
    asettings(prog.settings)
  }

  def transitiveSetting =
    transitiveAndroidLibs in Android := aparams.transitive

  def placeholderSetting =
    manifestPlaceholders in Android := placeholders(name)

  def platformSetting = platformTarget in Android := aparams.target

  def transitive = acopy(aparams.copy(transitive = true))

  def multidex(main: Seq[String] = List()) = {
    multidexDeps.multidexSettings(main)
  }

  def multidexDeps = {
    asettings(Multidex.deps)
  }

  def multidexSettings(main: Seq[String]) = {
    asettings(Multidex.settings(main))
  }

  def aproject(arefs: ProjectReference*) = {
    val refs = deps.aRefs(name)
    project
      .settings(placeholderSetting)
      .androidBuildWith(refs ++ arefs: _*)
      .transformIf(aparams.aar)(_.settings(Aar.settings: _*))
      .settings(aparams.settings: _*)
      .settings(transitiveSetting, platformSetting)
      .enablePlugins(TrypAndroid)
  }

  def androidDeps(projects: ProjectReference*) = {
    aproject(projects: _*)
  }

  def <<<(pros: ProjectReference*) = androidDeps(pros: _*)

  override def dep(pros: ClasspathDep[ProjectReference]*) = {
    aproject().dependsOn(pros: _*)
  }

  override def apply() = aproject()
}

object AndroidProjectBuilder
{
  def apply(name: String, deps: AndroidDeps, prog: Proguard,
  placeholders: Placeholders, defaults: Setts, adefaults: Setts,
  platform: String) =
    new AndroidProjectBuilder(name, deps, prog, placeholders,
      ProjectParams(defaults, name, false),
      AndroidParams(false, platform, false, adefaults)
    )
}
