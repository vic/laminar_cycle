package cycle

import com.raquo.laminar.api.L._
import zio.{ZIO, Runtime}

object ZIODriver {

  private type Sense[-R, +E, +A] = ZIO[R, E, A]
  private type Actuator[+E, +A]  = Either[E, A]

  type FutureActuatorSense[R, E <: Throwable, A] = CIO[A, Sense[R, E, A]]

  def unsafeFuture[R, E <: Throwable, A](
      runtime: Runtime[R]
  )(user: FutureActuatorSense[R, E, A] => Mod[Element]): Mod[Element] = {
    val (io, oi) = CIO[Sense[R, E, A], A]

    val res: EventStream[A] = io.flatMap { effect: ZIO[R, E, A] =>
      EventStream.fromFuture { runtime.unsafeRunToFuture(effect) }
    }

    amend(
      res --> io,
      user(oi)
    )
  }

}
