package example.zio_effects

import java.time.Instant

import com.raquo.laminar.api.L._
import com.raquo.laminar.nodes.ReactiveHtmlElement
import cycle._
import cycle.zioDriver._
import org.scalajs.dom
import org.scalajs.dom.html
import zio._
import zio.clock.Clock
import zio.duration._

object ClockApp {

  def apply(): ZIO[Clock, NoSuchElementException, ModEl] =
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

      // In Laminar.cycle, Drivers can perform effectful reads
      // in this case, we want an input device: `In[Instant]` we can read from.
      timeDriver: TimeDriver <- timeQueue.zDriveIn

      view <- viewTime.provide(Has(timeDriver))
    } yield view

  type TimeDriver = cycle.Driver[cycle.In[Instant]]
  type TimeUser   = cycle.User[cycle.In[Instant]]

  def time(user: TimeUser): ZIO[Has[TimeDriver], Nothing, ModEl] =
    ZIO.access(_.get.apply(user))

  def viewTime: ZIO[Has[TimeDriver], Nothing, ModEl] =
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
    app.as(0).tapCause { cause =>
      UIO(dom.console.error(cause.toString))
    } orElse ZIO.succeed(1)

}
