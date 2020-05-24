package example.zio_effects

import com.raquo.laminar.api.L._
import cycle.zioDriver._
import org.scalajs.dom
import zio._
import zio.clock.Clock
import zio.duration._

object ClockApp {

  def apply(): ZIO[Clock, NoSuchElementException, cycle.ModEl] =
    for {
      nanosQueue <- Queue.unbounded[Long]

      _ <- zio.clock.nanoTime
        .tap(nanosQueue.offer(_))
        .tap(v => UIO(dom.console.log("CLOCK", v.toString)))
        .tapCause(v => UIO(dom.console.error(v.prettyPrint)))
        .repeat(Schedule.fixed(1 second))
        .forkDaemon

      nanosDriver: cycle.Driver[cycle.In[Long]] <- nanosQueue.asIn
    } yield nanosDriver { nanos =>
      div(
        "ZIO CLOCK: ",
        code(child.text <-- nanos.map(_.toString))
      )
    }

}

object Main extends zio.App {

  val app = for {
    clock <- ClockApp()
  } yield {
    render(dom.document.getElementById("app"), div(clock))
  }

  override def run(args: List[String]): ZIO[ZEnv, Nothing, Int] =
    app.as(0).tapCause { cause =>
      UIO(dom.console.error(cause.toString))
    } orElse ZIO.succeed(1)

}
