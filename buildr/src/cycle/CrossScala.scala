package cycle

import scala.reflect.ClassTag
import mill.T
import mill.define.{Cross, Ctx}
import mill.scalajslib.ScalaJSModule

abstract class CrossScala[T: ClassTag](implicit ci: Cross.Factory[T], ctx: Ctx)
    extends Cross[T](Versions.crossScala: _*)

trait CrossScalaModule extends ScalaJSModule {
  def scalaCross: String
  def scalaJSCross: String

  override def scalaVersion: T[String]   = scalaCross
  override def scalaJSVersion: T[String] = scalaJSCross

  object resolver extends Cross.Resolver[CrossScalaModule] {
    def resolve[V <: CrossScalaModule](c: Cross[V]): V =
      c.get(List(scalaCross, scalaJSCross))
  }
}
