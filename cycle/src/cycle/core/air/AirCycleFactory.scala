package cycle.core.air

import cycle.Cycle
import cycle.core.*

import com.raquo.airstream.core.EventStream

private[air] object AirCycleFactory {

  private[air] def newAirCycle[I, S, O](
      emitter: EventEmitter[I, S, O]
  )(
      handler: => emitter.eventTypes.InputHandler,
      state: => StateHolder[S]
  ): Cycle[I, S, O] = {
    import emitter.eventTypes.*
    new AirCycle[I, S, O] {
      override protected[air] type Event = emitter.eventTypes.Event

      override protected[air] val initialHandler: InputHandler = handler
      override protected[air] val stateHolder: StateHolder[S]  = state

      override protected[air] def inEvent(i: I): Event = emitter.eventTypes.In(i)

      override protected[air] val inputFilter: EventStream[Event] => EventStream[I] =
        _.collect { case In(i) => i }

      override protected[air] val withStateFilter: EventStream[Event] => EventStream[S => EventStream[Event]] =
        _.collect { case CurrentState(f) => f }

      override protected[air] val setStateFiler: EventStream[Event] => EventStream[S] =
        _.collect { case SetState(s) => s }

      override protected[air] val outFilter: EventStream[Event] => EventStream[O] =
        _.collect { case Out(o) => o }

      override protected[air] val withHandlerFilter
          : EventStream[Event] => EventStream[(InputHandler) => EventStream[Event]] =
        _.collect { case CurrentHandler(f) => f }

      override protected[air] val noopFilter: EventStream[Event] => EventStream[Any] =
        _.collect { case Noop => Noop }

      override protected[air] val setHandlerFilter: EventStream[Event] => EventStream[InputHandler] =
        _.collect { case SetHandler(h) => h }

    }
  }
}

private[core] final class AirCycleFactory[I, S, O] private[core] (
    protected[core] val emitter: EventEmitter[I, S, O]
) {
  import emitter.eventTypes.*
  type Emitter = emitter.type

  def fromInputHandler(handler: Emitter => EventStream[I] => EventStream[Event]): PartiallyApplied =
    new PartiallyApplied(handler)

  def fromStateReducer(f: I => S => S): PartiallyApplied = fromInputHandler(_.stateReducer(f))

  private[air] final class PartiallyApplied private[air] (handler: Emitter => InputHandler) {
    def withStateHolder(state: StateHolder[S]): Cycle[I, S, O] =
      AirCycleFactory.newAirCycle(emitter)(handler(emitter), state)

    def withInitialState(s: => S): Cycle[I, S, O] = withStateHolder(StateHolder.fromValue(s))
  }

}
