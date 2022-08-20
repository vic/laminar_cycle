package cycle.core.air

import cycle.Cycle
import cycle.core.*

import com.raquo.airstream.core.EventStream

private[core] final class AirCycleFactory[I, S, O] private[core] (
    protected[core] val emitter: EventEmitter[I, S, O]
):

  def fromInputHandler(handler: EventEmitter[I, S, O] => EventStream[I] => EventStream[Event]): PartiallyApplied =
    new PartiallyApplied(handler)

  def fromStateReducer(f: I => S => S): PartiallyApplied = fromInputHandler(_.stateReducer(f))

  private[air] final class PartiallyApplied private[air] (
      handler: EventEmitter[I, S, O] => (EventStream[I] => EventStream[Event])
  ):
    def withStateHolder(state: => StateHolder[S]): Cycle[I, S, O] =
      AirCycle.fromEmitter(emitter)(handler(emitter), state)

    def withInitialState(s: => S): Cycle[I, S, O] = withStateHolder(StateHolder.fromValue(s))
