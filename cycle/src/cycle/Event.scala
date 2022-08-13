package cyle

import com.raquo.airstream.core.EventStream

sealed trait Event[-I, +S, +O]
private[cycle] object Event {
  object Noop extends Event[Nothing, Nothing, Nothing]

  final class In[I] private[cycle] (val in: I)   extends Event[I, Any, Nothing]
  final class Out[O] private[cycle] (val out: O) extends Event[Any, Any, O]

  final class CurrentState[I, S, O] private[cycle] (val f: S => EventStream[Event[I, S, O]]) extends Event[I, S, O]
  final class SetState[S] private[cycle] (val s: S)                                          extends Event[Any, S, Nothing]

  final class CurrentHandler[I, S, O] private[cycle] (val f: InputHandler[I, S, O] => EventStream[Event[I, S, O]]) extends Event[I, S, O]
  final class SetHandler[I, S, O] private[cycle] (val h: InputHandler[I, S, O])                                    extends Event[I, S, O]
}
