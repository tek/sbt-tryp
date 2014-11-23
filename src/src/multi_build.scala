package tryp

import sbt._
import Keys._
import android.Keys._

abstract class MultiBuild(deps: Deps, proguard: Proguard) extends Build {
  val platform: String

  lazy val platformSetting = (platformTarget in Android := platform)
  lazy val notransitiveSetting = (transitiveAndroidLibs in Android := false)

  lazy val defaults = Seq(
    notransitiveSetting,
    platformSetting
    )

  def p(name: String) = new ProjectParameters(name, deps, proguard,
    platformSetting)
}
