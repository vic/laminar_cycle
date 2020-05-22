package object cycle extends cycle.core.API {
  import com.raquo.laminar.api.L._

  implicit class DriverPlus[A](private val aDriver: Driver[A]) extends AnyVal {
    def ++[B](
        bDriver: Driver[B]
    ): Driver[(A, B)] = {
      val abDevices = (aDriver.devices, bDriver.devices)
      val abBinds   = aDriver.binds ++ bDriver.binds
      Driver(abDevices, abBinds: _*)
    }
  }

}
