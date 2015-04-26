package tryp

import sbt.Setting

object DefaultDeps extends Deps

abstract class MultiBuildBase(deps: Deps = DefaultDeps)
extends sbt.Build
{
  def globalSettings: List[Setting[_]] = Nil
}

class MultiBuild(deps: Deps = DefaultDeps)
extends MultiBuildBase
{
  def p(name: String) = new DefaultProjectBuilder(name, deps, globalSettings: _*)
}
