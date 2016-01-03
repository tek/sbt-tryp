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

  import TrypKeys.{Tryp ⇒ TrypC}

  val manifestTokens = Def.settingKey[Tokens](
    "additional replacement tokens for the manifest") in TrypC
  val generateManifest = Def.settingKey[Boolean](
    "automatically generate AndroidManifest.xml") in TrypC
  val manifestTemplate = Def.settingKey[File](
    "location of the template for AndroidManifest.xml generation") in TrypC
  val manifestOutput = Def.settingKey[File](
    "location of the generated AndroidManifest.xml") in TrypC
  val testManifest = Def.settingKey[File](
    "location of the AndroidManifest.xml for tests") in TrypC
  val symlinkLibs = taskKey[Seq[File]]("symlink libs for robolectric") in TrypC
  val androidPackage = Def.settingKey[String]("package for manifest") in TrypC
  val aarModule = Def.settingKey[String]("aar subpackage") in TrypC
  val appName = Def.settingKey[String]("build-wide app name") in TrypC
}
import TrypAndroidKeys._

object TrypAndroidSettings
{
  def manifestTemplateData = Def.setting {
    val tokens = Map(
      "version_code" → version.value
    ) ++ manifestTokens.value
    manifestTemplate.value → projectLayout.value.manifest → tokens
  }

  val aarsDir = Def.setting(target.value / "aars")
}
import TrypAndroidSettings._

object TrypAndroidTasks
{
  // workaround for robolectric and robotest
  // robolectric can include resources of deps only if their paths are added
  // to project.properties or manually during execution.
  // robotest has a machanism for that, but it just reads the aars dir of the
  // test project, which doesn't respect sbt deps and transitive aars.
  // this task symlinks any aar and sbt dependency in the whole dep graph
  // to the aars dir.
  // as manifest paths can vary, not just the bas dir gets linked, but the res
  // and manifest paths individually.
  val symlinkLibsTask = Def.task {
    implicit val struct = buildStructure.value
    val dir = aarsDir.value
    val sbtDeps = thisProjectRef.value
      .deepDeps
      .flatMap(r ⇒ sbt.Project.getProject(r, struct))
      .map(pro ⇒ (pro.base, s"${pro.base.getParentFile.getName}-${pro.id}"))
    val targets = (aars.value.map(a ⇒ (a.path, a.path.getName)) ++ sbtDeps)
      .filter { case (d, n) ⇒ (d / "res").exists }
      .distinct
    IO.delete(dir)
    IO.createDirectory(dir)
    targets.map { case (path, name) ⇒
      val aarDir = dir / name
      val maniDir =
        if ((path / "target" / manifestName).exists) path / "target"
        else path
      IO.createDirectory(aarDir)
      IO.createDirectory(maniDir)
      Files.createSymbolicLink((aarDir / "res").toPath, (path / "res").toPath)
      Files.createSymbolicLink((aarDir / manifestName).toPath,
        (maniDir / manifestName).toPath)
      aarDir
    }
  }
}
import TrypAndroidTasks._

object TrypAndroid
extends AutoPlugin
{
  override def requires = Templates && AndroidPlugin && Tryp

  import Templates.autoImport._
  import TrypBuildKeys._

  val autoImport = TrypAndroidKeys

  override lazy val projectSettings = super.projectSettings ++ Seq(
    aarModule := name.value,
    androidPackage :=
      s"${organization.value}.${appName.value}.${aarModule.value}",
    manifestOutput := {
      val dir = if (generateManifest.value) target.value
      else baseDirectory.value
      dir / manifestName
    },
    generateManifest := false,
    manifestTemplate := metaRes.value / manifestName,
    manifestTokens := Map(),
    templates ++= {
      if (generateManifest.value) Seq(manifestTemplateData.value)
      else Seq()
    },
    logbackOutput := projectLayout.value.assets / logbackName,
    symlinkLibs <<= symlinkLibsTask,
    scalacOptions += "-target:jvm-1.7",
    javacOptions in Compile ++= Seq("-source", "1.7", "-target", "1.7")
  )
}

object TrypAndroidBuildPlugin
extends TrypPlugin
{
  object autoImport
  extends AutoImport
  {
    def trypAndroidProjectBuild = mkTrypProjectBuild
  }

  override object deps
  extends PluginDeps
  {
    override def deps = super.deps ++ Map(
      projectBuildName → projectBuildDeps
    )

    def projectBuildDeps = ids(tryp)
  }
}
