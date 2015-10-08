package android

import sbt._
import sbt.Keys._

import android.Keys.Internal._

object Intrusion
{
  def aars = Def.task {
    transitiveAars.value
  }
}
