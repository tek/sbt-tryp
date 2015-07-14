package tryp

import reflect.macros.Context

import sbt._
import Keys._
import android.Keys._
import Types._

object Aar
{
  lazy val settings = android.Plugin.androidBuildAar ++ Export.settings
}

trait Proguard {
  lazy val settings = Seq(
    useProguard in Android := true,
    proguardScala in Android := true,
    proguardCache in Android ++= cache,
    proguardOptions in Android ++= options,
    apkbuildExcludes in Android ++= excludes
  )

  lazy val cache: Seq[String] = Seq()

  lazy val options: Seq[String] = Seq()

  lazy val excludes: Seq[String] = Seq()
}

trait Placeholders {
  lazy val defaults: Map[String, String] = Map()

  def apply(name: String) = {
    defaults ++ specific.get(name).getOrElse(Map[String, String]())
  }

  lazy val specific: Map[String, Map[String, String]] = Map()
}

object Tests {
  def settings(dep: Project) = Seq(
    testOptions in Test += sbt.Tests.Argument("-oF"),
    exportJars in Test := false,
    fork in Test := true,
    javaOptions in Test ++= Seq(
      "-XX:MaxPermSize=2048M", "-XX:+CMSClassUnloadingEnabled", "-noverify"
    ),
    unmanagedClasspath in Test ++= (bootClasspath in Android).value
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
        libraryDependencies += aar($id), $path, Seq(..$sub), true
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
    "com.geteit" %% "robotest" % "0.+"
  )

  override def integration = super.integration ++ ids(
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
    dexMainFileClasses in Android := main ++ Seq(
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
    dexMinimizeMainFile in Android := false
  )

  def deps = Seq(
    libraryDependencies ++= Seq(
      aar("com.android.support" % "multidex" % "1.+")
    )
  )
}

class AndroidProjectBuilder(name: String, deps: Deps, prog: Proguard,
  placeholders: Placeholders, defaultSettings: Setting[_]*)
extends ProjectBuilder[AndroidProjectBuilder](name, deps, defaultSettings: _*)
{
  var pTransitive = false

  settings(Seq(transitiveSetting, placeholderSetting))

  def test(deps: Project*) = {
    settings(Tests.settings(deps.head))
    androidDeps(deps: _*)
  }

  def testOnly(deps: Project*) = {
    settings(Seq(scalaSource in Test := baseDirectory.value / "src"))
    test(deps: _*)
  }

  def aar = {
    Aar.settings ++=: pSettings
    this
  }

  def proguard = {
    prog.settings ++=: pSettings
    this
  }

  def transitiveSetting = transitiveAndroidLibs in Android := pTransitive

  def placeholderSetting =
    manifestPlaceholders in Android := placeholders(name)

  def transitive = {
    pTransitive = true
    this
  }

  def multidex(main: Seq[String] = List()) = {
    multidexDeps
    multidexSettings(main)
    this
  }

  def multidexDeps = {
    pSettings ++= Multidex.deps
    this
  }

  def multidexSettings(main: Seq[String]) = {
    pSettings ++= Multidex.settings(main)
    this
  }

  def rootDepSettings(pro: ProjectReference) = {
    Seq(
      collectResources in Android <<= collectResources in Android dependsOn (
        compile in Compile in pro),
      compile in Compile <<= compile in Compile dependsOn(
        sbt.Keys.`package` in Compile in pro),
      localProjects in Android += android.Dependencies.LibraryProject(
        (baseDirectory in pro).value)
    )
  }

  override def rootDeps(projects: ProjectReference*) = {
    projects foreach { p ⇒ pSettings ++= rootDepSettings(p) }
    super.rootDeps(projects: _*)
  }

  override def project(callback: (Project) ⇒ Project = identity) = {
    val refs = deps.aRefs(name)
    super.project(callback)
      .settings(refs flatMap(rootDepSettings): _*)
  }

  def androidDeps(projects: Project*) = {
    project { _.androidBuildWith(projects: _*) }
  }
}
