package cycle

import com.raquo.laminar.api.L._

object topic {

  // type T = Topic
  type Handler[T, I, O] = EventStream[Pub[T, I, O]] => EventStream[Msg[T, I, O]]

  sealed trait Input[T, I, O]
  final case class Pub[T, I, O](topic: T, payload: I) extends Input[T, I, O]
  final case class Sub[T, I, O](handler: Handler[T, I, O])
      extends Input[T, I, O]
  final case class UnSub[T, I, O](handler: Handler[T, I, O])
      extends Input[T, I, O]

  final case class Msg[T, I, O](topic: T, payload: O)

  type TopicIO[T, I, O] = CIO[Msg[T, I, O], Input[T, I, O]]

  def apply[T, I, O]: DriverEl[TopicIO[T, I, O]] = {
    val pio = PIO[Input[T, I, O], Msg[T, I, O]]

    val handlers        = EIO[Set[Handler[T, I, O]]]
    val currentHandlers = handlers.startWith(Set.empty)

    val subIn   = pio.collect { case sub: Sub[T, I, O]     => sub }
    val unsubIn = pio.collect { case unsub: UnSub[T, I, O] => unsub }
    val pubIn   = pio.collect { case pub: Pub[T, I, O]     => pub }

    val subs = subIn.map(_.handler).withCurrentValueOf(currentHandlers).map {
      case (handler, handlers) => handlers + handler
    }

    val unsubs =
      unsubIn.map(_.handler).withCurrentValueOf(currentHandlers).map {
        case (handler, handlers) => handlers - handler
      }

    val currentHandlingStream: Signal[EventStream[Handler[T, I, O]]] =
      currentHandlers
        .map(set => EventStream.fromSeq(set.toSeq, emitOnce = false))

    val msgs: EventStream[Msg[T, I, O]] =
      pubIn.withCurrentValueOf(currentHandlingStream).flatMap {
        case (
            pub: Pub[T, I, O],
            handlerStream: EventStream[Handler[T, I, O]]
            ) =>
          handlerStream.flatMap { handler =>
            handler(EventStream.fromValue(pub, emitOnce = true))
          }
      }

    Driver(pio, msgs --> pio, subs --> handlers, unsubs --> handlers)
  }

}
