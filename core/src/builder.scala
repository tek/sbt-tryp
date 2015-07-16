package tryp

import reflect.macros.Context

import scala.collection.mutable.ListBuffer

import sbt._
import sbt.Keys._

import bintray.BintrayPlugin

object Types
{
  type DepSpec = Setting[Seq[ModuleID]]
  type Setts = Seq[Setting[_]]
}
import Types._

object Env
{
  val current = sys.props.getOrElse("env", "development")

  def development = current == "development"

  val projectBaseEnvVar = "TRYP_SCALA_PROJECT_DIR"

  lazy val projectBase = new File(
    sys.env.get(projectBaseEnvVar)
      .getOrElse(sys.error(s"Need to set $$${projectBaseEnvVar}"))
  )

  def cloneRepo(path: String, dirname: String) = {
    s"hub clone $path ${Env.projectBase}/$dirname" !
  }

  def localProject(path: String) = {
    val dirname = path.split("/").last
    val localPath = Env.projectBase / dirname
    if (!localPath.isDirectory) cloneRepo(path, dirname)
    localPath
  }
}

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

class TrypId(plainId: DepSpec, path: String, sub: Seq[String], dev: Boolean)
{
  def no = new TrypId(plainId, path, sub, false)

  def development = Env.development && dev

  def id = if(development) TrypId.empty else plainId

  def projects = {
    if (sub.isEmpty) List(RootProject(Env.localProject(path)))
    else sub map { n ⇒ ProjectRef(Env.localProject(path), n) }
  }

  def refs = {
    if (development) projects.map(a ⇒ a: ClasspathDep[ProjectReference])
    else List()
  }
}

object TrypId
{
  def empty = libraryDependencies ++= List()
}

object Deps
{
  def ddImpl(c: Context)(id: c.Expr[ModuleID], path: c.Expr[String],
    sub: c.Expr[String]*) =
  {
    import c.universe._
    c.Expr[TrypId] {
      q"""new tryp.TrypId(
        libraryDependencies += $id, $path, Seq(..$sub), true
      )
      """
    }
  }

  def dImpl(c: Context)(id: c.Expr[ModuleID]) =
  {
    import c.universe._
    c.Expr[TrypId] {
      q"""new tryp.TrypId(libraryDependencies += $id, "", Seq(), false)"""
    }
  }
}

trait Deps {
  implicit def ModuleIDtoTrypId(id: ModuleID) =
    new TrypId(libraryDependencies += id, "", List(), false)

  implicit class MapShortcuts[A, B](m: Map[A, _ <: Seq[B]]) {
    def fetch(key: A) = m.get(key).toSeq.flatten
  }

  def ids(i: TrypId*) = Seq[TrypId](i: _*)

  def deps: Map[String, Seq[TrypId]] = Map(
    "unit" → unit,
    "integration" → integration
  )

  def dd(id: ModuleID, path: String, sub: String*) = macro Deps.ddImpl

  def d(id: ModuleID) = macro Deps.dImpl

  def manualDd(normal: DepSpec, path: String, sub: String*) =
    new TrypId(normal, path, sub, true)

  def defaultResolvers = Seq(
      Resolver.sonatypeRepo("releases"),
      Resolver.sonatypeRepo("snapshots"),
      Resolver.sonatypeRepo("staging"),
      Resolver.bintrayRepo("scalaz", "releases"),
      Resolver.jcenterRepo
    )

  def resolvers: Map[String, Seq[Resolver]] = Map()

  def apply(name: String): Setts = {
    Seq(Keys.resolvers ++= defaultResolvers ++ this.resolvers.fetch(name)) ++
      (common ++ deps.fetch(name)).map(_.id)
  }

  def refs(name: String) = {
    (common ++ deps.get(name).toSeq.flatten).map(_.refs).flatten
  }

  val scalazV = "7.1.+"
  val specsV = "3.6"

  def common = ids(
    "org.scalaz" %% "scalaz-concurrent" % scalazV
  )

  def unit = ids(
    "org.scalatest" %% "scalatest" % "2.2.+",
    "org.specs2" %% "specs2-core" % specsV
  )

  def integration = ids(
    "org.scalatest" %% "scalatest" % "2.2.+",
    "org.specs2" %% "specs2-core" % specsV
  )
}

case class ProjectParams(settings: Setts, path: String, bintray: Boolean,
  transitive: Boolean)

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

  def path(p: String) = {
    copy(params.copy(path = p))
  }

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

  def project(callback: (Project) ⇒ Project = identity) = {
    callback(Project(name, file(params.path)))
      .settings(deps(name) ++ params.settings: _*)
      .dependsOn(deps.refs(name): _*)
      .transformIf(!params.bintray) { _.disablePlugins(BintrayPlugin) }
  }

  def dep(pros: ClasspathDep[ProjectReference]*) = {
    project { _.dependsOn(pros: _*) }
  }

  def apply() = project()

  def aggregate(projects: ProjectReference*) = {
    project().aggregate(projects: _*)
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
  def apply(name: String, deps: Deps, defaults: Setts) =
    new DefaultProjectBuilder(name, deps,
      ProjectParams(defaults, name, false, false))
}
