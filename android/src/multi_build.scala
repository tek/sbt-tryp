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

  lazy val platformSetting = platformTarget in Android := platform

  lazy val warningSetting = transitiveAndroidWarning in Android := false

  lazy val layoutSetting = projectLayout in Android :=
    new ProjectLayout.Wrapped(ProjectLayout.Ant(baseDirectory.value))
  {
    override def testSources = (sourceDirectory in Test).value
    override def testJavaSource = testSources
    override def testScalaSource = testSources
  }

  override def globalSettings =
    platformSetting :: warningSetting :: layoutSetting :: super.globalSettings

  def pb(name: String) =
    AndroidProjectBuilder(name, deps, proguard, placeholders, globalSettings)

  override def tdp(name: String) = super.tdp(name).export
}
