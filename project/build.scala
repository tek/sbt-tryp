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
  )

  lazy val core = pluginSubProject("core")
    .settings(common: _*)
    .settings(
      name := "tryp-build",
      addSbtPlugin("tryp.sbt" % "tryplug-macros" % "2"),
      addSbtPlugin("tryp.sbt" % "tryplug" % "2"),
      addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "0.13.0"),
      addSbtPlugin("com.earldouglas" % "xsbt-web-plugin" % "2.0.1"),
      addSbtPlugin("me.lessis" % "bintray-sbt" % "0.3.0")
    )

  lazy val android = pluginSubProject("android")
      .settings(
        name := "tryp-android"
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
  extends DepsBase
  {
    override def deps = super.deps ++ Map(
      "android" → android
    )


    val huy = "com.hanhuy.sbt"
    val sdkName = "android-sdk-plugin"

    val android = ids(
      pd(huy, sdkName, sdkVersion, s"pfn/$sdkName"),
      pd(huy, "android-protify", protifyVersion, "pfn/protify", "plugin")
    )
  }

  override def deps = TrypDeps
}
