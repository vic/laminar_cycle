package cycle.core

import cycle._
import com.raquo.laminar.api.L._
import com.raquo.laminar.nodes.ReactiveElement

private[cycle] trait API extends Core with IODevices with Bijection with Helper

private[core] trait Core {

  trait BindFn[A] extends ((A, A) => A)

  trait UserFn[D, V] extends (D => V)

  trait CycleFn[D, V] extends (UserFn[D, V] => V) {

    def map[D2](fn: D => D2): CycleFn[D2, V] = { user2: UserFn[D2, V] =>
      apply { devices => user2(fn(devices)) }
    }

    @inline def >>=[D2](fn: D => CycleFn[D2, V]): CycleFn[D2, V] =
      flatMap(fn)

    def flatMap[D2](fn: D => CycleFn[D2, V]): CycleFn[D2, V] = {
      user2: UserFn[D2, V] => apply { devices => fn(devices)(user2) }
    }

    def withFilter(p: D => Boolean): CycleFn[D, V] =
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

  class DriverFn[D, V](
      val device: D,
      val binder: V,
      val bindFn: BindFn[V]
  ) extends CycleFn[D, V] {
    def tuple: (D, V)        = device -> binder
    def cycle: CycleFn[D, V] = { user => user(device) }

    override def apply(user: UserFn[D, V]): V =
      bindFn(binder, cycle(user))
  }

  type UserEl[D, El <: Element]   = UserFn[D, Mod[El]]
  type CycleEl[D, El <: Element]  = CycleFn[D, Mod[El]]
  type DriverEl[D, El <: Element] = DriverFn[D, Mod[El]]

  type User[D]   = UserEl[D, Element]
  type Cycle[D]  = CycleEl[D, Element]
  type Driver[D] = DriverEl[D, Element]
  object Driver {
    def apply[D, El <: Element](
        devices: D,
        binds: Mod[El]*
    ) = new DriverFn[D, Mod[El]](devices, amend(binds: _*), amend(_, _))
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
