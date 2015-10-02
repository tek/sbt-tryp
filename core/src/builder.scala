package tryp

import reflect.macros.Context

import scala.collection.mutable.ListBuffer

import sbt._
import sbt.Keys._

import bintray.BintrayPlugin

import Types._

object Export {
  lazy val settings = Seq(exportJars := true)
}

object Paradise {
  def settings(version: String) = Seq(
    incOptions := incOptions.value.withNameHashing(false),
    addCompilerPlugin(
      "org.scalamacros" % "paradise" % version cross CrossVersion.full
    )
  )
}

case class ProjectParams(settings: Setts, path: String, bintray: Boolean)

object ProjectBuilder
{
  implicit class TransformIf[A](a: A) {
    def transformIf(pred: ⇒ Boolean)(transform: A ⇒ A) =
      if(pred) transform(a) else a
  }
}

abstract class ProjectBuilder[A]
(name: String, deps: Deps, params: ProjectParams)
{ self: A ⇒

  import ProjectBuilder._

  def copy(newParams: ProjectParams): A

  def export = {
    settings(Export.settings)
  }

  def path(pt: String) = {
    copy(params.copy(path = pt))
  }

  def at(pt: String) = path(pt)

  def settings(extra: Setts) = {
    copy(params.copy(settings = extra ++ params.settings))
  }

  def settingsV(extra: Setting[_]*) = settings(extra)

  def paradise(version: String = "2.+") = {
    settings(Paradise.settings(version))
  }

  def antSrc = {
    settingsV (
      sourceDirectory in Compile := baseDirectory.value / "src",
      scalaSource in Compile := (sourceDirectory in Compile).value,
      resourceDirectory in Compile := baseDirectory.value / "resources",
      sourceDirectory in Test := baseDirectory.value / "test-src",
      scalaSource in Test := (sourceDirectory in Test).value
    )
  }

  def bintray = {
    copy(params.copy(bintray = true))
  }

  def project = {
    Project(name, file(params.path))
      .settings(deps(name) ++ params.settings: _*)
      .dependsOn(deps.refs(name): _*)
      .transformIf(!params.bintray) { _.disablePlugins(BintrayPlugin) }
  }

  def dep(pros: ClasspathDep[ProjectReference]*) = {
    project.dependsOn(pros: _*)
  }

  def apply() = project

  def <<(pros: ClasspathDep[ProjectReference]*) = dep(pros: _*)

  def aggregate(projects: ProjectReference*) = {
    project.aggregate(projects: _*)
  }
}

class DefaultProjectBuilder(name: String, deps: Deps, params: ProjectParams)
extends ProjectBuilder[DefaultProjectBuilder](name, deps, params)
{
  def copy(newParams: ProjectParams) =
    new DefaultProjectBuilder(name, deps, newParams)
}

object DefaultProjectBuilder
{
  def apply(name: String, deps: Deps, defaults: Setts = Nil) =
    new DefaultProjectBuilder(name, deps,
      ProjectParams(defaults, name, false))
}
