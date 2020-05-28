package cycle.core

import cycle._
import com.raquo.laminar.api.L._

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
