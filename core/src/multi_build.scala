package tryp

import sbt._
import Keys._

object DefaultDeps extends Deps

abstract class MultiBuildBase(deps: Deps = DefaultDeps)
extends sbt.Build
{
  override def settings = super.settings ++ Seq(
    scalaVersion := "2.11.6",
    scalacOptions ++= Seq(
      "-feature",
      "-deprecation",
      "-language:implicitConversions",
      "-language:postfixOps",
      "-language:reflectiveCalls",
      "-language:experimental.macros"
    )
  )

  def globalSettings: List[Setting[_]] = Nil
}

class MultiBuild(deps: Deps = DefaultDeps)
extends MultiBuildBase
{
  def p(name: String) =
    new DefaultProjectBuilder(name, deps, globalSettings: _*)
}
