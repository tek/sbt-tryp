package tryp

import scalaz._, Scalaz._
import scalaz.Show

import monocle.Lens
import monocle.macros.Lenses

import sbt._
import Keys._
import android.Keys._
import android.protify.Keys._
import android.protify.AndroidProtify
import Types._
import TrypAndroidKeys._

@Lenses
case class AndroidParams(transitive: Boolean, target: String, aar: Boolean,
  manifest: TemplateParams, multidex: Boolean,
  multidexMain: List[String] = List())

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
      AndroidParams(false, platform, false, TemplateParams(), false), prog
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

  def robotest = pro.settings(Tests.robotest)

  def integration = debug.settings(Tests.integration)

  def aar = AP.aar.!!

  def proguard = pro.settings(builder.prog(pro).settings)

  def transitiveSetting =
    transitiveAndroidLibs := aparams.transitive

  def platformSetting = platformTarget := aparams.target

  def transitive = AP.transitive.!!

  def multidex(main: String*) =
    (AP.multidexMain ++ main.toList >>> AP.multidex.!)(pro)

  def manifest(tokens: (String, String)*) = {
    (AP.manifest.tokens ++ tokens.toMap >>> AP.manifest.write.!)(pro)
  }

  def trypAndroidPlug = if (aparams.aar) TrypAndroidLib else TrypAndroidApp

  def enablePlugins(plug: sbt.AutoPlugin*) = pro map (_.enablePlugins(plug: _*))

  def disablePlugins(plug: sbt.AutoPlugin*) =
    pro map (_.disablePlugins(plug: _*))

  def protify = enablePlugins(AndroidProtify)

  def multidexWithDeps(main: List[String] = List()) = {
    builder.multidex(pro)(main)
  }

  def multidexDeps = {
    pro.settings(Multidex.deps)
  }

  def release = {
    proguard
      .settingsV(
        apkbuildDebug ~= { a ⇒ a(false); a },
        dexMainClasses ++= {
          val path = androidPackage.value.replace('.', '/')
          Seq(s"$path/Application.class", s"$path/MainActivity.class")
        }
      )
  }

  def debug = {
    pro.settingsV(apkbuildDebug ~= { a ⇒ a(true); a })
  }

  def aarModuleSetting = aarModule := pro.name.replace('-', '.')

  def reifyManifestSettings = {
    aparams.manifest.write ??
      List(generateManifest := true,
        manifestTokens ++= aparams.manifest.tokens)
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
      .settingsVPre(aarModuleSetting)
      .settings(reifyManifestSettings)
      .settings(reifyMultidexSettings)
      .settingsV(reifyPackagingOptions)
      .settings(pro.libraryDeps)
      .settingsV(transitiveSetting, platformSetting)
  }

  def androidProject = {
    val p = pro.basicProject
      .settings(pro.params.settings: _*)
      .enablePlugins(trypAndroidPlug)
    pro.params.trans.foldLeft(p)((a, b) => b(a))
  }
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

  def multidex(pro: A)(main: List[String]): A

  def aparamLens: monocle.Lens[A, AndroidParams]
}

class AndroidBuilder
extends AndroidProjectBuilder[AndroidProject]
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

  def multidex(pro: P)(main: List[String]) = {
    pro.multidexDeps.multidex(main: _*)
  }

  def show(pro: P) = {
    val PS = ProjectShow
    val info =
      PS.deps(~pro.adeps.deps.get(pro.name)) ++ PS.settings(pro) ++
        PS.refs(pro)
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
