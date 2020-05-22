package object cycle extends cycle.core.API {

  implicit class CyclePlus[A <: Has[_]](private val aCycle: Cycle[A])
      extends AnyVal {
    def ++[B <: Has[_]](
        bCycle: Cycle[B]
    )(implicit ev: Tag[A], ev2: Tag[B]): Cycle[A with B] = { abUser =>
      aCycle { aDevices: A =>
        bCycle { bDevices: B => abUser(aDevices ++ bDevices) }
      }
    }
  }

  implicit class DriverPlus[A <: Has[A]](private val aDriver: Driver[A])
      extends AnyVal {
    def ++[B <: Has[_]](
        bDriver: Driver[B]
    )(implicit ev: Tag[A], ev2: Tag[B]): Driver[A with B] = {
      val abDevices = aDriver.devices ++ bDriver.devices
      val abBinds   = aDriver.binds ++ bDriver.binds
      Driver(abDevices, abBinds: _*)
    }
  }
}
