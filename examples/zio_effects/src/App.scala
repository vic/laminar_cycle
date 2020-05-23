package example.zio_effects

import java.time.DateTimeException
import java.util.concurrent.TimeUnit

import com.raquo.laminar.api.L._
import cycle._
import org.scalajs.dom
import zio._
import zio.duration._

object Example {

  type ZD = ZDriver[ZEnv, Nothing, ZD.API]

  def apply() =
    for {
      _ <- ZIO.unit
      names = new EventBus[String]
      counter <- zio.clock.nanoTime.tap { time =>
        ZIO.effectTotal {
          println(time)
          names.writer.onNext(time.toString)
        }
      }.repeat(Schedule.fixed(1 second)).forkDaemon
    } yield {
      div(
        "HELLO ZIO",
        child.text <-- names.events
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
    app.as(0).tapCause { cause =>
      ZIO.effectTotal {
        println(cause.toString)
      }
    } // orElse ZIO.succeed(1)

}
