package tryp

import sbt._
import sbt.Keys._
import android.Keys._

object DefaultDeps extends AndroidDeps
object DefaultProguard extends Proguard
object DefaultPlaceholders extends Placeholders

abstract class AndroidBuild(
  deps: Deps = DefaultDeps,
  proguard: Proguard = DefaultProguard,
  placeholders: Placeholders = DefaultPlaceholders
)
extends MultiBuildBase(deps)
{
  val platform = "android-21"

  lazy val platformSetting = (platformTarget in Android := platform)

  lazy val warningSetting = (transitiveAndroidWarning in Android := false)

  override def globalSettings =
    platformSetting :: warningSetting :: super.globalSettings

  def pb(name: String) =
    new AndroidProjectBuilder(name, deps, proguard, placeholders,
      globalSettings: _*)
}
