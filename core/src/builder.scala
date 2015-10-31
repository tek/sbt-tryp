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

case class Params(name: String, settings: Setts,
  path: String, bintray: Boolean, deps: List[SbtDep] = Nil)

case class Project[D <: Deps](params: Params, deps: D)

final class TransformIf[A](a: A) {
  def transformIf(pred: ⇒ Boolean)(transform: A ⇒ A) =
    if(pred) transform(a) else a
}

trait ToTransformIf
{
  implicit def ToTransformIf[A](a: A) = new TransformIf(a)
}

class ProjectOps[A](pro: A)
(implicit builder: ProjectBuilder[A])
extends ToTransformIf
{
  def name = builder.params(pro).name

  def deps = builder.deps(pro)

  def params = builder.params(pro)

  def withParams(newParams: Params) =
    builder.withParams(pro)(newParams)

  def export = {
    settings(Export.settings)
  }

  def path(pt: String) = {
    withParams(params.copy(path = pt))
  }

  def at(pt: String) = path(pt)

  val ~ = at _

  def desc(text: String) = {
    settingsV(description := text)
  }

  val / = desc _

  def settings(extra: Setts) = {
    withParams(params.copy(settings = extra ++ params.settings))
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
    withParams(params.copy(bintray = true))
  }

  def dep(pros: SbtDep*) = {
    withParams(params.copy(deps = params.deps ++ pros))
  }

  def refs = builder.refs(pro)

  def project = builder.project(pro)

  def ! = project

  def basicProject = {
    sbt.Project(name, file(params.path))
      .dependsOn(refs: _*)
      .settings(deps(name) ++ params.settings: _*)
      .transformIf(!params.bintray)(_.disablePlugins(BintrayPlugin))
  }

  def <<(pros: SbtDep*) = dep(pros: _*)

  def <<!(pros: SbtDep*) =
    builder.project(dep(pros: _*))

  def aggregate(projects: ProjectReference*) = {
    project.aggregate(projects: _*)
  }
}

trait ToProjectOps
{
  implicit def ToProjectOps[A: ProjectBuilder](pro: A) = new ProjectOps(pro)
}

trait ProjectBuilder[A]
{
  def withParams(pro: A)(newParams: Params): A
  def params(pro: A): Params
  def deps(pro: A): Deps
  def project(pro: A): sbt.Project
  def refs(pro: A): Seq[SbtDep]
}

object Project
extends ProjectInstances
with ToProjectOps
{
  def apply[D <: Deps](name: String, deps: D, defaults: Setts = Nil)
  : Project[D] =
    Project(Params(name, defaults, name, false), deps)
}

class BasicProjectBuilder[D <: Deps]
extends ProjectBuilder[Project[D]]
with ToTransformIf
{
  type P = Project[D]

  def project(pro: P) = pro.basicProject

  def deps(pro: P) = pro.deps

  def params(pro: P) = pro.params

  def withParams(pro: P)(newParams: Params) =
    pro.copy(params = newParams)

  def refs(pro: P) = pro.deps.refs(pro.name) ++ pro.params.deps
}

trait ProjectInstances
{
  implicit def projectBuilder[D <: Deps]: ProjectBuilder[Project[D]] =
    new BasicProjectBuilder[D]
}
