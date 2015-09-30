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

  lazy val root = project in file(".") settings(
    addSbtPlugin("tryp" % "tryplug" % "1")
  )
}
