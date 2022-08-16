package cycle.core

import com.raquo.airstream.core.EventStream

private[core] trait EventTypes[I, S, O] {
  sealed trait Event

  private[core] object Noop extends Event

  final case class In private[core] (in: I)   extends Event
  final case class Out private[core] (out: O) extends Event

  final case class CurrentState private[core] (f: S => EventStream[Event]) extends Event

  final case class SetState private[core] (s: S) extends Event

  final case class CurrentHandler private[core] (f: InputHandler => EventStream[Event]) extends Event

  final case class SetHandler private[core] (h: InputHandler) extends Event

  type InputHandler = EventStream[I] => EventStream[Event]

  type Emitter = EventEmitter[I, S, O]

}
