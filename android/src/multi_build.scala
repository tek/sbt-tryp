package tryp

import sbt._
import sbt.Keys._
import android.Keys._
import android.protify.Keys.Protify

import TrypAndroidKeys._

object DefaultDeps extends AndroidDeps
object DefaultProguard extends Proguard

trait AndroidBuildBase
extends ExtMultiBuild
with ToProjectOps
with ToAndroidProjectOps
with AndroidProjectInstances
{
  override val deps: AndroidDeps = DefaultDeps

  val proguard: Proguard

  val platform = "android-21"

  override def settings = super.settings ++ Seq(
    appName := title getOrElse((appName ?? "app_name").value)
  )

  lazy val warningSetting = transitiveAndroidWarning := false

  lazy val layoutSetting = projectLayout :=
    new ProjectLayout.Wrapped(ProjectLayout.Ant(baseDirectory.value))
    {
      override def testSources = (sourceDirectory in Test).value
      override def testJavaSource = testSources
      override def testScalaSource = testSources
      override def bin = (target in Compile).value / "bin"
      override def gen = (target in Compile).value / "gen"
      override def manifest = manifestOutput.value
      override def testManifest = {
        TrypAndroidKeys.testManifest.?.value getOrElse(super.testManifest)
      }
    }

  lazy val typedResSetting = typedResources := false

  lazy val lintSetting = lintEnabled := false

  lazy val debugIncludesTestsSetting = debugIncludesTests := false

  lazy val proguardInDebugSetting = useProguardInDebug := false

  lazy val updateCheckSettings = Seq(
    updateCheck := (),
    updateCheck in Protify := ()
  )

  lazy val manifestDep =
    manifest <<= manifest dependsOn TemplatesKeys.templateResources

  lazy val manifestResourceFilter =
    (managedResources in Compile) := {
      (managedResources in Compile).value
        .filterNot { _.getName == manifestName }
    }

  def androidDefaults: List[Setting[_]] = List(
    warningSetting,
    layoutSetting,
    typedResSetting,
    lintSetting,
    debugIncludesTestsSetting,
    proguardInDebugSetting,
    manifestDep,
    manifestResourceFilter
  ) ++ updateCheckSettings

  def apb(name: String) =
    AndroidProject(name, deps, proguard, androidDefaults, platform)

  def adp(name: String) =
    apb(name).antSrc.paradise().settingsV(prefixedName).export

  def aar(name: String) = adp(name).aar
}

class AndroidBuild(
  t: String,
  override val deps: AndroidDeps = DefaultDeps,
  val proguard: Proguard = DefaultProguard
)
extends TrypBuild
with AndroidBuildBase
with ToAndroidProjectOps
with AndroidProjectInstances
{
  override val title = Some(t)

  def defaultBuilder = adp _

  implicit def stringToBuilder(name: String) =
    ToProjectOps(defaultBuilder(name))

  implicit def stringToAndroidBuilder(name: String) =
    ToAndroidProjectOps(defaultBuilder(name))
}

class AarsBuild(t: String, deps: AndroidDeps = DefaultDeps,
  proguard: Proguard = DefaultProguard)
extends AndroidBuild(t, deps, proguard)
{
  override def defaultBuilder = aar _
}
