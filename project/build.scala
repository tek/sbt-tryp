import sbt._
import sbt.Keys._

import bintray._
import BintrayKeys._

import ScriptedPlugin._

object TrypSettings
{
  val sdkVersion = settingKey[String]("android-sdk-plugin version")
}
import TrypSettings._

object TrypBuild
extends sbt.Build
{
  override def settings = super.settings ++ Seq(
    sdkVersion := sys.props.getOrElse("sdk", "1.5.1")
    )

  lazy val common = List(
    organization := "tryp.sbt",
    sbtPlugin := true,
    scalaSource in Compile := baseDirectory.value / "src",
    licenses += ("MIT", url("http://opensource.org/licenses/MIT")),
    bintrayRepository in bintray := "sbt-plugins",
    bintrayOrganization in bintray := None,
    libraryDependencies +=
      "org.scalamacros" % "quasiquotes" % "2.+" cross CrossVersion.binary,
    addCompilerPlugin(
      "org.scalamacros" % "paradise" % "2.+" cross CrossVersion.full),
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
      name := "tryp-build",
      addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "0.13.0"),
      addSbtPlugin("com.earldouglas" % "xsbt-web-plugin" % "2.0.1"),
      addSbtPlugin("me.lessis" % "bintray-sbt" % "0.3.0")
    )

  lazy val android = (project in file("android"))
    .settings(
      name := "tryp-android"
    )
    .settings(common ++ aPluginDeps)
    .dependsOn(core)
    .dependsOn(devdeps: _*)

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
        "-Xmx2048m",
        "-XX:MaxPermSize=1024m",
        "-Dsdk=1.5.1",
        s"-Dtryp.projectsdir=${baseDirectory.value / "meta"}",
        s"-Dtryp.version=${version.value}"
      )
    )

  val wantDevdeps = true

  def devdeps = {
    if (wantDevdeps)
      List(sdkLocal, protifyLocal) map(a ⇒ a: ClasspathDep[ProjectReference])
    else Nil
  }

  def aPluginDeps = {
    if (wantDevdeps) Nil
    else protify ++ sdk
  }

  lazy val protify =
    List(addSbtPlugin("com.hanhuy.sbt" % "android-protify" % "1.1.5"))

  lazy val sdk = List(
    libraryDependencies +=
      Defaults.sbtPluginExtra(
        "com.hanhuy.sbt" % "android-sdk-plugin" % sdkVersion.value,
        (sbtBinaryVersion in update).value,
        (scalaBinaryVersion in update).value
      )
  )

  lazy val sdkLocal = RootProject(file("../android-sdk-plugin"))
  lazy val protifyLocal = ProjectRef(file("../protify"), "plugin")
}
