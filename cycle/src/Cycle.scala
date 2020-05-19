package cycle.core

import com.raquo.laminar.api.L._
import com.raquo.laminar.nodes.ReactiveElement

private[cycle] object Devices extends Devices
private[cycle] trait Devices {
  import zio.Has
  import Helper.ModEl

  type Tag[T] = izumi.reflect.Tag[T]
  type Has[T] = zio.Has[T]

  trait User[-Devices <: Has[_]] {
    def apply(devices: Devices): ModEl
  }

  implicit def userFromFunction[Devices <: Has[_]](
      fun: Devices => ModEl
  ): User[Devices] =
    new User[Devices] {
      override def apply(devices: Devices): ModEl = fun(devices)
    }

  trait Cycle[+Devices <: Has[_]] {
    def apply(user: User[Devices]): ModEl
  }

  implicit def cycleFromFunction[Devices <: Has[_]](
      fun: User[Devices] => ModEl
  ): Cycle[Devices] = new Cycle[Devices] {
    def apply(user: User[Devices]): ModEl = fun(user)
  }

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

  def EMO[T: Tag](m: Signal[T], o: WriteBus[T]): EMO[T] =
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
    hasIn(ib.events) ++ hasOut(ib.writer) ++
      hasIn(ob.events) ++ hasOut(ob.writer)

  def PIO[I: Tag, O: Tag]: PIO[I, O] = PIO(new EventBus[I], new EventBus[O])

}

private[cycle] trait Bijection {
  import Devices._

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

  def emoBiject[A: Tag, B: Tag](
      from: EMO[A]
  )(implicit bijection: MemBijection[A, B]): (Binder[Element], EMO[B]) = {
    val signalB: Signal[B]  = from.compose(bijection.fwd)
    val writeB: EventBus[B] = new EventBus[B]
    val emoB: EMO[B]        = hasMem(signalB) ++ hasOut(writeB.writer)
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
    binder -> emoB
  }

}

private[cycle] object Helper extends Helper
private[cycle] trait Helper {
  type ModEl = Mod[Element]

  def amend(mods: ModEl*): ModEl = inContext(_.amend(mods: _*))

  def ownerMod(fn: Owner => ModEl): ModEl = {
    onMountCallback { ctx => ctx.thisNode.amend(fn(ctx.owner)) }
  }
}
