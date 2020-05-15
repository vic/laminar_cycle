package cycle

import com.raquo.laminar.api.L._

object TopicDriver {

  // type T = Topic
  type Handler[T, I, O] = EventStream[Request[T, I, O]] => EventStream[Response[T, I, O]]

  sealed trait Sense[T, I, O]
  final case class Request[T, I, O](topic: T, payload: I)         extends Sense[T, I, O]
  final case class Register[T, I, O](handler: Handler[T, I, O])   extends Sense[T, I, O]
  final case class Deregister[T, I, O](handler: Handler[T, I, O]) extends Sense[T, I, O]

  sealed trait Actuator[T, I, O]
  final case class Response[T, I, O](topic: T, payload: O) extends Actuator[T, I, O]

  type ActuatorSense[T, I, O] = CIO[Actuator[T, I, O], Sense[T, I, O]]

  def apply[T, I, O](user: ActuatorSense[T, I, O] => Mod[Element]): Mod[Element] = {
    val (io, oi) = CIO[Sense[T, I, O], Actuator[T, I, O]]

    val handlers        = EIO[Set[Handler[T, I, O]]]
    val currentHandlers = handlers.startWith(Set.empty)

    val registers   = io.collect { case register: Register[T, I, O]     => register }
    val deregisters = io.collect { case deregister: Deregister[T, I, O] => deregister }
    val requests    = io.collect { case request: Request[T, I, O]       => request }

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
      responses --> io,
      registrations --> handlers,
      deregistrations --> handlers,
      user(oi)
    )
  }

}
