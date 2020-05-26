package cycle

import com.raquo.laminar.api.L._

object state {
  type Devices[M, T] = MIO[M, T, T]

  def apply[M, T, El <: Element](
      initial: EventStream[T] => Signal[M]
  ): Driver[Devices[M, T], El] =
    Driver(MIO(initial))

  def apply[M, El <: Element](initial: => M): Driver[Devices[M, M], El] =
    Driver(MIO(initial))
}

object onion {

  def apply[A, B, El <: Element](
      from: EMO[A]
  )(fwd: A => B)(bwd: (B, A) => A): Driver[EMO[B], El] = {
    implicit val bij = memBijection[A, B](fwd, bwd)
    emoBiject[A, B, El](from)
  }

  def apply[A, B, El <: Element](
      bijection: MemBijection[A, B]
  )(from: EMO[A]): Driver[EMO[B], El] = {
    implicit val bij = bijection
    emoBiject[A, B, El](from)
  }

}
