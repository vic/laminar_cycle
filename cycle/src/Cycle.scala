package cycle.core

import com.raquo.laminar.api.L._

private[cycle] object Devices extends Devices
private[cycle] trait Devices {
  import zio.Has

  type Tag[T] = izumi.reflect.Tag[T]
  type Has[T] = zio.Has[T]

  type User[R, V]  = R => V
  type Cycle[R, V] = User[R, V] => V

  type In[T]  = Has[EventStream[T]]
  type Out[T] = Has[WriteBus[T]]
  type Mem[T] = Has[Signal[T]]

  type InOut[I, O]  = In[I] with Out[O]
  type MemOut[M, O] = Mem[M] with Out[O]

  type EqlInOut[T]       = InOut[T, T]
  type MemInOut[M, I, O] = Mem[M] with InOut[I, O]

  implicit def hasIn[T: Tag](o: EventStream[T]): In[T] = Has(o)
  implicit def in[T: Tag](in: In[T]): EventStream[T]   = in.get[EventStream[T]]

  implicit def hasOut[T: Tag](o: WriteBus[T]): Out[T] = Has(o)
  implicit def out[T: Tag](out: Out[T]): WriteBus[T]  = out.get[WriteBus[T]]

  implicit def hasMem[T: Tag](s: Signal[T]): Mem[T] = Has(s)
  implicit def mem[T: Tag](mem: Mem[T]): Signal[T]  = mem.get[Signal[T]]

  type EIO[T] = EqlInOut[T]

  implicit def EIO[T: Tag](b: EventBus[T]): EIO[T] =
    hasIn(b.events) ++ hasOut(b.writer)

  def EIO[T: Tag]: EIO[T] = new EventBus[T]

  type EMO[T] = MemOut[T, T]

  def EMO[T: Tag](m: Signal[T], i: EventStream[T], o: WriteBus[T]): EMO[T] =
    hasMem(m) ++ hasOut(o)

  def EMO[M: Tag](initial: => M): EMO[M] = MIO(initial)

  type MIO[M, I, O] = MemInOut[M, I, O]

  def MIO[M: Tag, I: Tag, O: Tag](
      m: Signal[M],
      i: EventStream[I],
      o: WriteBus[O]
  ): MIO[M, I, O] =
    hasMem(m) ++ hasIn(i) ++ hasOut(o)

  def MIO[M: Tag](initial: => M): MIO[M, M, M] = MIO[M, M](_.startWith(initial))

  def MIO[T: Tag, M: Tag](
      initial: EventStream[T] => Signal[M]
  ): MIO[M, T, T] = {
    val bus = new EventBus[T]
    val sig = initial(bus.events)
    MIO[M, T, T](sig, bus.events, bus.writer)
  }

  type CIO[I, O] = InOut[I, O]

  type PIO[I, O] = InOut[I, O] with InOut[O, I]

  def PIO[I: Tag, O: Tag](ib: EventBus[I], ob: EventBus[O]): PIO[I, O] =
    hasIn(ib.events) ++ hasOut(ib.writer) ++ hasIn(ob.events) ++ hasOut(
      ob.writer
    )

  def PIO[I: Tag, O: Tag]: PIO[I, O] = PIO(new EventBus[I], new EventBus[O])

}

private[cycle] trait Bijection {

  implicit def streamMap[A, B](
      implicit ab: A => B
  ): EventStream[A] => EventStream[B] =
    _.map(ab)

  implicit def signalMap[A, B](implicit ab: A => B): Signal[A] => Signal[B] =
    _.map(ab)

  final class MemBijection[A, B](
      val fwd: Signal[A] => Signal[B],
      val bwd: EventStream[(B, A)] => EventStream[A]
  )

  implicit def memBijection[A, B](
      implicit
      fwd: Signal[A] => Signal[B],
      bwd: EventStream[(B, A)] => EventStream[A]
  ): MemBijection[A, B] = new MemBijection[A, B](fwd, bwd)

}

private[cycle] object Helper extends Helper
private[cycle] trait Helper {
  type ModEl = Mod[Element]

  def amend(mods: ModEl*): ModEl = inContext(_.amend(mods: _*))

  def ownerMod(fn: Owner => ModEl): ModEl = {
    onMountCallback { ctx => ctx.thisNode.amend(fn(ctx.owner)) }
  }
}
