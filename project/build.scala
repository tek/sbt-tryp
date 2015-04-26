import sbt._
import sbt.Keys._

object TrypBuild extends sbt.Build
{
  override lazy val settings = super.settings ++ Seq(
    name := "tryp-plugin",
    version := "1.3.1",
    organization := "tryp"
  )

  lazy val common = Seq(
    sbtPlugin := true,
    scalaSource in Compile <<= baseDirectory(_ / "src")
  )

  lazy val core = (project in file("core"))
    .settings(common: _*)

  lazy val android = (project in file("android"))
    .settings(common: _*)
    .dependsOn(sdk, core)

  lazy val sdk = RootProject(file("../android-sdk-plugin"))
}
