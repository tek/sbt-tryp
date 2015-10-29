libraryDependencies += Defaults.sbtPluginExtra(
  "tryp.sbt" % "tryplug" % P.tryplugVersion.value,
  (sbtBinaryVersion in update).value,
  (scalaBinaryVersion in update).value
)
addSbtPlugin("com.earldouglas" % "xsbt-web-plugin" % "2.0.1")
libraryDependencies += "org.scala-sbt" % "scripted-plugin" % sbtVersion.value
