package cycle

import com.raquo.laminar.api.L._
import zio.{CanFail, Runtime, ZIO}

object ZIODriver {

  def zioUnsafeEither[R: Tag, E: CanFail: Tag, A: Tag](
      runtime: Runtime[R]
  ): Cycle[CIO[Either[E, A], ZIO[R, E, A]], ModEl] = { user =>
    val pio = PIO[ZIO[R, E, A], Either[E, A]]

    val res: EventStream[Either[E, A]] = pio.flatMap { effect: ZIO[R, E, A] =>
      EventStream.fromValue(runtime.unsafeRun(effect.either), emitOnce = true)
    }

    amend(
      res --> pio,
      user(pio)
    )
  }

  def zioUnsafeFuture[R: Tag, E <: Throwable: Tag, A: Tag](
      runtime: Runtime[R]
  ): Cycle[CIO[A, ZIO[R, E, A]], ModEl] = { user =>
    val pio = PIO[ZIO[R, E, A], A]

    val res: EventStream[A] = pio.flatMap { effect: ZIO[R, E, A] =>
      EventStream.fromFuture { runtime.unsafeRunToFuture(effect) }
    }

    amend(
      res --> pio,
      user(pio)
    )
  }

}
