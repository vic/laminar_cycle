import mill._, scalalib._, scalajslib._

trait BaseModule extends ScalaJSModule {
  override def scalaVersion = "2.12.11"
  override def scalaJSVersion = "1.0.1"

  override def ivyDeps =  super.ivyDeps() ++ Agg(
    ivy"com.raquo::laminar::0.9.0"
  )
}

object cycle extends BaseModule

object examples extends Module {

  trait Example extends BaseModule {
    override def moduleDeps = Seq(cycle)
  }

  object cycle_counter extends Example
  object nested_indent extends Example

}
