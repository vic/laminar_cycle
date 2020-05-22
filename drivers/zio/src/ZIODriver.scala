package cycle

import com.raquo.laminar.api.L._
import zio.{CanFail, Runtime, ZIO, URIO, RIO}

case class zioUnsafeRun[R, E, A](runtime: Runtime[R])
object zioUnsafeRun {

  type InfallibleIO[R, A] = CIO[A, URIO[R, A]]
  implicit def cycleInfallible[R, A](
      zura: zioUnsafeRun[R, Nothing, A]
  ): Cycle[InfallibleIO[R, A]] = { user =>
    import zura._
    val pio = PIO[URIO[R, A], A]

    val res: EventStream[A] = pio.flatMap { effect: URIO[R, A] =>
      EventStream.fromValue(runtime.unsafeRun(effect), emitOnce = true)
    }

    amend(
      res --> pio,
      user(pio)
    )
  }

  type EitherIO[R, E, A] = CIO[Either[E, A], ZIO[R, E, A]]
  implicit def cycleFallible[R, E: CanFail, A](
      zura: zioUnsafeRun[R, E, A]
  ): Cycle[EitherIO[R, E, A]] = { user =>
    import zura._
    val pio = PIO[ZIO[R, E, A], Either[E, A]]

    val res: EventStream[Either[E, A]] = pio.flatMap { effect: ZIO[R, E, A] =>
      EventStream.fromValue(runtime.unsafeRun(effect.either), emitOnce = true)
    }

    amend(
      res --> pio,
      user(pio)
    )
  }

  type FutureIO[R, A] = CIO[A, RIO[R, A]]
  implicit def cycleFuture[R, Throwable, A](
      zura: zioUnsafeRun[R, Throwable, A]
  ): Cycle[FutureIO[R, A]] = { user =>
    import zura._
    val pio = PIO[RIO[R, A], A]

    val res: EventStream[A] = pio.flatMap { effect: RIO[R, A] =>
      EventStream.fromFuture { runtime.unsafeRunToFuture(effect) }
    }

    amend(
      res --> pio,
      user(pio)
    )
  }

}
