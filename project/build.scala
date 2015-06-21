import sbt._
import sbt.Keys._

object TrypBuild extends sbt.Build
{
  val aVersion = "1.3.24"

  override lazy val settings = super.settings ++ Seq(
    name := "tryp-plugin",
    version := aVersion,
    organization := "tryp.sbt"
  )

  lazy val common = List(
    sbtPlugin := true,
    scalaSource in Compile <<= baseDirectory(_ / "src"),
    licenses += ("MIT", url("http://opensource.org/licenses/MIT")),
    credentials += Credentials(Path.userHome / ".ivy2" / ".credentials"),
    publishTo := {
      val nexusUri = sys.props.getOrElse("NEXUS_HOST",
        default = "http://localhost:8081")
      val repos = s"$nexusUri/nexus/content/repositories/"
      val tpe = if (isSnapshot.value) "snapshots" else "releases"
      Some(tpe at repos + tpe)
    },
    publishArtifact in (Compile, packageDoc) := false,
    publishArtifact in (Compile, packageSrc) := false
  )

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
