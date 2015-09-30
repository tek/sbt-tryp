package tryp

import sbt._
import sbt.Keys._

import ScriptedPlugin._

import TrypKeys._

object TrypBuild
extends sbt.Build
with Tryplug
{
  override def settings = super.settings ++ pluginVersionDefaults

  lazy val common = List(
    libraryDependencies +=
      "org.scalamacros" % "quasiquotes" % "2.+" cross CrossVersion.binary,
    addCompilerPlugin(
      "org.scalamacros" % "paradise" % "2.+" cross CrossVersion.full)
  ) ++ basicPluginSettings

  lazy val core = (project in file("core"))
    .settings(common: _*)
    .settings(
      name := "tryp-build",
      addSbtPlugin("tryp" % "tryplug" % "1"),
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

  val wantDevdeps = false

  def devdeps = {
    if (wantDevdeps)
      List(sdkLocal, protifyLocal) map(a ⇒ a: ClasspathDep[ProjectReference])
    else Nil
  }

  def aPluginDeps = {
    if (wantDevdeps) Nil
    else List(protify, sdk)
  }

  lazy val protify =
    plugin("com.hanhuy.sbt", "android-protify", protifyVersion)

  lazy val sdk = plugin("com.hanhuy.sbt", "android-sdk-plugin", sdkVersion)

  lazy val sdkLocal = RootProject(file("../android-sdk-plugin"))
  lazy val protifyLocal = ProjectRef(file("../protify"), "plugin")
}
