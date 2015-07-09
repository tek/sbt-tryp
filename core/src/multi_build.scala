package tryp

import sbt._
import Keys._

object DefaultDeps extends Deps

abstract class MultiBuildBase(deps: Deps = DefaultDeps)
extends sbt.Build
{
  override def settings = super.settings ++ basicSettings

  def basicSettings = List(
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
}

class MultiBuild(deps: Deps = DefaultDeps)
extends MultiBuildBase
{
  def pb(name: String) =
    new DefaultProjectBuilder(name, deps, globalSettings: _*)

  def p(name: String) = pb(name)
}
