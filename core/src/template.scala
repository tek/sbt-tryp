package tryp

import java.io.File

import sbt._
import Keys._

object TemplatesKeys
{
  type Tokens = Map[String, String]

  import TrypKeys.Tryp

  lazy val templates =
    settingKey[Seq[((File, File), Tokens)]]("template files") in Tryp

  lazy val keyFormatter =
    settingKey[String ⇒ String]("map keys to placeholders") in Tryp

  lazy val metaRes = Def.settingKey[File]("meta resource dir") in Tryp

  lazy val templateResources =
    taskKey[Seq[File]]("generate resources from templates") in Tryp
}

object Templates
extends AutoPlugin
{
  override def requires = plugins.JvmPlugin
  override def trigger = allRequirements

  val autoImport = TemplatesKeys
  import autoImport._

  def replaceTokens(content: String, values: Tokens,
    formatter: String ⇒ String) = {
      values.foldLeft(content) { case (text, (key, value)) ⇒
        text.replaceAllLiterally(formatter(key), value)
      }
  }

  def template(source: File, target: File, values: Tokens,
    formatter: String ⇒ String) = {
      IO.write(target, replaceTokens(IO.read(source), values, formatter))
      target
  }

  def templatesTask = Def.task {
    templates.value map {
      case ((source, target), values) ⇒
        streams.value.log.info(s"generating $target")
        template(source, target, values, keyFormatter.value)
    }
  }

  override lazy val projectSettings = Seq(
    templates := Seq(),
    keyFormatter := { (k: String) ⇒ s"$${$k}" },
    templateResources <<= templatesTask,
    resourceGenerators in Compile <+= templateResources,
    resourceGenerators in Test <+= templateResources,
    metaRes := (baseDirectory in ThisBuild).value / "meta" / "resources"
  )
}
