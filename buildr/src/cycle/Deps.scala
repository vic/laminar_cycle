package cycle

import mill.scalalib.DepSyntax

object Deps {
  val javaTime   = ivy"io.github.cquiroz::scala-java-time::2.0.0"
  val laminar    = ivy"com.raquo::laminar::${Versions.laminar}"
  val urlDsl     = ivy"be.doeraene::url-dsl::0.4.0"
  val zio        = ivy"dev.zio::zio::${Versions.zio}"
  val zioStreams = ivy"dev.zio::zio-streams::${Versions.zio}"
}
