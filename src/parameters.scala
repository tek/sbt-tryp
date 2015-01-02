package tryp

import sbt._
import Keys._
import android.Keys._

import scala.collection.mutable.ListBuffer

object Export {
  lazy val settings = Seq((exportJars := true))

  lazy val aar = android.Plugin.androidBuildAar ++ settings
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

object Paradise {
  def settings(version: String) = Seq(
    incOptions := incOptions.value.withNameHashing(false),
    addCompilerPlugin(
      "org.scalamacros" % "paradise" % version cross CrossVersion.full
    )
  )
}

trait Deps {
  def deps: Map[String, Seq[Setting[_]]] = Map(
    "macros" → macros,
    "unit" → unit,
    "integration" → integration
  )

  def apply(name: String) = {
    deps.get(name) getOrElse Seq()
  }

  lazy val macros = Seq(
    libraryDependencies += "org.scala-lang" % "scala-reflect" %  "2.11.2"
  )

  lazy val unit = Seq(
    resolvers += ("RoboTest releases" at
      "https://github.com/zbsz/mvn-repo/raw/master/releases/"),
    libraryDependencies ++= Seq(
      "org.apache.maven" % "maven-ant-tasks" % "2.1.3",
      "junit" % "junit" % "4.+",
      "org.scalatest" %% "scalatest" % "2.1.6",
      "org.robolectric" % "robolectric" % "2.+",
      "com.geteit" %% "robotest" % "0.+"
    )
  )

  lazy val integration = Seq(
    libraryDependencies ++= Seq(
      "junit" % "junit" % "4.+",
      "com.jayway.android.robotium" % "robotium-solo" % "5.+"
    )
  )
}

trait Placeholders {
  lazy val defaults: Map[String, String] = Map()

  def apply(name: String) = {
    defaults ++ specific.get(name).getOrElse(Map[String, String]())
  }

  lazy val specific: Map[String, Map[String, String]] = Map()
}

class ProjectParameters(name: String, deps: Deps, prog: Proguard, placeholders:
  Placeholders, defaultSettings: Setting[_]*)
{
  var pTransitive = false
  var pSettings = ListBuffer[Setting[_]](defaultSettings: _*)
  var pPath = name
  var pRootDeps: Seq[RootProject] = Seq()

  def aar = {
    Export.aar ++=: pSettings
    this
  }

  def export = {
    Export.settings ++=: pSettings
    this
  }

  def transitive = {
    pTransitive = true
    this
  }

  def path(p: String) = {
    pPath = p
    this
  }

  def settings(extra: Seq[Setting[_]]) = {
    extra ++=: pSettings
    this
  }

  def proguard = {
    prog.settings ++=: pSettings
    this
  }

  def placeholderSetting = {
    manifestPlaceholders in Android := placeholders(name)
  }

  def test(deps: Project*) = {
    settings(Tests.settings(deps.head))
    androidDeps(deps: _*)
  }

  def paradise(version: String = "2.0.1") = {
    pSettings ++= Paradise.settings(version)
    this
  }

  def project(callback: (Project) => Project = identity) = {
    callback(Project(name, file(pPath)))
      .settings(deps(name) ++ pSettings :+ placeholderSetting :+
      (transitiveAndroidLibs in Android := pTransitive): _*)
  }

  def dep(pros: ClasspathDep[ProjectReference]*) = {
    project { _.dependsOn(pros: _*) }
  }

  def androidDeps(projects: Project*) = {
    project { _.androidBuildWith(projects: _*) }
  }

  def rootDeps(projects: RootProject*) = {
    pRootDeps ++= projects
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
    this
  }

  def apply() = project()

  def aggregate(projects: ProjectReference*) = {
    project().aggregate(projects: _*)
  }
}
