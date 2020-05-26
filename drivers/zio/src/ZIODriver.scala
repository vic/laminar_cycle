package cycle

import com.raquo.laminar.api.L._
import com.raquo.laminar.nodes.ReactiveElement
import org.scalajs.dom
import zio._
import zio.stream._

object zioDriver {

  class ZCycle[D: Tag, El <: Element: Tag] private[ZCycle] {
    type Devices  = D
    type Driver   = cycle.Driver[Devices, El]
    type Cycle    = cycle.Cycle[Devices, El]
    type User     = cycle.User[Devices, El]
    type HasCycle = zio.Has[Cycle]

    def cycleLayer[R, E](driver: Driver): ZLayer[R, E, HasCycle] =
      ZLayer.fromFunction(_ => driver.cycle)

    def apply[E](user: User): ZIO[HasCycle, E, Mod[El]] =
      ZIO.access[HasCycle](_.get).map(_.apply(user))
  }

  object ZCycle {
    def apply[Devices: Tag, El <: Element: Tag]: ZCycle[Devices, El] =
      new ZCycle[Devices, El]
  }

  implicit class StreamOps[R, E, O](
      private val stream: ZStream[R, E, O]
  ) extends AnyVal {

    def zDriveIn[El <: Element]: ZIO[R, Nothing, Driver[In[O], El]] =
      toEventStream[El].map(t => Driver(In(t._1), t._2))

    def toEventStream[El <: Element]
        : ZIO[R, Nothing, (EventStream[O], Mod[El])] =
      for {
        _ <- ZIO.unit
        bus = new EventBus[O]
        binder <- writeToBus[El](bus.writer)
      } yield {
        bus.events -> binder
      }

    def writeToBus[El <: Element](
        wb: WriteBus[O]
    ): ZIO[R, Nothing, Mod[El]] =
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
              dom.console.error("Failed forking drain", cause.prettyPrint)
              throw cause.dieOption.getOrElse(new Exception(cause.prettyPrint))
            case Exit.Success(fiber: Fiber.Runtime[E, Unit]) =>
              draining = fiber
          }
          new Subscription(ctx.owner, cleanup = { () =>
            if (draining != null) runtime.unsafeRunAsync_(draining.interrupt)
          })
        }
      )

  }

  implicit class QueueOps[RA, RB, EA, EB, A, B](
      private val queue: ZQueue[RA, RB, EA, EB, A, B]
  ) extends AnyVal {

    def readFromEventStream[El <: Element](
        eb: EventStream[A]
    ): ZIO[RA, Nothing, Mod[El]] =
      for {
        runtime <- ZIO.runtime[RA]
      } yield Binder(
        ReactiveElement.bindSubscription(_) { ctx =>
          eb.foreach(t => runtime.unsafeRunAsync_(queue.offer(t)))(ctx.owner)
        }
      )

    def writeToBus[El <: Element](
        wb: WriteBus[B]
    ): ZIO[RB, Nothing, Mod[El]] =
      for {
        _ <- ZIO.unit
        stream = ZStream.fromQueue(queue)
        binder <- stream.writeToBus[El](wb)
      } yield binder

    def zDriveCIO[El <: Element]
        : ZIO[RA with RB, Nothing, Driver[InOut[B, A], El]] =
      for {
        inDriver  <- zDriveIn[El]
        outDriver <- zDriveOut[El]
      } yield {
        val devices = CIO(inDriver.devices.in, outDriver.devices.out)
        Driver(devices, inDriver.binder, outDriver.binder)
      }

    def toEventStream[El <: Element]
        : ZIO[RB, Nothing, (EventStream[B], Mod[El])] =
      for {
        _ <- ZIO.unit
        bus = new EventBus[B]
        binder <- writeToBus[El](bus.writer)
      } yield {
        bus.events -> binder
      }

    def zDriveIn[El <: Element]: ZIO[RB, Nothing, Driver[In[B], El]] =
      toEventStream[El].map(t => Driver(In(t._1), t._2))

    def toWriteBus[El <: Element]: ZIO[RA, Nothing, (WriteBus[A], Mod[El])] =
      for {
        _ <- ZIO.unit
        bus = new EventBus[A]
        binder <- readFromEventStream[El](bus.events)
      } yield {
        bus.writer -> binder
      }

    def zDriveOut[El <: Element]: ZIO[RA, Nothing, Driver[Out[A], El]] =
      toWriteBus[El].map(t => Driver(Out(t._1), t._2))
  }

  implicit class EventStreamOps[A](
      private val eb: EventStream[A]
  ) extends AnyVal {

    def toZQueue[El <: Element](
        capacity: Int = 16
    ): ZIO[Any, Nothing, (Queue[A], Mod[El])] =
      for {
        queue  <- ZQueue.bounded[A](capacity)
        binder <- intoZQueue[El](queue)
      } yield queue -> binder

    def intoZQueue[El <: Element](queue: Queue[A]): ZIO[Any, Nothing, Mod[El]] =
      queue.readFromEventStream[El](eb)

    def toZStream[El <: Element](capacity: Int = 16): ZIO[
      Any,
      Nothing,
      (ZStream[Any, Nothing, A], Mod[El])
    ] =
      for {
        queueAndBinder <- toZQueue[El](capacity)
        (queue, binder) = queueAndBinder
        stream          = ZStream.fromQueueWithShutdown(queue)
      } yield stream -> binder

  }

  implicit class WriteBusOps[A](private val wb: WriteBus[A]) extends AnyVal {
    def toZQueue[El <: Element](capacity: Int = 16) =
      for {
        queue  <- ZQueue.bounded[A](capacity)
        binder <- intoZQueue[El](queue)
      } yield queue -> binder

    def intoZQueue[El <: Element](queue: Queue[A]): ZIO[Any, Nothing, Mod[El]] =
      queue.writeToBus[El](wb)

    def toZStream[El <: Element](capacity: Int = 16): ZIO[
      Any,
      Nothing,
      (ZStream[Any, Nothing, A], Mod[El])
    ] =
      for {
        queueAndBinder <- toZQueue[El](capacity)
        (queue, binder) = queueAndBinder
        stream          = ZStream.fromQueueWithShutdown(queue)
      } yield stream -> binder
  }

}
