package tryp

import sbt._
import Keys._

object TrypBuildKeys
{
  val paradiseJar = settingKey[Option[File]](
    "location of the macro paradise jar")
}

object TrypBuildPlugin
extends AutoPlugin
with Tryplug
{
  override def requires = plugins.JvmPlugin
  override def trigger = allRequirements

  val autoImport = TrypBuildKeys
  import autoImport._

  override def globalSettings = super.globalSettings ++ Seq(
    paradiseJar := {
      val name = s"paradise_${scalaVersion.value}"
      homeDir flatMap { home ⇒
        (home / ".ivy2" / "cache" / "org.scalamacros" / name / "jars" *
          s"$name*.jar").get.headOption
      }
    }
  )
}
