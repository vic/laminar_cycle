package object cycle extends cycle.core.API {
  import com.raquo.laminar.api.L._

  implicit class DriverPlus[A, El <: Element](
      private val aDriver: Driver[A, El]
  ) extends AnyVal {
    def ++[B](
        bDriver: Driver[B, El]
    ): Driver[(A, B), El] = {
      val abDevices = (aDriver.devices, bDriver.devices)
      Driver(abDevices, aDriver.binder, bDriver.binder)
    }
  }

}
