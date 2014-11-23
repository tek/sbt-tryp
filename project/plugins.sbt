lazy val root = project.in(file(".")).dependsOn(android)
lazy val android = file("../../android-sdk-plugin")
