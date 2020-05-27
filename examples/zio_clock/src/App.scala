package example.zio_clock

import java.time.Instant

import com.raquo.laminar.api.L._
import cycle._
import cycle.zioDriver._
import org.scalajs.dom
import zio._
import zio.duration._

object ClockApp {

  val time = ZCycle[In[Instant], Element]

  def apply(): ZIO[ZEnv, Nothing, Mod[Element]] =
    for {
      timeQueue <- Queue.unbounded[Instant]

      _ <- ZIO
      // This could be zio.clock.currentDateTime but it wasn't working on my laptop
      // because the JS runtime does not have an America/Mexico_City timezone.
      // Anyways this is still a good example of ZIO fibers running in the background
        .effect(Instant.now)
        .tap(timeQueue.offer(_))
        .tapCause { cause => UIO(dom.console.error(cause.toString)) }
        .repeat(Schedule.fixed(1 second))
        .forkDaemon

      timeDriver <- timeQueue.zDriveIn
      view       <- viewTime.provideCustomLayer(time.cycleLayer(timeDriver))
    } yield timeDriver(_ => view)

  def viewTime: ZIO[time.HasCycle, Nothing, Mod[Element]] =
    time { io =>
      div(
        "ZIO CLOCK: ",
        pre(child.text <-- io.map(_.toString))
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
    app.as(0).tapCause { cause => UIO(dom.console.error(cause.toString)) }

}
