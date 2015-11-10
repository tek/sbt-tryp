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

  override lazy val options = List(
    "-dontwarn **"
  )
}

object B
extends tryp.AndroidBuild(deps = Deps, proguard = Proguard)
{
  lazy val core = aar("core")

  lazy val pkg = adp("pkg").apk("org.test.app") <<< core
}
