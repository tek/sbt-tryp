package tryp

import scalaz._, Scalaz._
import scalaz.std.list.listShow
import scalaz.Show

import monocle.Lens
import monocle.macros.Lenses

import sbt._
import sbt.Keys._

import bintray.BintrayPlugin

import Types._
import TrypBuildKeys._

object Export {
  lazy val settings = List(exportJars := true)
}

object Paradise {
  def settings(version: String) = List(
    incOptions := incOptions.value.withNameHashing(false),
    addCompilerPlugin(
      "org.scalamacros" % "paradise" % version cross CrossVersion.full
    )
  )
}

@Lenses
case class TemplateParams(write: Boolean = false,
  tokens: TemplatesKeys.Tokens = Map())

@Lenses
case class Params(settings: Setts, deps: List[SbtDep], name: String,
  path: String, bintray: Boolean, logback: TemplateParams,
  trans: List[sbt.Project => sbt.Project])

object Params
{
  def create(name: String, settings: Setts) = {
    Params(settings, Nil, name, name, false, TemplateParams(), List())
  }
}

abstract class ProjectI[A <: ProjectI[A]](implicit builder: ProjectBuilder[A])
{
  self: A =>

  lazy val reify: sbt.Project = builder.project(this)

  def ! = reify

  def info = builder.show(self)

  def name = builder.params(self).name
}

@Lenses
case class Project[D <: Deps](params: Params, deps: D)
extends ProjectI[Project[D]]

final class TransformIf[A](a: A) {
  def transformIf(pred: => Boolean)(transform: A => A) =
    if(pred) transform(a) else a
}

trait ToTransformIf
{
  implicit def ToTransformIf[A](a: A) = new TransformIf(a)
}

trait ToCD[A]
{
  def reify(project: A): ClasspathDep[ProjectReference]
}

object ToCD
{
  implicit def ProjectIToCD[A <: ProjectI[A]] =
    new ToCD[A] {
      def reify(project: A) = project.reify
    }

  implicit val sbtToCD =
    new ToCD[sbt.Project] {
      def reify(project: sbt.Project) = project
    }

  implicit def ClasspathDepToCD =
    new ToCD[ClasspathDep[ProjectReference]] {
      def reify(dep: ClasspathDep[ProjectReference]) = dep
    }

  implicit def ClasspathDependencyToCD =
    new ToCD[ClasspathDependency] {
      def reify(dep: ClasspathDependency) = dep
    }
}

trait ParamLensSyntax[Par, Pro]
{
  def pro: Pro
  def paramLens: Lens[Pro, Par]

  implicit class LensOps[A](l: Lens[Par, A])
  {
    def chain = paramLens ^|-> l

    def ⇐(v: A) = chain.set(v)
    def ⇐!(v: A) = l.⇐(v)(pro)
    def ++(v: A)(implicit m: Monoid[A]) = chain.append(v)
    def ++!(v: A)(implicit m: Monoid[A]) = l.++(v)(m)(pro)
    def ::(v: A)(implicit m: Monoid[A]) = chain.prepend(v)
    def !::(v: A)(implicit m: Monoid[A]) = l.::(v)(m)(pro)
  }

  implicit class MapLensOps[A, B](l: Lens[Par, Map[A, B]])
  {
    def ++(v: Map[A, B]) = l.chain.modify(_ ++ v)
  }

  implicit class BooleanLensOps(l: Lens[Par, Boolean])
  {
    def ! = l ⇐ true
    def !! = this.!(pro)
  }

  implicit class TemplateLensOps(l: Lens[Par, TemplateParams])
  {
    def write = l ^|-> TemplateParams.write
    def tokens = l ^|-> TemplateParams.tokens
  }
}

class ProjectOps[A](val pro: A)
(implicit builder: ProjectBuilder[A])
extends ToTransformIf
with ParamLensSyntax[Params, A]
{
  def name = builder.params(pro).name

  def deps = builder.deps(pro)

  def params = builder.params(pro)

  def paramLens = builder.paramLens

  val P = Params

  def export = {
    settings(Export.settings)
  }

  val path = P.path ⇐! _

  def at(pt: String) = path(pt)

  val ~ = at _

  def desc(text: String) = {
    settingsV(description := text)
  }

  val / = desc _

  def settings(extra: Seq[Setting[_]]) = {
    P.settings ++! extra.toList
  }

  def settingsPre(extra: Seq[Setting[_]]) = {
    extra.toList !:: P.settings
  }

  def settingsV(extra: Setting[_]*) = settings(extra.toList)

  def settingsVPre(extra: Setting[_]*) = settingsPre(extra.toList)

  def ++(s: List[Setting[_]]) = settings(s)

  def :+(s: Setting[_]) = settingsV(s)

  def ::(s: Setting[_]) = settingsPre(List(s))

  def :::(s: List[Setting[_]]) = settingsPre(s)

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

  def bintray = P.bintray.!!

  def logback(tokens: (String, String)*) = {
    (P.logback.tokens ++ tokens.toMap >>> P.logback.write.!)(pro)
  }

  def map(cb: sbt.Project => sbt.Project) = {
    P.trans ++! List(cb)
  }

  def reifyLogbackSettings = {
    params.logback.write ??
      List(generateLogback := true, logbackTokens ++= params.logback.tokens)
  }

  def macroConsole =
    settingsV(
      scalacOptions ++= {
        paradiseJar.value.map(p => s"-Xplugin:$p").toSeq
      },
      initialCommands in sbt.Keys.console += {
        val uni = """
          val universe: scala.reflect.runtime.universe.type =
            scala.reflect.runtime.universe
          import universe._
          import scala.tools.reflect.ToolBox
          import scala.reflect.runtime.currentMirror
          val tb = currentMirror.mkToolBox()
        """
        (if(paradiseJar.value.isDefined) uni else "")
      }
    )

  def console(imports: String) =
    settingsV(initialCommands in sbt.Keys.console += imports)

  def dep(pros: SbtDep*) = P.deps ++! pros.toList

  def refs = builder.refs(pro)

  def libraryDeps = deps(name)

  def project = builder.project(pro)

  def basicProject = {
    sbt.Project(name, file(params.path))
      .settings(reifyLogbackSettings)
      .dependsOn(refs: _*)
      .transformIf(!params.bintray)(_.disablePlugins(BintrayPlugin))
  }

  def configuredProject = {
    val p = basicProject
      .settings(libraryDeps ++ params.settings: _*)
    params.trans.foldLeft(p)((a, b) => b(a))
  }

  def <<[B](pro: B)(implicit tcd: ToCD[B]) = dep(tcd.reify(pro))

  def <<![B](pro: B)(implicit tcd: ToCD[B]) = builder.project(<<(pro))

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
  def params(pro: P): Params
  def deps(pro: P): Deps
  def project(pro: P): sbt.Project
  def refs(pro: P): List[SbtDep]
  def show(pro: P): List[String]
  def paramLens: monocle.Lens[P, Params]
}

object Project
extends ProjectInstances
with ToProjectOps
{
  def apply[D <: Deps](name: String, deps: D, defaults: Setts = Nil)
  : Project[D] =
    Project(Params.create(name, defaults), deps)

  implicit def ProjectToClasspathDep[D <: Deps](p: Project[D]): ClasspathDep[ProjectReference] = p.reify
  implicit def ProjectToProjectReference[D <: Deps](p: Project[D]): ProjectReference = p.reify
}

class BasicProjectBuilder[D <: Deps]
extends ProjectBuilder[Project[D]]
with ToTransformIf
{
  type P = Project[D]

  def project(pro: P) = pro.configuredProject

  def deps(pro: P) = pro.deps

  def params(pro: P) = pro.params

  def refs(pro: P) = pro.deps.refs(pro.name).toList ++ pro.params.deps

  def show(pro: P) = {
    import ProjectShow._
    val info = ProjectShow.deps(~pro.deps.deps.get(pro.name)) ++
      settings(pro) ++ ProjectShow.refs(pro)
    s" ○ project ${pro.name}" :: shift(info)
  }

  def paramLens = Project.params
}

object ProjectShow
extends ToProjectOps
{
  def settings[A: ProjectBuilder](pro: A) = {
    "settings:" :: shift(pro.params.settings.map(_.show.toString))
  }

  def deps(ds: List[TrypId]) = {
    if (ds.isEmpty) Nil
    else "deps:" :: shift(ds map(_.info))
  }

  def refs[A: ProjectBuilder](pro: A) = {
    val r = pro.refs map(_.toString)
    if (r.isEmpty) Nil
    else "refs:" :: shift(r)
  }

  def shift(lines: List[String]) = {
    lines map("  " + _)
  }
}

trait ProjectInstances
{
  implicit def projectBuilder[D <: Deps]: ProjectBuilder[Project[D]] =
    new BasicProjectBuilder[D]

  implicit def projectShow[A](implicit builder: ProjectBuilder[A]): Show[A] =
    new Show[A] {
      import ProjectShow._
      override def show(pro: A) = {
        builder.show(pro) mkString("\n")
      }
    }
}
