package tryp

import reflect.macros.Context

import sbt._
import Keys._

import Types._

class AndroidTrypId(id: ModuleID, depspec: DepSpec, path: String,
  sub: List[String], dev: Boolean)
extends TrypId(id, depspec, path, sub, dev)
{
  def aRefs = {
    if (development) super.projects
    else Nil
  }

  override def refs = Nil

  override def info = {
    s"aar ${super.info}"
  }

  override def no = new AndroidTrypId(id, depspec, path, sub, false)
}

object AndroidDeps
{
  def adImpl(c: Context)(id: c.Expr[ModuleID], path: c.Expr[String],
    sub: c.Expr[String]*) =
  {
    import c.universe._
    c.Expr[AndroidTrypId] {
      q"""new tryp.AndroidTrypId(
        $id, libraryDependencies += android.Keys.aar($id), $path, List(..$sub),
        true
      )
      """
    }
  }
}

trait AndroidDeps
extends Deps
{
  override def unit = super.unit ++ ids(
    "tryp" %% "speclectic" % "+"
  )

  def integration = ids(
    "com.jayway.android.robotium" % "robotium-solo" % "5.+"
  )

  def ad(id: ModuleID, path: String, sub: String*) = macro AndroidDeps.adImpl

  def aRefs(name: String) = {
    (common ++ deps.fetch(name)).flatMap {
      case id: AndroidTrypId ⇒ id.aRefs
      case _ ⇒ List()
    }
  }

  import ModuleID._

  override implicit def moduleIDtoTrypId(id: ModuleID) = {
    if (id.isAar)
      new AndroidTrypId(id, libraryDependencies += id, "", List(), false)
    else super.moduleIDtoTrypId(id)
  }
}
