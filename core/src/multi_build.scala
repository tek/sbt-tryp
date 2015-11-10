package tryp

import sbt._
import Keys._

object DefaultDeps extends Deps

trait MultiBuildBase
extends sbt.Build
with Tryplug
with ToProjectOps
with ProjectInstances
{
  import Tryp.autoImport._

  def pb(name: String) = Project(name, deps)

  val prefix: Option[String] = None

  lazy val namePrefix = name := {
    prefix map(a ⇒ s"$a-${name.value}") getOrElse(name.value)
  }

  def tdp(name: String) = pb(name).antSrc.paradise().settingsV(namePrefix)

  val home = new File(sys.env.get("HOME").getOrElse("/"))

  lazy val root = pb("root") ~ "." !

  def metaProject(n: String) = tdp(n)
    .path(".")
    .settingsV(target := (target in root).value / name.value)

  def mpb(name: String) = metaProject(name)

  lazy val macroConsole = metaProject("macro-console")
    .settingsV(
      scalacOptions ++= {
        paradiseJar.value map(p ⇒ s"-Xplugin:$p") toSeq
      },
      initialCommands in console := {
        val uni = """
          val universe: scala.reflect.runtime.universe.type =
            scala.reflect.runtime.universe
          import universe._
        """
        val sz = """
          import scalaz._
          import Scalaz._
        """
        if(paradiseJar.value.isDefined) uni + sz else sz
      }
    )

  override def rootProject = Some(macroConsole.!)
}

trait ExtMultiBuild
extends MultiBuildBase
{
  override def settings = super.settings ++ Seq(
    commands += projectInfoCommand
  )

  def trypProjects = ReflectUtilities.allVals[ProjectI[_]](this).values

  override def projects = super.projects ++ trypProjects.map(_.reify)

  def commandName = prefix map(_ + "-projects") getOrElse("tryp-projects")

  def projectInfoCommand = {
    Command.args(commandName, "<projects>") { (state, projects) ⇒
      trypProjects flatMap(_.info :+ "") foreach(state.log.info(_))
      state
    }
  }
}

trait TrypBuild
extends ExtMultiBuild
{
  override def settings = super.settings ++ trypSettings
}

class MultiBuild(override val deps: Deps = DefaultDeps)
extends TrypBuild
