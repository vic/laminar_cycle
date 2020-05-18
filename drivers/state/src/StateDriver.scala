package cycle

import com.raquo.laminar.api.L._
import com.raquo.laminar.nodes.ReactiveElement

case class state[M, T](initial: EventStream[T] => Signal[M])
object state {

  type StateIO[M, T]    = MIO[M, T, T]
  type StateCycle[M, T] = Cycle[StateIO[M, T], ModEl]

  def apply[M](initial: => M): state[M, M] = state[M, M](_.startWith(initial))

  implicit def cycle[M: Tag, T: Tag](
      st: state[M, T]
  ): StateCycle[M, T] = { user =>
    import st._
    user(MIO(initial))
  }
}

case class stateSlice[A, B](bijection: MemBijection[A, B])(val emoA: EMO[A])
object stateSlice {

  implicit def cycle[A: Tag, B: Tag](
      slice: stateSlice[A, B]
  ): Cycle[EMO[B], ModEl] = { user =>
    import slice._
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
