package object cycle extends cycle.core.API {
  import com.raquo.laminar.api.L._

  implicit class DriverPlus[A, R](private val aDriver: DriverFn[A, R])
      extends AnyVal {
    def ++[B](
        bDriver: DriverFn[B, R]
    )(
        implicit DriverFn: ((A, B), Binds) => DriverFn[(A, B), R]
    ): DriverFn[(A, B), R] = {
      val abDevices = (aDriver.devices, bDriver.devices)
      val abBinds   = aDriver.binds ++ bDriver.binds
      DriverFn(abDevices, abBinds)
    }
  }

}
