package cycle.core

import cycle._
import com.raquo.laminar.api.L._
import com.raquo.laminar.nodes.ReactiveElement

private[cycle] trait API extends Core with Devices with Bijection with Helper

private[core] trait Core {

  trait UserFn[-Devices, Ret] extends (Devices => Ret)
  type User[-Devices] = UserFn[Devices, ModEl]

  trait CycleFn[+Devices, Ret] extends (User[Devices] => Ret)
  type Cycle[+Devices] = CycleFn[Devices, ModEl]

  sealed trait Driver[+Devices] {
    val devices: Devices
    val binds: Binds

    def cycle: Cycle[Devices] = { user => amend(binds, user(devices)) }

    def apply(user: User[Devices]): ModEl = cycle(user)
    def toTuple: (Devices, Binds)         = devices -> binds
  }

  object Driver {
    def apply[Devices](
        devices: Devices,
        binds: Binder[Element]*
    ): Driver[Devices] = {
      val (aDevices, aBinds) = (devices, binds)
      new Driver[Devices] {
        val devices = aDevices
        val binds   = aBinds
      }
    }
  }

}

private[core] trait Devices {
  type Mem[T] = (Signal[T], _, _)
  type In[T]  = (_, EventStream[T], _)
  type Out[T] = (_, _, WriteBus[T])

  type InOut[I, O]  = In[I] with Out[O]
  type MemOut[M, O] = Mem[M] with Out[O]
  type EMO[T]       = MemOut[T, T]

  type EqlInOut[T] = InOut[T, T]
  type EIO[T]      = EqlInOut[T]

  type MemInOut[M, I, O] = Mem[M] with InOut[I, O]
  type MIO[M, I, O]      = MemInOut[M, I, O]

  type CIO[I, O] = InOut[I, O]
  type PIO[I, O] = (InOut[I, O], InOut[O, I])

  implicit def mem[T](mem: Mem[T]): Signal[T]   = mem._1
  implicit def in[T](in: In[T]): EventStream[T] = in._2
  implicit def out[T](out: Out[T]): WriteBus[T] = out._3

  object EIO {
    implicit def apply[T](b: EventBus[T]): EIO[T] = (None, b.events, b.writer)
    def apply[T]: EIO[T]                          = new EventBus[T]
  }

  object EMO {
    def apply[T](m: Signal[T], o: WriteBus[T]): EMO[T] = (m, None, o)
    def apply[M](initial: => M): EMO[M]                = MIO(initial)
  }

  object MIO {
    def apply[M, I, O](
        m: Signal[M],
        i: EventStream[I],
        o: WriteBus[O]
    ): MIO[M, I, O] = (m, i, o)

    def apply[M](initial: => M): MIO[M, M, M] = MIO[M, M](_.startWith(initial))

    def apply[T, M](
        initial: EventStream[T] => Signal[M]
    ): MIO[M, T, T] = {
      val bus = new EventBus[T]
      val sig = initial(bus.events)
      MIO[M, T, T](sig, bus.events, bus.writer)
    }
  }

  object PIO {
    def apply[I, O](ib: EventBus[I], ob: EventBus[O]): PIO[I, O] =
      ((None, ib.events, ob.writer), (None, ob.events, ib.writer))
    def apply[I, O]: PIO[I, O] = PIO(new EventBus[I], new EventBus[O])
  }

  implicit def io[I, O](pio: PIO[I, O]): CIO[I, O]       = pio._1
  implicit def in1[I, O](pio: PIO[I, O]): EventStream[I] = pio._1
  implicit def out1[I, O](pio: PIO[I, O]): WriteBus[O]   = pio._1
  implicit def oi[I, O](pio: PIO[I, O]): CIO[O, I]       = pio._2
  //  implicit def oii[I](pio: PIO[_, I]): EventStream[I] = pio._2
  //  implicit def oio[I](pio: PIO[I, _]): WriteBus[I]    = pio._2

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
  )(implicit bijection: MemBijection[A, B]): cycle.Driver[EMO[B]] = {
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
  type ModEl = Mod[Element]
  type Binds = Seq[Binder[Element]]

  def amend(mods: ModEl*): ModEl = inContext(_.amend(mods: _*))

  def ownerMod(fn: Owner => ModEl): ModEl = {
    onMountCallback { ctx => ctx.thisNode.amend(fn(ctx.owner)) }
  }
}
