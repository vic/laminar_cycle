package cycle

import com.raquo.laminar.api.L._

case class state[M, T](initial: EventStream[T] => Signal[M]) extends AnyVal
object state {
  type StateIO[M, T] = MIO[M, T, T]

  def apply[M](initial: => M): state[M, M] = state[M, M](_.startWith(initial))

  implicit def driver[M: Tag, T: Tag](
      state: state[M, T]
  ): Driver[StateIO[M, T]] = Driver(MIO(state.initial))
}

case class onion[A, B](bijection: MemBijection[A, B])(val from: EMO[A])
object onion {

  def layer[A: Tag, B: Tag](
      from: EMO[A]
  )(fwd: A => B)(bwd: (B, A) => A): Driver[EMO[B]] = {
    implicit val bij = memBijection[A, B](fwd, bwd)
    emoBiject[A, B](from)
  }

  implicit def driver[A: Tag, B: Tag](onion: onion[A, B]): Driver[EMO[B]] = {
    implicit val bij = onion.bijection
    emoBiject[A, B](onion.from)
  }

}
