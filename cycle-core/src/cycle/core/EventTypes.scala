package cycle.core

import com.raquo.airstream.core.EventStream

sealed trait Event

private[core] class EventTypes[I, S, O] private[core] ():
  type InputHandler = EventStream[I] => EventStream[Event]
  private[core] object Noop extends Event

  private[core] final case class In private[core] (in: I)   extends Event
  private[core] final case class Out private[core] (out: O) extends Event

  private[core] final case class CurrentState private[core] (f: S => EventStream[Event]) extends Event

  private[core] final case class SetState private[core] (s: S) extends Event

  private[core] final case class CurrentHandler private[core] (f: InputHandler => EventStream[Event]) extends Event

  private[core] final case class SetHandler private[core] (h: InputHandler) extends Event
