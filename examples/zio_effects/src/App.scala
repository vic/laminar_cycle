package example.zio_effects

import com.raquo.laminar.api.L._
import cycle._
import org.scalajs.dom
import zio._

object Example {

  type ZD = ZDriver[ZEnv, Nothing, ZD.API]

  def apply(): ZIO[Any, Nothing, Div] =
    for {
      _ <- ZIO.unit
    } yield {
      div(
        "HELLO ZIO"
      )
    }

}

object ZD {
  trait API {}
  def apply[R, E]: ZIO[R, E, ZDriver[R, E, API]] =
    for {
      rt <- ZIO.runtime
    } yield {
      val api = new API {}
      ZDriver(api)
    }
}

object Main extends zio.App {

  val app = for {
    el <- Example()
  } yield {
    render(dom.document.getElementById("app"), el)
  }

  override def run(args: List[String]): ZIO[ZEnv, Nothing, Int] =
    app.as(0)
//  orElse ZIO.succeed(1)

}
