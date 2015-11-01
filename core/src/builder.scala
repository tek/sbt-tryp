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

class ProjectI[A <: ProjectI[A]](implicit builder: ProjectBuilder[A])
{
  self: A ⇒

  lazy val reify: sbt.Project = builder.project(this)

  def info: List[String] = builder.info(this)
}

case class Project[D <: Deps](params: Params, deps: D)
extends ProjectI[Project[D]]

final class TransformIf[A](a: A) {
  def transformIf(pred: ⇒ Boolean)(transform: A ⇒ A) =
    if(pred) transform(a) else a
}

trait ToTransformIf
{
  implicit def ToTransformIf[A](a: A) = new TransformIf(a)
}

trait ToSbt[A]
{
  def reify(project: A): sbt.Project
}

object ToSbt
{
  implicit def ProjectIToSbt[A <: ProjectI[A]] =
    new ToSbt[A] {
      def reify(project: A) = project.reify
    }

  implicit val sbtToSbt =
    new ToSbt[sbt.Project] {
      def reify(project: sbt.Project) = project
    }
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

  def logback = {
    settingsV(TrypBuildKeys.generateLogback := true)
  }

  def dep(pros: SbtDep*) = {
    withParams(params.copy(deps = params.deps ++ pros))
  }

  def refs = builder.refs(pro)

  def libraryDeps = deps(name)

  def project = builder.project(pro)

  def ! = project

  def basicProject = {
    sbt.Project(name, file(params.path))
      .dependsOn(refs: _*)
      .transformIf(!params.bintray)(_.disablePlugins(BintrayPlugin))
  }

  def configuredProject = {
    basicProject
      .settings(libraryDeps ++ params.settings: _*)
  }

  def <<[B](pro: B)(implicit ts: ToSbt[B]) = dep(ts.reify(pro))

  def <<![B](pro: B)(implicit ts: ToSbt[B]) = builder.project(<<(pro))

  def aggregate(projects: ProjectReference*) = {
    project.aggregate(projects: _*)
  }
}

trait ToProjectOps
{
  implicit def ToProjectOps[A: ProjectBuilder](pro: A) = new ProjectOps(pro)
}

trait ProjectBuilder[P]
{
  def withParams(pro: P)(newParams: Params): P
  def params(pro: P): Params
  def deps(pro: P): Deps
  def project(pro: P): sbt.Project
  def refs(pro: P): Seq[SbtDep]
  def info(pro: P): List[String]
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

  def project(pro: P) = pro.configuredProject

  def deps(pro: P) = pro.deps

  def params(pro: P) = pro.params

  def withParams(pro: P)(newParams: Params) =
    pro.copy(params = newParams)

  def refs(pro: P) = pro.deps.refs(pro.name) ++ pro.params.deps

  def info(pro: P) = {
    List(
      s" ○ project ${pro.name}"
    )
  }
}

trait ProjectInstances
{
  implicit def projectBuilder[D <: Deps]: ProjectBuilder[Project[D]] =
    new BasicProjectBuilder[D]
}
