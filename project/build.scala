package tryp

import sbt._
import sbt.Keys._

import ScriptedPlugin._

import TrypKeys._
import VersionUpdateKeys._

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
  )

  lazy val core = pluginSubProject("core")
    .settings(common: _*)
    .settings(
      name := "tryp-build",
      addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "0.13.0"),
      addSbtPlugin("com.earldouglas" % "xsbt-web-plugin" % "2.0.1"),
      addSbtPlugin("me.lessis" % "bintray-sbt" % "0.3.0")
    )

  lazy val android = pluginSubProject("android")
    .settings(
      name := "tryp-android",
      update <<= update dependsOn(updateVersions)
    )
    .settings(common: _*)
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
        "-Xmx2048m",
        "-XX:MaxPermSize=1024m",
        "-Dsdk=1.5.1",
        s"-Dtryp.projectsdir=${baseDirectory.value / "meta"}",
        s"-Dtryp.version=${version.value}"
      )
    )

  val wantDevdeps = false

  object TrypDeps
  extends Deps
  {
    override def deps = super.deps ++ Map(
      "core" → core,
      "android" → android
    )

    val huy = "com.hanhuy.sbt"
    val sdkName = "android-sdk-plugin"

    val core = ids(
      pd("tryp.sbt", "tryplug", tryplugVersion, "tek", "tek/tryplug",
        "tryplug", "tryplug-macros")
    )

    val android = ids(
      pd(huy, sdkName, sdkVersion, "pfn", s"pfn/$sdkName"),
      pd(huy, "android-protify", protifyVersion, "pfn", "pfn/protify",
        "plugin")
    )
  }

  override def deps = TrypDeps
}
