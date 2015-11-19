package tryp

import sbt._
import Keys._
import android.Keys._
import TrypAndroidKeys._
import TrypBuildKeys._

object Aar
{
  lazy val settings = android.Plugin.buildAar.toList ++ Export.settings
}

trait Proguard {
  lazy val settings = List(
    useProguard := true,
    proguardScala := true,
    proguardCache ++= cache,
    proguardOptions ++= options
  )

  lazy val packaging = {
    packagingOptions := PackagingOptions(excludes, List(), merges)
  }

  lazy val cache: List[String] = List()

  lazy val options: List[String] = List()

  lazy val excludes: List[String] = List()

  lazy val merges: List[String] = List()
}

object Tests {
  def integration = List(
    testManifest := manifestOutput.value,
    debugIncludesTests := true
  )

  def robotest = List(
    exportJars in Test := false,
    fork in Test := true,
    unmanagedClasspath in Test ++= bootClasspath.value,
    Keys.test in Test <<=
      Keys.test in Test dependsOn TrypAndroidKeys.symlinkLibs,
    testOnly in Test <<=
      testOnly in Test dependsOn TrypAndroidKeys.symlinkLibs,
    logbackOutput := (resourceDirectory in Compile).value / manifestName,
    javaOptions in Test ++= Seq(
      "-XX:+CMSClassUnloadingEnabled",
      "-noverify",
      s"-Dandroid.manifest=${manifestOutput.value}",
      s"-Dandroid.resources=${projectLayout.value.res}",
      s"-Dandroid.assets=${projectLayout.value.assets}"
    )
  )
}

object Multidex
{
  def settings(main: List[String]) = List(
    dexMainClasses := main ++ List(
      "android/support/multidex/BuildConfig.class",
      "android/support/multidex/MultiDex$V14.class",
      "android/support/multidex/MultiDex$V19.class",
      "android/support/multidex/MultiDex$V4.class",
      "android/support/multidex/MultiDex.class",
      "android/support/multidex/MultiDexApplication.class",
      "android/support/multidex/MultiDexExtractor$1.class",
      "android/support/multidex/MultiDexExtractor.class",
      "android/support/multidex/ZipUtil$CentralDirectory.class",
      "android/support/multidex/ZipUtil.class"
    ),
    dexMulti := true,
    dexMinimizeMain := false
  )

  def deps = List(
    libraryDependencies ++= List(
      aar("com.android.support" % "multidex" % "1.+")
    )
  )
}
