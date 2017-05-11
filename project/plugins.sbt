resolvers += Resolver.url("bintray-tek-sbt", url("https://dl.bintray.com/tek/sbt-plugins"))(Resolver.ivyStylePatterns)
libraryDependencies += Defaults.sbtPluginExtra(
  "tryp.sbt" % "tryplug" % P.tryplugVersion.value,
  (sbtBinaryVersion in update).value,
  (scalaBinaryVersion in update).value
)
libraryDependencies += "org.scala-sbt" % "scripted-plugin" % sbtVersion.value
