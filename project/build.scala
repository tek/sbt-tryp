import sbt._
import sbt.Keys._

import bintray._
import BintrayKeys._

object TrypBuild extends sbt.Build
{
  val aVersion = "1.4.2"

  lazy val common = List(
    organization := "tryp.sbt",
    sbtPlugin := true,
    scalaSource in Compile <<= baseDirectory(_ / "src"),
    licenses += ("MIT", url("http://opensource.org/licenses/MIT")),
    bintrayRepository in bintray := "sbt-plugins",
    bintrayOrganization in bintray := None
  ) ++ BintrayPlugin.bintrayPublishSettings

  lazy val core = (project in file("core"))
    .settings(
      (name := "tryp-build") ::
      addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "0.13.0") ::
      addSbtPlugin("com.earldouglas" % "xsbt-web-plugin" % "1.1.0") ::
      addSbtPlugin("me.lessis" % "bintray-sbt" % "0.3.0") ::
      common
      : _*
    )

  lazy val android = (project in file("android"))
    .settings(
      (name := "tryp-android") ::
      common ++ sdk
      : _*
    )
    .dependsOn(core)

  lazy val root = (project in file("."))
    .settings(publish := ())
    .aggregate(core, android)

  lazy val sdk = List(
    addSbtPlugin("com.hanhuy.sbt" % "android-sdk-plugin" % aVersion)
  )
}
