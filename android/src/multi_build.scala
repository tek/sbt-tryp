package tryp

import sbt._
import sbt.Keys._
import android.Keys._

object DefaultDeps extends AndroidDeps
object DefaultProguard extends Proguard

trait AndroidBuildBase
extends ExtMultiBuild
with ToAndroidProjectOps
with AndroidProjectInstances
{
  override val deps: AndroidDeps = DefaultDeps

  val proguard: Proguard

  val platform = "android-21"

  lazy val warningSetting = transitiveAndroidWarning := false

  lazy val layoutSetting = projectLayout :=
    new ProjectLayout.Wrapped(ProjectLayout.Ant(baseDirectory.value))
    {
      override def testSources = (sourceDirectory in Test).value
      override def testJavaSource = testSources
      override def testScalaSource = testSources
      override def bin = (target in Compile).value / "bin"
      override def gen = (target in Compile).value / "gen"
    }

  lazy val typedResSetting = typedResources := false

  lazy val lintSetting = lintEnabled := false

  lazy val debugIncludesTestsSetting = debugIncludesTests := false

  lazy val proguardInDebugSetting = useProguardInDebug := false

  lazy val updateCheckSetting = updateCheck := {}

  def androidDefaults: List[Setting[_]] = List(
    warningSetting,
    layoutSetting,
    typedResSetting,
    lintSetting,
    debugIncludesTestsSetting,
    proguardInDebugSetting,
    updateCheckSetting
  )

  def apb(name: String) =
    AndroidProject(name, deps, proguard, androidDefaults, platform)

  def adp(name: String) =
    apb(name).antSrc.paradise().settingsV(namePrefix).export

  def aar(name: String) = adp(name).aar
}

abstract class AndroidBuild(
  override val deps: AndroidDeps = DefaultDeps,
  val proguard: Proguard = DefaultProguard
)
extends TrypBuild
with AndroidBuildBase
with ToAndroidProjectOps
with AndroidProjectInstances
