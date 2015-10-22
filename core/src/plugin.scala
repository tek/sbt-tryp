package tryp

import sbt._
import Keys._

object TrypBuildKeys
{
  import Templates.autoImport.Tokens

  val paradiseJar = settingKey[Option[File]](
    "location of the macro paradise jar")
  val generateLogback = Def.settingKey[Boolean](
    "automatically generate logback.xml")
  val logbackTokens = Def.settingKey[Tokens](
    "additional replacement tokens for logback.xml")
  val logbackTemplate = Def.settingKey[File](
    "location of the template for logback.xml generation")
}
import TrypBuildKeys._

object TrypLogbackSettings
{
  def logbackTemplateData = Def.setting {
    val tokens = Map(
      "log_file_name" → name.value
    ) ++ logbackTokens.value
    logbackTemplate.value → (resourceManaged.value / "logback.xml") → tokens
  }

  val aarsDir = Def.setting(target.value / "aars")
}
import TrypLogbackSettings._

object Tryp
extends AutoPlugin
with Tryplug
{
  override def requires = plugins.JvmPlugin
  override def trigger = allRequirements

  import Templates.autoImport._

  val autoImport = TrypBuildKeys

  override def projectSettings = super.projectSettings ++ Seq(
    generateLogback := false,
    logbackTemplate := metaRes.value / "logback.xml",
    logbackTokens := Map(),
    templates ++= {
      if (generateLogback.value) Seq(logbackTemplateData.value)
      else Seq()
    },
    paradiseJar := {
      val name = s"paradise_${scalaVersion.value}"
      homeDir flatMap { home ⇒
        (home / ".ivy2" / "cache" / "org.scalamacros" / name / "jars" *
          s"$name*.jar").get.headOption
      }
    }
  )
}
