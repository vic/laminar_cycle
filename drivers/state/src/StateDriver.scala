package cycle

import com.raquo.laminar.api.L._
import com.raquo.laminar.nodes.ReactiveElement

object StateDriver {

  case class StateCycle[M, T](
      initial: EventStream[T] => Signal[M],
      user: User[MIO[M, T, T], ModEl]
  ) {
    def apply(implicit ev1: Tag[M], ev2: Tag[T]): ModEl = user(MIO(initial))
  }

  implicit def apply[M: Tag, T: Tag](s: StateCycle[M, T]): ModEl = s.apply

  def state[M, T] =
    (StateCycle[M, T] _).curried

  def state[T](t: => T) =
    (StateCycle[T, T] _).curried(_.startWith(t))

  case class LensCycle[A, B](
      bijection: MemBijection[A, B],
      emoA: EMO[A],
      user: User[EMO[B], ModEl]
  ) {
    def apply(implicit ev1: Tag[A], ev2: Tag[B]): ModEl = {
      Binder { el =>
        ReactiveElement.bindCallback(el) { ctx =>
          implicit val owner     = ctx.owner
          val signalB: Signal[B] = emoA.compose(bijection.fwd)
          val writeB: WriteBus[B] = emoA.contracomposeWriter { bStream =>
            bStream.withCurrentValueOf(emoA).compose(bijection.bwd)
          }
          val memB = hasMem(signalB) ++ hasOut(writeB)
          user(memB)(ctx.thisNode)
        }
      }
    }
  }

  implicit def apply[A: Tag, B: Tag](o: LensCycle[A, B]): ModEl = o.apply

  def lens[A, B] =
    (LensCycle[A, B] _).curried

}
