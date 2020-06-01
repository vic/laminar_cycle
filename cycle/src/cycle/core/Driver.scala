package cycle.core
import cycle._

import com.raquo.laminar.api.L._

private[core] trait Driver {

  type UserMod[D, N <: Node]   = User[D, Mod[N]]
  type CycleMod[D, N <: Node]  = Cycle[D, Mod[N], User[D, Mod[N]]]
  type DriverMod[D, N <: Node] = Driver[D, Mod[N]]

  type UserEl[D]   = UserMod[D, Element]
  type CycleEl[D]  = CycleMod[D, Element]
  type DriverEl[D] = DriverMod[D, Element]

  /**
    * Combine two binders into one.
    *
    * @see cycle#amend
    * @tparam B - In most cases this will be a Laminar Modifier: `Mod[El]`
    */
  trait Bind[B] {
    def apply(a: B, b: B): B
  }

  /**
    * A Driver is basically a tuple of a `device` and a `binder` objects.
    *
    * A `device` is an object than can be given to a `Cycle` function in order
    * for it to produce a `V`. In most cases V will be something like a
    * Laminar Mod[El], like an Element view itself or an stream Binder.
    *
    * The `binder` object of this driver is a Laminar Binder[El] (or any Mod[El])
    * that specifies how to connect the driver's inner streams in order for it to work.
    *
    * Note, since a `binder` can be any Laminar modifier, you must understand
    * Laminar modifier's semantics. Since mounting a driver in different places
    * will invoke the driver's binder in those respective places (calling the modifier).
    *
    * If you want to share a driver devices into many sub-components, bind this driver
    * only once and use the `Driver#cycle` method to obtain a re-usable cycle from
    * this driver's devices.
    *
    * @see #cycle - Obtain a re-usable cycle from this driver.
    * @see #apply - Install this driver, activating it's `binder`.
    *
    * @param device
    * @param binder
    * @param emptyBinder
    * @param bindFn
    * @tparam D
    * @tparam B
    */
  sealed class Driver[D, B] private[Driver] (
      private[core] val device: D,
      private[core] val binder: B,
      private[core] val emptyBinder: B,
      private[core] val bindFn: Bind[B]
  ) extends Cycle[D, B, User[D, B]]
      with DriverOps[D, B] {

    /**
      *
      * Install this driver. Activate it's `binder`, and yield it's `device` into
      * the provided User function.
      *
      * Important Note: Calling this function will actually start this driver's
      * inner streams by means of calling the `binder` modifier.
      *
      * You might want call this function only once (depending on the Driver's nature)
      * and pass the yielded devices around to sub-components.
      *
      * @see #cycle
      * @param user - A function taking this driver devices and producing it's own modifier.
      * @return The combination of this driver's binder and the result of the user function.
      */
    override def apply(user: User[D, B]): B = bindFn(binder, cycle(user))

    /**
      * Obtain a Cycle function that can be passed around and invoked many times.
      *
      * Important Note: Since drivers have a `binder` object that can start their
      * inner stream subscriptions. The Cycle returned by this function *WONT*
      * use the `binder` object. Thus, allowing you to pass the resulting cycle
      * around and call it many times (possibly having other sub-components read
      * the driver's devices).
      *
      * @see #apply
      * @return A cycle function that only yields this driver devices.
      */
    def cycle: Cycle[D, B, User[D, B]] = { user => user(device) }

    /**
      * Yields a copy of this driver that will never bind and will never
      * call it's user function.
      *
      * @return
      */
    def never: Driver[D, B] =
      new Driver[D, B](device, emptyBinder, emptyBinder, bindFn) {
        override def cycle: Cycle[D, B, User[D, B]] = _ => emptyBinder
      }

    /**
      * Lifts this driver as the device of a new Driver.
      *
      * Important Note: The new driver's binder does nothing.
      *
      * @see #liftCycle
      * @return A new driver encapsulating this one.
      */
    def lift: Driver[Driver[D, B], B] =
      copy(newDevice = this, newBinder = emptyBinder)

    /**
      * Lowers a lifted driver into a single one, combining the contained's
      * and the container's `binder` objects into one.
      *
      * @param ev - evidence that this is a lifted driver.
      * @tparam D0
      * @return
      */
    def lower[D0](implicit ev: D <:< Driver[D0, B]): Driver[D0, B] =
      copy(
        newDevice = device.device,
        newBinder = bindFn(binder, device.binder)
      )

    /**
      * Lifts only this driver's devices into a new Driver
      * keeping the `binder` in a lower level.
      *
      * This means the lifted Driver has a binder that does nothing,
      * but the container Driver has the original binder object.
      *
      * In other words, the contained driver is totally re-usable.
      * But streams will only work if the outer driver is installed.
      *
      * @return An split new driver
      */
    def liftCycle: Driver[Driver[D, B], B] =
      copy(newDevice = copy(newDevice = device, newBinder = emptyBinder))

    def widen[B0 >: B]: Driver[D, B0] = asInstanceOf[Driver[D, B0]]

    private[core] def copy[D2](
        newDevice: D2 = device,
        newBinder: B = binder
    ): Driver[D2, B] =
      new Driver(
        device = newDevice,
        binder = newBinder,
        emptyBinder = emptyBinder,
        bindFn = bindFn
      )

  }

  object Driver {
    def apply[D, El <: Element](device: D, mods: Mod[El]*) =
      value(device, mods: _*)

    def unapply[D, B](driver: Driver[D, B]): Option[(D, B)] =
      Some(driver)

    def value[D, El <: Element](device: D, mods: Mod[El]*): Driver[D, Mod[El]] =
      new Driver[D, Mod[El]](
        device,
        cycle.amend[El](mods: _*),
        emptyMod,
        cycle.amend[El](_, _)
      )

    def unit[El <: Element]: Driver[Unit, Mod[El]] =
      value[Unit, El](device = ())

    def bind[El <: Element](mods: Mod[El]*) =
      value[Unit, El](device = (), mods: _*)

    implicit def asTuple[D, B](driver: Driver[D, B]): (D, B) =
      driver.device -> driver.binder

    implicit def fromTuple[D, El <: Element](
        tuple: (D, Mod[El])
    ): Driver[D, Mod[El]] =
      value[D, El](device = tuple._1, mods = tuple._2)

  }

  trait DriverOps[D, B] { driver: Driver[D, B] =>
    def tuple: (D, B) = driver

    def map[D2](f: D => D2): Driver[D2, B] =
      copy(f(device))

    /** alias for #flatMap */
    def >>=[D2](f: D => Driver[D2, B]): Driver[D2, B] = flatMap(f)
    def flatMap[D2](f: D => Driver[D2, B]): Driver[D2, B] =
      copy(f(device)).lower

    def flatten[D2](implicit ev: D <:< Driver[D2, B]): Driver[D2, B] =
      lower

    def withFilter(p: D => Boolean): Driver[Option[D], B] =
      if (p(device)) driver.map(Some(_)) else never.map(_ => None)

    /** alias for #zip */
    def ++[D2](other: Driver[D2, B]): Driver[(D, D2), B] = zip(other)
    def zip[D2](other: Driver[D2, B]): Driver[(D, D2), B] =
      copy(
        newDevice = (device, other.device),
        newBinder = bindFn(binder, other.binder)
      )

    def map2[D0, D1, D2](
        f: (D0, D1) => D2
    )(implicit ev: D <:< (D0, D1)): Driver[D2, B] =
      map(d => f(d._1, d._2))

    def widen[B0 >: B](f: B => B0): Driver[D, B0] =
      widen[B0].copy(newBinder = f(binder))

  }

}
