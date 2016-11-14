package tryp

import sbt._
import Keys._

import com.earldouglas.xwp.JettyPlugin
import JettyPlugin._

object JettyDeploy
extends AutoPlugin
{
  override def requires = JettyPlugin
  override def trigger = allRequirements

  object autoImport {
    lazy val deploy = inputKey[Unit]("deploy war to a location")
  }
  import autoImport._

  def timestamp = System.currentTimeMillis / 1000

  override lazy val projectSettings = Seq(
    deploy := (deployTask dependsOn Keys.`package`).evaluated
  )

  def deployTask = Def.inputTask {
    val args = Def.spaceDelimited().parsed
    val msg = "specify a single directory"
    if (args.length != 1) sys.error(msg)
    val targetDir = args.headOption map { f ⇒ new File(f) } getOrElse {
      sys.error(msg) }
    if (!targetDir.isDirectory) sys.error("argument is not a directory")
    val outName = s"${Keys.name.value}-${Keys.version.value}-$timestamp.war"
    val targetPath = targetDir / outName
    IO.copyFile(Keys.`package`.value, targetPath)
    streams.value.log.info(s"Copied war to ${targetPath}")
  }
}
