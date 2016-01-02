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
  import TrypBuildKeys._

  def pb(name: String) = Project(name, deps)

  val title: Option[String] = None

  lazy val prefixedName = name := {
    title map(a ⇒ s"$a-${name.value}") getOrElse(name.value)
  }

  def tdp(name: String) =
    pb(name)
      .antSrc
      .paradise()
      .settingsV(prefixedName)

  val home = new File(sys.env.get("HOME").getOrElse("/"))

  lazy val root = pb("root") ~ "." !

  def metaTarget = target := (target in root).value / name.value

  def metaProject(n: String) = tdp(n)
    .path(".")
    .settingsV(metaTarget)

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
        if(paradiseJar.value.isDefined) uni + consoleImports
        else consoleImports
      }
    )

  def consoleImports = """
  import scalaz._
  import Scalaz._
  """

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

  def projectInfoCommandName = "tryp-projects"

  def projectInfoCommand = {
    Command.args(projectInfoCommandName, "<projects>") { (state, projects) ⇒
      val pros = if (projects.isEmpty) trypProjects
      else trypProjects filter(a ⇒ projects contains a.name)
      pros flatMap(_.info :+ "") foreach(state.log.info(_))
      state
    }
  }
}

trait TrypBuild
extends ExtMultiBuild
{
  override def settings = super.settings ++ trypSettings
}

class MultiBuild(t: String, override val deps: Deps = DefaultDeps)
extends TrypBuild
{
  override val title = Some(t)

  def defaultBuilder = tdp _

  implicit def stringToBuilder(name: String) =
    ToProjectOps(defaultBuilder(name))
}

class LibsBuild(t: String, deps: Deps = DefaultDeps)
extends MultiBuild(t, deps)
{
  override def defaultBuilder = tdp(_).export
}
