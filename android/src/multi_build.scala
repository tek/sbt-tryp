package tryp

import sbt._
import sbt.Keys._
import android.Keys._

object DefaultDeps extends AndroidDeps
object DefaultProguard extends Proguard
object DefaultPlaceholders extends Placeholders

abstract class AndroidBuild(
  deps: AndroidDeps = DefaultDeps,
  proguard: Proguard = DefaultProguard,
  placeholders: Placeholders = DefaultPlaceholders
)
extends MultiBuildBase[AndroidProjectBuilder](deps)
{
  val platform = "android-21"

  lazy val warningSetting = transitiveAndroidWarning in Android := false

  lazy val layoutSetting = projectLayout in Android :=
    new ProjectLayout.Wrapped(ProjectLayout.Ant(baseDirectory.value))
    {
      override def testSources = (sourceDirectory in Test).value
      override def testJavaSource = testSources
      override def testScalaSource = testSources
      override def bin = (target in Compile).value / "bin"
      override def gen = (target in Compile).value / "gen"
    }

  def adefaults: List[Setting[_]] = warningSetting :: layoutSetting :: Nil

  def apb(name: String) =
    AndroidProjectBuilder(name, deps, proguard, placeholders, Nil,
      adefaults, platform)

  def adp(name: String) =
    apb(name).antSrc.paradise().settingsV(namePrefix).export

  def aar(name: String) = adp(name).aar

  override lazy val macroConsole = macroConsoleBuilder
    .settingsV(
      updateCheck := {},
      antLayoutDetector := {}
    )()
}
