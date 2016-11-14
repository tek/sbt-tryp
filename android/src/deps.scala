package tryp

import reflect.macros.Context

import sbt._
import Keys._

import Types._

class AndroidTrypId(id: ModuleID, depspec: DepSpec, path: String,
  sub: List[String], dev: Boolean, hook: DepCond,
  cond: Option[SettingKey[Boolean]])
extends TrypId(id, depspec, path, sub, dev, hook, cond)
{
  override def info = {
    s"aar ${super.info}"
  }

  override def no =
    new AndroidTrypId(id, depspec, path, sub, false, hook, cond)
}

object AndroidDeps
{
  def adImpl(c: Context)(id: c.Expr[ModuleID], path: c.Expr[String],
    sub: c.Expr[String]*) =
  {
    import c.universe._
    c.Expr[AndroidTrypId] {
      q"""new tryp.AndroidTrypId(
        $id, libraryDependencies += $id, $path, List(..$sub), true, identity,
        None
      )
      """
    }
  }
}

trait AndroidDeps
extends Deps
{
  def unit = ids(
    "tryp" %% "speclectic" % "1.0.0"
  )

  def integration = ids(
    "com.jayway.android.robotium" % "robotium-solo" % "5.+"
  )

  def ad(id: ModuleID, path: String, sub: String*) = macro AndroidDeps.adImpl

  import ModuleID._

  override implicit def moduleIDtoTrypId(id: ModuleID) = {
    if (id.isAar)
      new AndroidTrypId(id, libraryDependencies += id, "", List(), false,
        identity, None)
    else super.moduleIDtoTrypId(id)
  }
}
