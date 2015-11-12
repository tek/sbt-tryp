package tryp

import scalaz._, Scalaz._
import scalaz.Show

import monocle.Lens
import monocle.macros.Lenses

import sbt._
import Keys._
import android.Keys._
import android.protify.Keys._
import Types._
import TrypAndroidKeys._

object Aar
{
  lazy val settings = android.Plugin.buildAar.toList ++ Export.settings
}

trait Proguard {
  lazy val settings = List(
    useProguard in Android := true,
    proguardScala in Android := true,
    proguardCache in Android ++= cache,
    proguardOptions in Android ++= options,
    proguardOptions ++= options
  )

  lazy val packaging = {
    packagingOptions := PackagingOptions(excludes, List(), merges)
  }

  lazy val cache: List[String] = List()

  lazy val options: List[String] = List()

  lazy val excludes: List[String] = List()

  lazy val merges: List[String] = List()
}

object Tests {
  def settings = List(
    testOptions in Test += sbt.Tests.Argument("-oF"),
    exportJars in Test := false,
    fork in Test := true,
    javaOptions in Test ++= List("-XX:+CMSClassUnloadingEnabled", "-noverify"),
    unmanagedClasspath in Test ++= (bootClasspath in Android).value,
    Keys.test in Test <<=
      Keys.test in Test dependsOn TrypAndroidKeys.symlinkLibs,
    testOnly in Test <<= testOnly in Test dependsOn TrypAndroidKeys.symlinkLibs
  )
}

object Multidex
{
  def settings(main: List[String]) = List(
    dexMainClasses in Android := main ++ List(
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

  def deps = List(
    libraryDependencies ++= List(
      aar("com.android.support" % "multidex" % "1.+")
    )
  )
}

@Lenses
case class AndroidParams(transitive: Boolean, target: String, aar: Boolean,
  multidex: Boolean, manifest: Boolean, multidexMain: List[String] = List(),
  manifestTokens: TemplatesKeys.Tokens = Map(),
  deps: List[ProjectReference] = Nil)

@Lenses
case class AndroidProject(basic: Project[AndroidDeps], aparams: AndroidParams,
  prog: Proguard)
extends ProjectI[AndroidProject]

object AndroidProject
extends AndroidProjectInstances
with ToAndroidProjectOps
{
  def apply(name: String, deps: AndroidDeps, prog: Proguard,
  defaults: Setts, platform: String): AndroidProject =
    AndroidProject(Project(name, deps, defaults),
      AndroidParams(false, platform, false, false, false), prog
    )
}

class AndroidProjectOps[A](val pro: A)
(implicit builder: AndroidProjectBuilder[A])
extends ToProjectOps
with ToTransformIf
with ParamLensSyntax[AndroidParams, A]
{
  def aparams = builder.aparams(pro)

  def paramLens = builder.aparamLens

  val AP = AndroidParams

  def adeps = builder.adeps(pro)

  def androidTest = {
    pro.settings(Tests.settings)
  }

  def aar = AP.aar.!!

  def proguard = {
    pro.settings(builder.prog(pro).settings)
  }

  def transitiveSetting =
    transitiveAndroidLibs in Android := aparams.transitive

  def platformSetting = platformTarget in Android := aparams.target

  def transitive = AP.transitive.!!

  def multidex(main: String*) =
    (AP.multidexMain ++ main.toList >>> AP.multidex.!)(pro)

  def manifest(tokens: (String, String)*) = {
    (AP.manifestTokens ++ tokens.toMap >>> AP.manifest.!)(pro)
  }

  def protify = pro.settings(protifySettings)

  def multidexWithDeps(main: List[String] = List()) = {
    builder.multidex(pro)(main)
  }

  def multidexDeps = {
    pro.settings(Multidex.deps)
  }

  def apk(pkg: String) =
    builder.apk(pro)(pkg)

  def release = {
    pro.settingsV(apkbuildDebug ~= { a ⇒ a(false); a })
  }

  def arefs = adeps.aRefs(pro.name) ++ aparams.deps

  def androidDeps(projects: ProjectReference*) = {
    AP.deps ++! projects.toList
  }

  def reifyManifestSettings = {
    aparams.manifest ??
      List(generateManifest := true, manifestTokens ++= aparams.manifestTokens)
  }

  val multidexRunnerSetting = {
    instrumentTestRunner := "com.android.test.runner.MultiDexTestRunner"
  }

  def reifyMultidexSettings = {
    aparams.multidex ?? Multidex.settings(aparams.multidexMain)
  }

  def reifyPackagingOptions = {
    builder.prog(pro).packaging
  }

  // turns abstracted settings into sbt.Setting instances
  // although this returns a builder, it doesn't commute
  def reifyAccSettings = {
    pro
      .transformIf(aparams.aar)(_.settings(Aar.settings))
      .settings(reifyManifestSettings)
      .settings(reifyMultidexSettings)
      .settingsV(reifyPackagingOptions)
      .settings(pro.libraryDeps)
      .settingsV(transitiveSetting, platformSetting)
  }

  def androidProject = {
    pro.basicProject
      .androidBuildWith(arefs: _*)
      .settings(pro.params.settings: _*)
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

  def apk(pro: A)(pkg: String): A

  def multidex(pro: A)(main: List[String]): A

  def aparamLens: monocle.Lens[A, AndroidParams]
}

object AndroidProjectShow
extends ToAndroidProjectOps
{
  import ProjectShow._

  def arefs[A: AndroidProjectBuilder](pro: A) = {
    val r = pro.arefs map(_.toString)
    if (r.isEmpty) Nil
    else "android refs:" :: shift(r)
  }
}

class AndroidBuilder
extends AndroidProjectBuilder[AndroidProject]
with ToProjectOps
with ToAndroidProjectOps
with ToTransformIf
{
  type P = AndroidProject

  def project(pro: P) = {
    pro.reifyAccSettings.androidProject
  }

  def deps(pro: P) = pro.basic.deps

  def params(pro: P) = pro.basic.params

  def aparams(pro: P) = pro.aparams

  def prog(pro: P) = pro.prog

  def adeps(pro: P) = pro.basic.deps

  def refs(pro: P) = deps(pro).refs(pro.name) ++ pro.params.deps

  def apk(pro: P)(pkg: String) = {
    val path = pkg.replace('.', '/')
    pro
      .proguard
      .multidex(s"$path/Application.class", s"$path/MainActivity.class")
  }

  def multidex(pro: P)(main: List[String]) = {
    pro.multidexDeps.multidex(main: _*)
  }

  def show(pro: P) = {
    val PS = ProjectShow
    val info =
      PS.deps(~pro.adeps.deps.get(pro.name)) ++ PS.settings(pro) ++
        PS.refs(pro) ++ AndroidProjectShow.arefs(pro)
    s" ○ android project ${pro.name}" :: PS.shift(info)
  }

  def paramLens = AndroidProject.basic ^|-> Project.params

  def aparamLens = AndroidProject.aparams
}

trait AndroidProjectInstances
extends ToProjectOps
{
  implicit val androidProjectBuilder: AndroidProjectBuilder[AndroidProject] =
    new AndroidBuilder
}
