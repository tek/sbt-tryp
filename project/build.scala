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
  override def settings = super.settings ++ pluginVersionDefaults ++ Seq(
    propVersion(sdkVersion, "sdk", "1.5.1"),
    propVersion(protifyVersion, "protify", "1.1.4")
  )

  def tryplugVersion = TrypKeys.tryplugVersion

  def androidName = "android"
  val huy = "org.scala-android"
  val sdkName = "sbt-android"
  val protifyName = "sbt-android-protify"

  val sdkVersion = settingKey[String]("android-sdk-plugin version") in Tryp

  val protifyVersion = settingKey[String]("protify version") in Tryp

  lazy val common = List(
    libraryDependencies += "org.scalamacros" % "quasiquotes" % "2.+" cross CrossVersion.binary,
    addCompilerPlugin("org.scalamacros" % "paradise" % "2.+" cross CrossVersion.patch)
  )

  lazy val core = pluginSubProject("core")
    .settings(common: _*)
    .settings(
      name := "tryp-build",
      addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "0.13.0"),
      addSbtPlugin("com.earldouglas" % "xsbt-web-plugin" % "2.0.1"),
      addSbtPlugin("org.foundweekends" % "sbt-bintray" % "0.4.0")
    )

  lazy val android = pluginSubProject("android")
    .settings(
      name := "tryp-android",
      update := (update dependsOn updateVersions).value,
      updateAllPlugins := true
    )
    .settings(common: _*)
    .dependsOn(core)

  lazy val root = pluginProject("tryp")
    .aggregate(core, android)

  lazy val scripted = (project in file("scripted"))
    .settings(scriptedSettings: _*)
    .settings(
      resolvers += Resolver.typesafeIvyRepo("releases"),
      sbtTestDirectory := baseDirectory.value / "test",
      scriptedRun :=
        scriptedRun.dependsOn(publishLocal in core,
          publishLocal in android).value,
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
  extends PluginDeps
  {
    override def deps = super.deps ++ Map(
      "core" -> core,
      "android" -> android,
      "tryp" -> root
    )

    def androidSdk =
      plugin(huy, sdkName, sdkVersion, s"scala-android/$sdkName")
        .bintray("pfn")

    def protify =
      plugin(huy, s"$protifyName", protifyVersion,
        s"scala-android/$protifyName")
          .bintray("pfn")

    val core = ids(
      tryplug,
      "com.github.julien-truffaut" %% "monocle-macro" % "1.1.1"
    )

    val android = ids(androidSdk, protify)

    val root = ids(tryplug)
  }

  override def deps = TrypDeps
}
