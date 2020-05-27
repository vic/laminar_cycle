package cycle

import com.raquo.laminar.api.L._

object state {
  type Devices[M, T] = MIO[M, T, T]

  def apply[M, T](
      initial: EventStream[T] => Signal[M]
  ): Driver[Devices[M, T]] =
    Driver(MIO(initial))

  def apply[M](initial: => M): Driver[Devices[M, M]] =
    Driver(MIO(initial))
}

object onion {

  def apply[A, B](
      from: EMO[A]
  )(fwd: A => B)(bwd: (B, A) => A): Driver[EMO[B]] = {
    implicit val bij = memBijection[A, B](fwd, bwd)
    emoBiject[A, B](from)
  }

  def apply[A, B](
      bijection: MemBijection[A, B]
  )(from: EMO[A]): Driver[EMO[B]] = {
    implicit val bij = bijection
    emoBiject[A, B](from)
  }

}
