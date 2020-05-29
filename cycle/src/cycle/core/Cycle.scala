package cycle.core

import cycle._
import com.raquo.laminar.api.L._

private[core] trait Core {

  /**
    * Combine two binders into one.
    *
    * @see cycle#amend
    * @tparam A - In most cases this will be a Laminar Modifier: `Mod[El]`
    */
  trait Bind[A] {
    def apply(a: A, b: A): A
  }

  /**
    * A User function takes devices of type `D` and returns a `V` which can be
    * either a Laminar view or just Laminar binders.
    *
    * @tparam D The type of devices this function consumes
    * @tparam V The type of view or result this function produces.
    */
  trait User[-D, +V] {
    def apply(device: D): V
  }

  /**
    * A Cycle function takes a User function and invokes it with some `D` devices
    * in order to produce a `V` result.
    *
    * @tparam D
    * @tparam V
    */
  trait Cycle[-D, +V, -U <: User[D, V]] {
    def apply(user: U): V
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
    * @tparam V
    */
  case class Driver[D, V](
      device: D,
      binder: V,
      emptyBinder: V,
      bindFn: Bind[V]
  ) extends Cycle[D, V, User[D, V]]
      with FlatMap[D, V, Driver] {

    def tuple: (D, V) = device -> binder

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
    def cycle: Cycle[D, V, User[D, V]] = { user => user(device) }

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
    def apply(user: User[D, V]): V =
      bindFn(binder, cycle(user))

    /**
      * Lifts this driver as the device of a new Driver.
      *
      * Important Note: The new driver's binder does nothing.
      *
      * @see #liftCycle
      * @return A new driver encapsulating this one.
      */
    def lift: Driver[Driver[D, V], V] =
      copy(device = this, binder = emptyBinder)

    def liftTuple: Driver[(D, V), V] =
      lift.map(_.tuple)

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
    def liftCycle: Driver[Driver[D, V], V] =
      copy(device = copy(device = device, binder = emptyBinder))

    /**
      * Lowers a lifted driver into a single one, combining the contained's
      * and the container's `binder` objects into one.
      *
      * @param ev - evidence that this is a lifted driver.
      * @tparam D0
      * @return
      */
    def lower[D0](implicit ev: D <:< Driver[D0, V]): Driver[D0, V] =
      copy(
        device = device.device,
        binder = bindFn(binder, device.binder)
      )

    def self: Driver[D, V]  = this
    def never: Driver[D, V] = mod(_ => emptyBinder)

    def bimap[D2, B2](fn: D => D2, fb: B2 => V): User[D2, B2] => V =
      map(fn).contramap(fb)

    def contramap[B2](fn: B2 => V): User[D, B2] => V = { user =>
      fn(user(device))
    }

    def flatMap[D2](fn: D => Driver[D2, V]): Driver[D2, V] =
      copy(device = fn(device)).lower

    def flatten[D2](implicit ev: D <:< Driver[D2, V]): Driver[D2, V] =
      lower

    def map[D2](fn: D => D2): Driver[D2, V] =
      copy(device = fn(device))

    def mod(fn: V => V): Driver[D, V] =
      copy(binder = fn(binder))

    def zip[D2](other: Driver[D2, V]): Driver[(D, D2), V] =
      copy(
        device = (device, other.device),
        binder = bindFn(binder, other.binder)
      )

    def unzip[A, B, D2](
        fn: (A, B) => D2
    )(implicit ev: D <:< (A, B)): Driver[D2, V] =
      copy(device = fn(device._1, device._2))

  }

  type UserMod[D, El <: Element]   = User[D, Mod[El]]
  type CycleMod[D, El <: Element]  = Cycle[D, Mod[El], User[D, Mod[El]]]
  type DriverMod[D, El <: Element] = Driver[D, Mod[El]]

  type UserEl[D]   = UserMod[D, Element]
  type CycleEl[D]  = CycleMod[D, Element]
  type DriverEl[D] = DriverMod[D, Element]

  object Driver {
    lazy val unit = bind[Element]()

    def bind[El <: Element](binds: Mod[El]*): Driver[Unit, Mod[El]] =
      apply((), binds: _*)

    def apply[D, El <: Element](
        devices: D,
        binds: Mod[El]*
    ): Driver[D, Mod[El]] =
      Driver[D, Mod[El]](devices, amend(binds: _*), emptyMod, amend(_, _))

  }

  trait FlatMap[D, V, Self[X, Y] <: Cycle[X, Y, User[X, Y]]] {

    def self: Self[D, V]
    def never: Self[D, V]

    /**
      * Alias for `flatMap`
      *
      * @see #flatMap
      * @param fn
      * @tparam D2
      * @return
      */
    @inline def >>=[D2](fn: D => Self[D2, V]): Self[D2, V] = flatMap(fn)

    def flatMap[D2](fn: D => Self[D2, V]): Self[D2, V]

    def flatten[D2](implicit ev: D <:< Self[D2, V]): Self[D2, V]

    /**
      * Maps this cycle's device `D` into `D2` by using the provided function.
      *
      * @param fn - A function to map from this device to something else.
      * @tparam D2
      * @return A new cycle that maps on devices.
      */
    def map[D2](fn: D => D2): Self[D2, V]

    def mod(fn: V => V): Self[D, V]

    def withFilter(p: D => Boolean): Self[D, V] =
      flatMap {
        case devices if p(devices) => self
        case _                     => never
      }

    @inline def ++[D2](
        cycle: Self[D2, V]
    ): Self[(D, D2), V] =
      zip(cycle)

    def zip[D2](cycle: Self[D2, V]): Self[(D, D2), V]

    def unzip[A, B, D2](fn: (A, B) => D2)(
        implicit ev: D <:< (A, B)
    ): Self[D2, V]

    def contramap[B2](fn: B2 => V): User[D, B2] => V

    def bimap[D2, B2](
        fn: D => D2,
        fb: B2 => V
    ): User[D2, B2] => V
  }

}
