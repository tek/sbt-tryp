package tryp

import sbt.Setting
import android.Keys._

object DefaultDeps extends Deps
object DefaultProguard extends Proguard
object DefaultPlaceholders extends Placeholders

class MultiBuild(
  deps: Deps, proguard: Proguard, placeholders: Placeholders
) extends sbt.Build
{
  def globalSettings: List[Setting[_]] = Nil

  def p(name: String) =
    new ProjectBuilder(name, deps, proguard, placeholders, globalSettings: _*)
}

abstract class AndroidBuild(
  deps: Deps = DefaultDeps,
  proguard: Proguard = DefaultProguard,
  placeholders: Placeholders = DefaultPlaceholders
)
extends MultiBuild(deps, proguard, placeholders)
{
  val platform: String

  lazy val platformSetting = (platformTarget in Android := platform)

  lazy val warningSetting = (transitiveAndroidWarning in Android := false)

  override def globalSettings =
    platformSetting :: warningSetting :: super.globalSettings
}

class Build(
  deps: Deps = DefaultDeps,
  proguard: Proguard = DefaultProguard,
  placeholders: Placeholders = DefaultPlaceholders
)
extends MultiBuild(deps, proguard, placeholders)
