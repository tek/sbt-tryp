package tryp

import java.io.File

import sbt._
import Keys._
import android._
import android.Keys._

object TrypAndroidKeys
{
  import Templates.autoImport.Tokens

  val metaRes = Def.settingKey[File]("meta resource dir")
  val manifestTokens = Def.settingKey[Tokens](
    "additional replacement tokens for the manifest")
  val generateManifest = Def.settingKey[Boolean](
    "automatically generate AndroidManifest.xml")
  val manifestTemplate = Def.settingKey[File](
    "location of the template for AndroidManifest.xml generation")
}

object TrypAndroid
extends AutoPlugin
{
  override def requires = Templates && AndroidPlugin

  import Templates.autoImport._

  val autoImport = TrypAndroidKeys
  import autoImport._

  def manifestTemplateData = Def.setting {
    val tokens = Map(
      "version_code" → version.value
    ) ++ manifestTokens.value
    manifestTemplate.value → (projectLayout in Android).value.manifest → tokens
  }

  override lazy val projectSettings = super.projectSettings ++ Seq(
    generateManifest := false,
    metaRes := (baseDirectory in ThisBuild).value / "meta" / "resources",
    manifestTemplate := metaRes.value / "AndroidManifest.xml",
    manifestTokens := Map(),
    templates ++= {
      if (generateManifest.value) Seq(manifestTemplateData.value)
      else Seq()
    }
  )
}
