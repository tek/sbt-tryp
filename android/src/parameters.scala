package tryp

import sbt._
import Keys._
import android.Keys._

object Aar
{
  lazy val settings = android.Plugin.androidBuildAar ++ Export.settings
}

trait Proguard {
  lazy val settings = Seq(
    useProguard in Android := true,
    proguardScala in Android := true,
    proguardCache in Android ++= cache,
    proguardOptions in Android ++= options,
    apkbuildExcludes in Android ++= excludes
  )

  lazy val cache: Seq[ProguardCache] = Seq()

  lazy val options: Seq[String] = Seq()

  lazy val excludes: Seq[String] = Seq()
}

trait Placeholders {
  lazy val defaults: Map[String, String] = Map()

  def apply(name: String) = {
    defaults ++ specific.get(name).getOrElse(Map[String, String]())
  }

  lazy val specific: Map[String, Map[String, String]] = Map()
}

object Tests {
  def settings(dep: Project) = Seq(
    testOptions in Test += sbt.Tests.Argument("-oF"),
    exportJars in Test := false,
    fork in Test := true,
    javaOptions in Test ++= Seq(
      "-XX:MaxPermSize=2048M", "-XX:+CMSClassUnloadingEnabled", "-noverify"
      ),
    managedClasspath in Test <++= (
      platformJars in Android, baseDirectory in dep
    ) map {
      case ((j,_), b) => {
        Seq(Attributed.blank(b / "bin" / "classes"), Attributed.blank(file(j)))
      }
    }
    )
}

trait AndroidDeps
extends Deps
{
  override def unit = super.unit ++ Seq(
    resolvers += ("RoboTest releases" at
      "https://github.com/zbsz/mvn-repo/raw/master/releases/"),
    libraryDependencies ++= Seq(
      "org.apache.maven" % "maven-ant-tasks" % "2.1.3",
      "junit" % "junit" % "4.+",
      "org.robolectric" % "robolectric" % "2.+",
      "com.geteit" %% "robotest" % "0.+"
    )
  )

  override def integration = super.integration ++ Seq(
    libraryDependencies ++= Seq(
      "junit" % "junit" % "4.+",
      "com.jayway.android.robotium" % "robotium-solo" % "5.+"
    )
  )
}

object Multidex
{
  def settings(main: Seq[String]) = Seq(
    dexMainFileClasses in Android := main ++ Seq(
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
    dexMulti in Android := true,
    dexMinimizeMainFile in Android := false
  )

  def deps = Seq(
    libraryDependencies ++= Seq(
      aar("com.android.support" % "multidex" % "1.+")
    )
  )
}

class AndroidProjectBuilder(name: String, deps: Deps, prog: Proguard,
  placeholders: Placeholders, defaultSettings: Setting[_]*)
extends ProjectBuilder[AndroidProjectBuilder](name, deps, defaultSettings: _*)
{
  var pTransitive = false

  settings(Seq(transitiveSetting, placeholderSetting))

  def test(deps: Project*) = {
    settings(Tests.settings(deps.head))
    androidDeps(deps: _*)
  }

  def testOnly(deps: Project*) = {
    settings(Seq(scalaSource in Test := baseDirectory.value / "src"))
    test(deps: _*)
  }

  def aar = {
    Aar.settings ++=: pSettings
    this
  }

  def proguard = {
    prog.settings ++=: pSettings
    this
  }

  def transitiveSetting = transitiveAndroidLibs in Android := pTransitive

  def placeholderSetting =
    manifestPlaceholders in Android := placeholders(name)

  def transitive = {
    pTransitive = true
    this
  }

  def multidex(main: Seq[String] = List()) = {
    multidexDeps
    multidexSettings(main)
    this
  }

  def multidexDeps = {
    pSettings ++= Multidex.deps
    this
  }

  def multidexSettings(main: Seq[String]) = {
    pSettings ++= Multidex.settings(main)
    this
  }

  override def rootDeps(projects: ProjectReference*) = {
    projects foreach { p ⇒
      pSettings ++= Seq(
      collectResources in Android <<= collectResources in Android dependsOn (
        compile in Compile in p),
      compile in Compile <<= compile in Compile dependsOn(
        packageT in Compile in p),
      (localProjects in Android ++= Seq(android.Dependencies.LibraryProject(
        (baseDirectory in p).value)))
        )
    }
    super.rootDeps(projects: _*)
  }

  def androidDeps(projects: Project*) = {
    project { _.androidBuildWith(projects: _*) }
  }
}
