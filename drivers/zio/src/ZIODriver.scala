package cycle

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L._
import com.raquo.laminar.nodes.ReactiveElement
import com.raquo.laminar.nodes.ReactiveElement.Base
import zio._
import zio.stream._

import org.scalajs.dom

object zioDriver {

  class ZDriver[R, E, +Devices](
      val devices: Devices,
      val binds: Binds
  ) extends DriverFn[Devices, ZIO[R, E, ModEl]] {
    override def cycle: CycleFn[Devices, ZIO[R, E, ModEl]] = { user =>
      user(devices).map(amend(binds, _))
    }
  }

  object ZDriver {
    def apply[R, E, Devices](
        devices: Devices,
        binds: Binder[Element]*
    ): ZDriver[R, E, Devices] = {
      new ZDriver(devices, binds)
    }

    implicit def fromDriver[R, E, D](d: Driver[D]): ZDriver[R, E, D] = {
      val (devices, binds) = d.toTuple
      ZDriver[R, E, D](devices, binds: _*)
    }
  }

  implicit class StreamOps[R, E, O](private val stream: ZStream[R, E, O])
      extends AnyVal {

    def asIn =
      for {
        _ <- ZIO.unit
        bus = new EventBus[O]
        binder <- writeToBus(bus.writer)
      } yield {
        Driver(In(bus.events), binder)
      }

    def writeToBus(
        wb: WriteBus[O]
    ): ZIO[R, Nothing, Binder[Base]] =
      for {
        runtime <- ZIO.runtime[R]
        drain: URIO[R, Fiber.Runtime[E, Unit]] = stream
          .tap(t => UIO(wb.onNext(t)))
          .runDrain
          .tapCause(cause =>
            UIO(dom.console.error("Failed draining stream", cause.prettyPrint))
          )
          .forkDaemon

      } yield Binder(
        ReactiveElement.bindSubscription(_) { ctx =>
          // Needed since in scala.js we cannot .unsafeRun sync.
          var draining: Fiber.Runtime[E, Unit] = null
          runtime.unsafeRunAsync(drain) {
            case Exit.Failure(cause) =>
              throw cause.dieOption.getOrElse(new Exception(cause.prettyPrint))
            case Exit.Success(fiber: Fiber.Runtime[E, Unit]) =>
//              dom.console.log("Fiber", fiber)
              draining = fiber
          }
          new Subscription(ctx.owner, cleanup = { () =>
            if (draining != null)
              runtime.unsafeRunAsync_(draining.interrupt)
          })
        }
      )

  }

  implicit class QueueOps[RA, RB, EA, EB, A, B](
      private val queue: ZQueue[RA, RB, EA, EB, A, B]
  ) extends AnyVal {

    def asEIO(
        implicit ev: A =:= B,
        ev1: RA =:= RB
    ): ZIO[RA with RB, NoSuchElementException, Driver[EIO[A]]] =
      for {
        (eventBus, binder) <- asEventBus
      } yield {
        Driver(EIO(eventBus), binder)
      }

    def asIn: ZIO[RB, NoSuchElementException, Driver[In[B]]] =
      for {
        (eventStream, binder) <- asEventStream
      } yield {
        Driver(In(eventStream), binder)
      }

    def asOut: ZIO[RA, NoSuchElementException, Driver[Out[A]]] =
      for {
        (writeBus, binder) <- asWriteBus
      } yield {
        Driver(Out(writeBus), binder)
      }

    def asCIO: ZIO[RA with RB, NoSuchElementException, Driver[CIO[B, A]]] =
      for {
        (in, inBinder)   <- asEventStream
        (out, outBinder) <- asWriteBus
      } yield {
        Driver(CIO(in, out), inBinder, outBinder)
      }

    def readFromEventStream(
        eb: EventStream[A]
    ): ZIO[RA, Nothing, Binder[Base]] =
      for {
        runtime <- ZIO.runtime[RA]
      } yield Binder(
        ReactiveElement.bindSubscription(_) { ctx =>
          eb.foreach(t => runtime.unsafeRunAsync_(queue.offer(t)))(ctx.owner)
        }
      )

    def writeToBus(
        wb: WriteBus[B]
    ): ZIO[RB, Nothing, Binder[Base]] =
      for {
        _ <- ZIO.unit
        stream = ZStream.fromQueue(queue)
        binder <- stream.writeToBus(wb)
      } yield binder

    def asEventBus(
        implicit ev: A =:= B,
        ev3: RA =:= RB
    ): ZIO[RA with RB, NoSuchElementException, (EventBus[A], Binder[Base])] =
      for {
        (in, inBinder)   <- asEventStream
        (out, outBinder) <- asWriteBus
        binder = Binder(
          ReactiveElement.bindCallback(_: Element) { ctx =>
            ctx.thisNode.amend(inBinder, outBinder)
          }
        )
        eventBus = new EventBus[A] {
          override val events: EventStream[A] = in.asInstanceOf[EventStream[A]]
          override val writer: WriteBus[A]    = out
        }
      } yield {
        eventBus -> binder
      }

    def asEventStream
        : ZIO[RB, NoSuchElementException, (EventStream[B], Binder[Base])] =
      for {
        _ <- ZIO.unit
        bus = new EventBus[B]
        binder <- writeToBus(bus.writer)
      } yield {
        bus.events -> binder
      }

    def asWriteBus: ZIO[RA, Nothing, (WriteBus[A], Binder[Base])] =
      for {
        _ <- ZIO.unit
        bus = new EventBus[A]
        binder <- readFromEventStream(bus.events)
      } yield {
        bus.writer -> binder
      }
  }

}
