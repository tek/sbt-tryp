package tryp

import scalaz._, Scalaz._

import monocle.Lens

class LensOps[A, B: Monoid](l: Lens[A, B]) {
  def append(addition: B) = l.modify(a ⇒ a ⊹ addition)
}

trait ToLensOps
{
  implicit def ToLensOps[A, B: Monoid](l: Lens[A, B]) = new LensOps(l)
}
