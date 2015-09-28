package tryp

import java.io.File

import sbt._
import Keys._

object TemplatesKeys
{
  type Tokens = Map[String, String]

  lazy val templates =
    settingKey[Seq[((File, File), Tokens)]]("template files")
  lazy val keyFormatter =
    settingKey[String ⇒ String]("map keys to placeholders")
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
      case ((source, target), values) ⇒ template(source, target, values,
        keyFormatter.value)
    }
  }

  override lazy val projectSettings = Seq(
    templates := Seq(),
    keyFormatter := { (k: String) ⇒ s"$${$k}" },
    resourceGenerators in Compile <+= templatesTask
  )
}
