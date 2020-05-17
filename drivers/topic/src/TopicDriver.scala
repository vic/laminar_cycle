package cycle

import com.raquo.laminar.api.L._
import izumi.reflect.Tag

object TopicDriver {

  // type T = Topic
  type Handler[T, I, O] = EventStream[Request[T, I, O]] => EventStream[Response[T, I, O]]

  sealed trait Input[T, I, O]
  final case class Request[T, I, O](topic: T, payload: I)         extends Input[T, I, O]
  final case class Register[T, I, O](handler: Handler[T, I, O])   extends Input[T, I, O]
  final case class Deregister[T, I, O](handler: Handler[T, I, O]) extends Input[T, I, O]

  final case class Response[T, I, O](topic: T, payload: O)

  def apply[T: Tag, I: Tag, O: Tag]: Cycle[CIO[Response[T, I, O], Input[T, I, O]], ModEl] = { user =>
    val pio = PIO[Input[T, I, O], Response[T, I, O]]

    val handlers        = EIO[Set[Handler[T, I, O]]]
    val currentHandlers = handlers.startWith(Set.empty)

    val registers   = pio.collect { case register: Register[T, I, O]     => register }
    val deregisters = pio.collect { case deregister: Deregister[T, I, O] => deregister }
    val requests    = pio.collect { case request: Request[T, I, O]       => request }

    val registrations = registers.map(_.handler).withCurrentValueOf(currentHandlers).map {
      case (handler, handlers) => handlers + handler
    }

    val deregistrations = deregisters.map(_.handler).withCurrentValueOf(currentHandlers).map {
      case (handler, handlers) => handlers - handler
    }

    val currentHandlingStream: Signal[EventStream[Handler[T, I, O]]] = currentHandlers
      .map(set => EventStream.fromSeq(set.toSeq, emitOnce = false))

    val responses: EventStream[Response[T, I, O]] =
      requests.withCurrentValueOf(currentHandlingStream).flatMap {
        case (request: Request[T, I, O], handlerStream: EventStream[Handler[T, I, O]]) =>
          handlerStream.flatMap { handler => handler(EventStream.fromValue(request, emitOnce = true)) }
      }

    amend(
      responses --> pio,
      registrations --> handlers,
      deregistrations --> handlers,
      user(pio)
    )
  }

}
