package example.zio_effects

import com.raquo.laminar.api.L._
import cycle.zioDriver._
import org.scalajs.dom
import zio._
import zio.duration._

object Example {

  def apply() =
    for {
      nanos <- Queue.unbounded[Long]

      _ <- zio.clock.nanoTime
        .tap(nanos.offer(_))
        .tap(v => UIO(dom.console.log("CLOCK", v.toString)))
        .tapCause(v => UIO(dom.console.error(v.prettyPrint)))
        .repeat(Schedule.fixed(1 second))
        .forkDaemon

      nanosIn: cycle.Driver[cycle.In[Long]] <- nanos.asIn
    } yield {
      div(
        "ZIO CLOCK: ",
        nanosIn(child.text <-- _.map(_.toString))
      )
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
      UIO(dom.console.error(cause.toString))
    } orElse ZIO.succeed(1)

}
