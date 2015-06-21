import sbt._
import sbt.Keys._

import bintray._
import BintrayKeys._

object TrypBuild extends sbt.Build
{
  val aVersion = "1.3.24"

  override lazy val settings = super.settings ++ Seq(
    name := "tryp-plugin",
    organization := "tryp.sbt"
  )

  lazy val common = List(
    sbtPlugin := true,
    scalaSource in Compile <<= baseDirectory(_ / "src"),
    licenses += ("MIT", url("http://opensource.org/licenses/MIT")),
    bintrayRepository in bintray := "sbt-plugins",
    bintrayOrganization in bintray := None
  ) ++ BintrayPlugin.bintrayPublishSettings

  lazy val core = (project in file("core"))
    .settings(common: _*)

  lazy val android = (project in file("android"))
    .settings(common ++ sdk: _*)
    .dependsOn(core)

  lazy val root = (project in file("."))
    .settings(publish := ())
    .aggregate(core, android)

  lazy val sdk = List(
    addSbtPlugin("com.hanhuy.sbt" % "android-sdk-plugin" % aVersion)
  )
}
