package tryp

import sbt._
import Keys._

object TrypBuildKeys
{
  import Templates.autoImport.Tokens

  import TrypKeys.{Tryp => TrypC}

  val paradiseJar = settingKey[Option[File]](
    "location of the macro paradise jar") in TrypC
  val generateLogback = Def.settingKey[Boolean](
    s"automatically generate $logbackName") in TrypC
  val logbackTokens = Def.settingKey[Tokens](
    s"additional replacement tokens for $logbackName") in TrypC
  val logbackTemplate = Def.settingKey[File](
    s"location of the template for $logbackName generation") in TrypC
  val logbackOutput = Def.settingKey[File](
    s"location of the generated $logbackName") in TrypC
  val tryplugVersion = settingKey[String]("tryplug version") in TrypC
}
import TrypBuildKeys._

object TrypLogbackSettings
{
  def logbackTemplateData = Def.setting {
    val tokens = Map(
      "log_file_name" -> name.value,
      "tag" -> name.value
    ) ++ logbackTokens.value
    logbackTemplate.value -> logbackOutput.value -> tokens
  }

  val aarsDir = Def.setting(target.value / "aars")
}
import TrypLogbackSettings._

object TrypKeysPlugin
extends AutoPlugin
with Tryplug
{
  override def requires = Templates
  override def trigger = allRequirements

  val autoImport = TrypBuildKeys
}

object Tryp
extends AutoPlugin
with Tryplug
{
  override def requires = Templates
  override def trigger = allRequirements

  import Templates.autoImport._
  import TrypBuildKeys._

  override def projectSettings =
    super.projectSettings ++ commonBasicSettings ++ Seq(
      generateLogback := false,
      logbackTemplate := metaRes.value / logbackName,
      logbackOutput := resourceManaged.value / logbackName,
      logbackTokens := Map(),
      templates ++= {
        if (generateLogback.value) Seq(logbackTemplateData.value)
        else Seq()
      },
      paradiseJar := {
        val name = s"paradise_${scalaVersion.value}"
        homeDir flatMap { home =>
          (home / ".ivy2" / "cache" / "org.scalamacros" / name / "jars" *
            s"$name*.jar").get.headOption
        }
      },
      addCompilerPlugin("org.spire-math" %% "kind-projector" % "0.9.3")
    )
}

trait TrypBuildBuild
extends Tryplug
{
  def mkTrypProjectBuild =
    projectBuild
      .settings(
        VersionUpdateKeys.updateAllPlugins := true,
        TrypKeys.trypVersion := (TrypKeys.trypVersion ?? "83").value
      )

  trait TrypBuildBuildDeps
  extends PluginDeps
  {
    override def deps = super.deps ++ Map(
      projectBuildName -> projectBuildDeps
    )
  }

  override lazy val deps = new TrypBuildBuildDeps {}

  def projectBuildDeps = deps.ids(deps.trypBuild)

  lazy val `project` = mkTrypProjectBuild
}

object TrypBuildPlugin
extends AutoPlugin
with TrypBuildBuild
{
  object autoImport
  {
    def trypVersion = TrypKeys.trypVersion
    def trypProjectBuild = mkTrypProjectBuild
  }
}
