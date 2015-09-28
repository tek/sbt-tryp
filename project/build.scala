import sbt._
import sbt.Keys._

import bintray._
import BintrayKeys._

import ScriptedPlugin._

object TrypBuild extends sbt.Build
{
  val aVersion = sys.props.getOrElse("sdk", "1.5.0")

  lazy val common = List(
    organization := "tryp.sbt",
    sbtPlugin := true,
    scalaSource in Compile <<= baseDirectory(_ / "src"),
    licenses += ("MIT", url("http://opensource.org/licenses/MIT")),
    bintrayRepository in bintray := "sbt-plugins",
    bintrayOrganization in bintray := None,
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

  lazy val core = (project in file("core"))
    .settings(common: _*)
    .settings(
      (name := "tryp-build"),
      addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "0.13.0"),
      addSbtPlugin("com.earldouglas" % "xsbt-web-plugin" % "2.0.1"),
      addSbtPlugin("me.lessis" % "bintray-sbt" % "0.3.0"),
      libraryDependencies +=
        "org.scalamacros" % "quasiquotes" % "2.+" cross CrossVersion.binary,
      addCompilerPlugin(
        "org.scalamacros" % "paradise" % "2.+" cross CrossVersion.full)
    )

  lazy val android = (project in file("android"))
    .settings(
      (name := "tryp-android") ::
      addCompilerPlugin("org.scalamacros" % "paradise" % "2.+" cross
        CrossVersion.full) ::
      common ++ sdk
      : _*
    )
    .dependsOn(core)

  lazy val root = (project in file("."))
    .settings(publish := (), publishLocal := ())
    .aggregate(core, android)

  lazy val scripted = (project in file("scripted"))
    .settings(scriptedSettings: _*)
    .settings(
      sbtTestDirectory := baseDirectory.value / "test",
      scriptedRun <<=
        scriptedRun dependsOn(publishLocal in core, publishLocal in android),
      scriptedBufferLog := false,
      scriptedLaunchOpts ++= Seq(
        "-Xmx4096m",
        "-XX:MaxPermSize=1024m",
        "-Dsdk=1.5.1-SNAPSHOT",
        s"-Dtryp.projectsdir=${baseDirectory.value / "meta"}",
        s"-Dtryp.version=${version.value}"
      )
    )

  lazy val sdk = List(
    addSbtPlugin("com.hanhuy.sbt" % "android-sdk-plugin" % aVersion)
  )
}
