package cycle.internal

import com.raquo.laminar.api.L._
import com.raquo.laminar.nodes.ReactiveElement

private[cycle] trait Cycle {

  sealed trait SubscribeOnMount[El <: Element] {
    val subscribeOnMount: Mod[El]
  }

  object SubscribeOnMount {
    def cycle[El <: Element, I, O](a: CIO[I, O], b: CIO[O, I]): SubscribeOnMount[El] = {
      val aSub = inOut[El, I](a, b)
      val bSub = inOut[El, O](b, a)
      new SubscribeOnMount[El] {
        override val subscribeOnMount: Mod[El] = amend[El](aSub, bSub)
      }
    }

    def inOut[El <: Element, T](in: CIO[T, _], out: CIO[_, T]): SubscribeOnMount[El] =
      apply(_ => in.input -> out.output)

    def apply[El <: Element, T](in: EventStream[T], out: WriteBus[T]): SubscribeOnMount[El] =
      apply(_ => in -> out)

    def apply[El <: Element, T](fn: MountContext[El] => (EventStream[T], WriteBus[T])): SubscribeOnMount[El] =
      new SubscribeOnMount[El] {
        val subscribeOnMount: Mod[El] = onMountBind[El] { ctx =>
          val (in: EventStream[T], out: WriteBus[T]) = fn(ctx)
          Binder[El](el => ReactiveElement.bindBus(el, in)(out))
        }
      }

    implicit def toMod[El <: Element](
        sub: SubscribeOnMount[El]
    ): Mod[El] = sub.subscribeOnMount
  }

  type CycleIO[I, O] = CIO[I, O]
  sealed trait CIO[I, O] { self =>
    val input: EventStream[I]
    val output: WriteBus[O]

    def cycle[El <: Element](inverse: CIO[O, I]): SubscribeOnMount[El] = {
      SubscribeOnMount.cycle[El, I, O](self, inverse)
    }

    def map[T](operator: I => T): CIO[T, O] = compose(_.map(operator))
    def compose[T](operator: EventStream[I] => EventStream[T]): CIO[T, O] =
      new CIO[T, O] {
        override val input  = self.input.compose(operator)
        override val output = self.output
      }

    def fold[X, Y, El <: Element](
        inOperator: I => X,
        outOperator: Y => O
    ): (CIO[X, Y], SubscribeOnMount[El]) =
      composeBoth(_.map(inOperator), _.map(outOperator))

    def composeBoth[X, Y, El <: Element](
        inOperator: EventStream[I] => EventStream[X],
        outOperator: EventStream[Y] => EventStream[O]
    ): (CIO[X, Y], SubscribeOnMount[El]) = {
      val bus = new EventBus[Y]
      val io = new CIO[X, Y] {
        override val input  = self.input.compose(inOperator)
        override val output = bus.writer
      }
      val som = SubscribeOnMount[El, Y] { ctx: MountContext[El] =>
        bus.events -> self.output.contracomposeWriter(outOperator)(ctx.owner)
      }
      io -> som
    }

    def contramap[T, El <: Element](
        operator: T => O
    ): (CIO[I, T], SubscribeOnMount[El]) =
      contracompose(_.map(operator))

    def contracompose[T, El <: Element](
        operator: EventStream[T] => EventStream[O]
    ): (CIO[I, T], SubscribeOnMount[El]) = {
      val bus = new EventBus[T]
      val io = new CIO[I, T] {
        override val input  = self.input
        override val output = bus.writer
      }
      val som = SubscribeOnMount[El, T] { ctx: MountContext[El] =>
        bus.events -> self.output.contracomposeWriter(operator)(ctx.owner)
      }
      io -> som
    }
  }

  object CIO {

    def apply[I, O]: (CIO[I, O], CIO[O, I]) =
      splitEventBus(new EventBus[I], new EventBus[O])

    implicit def fromEventBus[T](eventBus: EventBus[T]): EIO[T] =
      new CIO[T, T] {
        override val input  = eventBus.events
        override val output = eventBus.writer
      }

    def splitEventBus[I, O](iBus: EventBus[I], oBus: EventBus[O]): (CIO[I, O], CIO[O, I]) = {
      val io = new CIO[I, O] {
        override val input  = iBus.events
        override val output = oBus.writer
      }
      val oi = new CIO[O, I] {
        override val input  = oBus.events
        override val output = iBus.writer
      }
      io -> oi
    }

    def toEventBus[T](io: EIO[T]): EventBus[T] = new EventBus[T] {
      override val events = io.input
      override val writer = io.output
    }

    implicit def toEventStream[I](io: CIO[I, _]): EventStream[I] = io.input
    implicit def toWriteBus[O](io: CIO[_, O]): WriteBus[O]       = io.output

  }

  type EqualIO[T] = EIO[T]
  type EIO[T]     = CIO[T, T]
  object EIO {
    def apply[T]: EIO[T] = new EventBus[T]
  }

  def amend[El <: Element](mods: Mod[El]*): Mod[El] = inContext[El](el => el.amend(mods: _*))

  class OnMount[El <: Element, T] private[internal] (private val f: MountContext[El] => T) {
    def -->(observer: Observer[T]): Mod[El]     = onMountCallback(observer.contramap(f).onNext)
    def mapTo[V](v: => V): OnMount[El, V]       = new OnMount(f andThen (_ => v))
    def map[V](project: T => V): OnMount[El, V] = new OnMount(f andThen project)
  }

  def onMount[El <: Element] = new OnMount[El, MountContext[El]](identity)
}
