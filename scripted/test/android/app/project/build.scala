import sbt._
import sbt.Keys._

object Deps
extends tryp.AndroidDeps
{
  override def deps = super.deps ++ Map(
    "core" → core
  )

  lazy val core = ids(
    ad("test" % "dep" % "+", "tek/dep", "depcore")
  )
}

object Proguard
extends tryp.Proguard
{

  override lazy val options = Seq(
    "-dontwarn **"
  )
}

object B
extends tryp.AndroidBuild(deps = Deps, proguard = Proguard)
{
  lazy val core = tdp("core").aar()

  lazy val pkg = tdp("pkg").proguard <<< core
}
