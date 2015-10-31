package tryp

import sbt._
import Keys._

object DefaultDeps extends Deps

class MultiBuildBase
extends sbt.Build
with Tryplug
with ToProjectOps
with ProjectInstances
{
  import Tryp.autoImport._

  override def settings = super.settings ++ basicSettings

  def pb(name: String) = Project(name, deps)

  val prefix: Option[String] = None

  lazy val namePrefix = name := {
    prefix map(a ⇒ s"$a-${name.value}") getOrElse(name.value)
  }

  def tdp(name: String) = pb(name).antSrc.paradise().settingsV(namePrefix)

  val home = new File(sys.env.get("HOME").getOrElse("/"))

  lazy val root = pb("root").path(".").!

  def metaProject(n: String) = tdp(n)
    .path(".")
    .settingsV(target := (target in root).value / name.value)

  def mpb(name: String) = metaProject(name)

  lazy val macroConsole = macroConsoleBuilder.!

  lazy val macroConsoleBuilder = metaProject("macro-console")
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
}

class MultiBuild(override val deps: Deps = DefaultDeps)
extends MultiBuildBase
