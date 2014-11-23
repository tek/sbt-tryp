name := "trypdroid-plugin"

version := "1.3.1"

organization := "tryp.sbt"

sourceDirectories in Compile <<= baseDirectory(b => Seq(b / "src"))

scalaSource in Compile <<= baseDirectory(_ / "src")

sbtPlugin := true
