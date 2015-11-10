package tryp

import scalaz._, Scalaz._

import sbt.Setting

trait SettingInstances
{
  implicit def settingShow[A] = new Show[Setting[A]]
  {
    override def show(s: Setting[A]) = s.key.key.label
  }
}
