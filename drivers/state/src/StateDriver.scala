package cycle

import com.raquo.laminar.api.L._

case class state[M, T](initial: EventStream[T] => Signal[M])
object state {

  type StateIO[M, T]    = MIO[M, T, T]
  type StateCycle[M, T] = Cycle[StateIO[M, T]]

  def apply[M](initial: => M): state[M, M] = state[M, M](_.startWith(initial))

  implicit def cycle[M: Tag, T: Tag](st: state[M, T]): StateCycle[M, T] = {
    user =>
      import st._
      user(MIO(initial))
  }
}

case class onion[A, B](bijection: MemBijection[A, B])(val from: EMO[A])
object onion {

  def layer[A: Tag, B: Tag](
      from: EMO[A]
  )(fwd: A => B)(bwd: (B, A) => A): (Binder[Element], EMO[B]) = {
    implicit val bij = memBijection[A, B](fwd, bwd)
    emoBiject(from)
  }

  implicit def cycle[A: Tag, B: Tag](onion: onion[A, B]): Cycle[EMO[B]] = {
    user =>
      import onion._
      implicit val bij   = onion.bijection
      val (binder, emoB) = emoBiject[A, B](from)
      amend(binder, user(emoB))
  }

}
