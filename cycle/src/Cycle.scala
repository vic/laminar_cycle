package cycle.core

import cycle._
import com.raquo.laminar.api.L._
import com.raquo.laminar.nodes.ReactiveElement

private[cycle] trait API extends Core with Devices with Bijection with Helper

private[core] trait Core {

  trait UserFn[-Devices, Binds] extends (Devices => Binds)
  type User[-Devices, El <: Element] = UserFn[Devices, Mod[El]]

  trait CycleFn[+Devices, Binds] extends (UserFn[Devices, Binds] => Binds)
  type Cycle[+Devices, El <: Element] = CycleFn[Devices, Mod[El]]

  trait DriverFn[+Devices, Binds] extends CycleFn[Devices, Binds] { self =>
    val devices: Devices
    val binds: Binds

    def tuple: (Devices, Binds)        = devices -> binds
    def cycle: CycleFn[Devices, Binds] = { user => user(devices) }

    override def apply(user: UserFn[Devices, Binds]): Binds =
      bind(binds, user(devices))

    protected def bind(a: Binds, b: Binds): Binds

    def map[D2](
        f: Devices => D2
    ): DriverFn[D2, Binds] = new DriverFn[D2, Binds] {
      lazy val devices = f(self.devices)
      lazy val binds   = self.binds

      override protected def bind(a: Binds, b: Binds): Binds = self.bind(a, b)
    }

    def flatMap[D2](f: Devices => DriverFn[D2, Binds]): DriverFn[D2, Binds] = {
      lazy val (devices2, binds2) = f(devices).tuple
      new DriverFn[D2, Binds] {
        lazy val devices = devices2
        lazy val binds   = self.bind(self.binds, binds2)

        override protected def bind(a: Binds, b: Binds): Binds = self.bind(a, b)
      }
    }
  }

  type Driver[+Devices, El <: Element] = DriverFn[Devices, Mod[El]]

  object Driver {
    def apply[Devices, El <: Element](
        aDevices: Devices,
        aBinds: Mod[El]*
    ): Driver[Devices, El] = new Driver[Devices, El] {
      override val devices = aDevices
      override val binds   = aBinds

      override protected def bind(a: Mod[El], b: Mod[El]): Mod[El] = amend(a, b)
    }
  }

}

private[core] trait Devices {

  class Devices[M, I, O](
      private val memDevice: M,
      private val inDevice: I,
      private val outDevice: O
  ) {
    def in[T](implicit ev: I <:< EventStream[T]): EventStream[T] = inDevice
    def out[T](implicit ev: O <:< WriteBus[T]): WriteBus[T]      = outDevice
    def mem[T](implicit ev: M <:< Signal[T]): Signal[T]          = memDevice
  }

  object Devices {
    def apply[M, I, O](m: M, i: I, o: O): Devices[M, I, O] =
      new Devices(m, i, o)

    implicit def mem[T](device: Mem[T]): Signal[T]    = device.mem
    implicit def in[T](device: In[T]): EventStream[T] = device.in
    implicit def out[T](device: Out[T]): WriteBus[T]  = device.out
  }

  type Mem[T] = Devices[Signal[T], _, _]
  type In[T]  = Devices[_, EventStream[T], _]
  type Out[T] = Devices[_, _, WriteBus[T]]

  type InOut[I, O]  = In[I] with Out[O]
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

  def emoBiject[A, B, El <: Element](
      from: EMO[A]
  )(implicit bijection: MemBijection[A, B]): cycle.Driver[EMO[B], El] = {
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
