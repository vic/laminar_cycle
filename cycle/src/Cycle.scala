package cycle

import com.raquo.laminar.api.L._

trait Cycle {

  trait SubscribeOnMount[El <: Element] {
    val subscribeOnMount: Modifier[El]
  }

  object SubscribeOnMount {
    implicit def toModifier[El <: Element](
      sub: SubscribeOnMount[El]
    ): Modifier[El] = sub.subscribeOnMount
  }

  trait InOut[I, O] { self =>
    val in: EventStream[I]
    val out: WriteBus[O]

    def map[T](operator: I => T): InOut[T, O] = compose(_.map(operator))
    def compose[T](operator: EventStream[I] => EventStream[T]): InOut[T, O] =
      new InOut[T, O] {
        override val in = self.in.compose(operator)
        override val out = self.out
      }

    def fold[X, Y, El <: Element](
      inOperator: I => X,
      outOperator: Y => O
    ): InOut[X, Y] with SubscribeOnMount[El] =
      composeBoth(_.map(inOperator), _.map(outOperator))

    def composeBoth[X, Y, El <: Element](
      inOperator: EventStream[I] => EventStream[X],
      outOperator: EventStream[Y] => EventStream[O]
    ): InOut[X, Y] with SubscribeOnMount[El] = {
      val bus = new EventBus[Y]
      new InOut[X, Y] with SubscribeOnMount[El] {
        override val in = self.in.compose(inOperator)
        override val out = bus.writer
        override val subscribeOnMount: Modifier[El] = onMountCallback { ctx =>
          implicit val owner = ctx.owner
          self.out.contracomposeWriter(outOperator).addSource(bus.events)
        }
      }
    }

    def contramap[T, El <: Element](
      operator: T => O
    ): InOut[I, T] with SubscribeOnMount[El] =
      contracompose(_.map(operator))

    def contracompose[T, El <: Element](
      operator: EventStream[T] => EventStream[O]
    ): InOut[I, T] with SubscribeOnMount[El] = {
      val bus = new EventBus[T]
      new InOut[I, T] with SubscribeOnMount[El] {
        override val in = self.in
        override val out = bus.writer
        override val subscribeOnMount: Modifier[El] = onMountCallback { ctx =>
          implicit val owner = ctx.owner
          self.out.contracomposeWriter(operator).addSource(bus.events)
        }
      }
    }

    def addOut[El <: Element](source: EventStream[O]): SubscribeOnMount[El] =
      new SubscribeOnMount[El] {
        override val subscribeOnMount =
          onMountCallback { ctx =>
            org.scalajs.dom.console
              .log("AddOut subscribe on Mount", ctx.thisNode.ref)
            out.addSource(source)(ctx.owner)
          }
      }
  }

  object InOut {

    implicit def fromEventBus[T](eventBus: EventBus[T]): IO[T] =
      new InOut[T, T] {
        override val in = eventBus.events
        override val out = eventBus.writer
      }

    implicit def toEventBus[T](io: IO[T]): EventBus[T] = new EventBus[T] {
      override val events = io.in
      override val writer = io.out
    }

    implicit def toEventStream[I](io: InOut[I, _]): EventStream[I] = io.in
    implicit def toWriteBus[O](io: InOut[_, O]): WriteBus[O] = io.out

  }

  type IO[T] = InOut[T, T]

  def onMount[El <: Element]: IO[MountContext[El]] with SubscribeOnMount[El] = {
    val bus = new EventBus[MountContext[El]]
    new IO[MountContext[El]] with SubscribeOnMount[El] {
      override val in = bus.events
      override val out = bus.writer
      override val subscribeOnMount: Modifier[El] = onMountCallback { ctx =>
        org.scalajs.dom.console.log("Mount", ctx.thisNode.ref)
        val es =
          EventStream.fromValue(ctx, emitOnce = true).debugLog("MOOUNTED ")
        out.addSource(es)(ctx.owner)
      }
    }
  }

}
