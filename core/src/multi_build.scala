package tryp

import sbt._
import Keys._

object DefaultDeps extends Deps

abstract class MultiBuildBase[A <: ProjectBuilder[A]](deps: Deps = DefaultDeps)
extends sbt.Build
{
  override def settings = super.settings ++ basicSettings

  def basicSettings: List[Setting[_]] = List(
    scalaVersion := "2.11.7",
    scalacOptions ++= Seq(
      "-feature",
      "-deprecation",
      "-unchecked",
      "-language:implicitConversions",
      "-language:postfixOps",
      "-language:reflectiveCalls",
      "-language:experimental.macros",
      "-language:existentials",
      "-language:higherKinds"
    )
  )


  def globalSettings: List[Setting[_]] =
    (updateOptions := updateOptions.value.withCachedResolution(true)) ::
    basicSettings

  def pb(name: String): A

  val prefix: Option[String] = None

  lazy val namePrefix = name := {
    prefix map(a ⇒ s"$a-${name.value}") getOrElse(name.value)
  }

  def tdp(name: String) =
    pb(name).antSrc.paradise().settingsV(namePrefix)

  val home = sys.env.get("HOME").getOrElse("/")

  def macroConsole = pb("macro-console")
    .paradise()
    .antSrc()
    .settings(
      scalacOptions +=
        s"-Xplugin:$home/.ivy2/cache/org.scalamacros/paradise_" +
        s"${scalaVersion.value}/jars/paradise_${scalaVersion.value}" +
        "-2.1.0-M5.jar",
      initialCommands in console := """
      val universe: scala.reflect.runtime.universe.type =
        scala.reflect.runtime.universe
      import universe._
      import scalaz._
      import Scalaz._
      """
    )
}

class MultiBuild(deps: Deps = DefaultDeps)
extends MultiBuildBase[DefaultProjectBuilder]
{
  def pb(name: String) = DefaultProjectBuilder(name, deps, globalSettings)
}
