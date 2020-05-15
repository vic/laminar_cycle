package cycle

import com.raquo.laminar.api.L._
import com.raquo.laminar.nodes.ReactiveElement

trait Cycle {

  trait SubscribeOnMount[El <: Element] {
    val subscribeOnMount: Modifier[El]
  }

  object SubscribeOnMount {
    def cycle[El <: Element, I, O](a: InOut[I, O], b: InOut[O, I]): SubscribeOnMount[El] = {
      val aSub = inOut[El, I](a, b)
      val bSub = inOut[El, O](b, a)
      new SubscribeOnMount[El] {
        override val subscribeOnMount: Modifier[El] = amend[El](aSub, bSub)
      }
    }

    def inOut[El <: Element, T](in: InOut[T, _], out: InOut[_, T]): SubscribeOnMount[El] =
      apply(_ => in.in -> out.out)

    def apply[El <: Element, T](in: EventStream[T], out: WriteBus[T]): SubscribeOnMount[El] =
      apply(_ => in -> out)

    def apply[El <: Element, T](fn: MountContext[El] => (EventStream[T], WriteBus[T])): SubscribeOnMount[El] =
      new SubscribeOnMount[El] {
        val subscribeOnMount: Modifier[El] = onMountBind[El] { ctx =>
          val (in: EventStream[T], out: WriteBus[T]) = fn(ctx)
          Binder[El](el => ReactiveElement.bindBus(el, in)(out))
        }
      }

    implicit def toModifier[El <: Element](
        sub: SubscribeOnMount[El]
    ): Modifier[El] = sub.subscribeOnMount
  }

  trait InOut[I, O] { self =>
    private[cycle] val in: EventStream[I]
    private[cycle] val out: WriteBus[O]

    def cycle[El <: Element](inverse: InOut[O, I]): SubscribeOnMount[El] = {
      SubscribeOnMount.cycle[El, I, O](self, inverse)
    }

    def map[T](operator: I => T): InOut[T, O] = compose(_.map(operator))
    def compose[T](operator: EventStream[I] => EventStream[T]): InOut[T, O] =
      new InOut[T, O] {
        override val in  = self.in.compose(operator)
        override val out = self.out
      }

    def fold[X, Y, El <: Element](
        inOperator: I => X,
        outOperator: Y => O
    ): (InOut[X, Y], SubscribeOnMount[El]) =
      composeBoth(_.map(inOperator), _.map(outOperator))

    def composeBoth[X, Y, El <: Element](
        inOperator: EventStream[I] => EventStream[X],
        outOperator: EventStream[Y] => EventStream[O]
    ): (InOut[X, Y], SubscribeOnMount[El]) = {
      val bus = new EventBus[Y]
      val io = new InOut[X, Y] {
        override val in  = self.in.compose(inOperator)
        override val out = bus.writer
      }
      val som = SubscribeOnMount[El, Y] { ctx: MountContext[El] =>
        bus.events -> self.out.contracomposeWriter(outOperator)(ctx.owner)
      }
      io -> som
    }

    def contramap[T, El <: Element](
        operator: T => O
    ): (InOut[I, T], SubscribeOnMount[El]) =
      contracompose(_.map(operator))

    def contracompose[T, El <: Element](
        operator: EventStream[T] => EventStream[O]
    ): (InOut[I, T], SubscribeOnMount[El]) = {
      val bus = new EventBus[T]
      val io = new InOut[I, T] {
        override val in  = self.in
        override val out = bus.writer
      }
      val som = SubscribeOnMount[El, T] { ctx: MountContext[El] =>
        bus.events -> self.out.contracomposeWriter(operator)(ctx.owner)
      }
      io -> som
    }
  }

  object InOut {

    def apply[I, O]: (InOut[I, O], InOut[O, I]) =
      splitEventBus(new EventBus[I], new EventBus[O])

    implicit def fromEventBus[T](eventBus: EventBus[T]): IO[T] =
      new InOut[T, T] {
        override val in  = eventBus.events
        override val out = eventBus.writer
      }

    def splitEventBus[I, O](iBus: EventBus[I], oBus: EventBus[O]): (InOut[I, O], InOut[O, I]) = {
      val io = new InOut[I, O] {
        override val in  = iBus.events
        override val out = oBus.writer
      }
      val oi = new InOut[O, I] {
        override val in  = oBus.events
        override val out = iBus.writer
      }
      io -> oi
    }

    def toEventBus[T](io: IO[T]): EventBus[T] = new EventBus[T] {
      override val events = io.in
      override val writer = io.out
    }

    implicit def toEventStream[I](io: InOut[I, _]): EventStream[I] = io.in
    implicit def toWriteBus[O](io: InOut[_, O]): WriteBus[O]       = io.out

  }

  type IO[T] = InOut[T, T]
  object IO {
    def apply[T]: IO[T] = new EventBus[T]
  }

  def amend[El <: Element](mods: Mod[El]*): Mod[El] = inContext[El](el => el.amend(mods: _*))

}
