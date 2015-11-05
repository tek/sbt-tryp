package tryp

import reflect.macros.Context

import sbt._
import Keys._
import android.Keys._
import android.protify.Keys._
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

class AndroidTrypId(id: ModuleID, depspec: DepSpec, path: String,
  sub: Seq[String], dev: Boolean)
extends TrypId(id, depspec, path, sub, dev)
{
  def aRefs = {
    if (development) super.projects
    else Nil
  }

  override def refs = Nil

  override def info = {
    s"aar ${super.info}"
  }
}

object AndroidDeps
{
  def adImpl(c: Context)(id: c.Expr[ModuleID], path: c.Expr[String],
    sub: c.Expr[String]*) =
  {
    import c.universe._
    c.Expr[AndroidTrypId] {
      q"""new tryp.AndroidTrypId(
        $id, libraryDependencies += android.Keys.aar($id), $path, Seq(..$sub),
        true
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
    (common ++ deps.fetch(name)).flatMap {
      case id: AndroidTrypId ⇒ id.aRefs
      case _ ⇒ List()
    }
  }

  import ModuleID._

  override implicit def moduleIDtoTrypId(id: ModuleID) = {
    if (id.isAar)
      new AndroidTrypId(id, libraryDependencies += id, "", List(), false)
    else super.moduleIDtoTrypId(id)
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
  deps: List[ProjectReference] = Nil)

case class AndroidProject(basic: Project[AndroidDeps], aparams: AndroidParams,
  prog: Proguard)
extends ProjectI[AndroidProject]

object AndroidProject
extends AndroidProjectInstances
{
  def apply(name: String, deps: AndroidDeps, prog: Proguard,
  defaults: Setts, platform: String): AndroidProject =
    AndroidProject(Project(name, deps, defaults),
      AndroidParams(false, platform, false), prog
    )
}

class AndroidProjectOps[A](pro: A)
(implicit builder: AndroidProjectBuilder[A])
extends ToProjectOps
with ToTransformIf
{
  def aparams = builder.aparams(pro)

  def adeps = builder.adeps(pro)

  def withAndroidParams(newParams: AndroidParams) = {
    builder.withAndroidParams(pro)(newParams)
  }

  def androidTest = {
    pro.settings(Tests.settings)
  }

  def aar = {
    withAndroidParams(aparams.copy(aar = true))
  }

  def proguard = {
    pro.settings(builder.prog(pro).settings)
  }

  def transitiveSetting =
    transitiveAndroidLibs in Android := aparams.transitive

  def platformSetting = platformTarget in Android := aparams.target

  def transitive = withAndroidParams(aparams.copy(transitive = true))

  def protify = pro.settings(protifySettings)

  def multidex(main: Seq[String] = List()) = {
    builder.multidex(pro)(main)
  }

  def multidexDeps = {
    pro.settings(Multidex.deps)
  }

  def multidexSettings(main: Seq[String]) = {
    pro.settings(Multidex.settings(main))
  }

  def apk(pkg: String, mainDex: List[String] = Nil) =
    builder.apk(pro)(pkg, mainDex)

  def release = {
    pro.settings(apkbuildDebug ~= { a ⇒ a(false); a })
  }

  def arefs = adeps.aRefs(pro.name) ++ aparams.deps

  def androidDeps(projects: ProjectReference*) = {
    withAndroidParams(aparams.copy(deps = aparams.deps ++ projects))
  }

  def androidProject = {
    pro.basicProject
      .androidBuildWith(arefs: _*)
      .transformIf(aparams.aar)(_.settings(Aar.settings: _*))
      .settings(pro.libraryDeps ++ pro.params.settings: _*)
      .settings(transitiveSetting, platformSetting)
      .enablePlugins(TrypAndroid)
  }

  def <<<[B](pro: B)(implicit ts: ToSbt[B]) = androidDeps(ts.reify(pro))

  def <<<![B](pro: B)(implicit ts: ToSbt[B]) = builder.project(<<<(pro))
}

trait ToAndroidProjectOps
{
  implicit def ToAndroidProjectOps[A: AndroidProjectBuilder](pro: A) =
    new AndroidProjectOps(pro)
}

trait AndroidProjectBuilder[A]
extends ProjectBuilder[A]
{
  def aparams(pro: A): AndroidParams

  def prog(pro: A): Proguard

  def adeps(pro: A): AndroidDeps

  def withAndroidParams(pro: A)(newParams: AndroidParams): A

  def apk(pro: A)(pkg: String, mainDex: List[String]): A

  def multidex(pro: A)(main: Seq[String]): A
}

class AndroidBuilder
extends AndroidProjectBuilder[AndroidProject]
with ToProjectOps
with ToAndroidProjectOps
with ToTransformIf
{
  type P = AndroidProject

  def withParams(pro: P)(newParams: Params) =
    pro.copy(pro.basic.copy(params = newParams))

  def project(pro: P) = pro.androidProject

  def deps(pro: P) = pro.basic.deps

  def params(pro: P) = pro.basic.params

  def aparams(pro: P) = pro.aparams

  def prog(pro: P) = pro.prog

  def adeps(pro: P) = pro.basic.deps

  def refs(pro: P) = deps(pro).refs(pro.name) ++ pro.params.deps

  def withAndroidParams(pro: P)(par: AndroidParams) = pro.copy(aparams = par)

  def apk(pro: P)(pkg: String, mainDex: List[String]) = {
    val path = pkg.replace('.', '/')
    pro
      .proguard
      .multidexSettings(
        s"$path/Application.class" :: s"$path/MainActivity.class" :: mainDex)
      .settingsV(dexShards := true)
  }

  def multidex(pro: P)(main: Seq[String]) = {
    pro.multidexDeps.multidexSettings(main)
  }

  def info(pro: P) = {
    val deps = adeps(pro).deps.get(pro.name) map { d ⇒
      "   deps: " :: d.map("     " + _.info).toList
    } getOrElse(Nil)
    List(
      s" ○ android project ${pro.name}"
    ) ++ deps
  }
}

trait AndroidProjectInstances
{
  implicit val androidProjectBuilder: AndroidProjectBuilder[AndroidProject] =
    new AndroidBuilder
}
