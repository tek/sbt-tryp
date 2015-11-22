package tryp

import sbt._
import sbt.Keys._
import android.Keys._

import TrypAndroidKeys._

object DefaultDeps extends AndroidDeps
object DefaultProguard extends Proguard

abstract class StringToBuilder[A: AndroidProjectBuilder]
{
  def create(name: String): A
}

trait AndroidBuildBase
extends ExtMultiBuild
with ToAndroidProjectOps
with AndroidProjectInstances
{ build ⇒
  override val deps: AndroidDeps = DefaultDeps

  val proguard: Proguard

  val platform = "android-21"

  override def settings = super.settings ++ Seq(
    appName := title getOrElse(appName.value)
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

  lazy val updateCheckSetting = updateCheck := {}

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
    updateCheckSetting,
    manifestDep,
    manifestResourceFilter
  )

  def apb(name: String) =
    AndroidProject(name, deps, proguard, androidDefaults, platform)

  def adp(name: String) =
    apb(name).antSrc.paradise().settingsV(prefixedName).export

  def aar(name: String) = adp(name).aar

  object DefaultBuilder
  {
    lazy val aar = new StringToBuilder {
      def create(name: String) = build.aar(name)
    }

    lazy val basic = new StringToBuilder {
      def create(name: String) = build.adp(name)
    }
  }

  def defaultBuilder: StringToBuilder[AndroidProject] = DefaultBuilder.basic

  implicit def stringToBuilder(name: String) =
    ToProjectOps(defaultBuilder.create(name))

  implicit def stringToAndroidBuilder(name: String) =
    ToAndroidProjectOps(defaultBuilder.create(name))
}

abstract class AndroidBuild(
  override val deps: AndroidDeps = DefaultDeps,
  val proguard: Proguard = DefaultProguard
)
extends TrypBuild
with AndroidBuildBase
with ToAndroidProjectOps
with AndroidProjectInstances

class AarsBuild(t: String, deps: AndroidDeps = DefaultDeps,
  proguard: Proguard = DefaultProguard)
extends AndroidBuild(deps, proguard)
{
  override val title = Some(t)
  override def defaultBuilder = DefaultBuilder.aar
}
