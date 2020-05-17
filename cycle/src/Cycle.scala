package cycle.internal

import com.raquo.laminar.api.L._
import izumi.reflect.Tag
import zio.Has
import zio.Has._

private[cycle] object Devices extends Devices
private[cycle] trait Devices {

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
  implicit def EIO[T: Tag](b: EventBus[T]): EIO[T] = hasIn(b.events) ++ hasOut(b.writer)
  def EIO[T: Tag]: EIO[T]                          = new EventBus[T]

  type EMO[T] = MemInOut[T, T, T]
  def EMO[T: Tag](m: Signal[T], i: EventStream[T], o: WriteBus[T]): EMO[T] =
    hasMem(m) ++ hasIn(i) ++ hasOut(o)

  type MIO[M, I, O] = MemInOut[M, I, O]
  def MIO[M: Tag, I: Tag, O: Tag](m: Signal[M], i: EventStream[I], o: WriteBus[O]): MIO[M, I, O] =
    hasMem(m) ++ hasIn(i) ++ hasOut(o)

  type CIO[I, O] = InOut[I, O]

  type PIO[I, O] = InOut[I, O] with InOut[O, I]
  def PIO[I: Tag, O: Tag](ib: EventBus[I], ob: EventBus[O]): PIO[I, O] =
    hasIn(ib.events) ++ hasOut(ib.writer) ++ hasIn(ob.events) ++ hasOut(ob.writer)
  def PIO[I: Tag, O: Tag]: PIO[I, O] = PIO(new EventBus[I], new EventBus[O])

}

private[cycle] trait Bijection {
  import Devices._

  implicit def mapEventStream[A, B](implicit ab: A => B): EventStream[A] => EventStream[B] =
    _.map(ab)

  implicit def contramapWriteBus[A, B](implicit ba: B => A, owner: Owner): WriteBus[A] => WriteBus[B] =
    _.contramapWriter(ba)

  implicit def mapIn[A: Tag, B: Tag](implicit ab: EventStream[A] => EventStream[B]): In[A] => In[B] =
    aIn => hasIn(ab(in(aIn)))

  implicit def contramapOut[A: Tag, B: Tag](implicit ba: WriteBus[B] => WriteBus[A]): Out[B] => Out[A] =
    bOut => hasOut(ba(out(bOut)))

  type BijectCIO[I1, O1, I2, O2] = Has[In[I1] => In[I2]] with Has[Out[O2] => Out[O1]]

  implicit def bijectCIO[I1: Tag, O1: Tag, I2: Tag, O2: Tag](
      implicit mapI: In[I1] => In[I2],
      contramapO: Out[O2] => Out[O1]
  ): BijectCIO[I1, O1, I2, O2] = Has(mapI) ++ Has(contramapO)

}

private[cycle] object Helper extends Helper
private[cycle] trait Helper {
  type ModEl = Mod[Element]

  def amend(mods: ModEl*): ModEl = inContext(_.amend(mods: _*))
}
