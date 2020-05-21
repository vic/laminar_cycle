package object cycle extends cycle.core.Core {

  implicit class CyclePlus[A <: Has[_]](val aCycle: Cycle[A]) extends AnyVal {
    def ++[B <: Has[_]](
        bCycle: Cycle[B]
    )(implicit ev: Tag[A], ev2: Tag[B]): Cycle[A with B] = { abUser =>
      aCycle { aDevices: A =>
        bCycle { bDevices: B => abUser(aDevices.unionAll[B](bDevices)) }
      }
    }
  }
}
