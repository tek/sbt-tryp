package tryp

import sbt._
import Keys._
import android.Keys._

import scala.collection.mutable.ListBuffer

object Aar {
  lazy val settings = {
    android.Plugin.androidBuildAar :+ (exportJars := true)
  }
}

object Tests {
  def settings(dep: Project) = Seq(
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
    libraryDependencies ++= Seq(
      "junit" % "junit" % "4.+",
      "org.scalatest" %% "scalatest" % "2.1.6",
      "org.robolectric" % "robolectric" % "2.3"
    )
  )

  lazy val integration = Seq(
    libraryDependencies ++= Seq(
      "junit" % "junit" % "4.+",
      "com.jayway.android.robotium" % "robotium-solo" % "5.+"
    )
  )
}

class ProjectParameters(name: String, deps: Deps, prog: Proguard,
  defaultSettings: Setting[_]*)
{
  var pTransitive = false
  var pSettings = ListBuffer[Setting[_]](defaultSettings: _*)
  var pPath = name

  def aar = {
    Aar.settings ++=: pSettings
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

  def test(dep: Project) = {
    settings(Tests.settings(dep))
    androidDeps(dep)
  }

  def project(callback: (Project) => Project = identity) = {
    callback(Project(name, file(pPath)))
      .settings(deps(name) ++ pSettings :+
      (transitiveAndroidLibs in Android := pTransitive): _*)
  }

  def dep(pro: ProjectReference) = {
    project { _.dependsOn(pro) }
  }

  def androidDeps(projects: Project*) = {
    project { _.androidBuildWith(projects: _*) }
  }

  def apply() = project()

  def aggregate(projects: ProjectReference*) = {
    project().aggregate(projects: _*)
  }
}
