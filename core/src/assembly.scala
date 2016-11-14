package tryp

import sbt._
import Keys._

import sbtassembly.AssemblyPlugin
import AssemblyPlugin.autoImport._

object AssemblyDeploy
extends AutoPlugin
{
  override def requires = AssemblyPlugin
  override def trigger = allRequirements

  object autoImport {
    lazy val deployAssembly = inputKey[Unit]("deploy jar to a location")
  }
  import autoImport._

  def timestamp = System.currentTimeMillis / 1000

  override lazy val projectSettings = Seq(
    deployAssembly := (deployTask dependsOn assembly).evaluated,
    test in assembly := {},
    assemblyMergeStrategy in assembly := {
      case "application.conf" ⇒ MergeStrategy.concat
      case a ⇒ (assemblyMergeStrategy in assembly).value(a)
    },
    assemblyOption in assembly :=
      (assemblyOption in assembly).value
        .copy(prependShellScript = Some(AssemblyPlugin.defaultShellScript))
  )

  def deployTask = Def.inputTask {
    val args = Def.spaceDelimited().parsed
    val msg = "specify a single directory"
    if (args.length != 1) sys.error(msg)
    val targetDir = args.headOption map { f ⇒ new File(f) } getOrElse {
      sys.error(msg) }
    if (!targetDir.isDirectory) sys.error("argument is not a directory")
    val outName = s"${Keys.name.value}-$timestamp.jar"
    val jarPath = (assemblyOutputPath in assembly).value
    val targetFile = targetDir / outName
    IO.copyFile(jarPath, targetFile)
  }
}
