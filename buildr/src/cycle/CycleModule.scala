package cycle

import mill.Module
import os.Path

trait CycleModule extends Module {

  implicit val self: CycleModule = this

  override def millSourcePath: Path = super.millSourcePath / os.up

  object `cycle-core`                                               extends CrossScala[CycleCore]
  class CycleCore(val scalaCross: String, val scalaJSCross: String) extends CycleCoreModule

  object docs extends DocsModule

}
