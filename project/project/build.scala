import sbt._
import Keys._

object TrypBootstrap
extends Build
{
  lazy val resolver = {
    val name = "bintray-tek-sbt"
    val link = url("https://dl.bintray.com/tek/sbt-plugins")
    Resolver.url(name, link)(Resolver.ivyStylePatterns)
  }

  override def settings = super.settings ++ Seq(
    resolvers += resolver
  )

  lazy val root = project in file(".") settings(
    addSbtPlugin("tryp.sbt" % "tryplug-macros" % "2"),
    addSbtPlugin("tryp.sbt" % "tryplug" % "2")
  )
}
