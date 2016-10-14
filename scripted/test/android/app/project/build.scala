import sbt._
import sbt.Keys._

import tryp.TrypAndroid.autoImport._

object Deps
extends tryp.AndroidDeps
{
  override def deps = super.deps ++ Map(
    "core" → core
  )

  lazy val core = ids(
    ad("test" % "dep" % "+", "tek/dep", "depcore")
  )
}

object Proguard
extends tryp.Proguard
{

  override lazy val options = List(
    "-dontwarn **"
  )
}

object B
extends tryp.AndroidBuild("app", deps = Deps, proguard = Proguard)
{
  lazy val stuff = tdp("stuff").!

  lazy val core = aar("core")

  lazy val pkg = ("app-pkg" <<< core)
    .path("pkg")
    .release
    .manifest(
      "minSdk" → "21",
      "targetSdk" → "23",
      "activityClass" → ".MainActivity"
    )
    .settingsV(
      manifestTokens += ("package" → androidPackage.value)
    )
    .map(_.dependsOn(stuff))
}
