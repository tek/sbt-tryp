import sbt._
import sbt.Keys._

import android.Keys._

object Deps
extends tryp.AndroidDeps
{
  override def deps = super.deps ++ Map(
    "depcore" -> depcore
  )

  lazy val depcore = ids(
    aar("com.android.support" % "appcompat-v7" % "23.+")
  )
}

object B
extends tryp.AndroidBuild("dep", deps = Deps)
{
  val depcore = aar("depcore")
}
