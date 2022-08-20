package cycle

import mill.{Agg, T}
import mill.scalalib.Dep

trait CycleCoreModule extends BaseModule {
  override def ivyDeps: T[Agg[Dep]] = super.ivyDeps() ++ Seq(Deps.laminar)
}
