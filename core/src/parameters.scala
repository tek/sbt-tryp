package tryp

import reflect.macros.Context

import scala.collection.mutable.ListBuffer

import sbt._
import sbt.Keys._

import bintray.BintrayPlugin

object Types
{
  type DepSpec = Setting[Seq[ModuleID]]
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

class TrypId(normalId: DepSpec, providedId: DepSpec, path: String,
  sub: Seq[String], dev: Boolean)
{
  def no = new TrypId(normalId, providedId, path, sub, false)

  def id = if(dev) providedId else normalId

  def refs = {
    if (dev) {
      val r = if (sub.isEmpty) List(RootProject(Env.localProject(path)))
      else sub map { n ⇒ ProjectRef(Env.localProject(path), n) }
      r.map(a ⇒ a: ClasspathDep[ProjectReference])
    }
    else List()
  }
}

object Deps
{
  def ddImpl(c: Context)(id: c.Expr[ModuleID], path: c.Expr[String],
    sub: c.Expr[String]*) =
  {
    import c.universe._
    val prov = q"""${id} % "provided""""
    c.Expr[TrypId] {
      q"""new tryp.TrypId(
        libraryDependencies += $id, libraryDependencies += $prov,
        $path, Seq(..$sub), true
      )
      """
    }
  }

  def dImpl(c: Context)(id: c.Expr[ModuleID]) =
  {
    import c.universe._
    val prov = q"""${id} % "provided""""
    c.Expr[TrypId] {
      q"""new tryp.TrypId(
        libraryDependencies += $id, libraryDependencies += $prov,
        "", Seq(), false
      )
      """
    }
  }
}

trait Deps {
  implicit def ModuleIDtoTrypId(id: ModuleID) =
    new TrypId(libraryDependencies += id,
      libraryDependencies += id, "", List(), false)

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

  def manualDd(normal: DepSpec, prov: DepSpec,
    path: String, sub: String*) = new TrypId(normal, prov, path, sub, true)

  def defaultResolvers = Seq(
      Resolver.sonatypeRepo("releases"),
      Resolver.sonatypeRepo("snapshots"),
      Resolver.sonatypeRepo("staging"),
      Resolver.bintrayRepo("scalaz", "releases"),
      Resolver.jcenterRepo
    )

  def resolvers: Map[String, Seq[Resolver]] = Map()

  def apply(name: String): Seq[Setting[_]] = {
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

class ProjectBuilder[A](name: String, deps: Deps, defaultSettings: Setting[_]*)
{ self: A ⇒

  implicit class TransformIf[A](a: A) {
    def transformIf(pred: ⇒ Boolean)(transform: A ⇒ A) =
      if(pred) transform(a) else a
  }

  var pSettings = ListBuffer[Setting[_]](defaultSettings: _*)
  var pPath = name
  var pRootDeps: Seq[ProjectReference] = Seq()
  var pBintray = false

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

  def bintray = {
    pBintray = true
    this
  }

  def project(callback: (Project) ⇒ Project = identity) = {
    val pro = callback(Project(name, file(pPath)))
      .settings(deps(name) ++ pSettings: _*)
      .dependsOn(deps.refs(name): _*)
    pro.transformIf(!pBintray) {
      _.disablePlugins(BintrayPlugin)
    }
  }

  def dep(pros: ClasspathDep[ProjectReference]*) = {
    project { _.dependsOn(pros: _*) }
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
