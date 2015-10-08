package tryp

import java.io.File
import java.nio.file.Files

import sbt._
import Keys._
import android._
import android.Keys._
import android.Dependencies.ProjectRefOps
import android.Intrusion._

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
  val symlinkLibs = taskKey[Seq[File]]("symlink libs for robolectric")
}
import TrypAndroidKeys._

object TrypAndroidSettings
{
  def manifestTemplateData = Def.setting {
    val tokens = Map(
      "version_code" → version.value
    ) ++ manifestTokens.value
    manifestTemplate.value → (projectLayout in Android).value.manifest → tokens
  }

  val aarsDir = Def.setting(target.value / "aars")
}
import TrypAndroidSettings._

object TrypAndroidTasks
{
  val symlinkLibsTask = Def.task {
    implicit val struct = buildStructure.value
    val dir = aarsDir.value
    val projects = thisProjectRef.value
      .deepDeps
      .map(r ⇒ Project.getProject(r, struct))
      .flatten
      .map(pro ⇒ (pro.base, pro.id))
    val targets = (aars.value.map(a ⇒ (a.path, a.path.getName)) ++ projects)
      .filter { case (d, n) ⇒ (d / "res").exists }
      .distinct
    IO.delete(dir)
    IO.createDirectory(dir)
    targets.map { case (path, name) ⇒
      val link = dir / name
      Files.createSymbolicLink(link.toPath, path.toPath)
      link
    }
  }
}
import TrypAndroidTasks._

object TrypAndroid
extends AutoPlugin
{
  override def requires = Templates && AndroidPlugin

  import Templates.autoImport._

  val autoImport = TrypAndroidKeys

  override lazy val projectSettings = super.projectSettings ++ Seq(
    generateManifest := false,
    metaRes := (baseDirectory in ThisBuild).value / "meta" / "resources",
    manifestTemplate := metaRes.value / "AndroidManifest.xml",
    manifestTokens := Map(),
    templates ++= {
      if (generateManifest.value) Seq(manifestTemplateData.value)
      else Seq()
    },
    symlinkLibs <<= symlinkLibsTask
  )
}
