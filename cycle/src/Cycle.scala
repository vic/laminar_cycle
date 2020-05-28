package cycle.core

import cycle._
import com.raquo.laminar.api.L._
import com.raquo.laminar.nodes.ReactiveElement

private[cycle] trait API extends Core with IODevices with Bijection with Helper

private[core] trait Core {

  /**
    * Combine two binders into one.
    *
    * @see cycle#amend
    * @tparam A - In most cases this will be a Laminar Modifier: `Mod[El]`
    */
  trait BindFn[A] extends ((A, A) => A)

  /**
    * A User function takes devices of type `D` and returns a `V` which can be
    * either a Laminar view or just Laminar binders.
    *
    * @tparam D The type of devices this function consumes
    * @tparam V The type of view or result this function produces.
    */
  trait UserFn[-D, +V] extends (D => V)

  /**
    * A Cycle function takes a User function and invokes it with some `D` devices
    * in order to produce a `V` result.
    *
    * @tparam D
    * @tparam V
    */
  trait CycleFn[D, V] extends (UserFn[D, V] => V) {

    /**
      * Maps this cycle's device `D` into `D2` by using the provided function.
      *
      * @param fn - A function to map from this device to something else.
      * @tparam D2
      * @return A new cycle that maps on devices.
      */
    def map[D2](fn: D => D2): CycleFn[D2, V] = { user2: UserFn[D2, V] =>
      apply { devices => user2(fn(devices)) }
    }

    /**
      * Alias for `flatMap`
      *
      * @see #flatMap
      * @param fn
      * @tparam D2
      * @return
      */
    @inline def >>=[D2](fn: D => CycleFn[D2, V]): CycleFn[D2, V] =
      flatMap(fn)

    def flatMap[D2](fn: D => CycleFn[D2, V]): CycleFn[D2, V] = {
      user2: UserFn[D2, V] => apply { devices => fn(devices)(user2) }
    }

    def withFilter[E](p: D => Boolean): CycleFn[D, V] =
      flatMap {
        case devices if p(devices) => this
      }

    @inline def ++[D2](
        cycle: CycleFn[D2, V]
    ): CycleFn[(D, D2), V] =
      zip(cycle)

    def zip[D2](cycle: CycleFn[D2, V]): CycleFn[(D, D2), V] = {
      user3: UserFn[(D, D2), V] =>
        apply { devices => cycle { devices2 => user3(devices -> devices2) } }
    }

    def unzip[A, B, D2](fn: (A, B) => D2)(
        implicit ev: D <:< (A, B)
    ): CycleFn[D2, V] = { user2: UserFn[D2, V] =>
      apply { devices => user2(fn(devices._1, devices._2)) }
    }

    def contramap[B2](fn: B2 => V): UserFn[D, B2] => V = {
      user2: UserFn[D, B2] => apply { devices => fn(user2(devices)) }
    }

    def bimap[D2, B2](
        fn: D => D2,
        fb: B2 => V
    ): UserFn[D2, B2] => V = { user2: UserFn[D2, B2] =>
      apply { devices => fb(user2(fn(devices))) }
    }
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
    * only once and use the `DriverFn#cycle` method to obtain a re-usable cycle from
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
  case class DriverFn[D, V](
      device: D,
      binder: V,
      emptyBinder: V,
      bindFn: BindFn[V]
  ) extends CycleFn[D, V] {

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
    def cycle: CycleFn[D, V] = { user => user(device) }

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
    override def apply(user: UserFn[D, V]): V =
      bindFn(binder, cycle(user))

    /**
      * Lifts this driver as the device of a new Driver.
      *
      * Important Note: The new driver's binder does nothing.
      *
      * @see #liftCycle
      * @return A new driver encapsulating this one.
      */
    def lift: DriverFn[DriverFn[D, V], V] =
      copy(device = this, binder = emptyBinder)

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
    def liftCycle: DriverFn[DriverFn[D, V], V] =
      copy(device = copy(device = device, binder = emptyBinder))

    /**
      * Lowers a lifted driver into a single one, combining the contained's
      * and the container's `binder` objects into one.
      *
      * @param ev - evidence that this is a lifted driver.
      * @tparam D0
      * @return
      */
    def lower[D0](implicit ev: D <:< DriverFn[D0, V]): DriverFn[D0, V] =
      copy(
        device = device.device,
        binder = bindFn(binder, device.binder)
      )

  }

  object DriverFn {
    implicit def toTuple[D, V](driver: DriverFn[D, V]): (D, V) =
      driver.device -> driver.binder
  }

  type UserEl[D, El <: Element]   = UserFn[D, Mod[El]]
  type CycleEl[D, El <: Element]  = CycleFn[D, Mod[El]]
  type DriverEl[D, El <: Element] = DriverFn[D, Mod[El]]

  type User[D]   = UserEl[D, Element]
  type Cycle[D]  = CycleEl[D, Element]
  type Driver[D] = DriverEl[D, Element]

  object Driver {
    def apply[D, El <: Element](devices: D, binds: Mod[El]*) =
      DriverFn[D, Mod[El]](devices, amend(binds: _*), emptyMod, amend(_, _))
  }

}

private[core] object IODevices {

  implicit class MemOps[T](private val device: Mem[T]) extends AnyVal {
    def mem: Signal[T] = device
  }

  implicit class InOps[T](private val device: In[T]) extends AnyVal {
    def in: EventStream[T] = device
  }

  implicit class OutOps[T](private val device: Out[T]) extends AnyVal {
    def out: WriteBus[T] = device
  }

}

private[core] trait IODevices {

  class Devices[M, I, O](
      private[core] val memDevice: M,
      private[core] val inDevice: I,
      private[core] val outDevice: O
  )

  object Devices {
    def apply[M, I, O](m: M, i: I, o: O): Devices[M, I, O] =
      new Devices(m, i, o)
  }

  implicit def mem[T](device: Mem[T]): Signal[T]    = device.memDevice
  implicit def in[T](device: In[T]): EventStream[T] = device.inDevice
  implicit def out[T](device: Out[T]): WriteBus[T]  = device.outDevice

  type Mem[T] = Devices[Signal[T], _, _]
  type In[T]  = Devices[_, EventStream[T], _]
  type Out[T] = Devices[_, _, WriteBus[T]]

  type InOut[I, O]  = In[I] with Out[O]
  type MemIn[M, I]  = Mem[M] with In[I]
  type MemOut[M, O] = Mem[M] with Out[O]
  type EMO[T]       = MemOut[T, T]

  type EqlInOut[T] = InOut[T, T]
  type EIO[T]      = EqlInOut[T]

  type MemInOut[M, I, O] = Mem[M] with InOut[I, O]
  type MIO[M, I, O]      = MemInOut[M, I, O]

  type CIO[I, O] = InOut[I, O]

  object Mem {
    def apply[M](m: Signal[M]): Mem[M] = Devices(m, None, None)
  }

  object In {
    def apply[I](i: EventStream[I]): In[I] = Devices(None, i, None)
  }

  object Out {
    def apply[O](o: WriteBus[O]): Out[O] = Devices(None, None, o)
  }

  object CIO {
    def apply[I, O](i: EventStream[I], o: WriteBus[O]): CIO[I, O] =
      Devices(None, i, o)
  }

  object EIO {
    implicit def apply[T](b: EventBus[T]): EIO[T] =
      CIO[T, T](b.events, b.writer)
    def apply[T]: EIO[T] = new EventBus[T]
  }

  object MemIn {
    def apply[M, I](m: Signal[M], i: EventStream[I]): MemIn[M, I] =
      Devices(m, i, None)
  }
  object MemOut {
    def apply[M, O](m: Signal[M], o: WriteBus[O]): MemOut[M, O] =
      Devices(m, None, o)
  }

  object EMO {
    def apply[T](m: Signal[T], o: WriteBus[T]): EMO[T] = MemOut(m, o)
    def apply[M](initial: => M): EMO[M]                = MIO(initial)
  }

  object MIO {
    def apply[M, I, O](
        m: Signal[M],
        i: EventStream[I],
        o: WriteBus[O]
    ): MIO[M, I, O] = Devices(m, i, o)

    def apply[M](initial: => M): MIO[M, M, M] = MIO[M, M](_.startWith(initial))

    def apply[T, M](
        initial: EventStream[T] => Signal[M]
    ): MIO[M, T, T] = {
      val bus = new EventBus[T]
      val sig = initial(bus.events)
      MIO[M, T, T](sig, bus.events, bus.writer)
    }
  }

  class PairedIO[I, O](val io: CIO[I, O], val oi: CIO[O, I])
  type PIO[I, O] = PairedIO[I, O]

  object PairedIO {
    implicit def io[I, O](pio: PIO[I, O]): CIO[I, O]       = pio.io
    implicit def oi[I, O](pio: PIO[I, O]): CIO[O, I]       = pio.oi
    implicit def in1[I, O](pio: PIO[I, O]): EventStream[I] = pio.io
    implicit def out1[I, O](pio: PIO[I, O]): WriteBus[O]   = pio.io
    def in2[I, O](pio: PIO[I, O]): EventStream[O]          = pio.oi
    def out2[I, O](pio: PIO[I, O]): WriteBus[I]            = pio.oi
  }

  object PIO {
    def apply[I, O](ib: EventBus[I], ob: EventBus[O]): PIO[I, O] =
      new PairedIO(CIO(ib.events, ob.writer), CIO(ob.events, ib.writer))
    def apply[I, O]: PIO[I, O] = PIO(new EventBus[I], new EventBus[O])
  }

}

private[core] trait Bijection {
  implicit def streamMap[A, B](
      implicit ab: A => B
  ): EventStream[A] => EventStream[B] =
    _.map(ab)

  implicit def signalMap[A, B](implicit ab: A => B): Signal[A] => Signal[B] =
    _.map(ab)

  final case class MemBijection[A, B](
      fwd: Signal[A] => Signal[B],
      bwd: EventStream[(B, A)] => EventStream[A]
  )

  implicit def memBijection[A, B](
      implicit
      fwd: Signal[A] => Signal[B],
      bwd: EventStream[(B, A)] => EventStream[A]
  ): MemBijection[A, B] = MemBijection[A, B](fwd, bwd)

  implicit def memBijection[A, B](
      implicit
      fwd: A => B,
      bwd: (B, A) => A
  ): MemBijection[A, B] = MemBijection(
    fwd = _.composeChangesAndInitial(_.map(fwd), _.map(fwd)),
    bwd = _.map2(bwd)
  )

  def emoBiject[A, B](
      from: EMO[A]
  )(implicit bijection: MemBijection[A, B]): Driver[EMO[B]] = {
    val signalB: Signal[B]  = from.compose(bijection.fwd)
    val writeB: EventBus[B] = new EventBus[B]
    val emoB: EMO[B]        = EMO(signalB, writeB.writer)
    val binder = Binder[Element] { el =>
      ReactiveElement.bindCallback(el) { ctx =>
        // All this because contracomposeWriter expects a an implicit owner
        val contraB: WriteBus[B] = from.contracomposeWriter[B](
          _.withCurrentValueOf(from).compose(bijection.bwd)
        )(ctx.owner)
        el.amend(
          writeB.events --> contraB
        )
      }
    }
    Driver(emoB, binder)
  }

}

private[core] trait Helper {
  def amend[El <: Element](mods: Mod[El]*): Mod[El] =
    inContext[El](_.amend(mods: _*))

  def drain[El <: Element](eventStream: EventStream[_]): Binder[El] =
    eventStream.filter(_ => false).mapTo(()) --> Observer[Unit](_ => ())
}
