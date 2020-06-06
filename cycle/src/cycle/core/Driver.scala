package cycle.core
import cycle._

import com.raquo.laminar.api.L._

private[core] trait DriverMeta {

  type UserMod[D, E <: Element]   = User[D, Mod[E]]
  type CycleMod[D, E <: Element]  = Cycle[D, Mod[E], User[D, Mod[E]]]
  type DriverMod[D, E <: Element] = Driver[D, E]

  type UserEl[D]   = UserMod[D, Element]
  type CycleEl[D]  = CycleMod[D, Element]
  type DriverEl[D] = DriverMod[D, Element]

  /**
    * A Driver is basically a tuple of a `device` and a `binder`.
    *
    * A `device` is an object than can be given to a `User` function in order
    * for it to produce a `B` result. In most cases the user function will return
    * an `E` element itself, or a modifier `Mod[E]`.
    *
    * The `binder` object of this driver specifies how to connect the driver's
    * inner streams. Most of the time it will be of type `Binder[E]` but can be
    * any modifier `Mod[E]` or an element `E`
    *
    * _Note:_ since drivers contain an inner binder that is actually a Laminar
    * modifier `Mod[E]`, you must understand that Laminar's Modifiers are not
    * guaranteed to be idempotent. Because of this, when you install a driver
    * (by using it as a modifier on an element of your view tree)
    * this driver's binder will be run.
    *
    * @see https://github.com/raquo/Laminar/blob/master/docs/Documentation.md#modifiers-faq
    *
    * @see #cycle - Obtain a re-usable cycle from this driver.
    * @see #apply - Install this driver, activating it's `binder`.
    *
    * @param device An object yielded to the user function.
    * @param binder A `Mod[E]` modifier that setups this driver's inner streams subscriptions.
    *
    * @tparam D Type of the device this driver yields.
    * @tparam E Type of the element this driver's binder is a modifier of.
    */
  sealed class Driver[D, E <: Element] private[Driver] (
      private[core] val device: D,
      private[core] val binder: Mod[E]
  ) extends Cycle[D, Mod[E], User[D, Mod[E]]] {

    // Similar to Laminar.emptyMod but generic on node type.
    private def emptyBinder[E0 <: Element]: Mod[E0] = new Modifier[E0] {}

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
    override def apply(user: User[D, Mod[E]]): Mod[E] =
      amend[E](binder, cycle(user))

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
    def cycle: Cycle[D, Mod[E], User[D, Mod[E]]] = { user => user(device) }

    /**
      * Yields a copy of this driver that will never bind and will never
      * call it's user function.
      *
      * @return A driver that will never bind
      */
    def never: Driver[D, E] =
      new Driver[D, E](device, emptyBinder[E]) {
        override def cycle: Cycle[D, Mod[E], User[D, Mod[E]]] =
          _ => emptyBinder[E]
      }

    /**
      * Lifts this driver as the device of a new Driver.
      *
      * Important Note: The new driver's binder does nothing.
      *
      * @see #liftCycle
      * @tparam E0 Type of new driver's modified element.
      * @return A new driver encapsulating this one.
      */
    def lift[E0 >: E <: Element]: Driver[Driver[D, E], E0] =
      new Driver[Driver[D, E], E0](
        device = this,
        binder = emptyBinder[E0]
      )

    /**
      * Lowers a lifted driver into a single one, combining the contained's
      * and the container's `binder` objects into one.
      *
      * @param ev - evidence that this is a lifted driver.
      * @tparam D0 Type of lifted device
      * @tparam E0 Type of lifted modified element
      * @return
      */
    def lower[D0, E0 >: E <: Element](
        implicit ev: D <:< Driver[D0, E0]
    ): Driver[D0, E] =
      new Driver[D0, E](
        device = device.device,
        binder = amend[E](binder, device.binder)
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
    def liftCycle[E0 >: E <: Element]: Driver[Driver[D, E0], E] =
      new Driver[Driver[D, E0], E](
        device = new Driver[D, E0](device, emptyBinder[E0]),
        binder = binder
      )

    def liftTuple[E0 >: E <: Element]: Driver[(D, Mod[E]), E0] =
      new Driver[(D, Mod[E]), E0](
        device = tuple,
        binder = emptyBinder[E0]
      )

    def tuple: (D, Mod[E]) = device -> binder

    def map[D2](f: D => D2): Driver[D2, E] =
      new Driver[D2, E](f(device), binder)

    /** alias for #flatMap */
    def >>=[D2, E0 >: E <: Element](f: D => Driver[D2, E0]): Driver[D2, E] =
      flatMap[D2, E0](f)

    def flatMap[D2, E0 >: E <: Element](f: D => Driver[D2, E0]): Driver[D2, E] =
      new Driver[Driver[D2, E0], E](f(device), binder).lower[D2, E0]

    def flatten[D2, E0 >: E <: Element](
        implicit ev: D <:< Driver[D2, E0]
    ): Driver[D2, E] =
      lower[D2, E0]

    def withFilter(p: D => Boolean): Driver[Option[D], E] =
      if (p(device)) map(Some(_)) else never.map(_ => None)

    /** alias for #zip */
    def ++[D2, E0 >: E <: Element](other: Driver[D2, E0]): Driver[(D, D2), E] =
      zip(other)
    def zip[D2, E0 >: E <: Element](other: Driver[D2, E0]): Driver[(D, D2), E] =
      new Driver[(D, D2), E](
        device = (device, other.device),
        binder = amend[E](binder, other.binder)
      )

    def map2[D0, D1, D2](
        f: (D0, D1) => D2
    )(implicit ev: D <:< (D0, D1)): Driver[D2, E] =
      map(d => f(d._1, d._2))

  }

  object Driver {
    def apply[D, E <: Element](device: D, mods: Mod[E]*) =
      value(device, mods: _*)

    def unapply[D, E <: Element](driver: Driver[D, E]): Option[(D, Mod[E])] =
      Some(driver.tuple)

    def value[D, E <: Element](device: D, mods: Mod[E]*): Driver[D, E] =
      new Driver[D, E](
        device,
        cycle.amend[E](mods: _*)
      )

    def unit[E <: Element]: Driver[Unit, E] =
      value[Unit, E](device = ())

    def bind[E <: Element](mods: Mod[E]*) =
      value[Unit, E](device = (), mods: _*)

    implicit def fromTuple[D, E <: Element](
        tuple: (D, Mod[E])
    ): Driver[D, E] =
      value[D, E](device = tuple._1, mods = tuple._2)

  }

}
