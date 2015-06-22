package tryp

import sbt._
import sbt.Keys._

import scala.collection.mutable.ListBuffer

object Export {
  lazy val settings = Seq((exportJars := true))
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

  val scalazV = "7.1.+"

  def common = Seq(
    resolvers ++= Seq(
      Resolver.sonatypeRepo("releases")
    ),
    libraryDependencies ++= Seq(
      "org.scalaz" %% "scalaz-concurrent" % scalazV
    )
  )

  def apply(name: String) = {
    common ++ deps.get(name).getOrElse(Seq())
  }

  def macros: Seq[Setting[_]] = Seq(
    libraryDependencies +=
      "org.scala-lang" % "scala-reflect" % scalaVersion.value
  )

  val specsV = "3.6"

  def unit: Seq[Setting[_]] = Seq(
    libraryDependencies ++=
      "org.scalatest" %% "scalatest" % "2.2.+" ::
      "org.specs2" %% "specs2-core" % specsV ::
      Nil
  )

  def integration: Seq[Setting[_]] = Seq(
    libraryDependencies ++=
      "org.scalatest" %% "scalatest" % "2.2.+" ::
      "org.specs2" %% "specs2-core" % specsV ::
      Nil
  )
}

class ProjectBuilder[A](name: String, deps: Deps, defaultSettings: Setting[_]*)
{ self: A ⇒

  var pSettings = ListBuffer[Setting[_]](defaultSettings: _*)
  var pPath = name
  var pRootDeps: Seq[ProjectReference] = Seq()
  var pDevDeps: Seq[ClasspathDep[ProjectReference]] = Seq()

  def export = {
    Export.settings ++=: pSettings
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

  def paradise(version: String = "2.+") = {
    pSettings ++= Paradise.settings(version)
    this
  }

  def antSrc = {
    pSettings += (scalaSource in Compile := baseDirectory.value / "src")
    pSettings +=
      (resourceDirectory in Compile := baseDirectory.value / "resources")
    pSettings += (scalaSource in Test := baseDirectory.value / "test-src")
    this
  }

  def antTest = {
    pSettings += (scalaSource in Test := baseDirectory.value / "src")
    this
  }

  val env = sys.props.getOrElse("env", "development")

  def development = env == "development"

  def project(callback: (Project) ⇒ Project = identity) = {
    val pro = callback(Project(name, file(pPath)))
      .settings(deps(name) ++ pSettings: _*)
    if (development) pro.dependsOn(pDevDeps: _*) else pro
  }

  def dep(pros: ClasspathDep[ProjectReference]*) = {
    project { _.dependsOn(pros: _*) }
  }

  def devDeps(projects: ClasspathDep[ProjectReference]*) = {
    pDevDeps ++= projects
    this
  }

  def rootDeps(projects: ProjectReference*) = {
    pRootDeps ++= projects
    this
  }

  def apply() = project()

  def aggregate(projects: ProjectReference*) = {
    project().aggregate(projects: _*)
  }
}

class DefaultProjectBuilder(name: String, deps: Deps,
  defaultSettings: Setting[_]*)
extends ProjectBuilder[DefaultProjectBuilder](name, deps, defaultSettings: _*)
