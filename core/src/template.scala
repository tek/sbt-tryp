package tryp

import java.io.File

import sbt._
import Keys._

object TemplatesKeys
{
  type Tokens = Map[String, String]
  type Template = ((File, File), Tokens)

  import TrypKeys.Tryp

  lazy val templates =
    settingKey[Seq[Template]]("template files") in Tryp

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

  val cacheName = "templates"

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

  def cacher(cacheDir: File) =
    FileFunction.cached(cacheDir)(FilesInfo.lastModified, FilesInfo.exists) _

  def templatesTask = Def.task {
    val cacheDir = streams.value.cacheDirectory / cacheName
    val bySource = templates.value
      .map(_._1._1)
      .zip(templates.value)
      .toMap
    val byOutput = templates.value
      .map(_._1._2)
      .zip(templates.value)
      .toMap
    def generate(tpl: Template) = {
      val ((source, target), values) = tpl
      streams.value.log.info(s"generating $target")
      template(source, target, values, keyFormatter.value)
    }
    val update: (ChangeReport[File], ChangeReport[File]) ⇒ Set[File] =
      (src, out) ⇒ {
        val candidates = src.modified.flatMap(bySource.get) ++
          out.modified.flatMap(byOutput.get)
        candidates map(generate)

    }
    cacher(cacheDir)(update)(bySource.keys.toSet).toSeq
  }

  override lazy val projectSettings = Seq(
    templates := Seq(),
    keyFormatter := { (k: String) ⇒ s"$${$k}" },
    templateResources := templatesTask.value,
    (resourceGenerators in Compile) += templateResources.taskValue,
    metaRes := (baseDirectory in ThisBuild).value / "meta" / "resources"
  )
}
