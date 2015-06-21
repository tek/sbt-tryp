import sbt._
import sbt.Keys._

object TrypBuild extends sbt.Build
{
  val aVersion = "1.3.24"

  override lazy val settings = super.settings ++ Seq(
    name := "tryp-plugin",
    version := aVersion,
    organization := "tryp.sbt"
  )

  lazy val common = List(
    sbtPlugin := true,
    scalaSource in Compile <<= baseDirectory(_ / "src")
  )

  lazy val core = (project in file("core"))
    .settings(common: _*)

  lazy val android = (project in file("android"))
    .settings(common ++ sdk: _*)
    .dependsOn(core)

  lazy val sdk = List(
    addSbtPlugin("com.hanhuy.sbt" % "android-sdk-plugin" % aVersion)
  )
}
