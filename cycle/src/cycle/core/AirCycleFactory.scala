package cycle.core

import com.raquo.airstream.core.EventStream
import cycle.Cycle

private[cycle] object AirCycleFactory {

  private[cycle] def newAirCycle[I, S, O](
      emitter: EventEmitter[I, S, O]
  )(
      handler: emitter.type => emitter.eventTypes.InputHandler,
      state: StateHolder[S]
  ): Cycle[I, S, O] = {
    import emitter.eventTypes.*
    new AirCycle[I, S, O] {
      override protected[cycle] type Event = emitter.eventTypes.Event

      override protected[cycle] val initialHandler: InputHandler = handler(emitter)
      override protected[cycle] val stateHolder: StateHolder[S]  = state

      override protected[cycle] def inEvent(i: I): Event = emitter.eventTypes.In(i)

      override protected[cycle] val inputFilter: EventStream[Event] => EventStream[I] =
        _.collect { case In(i) => i }

      override protected[cycle] val withStateFilter: EventStream[Event] => EventStream[S => EventStream[Event]] =
        _.collect { case CurrentState(f) => f }

      override protected[cycle] val setStateFiler: EventStream[Event] => EventStream[S] =
        _.collect { case SetState(s) => s }

      override protected[cycle] val outFilter: EventStream[Event] => EventStream[O] =
        _.collect { case Out(o) => o }

      override protected[cycle] val withHandlerFilter
          : EventStream[Event] => EventStream[(InputHandler) => EventStream[Event]] =
        _.collect { case CurrentHandler(f) => f }

      override protected[cycle] val noopFilter: EventStream[Event] => EventStream[Any] =
        _.collect { case Noop => Noop }

      override protected[cycle] val setHandlerFilter: EventStream[Event] => EventStream[InputHandler] =
        _.collect { case SetHandler(h) => h }

    }
  }
}

private[cycle] final class AirCycleFactory[I, S, O] private[cycle] () {
  private[cycle] val emitter: EventEmitter[I, S, O] = new EventEmitter(new EventTypes)
  import emitter.eventTypes.InputHandler

  def fromInputHandler(handler: emitter.type => EventStream[I] => EventStream[emitter.eventTypes.Event])
      : PartiallyApplied =
    new PartiallyApplied(handler)

  def fromStateReducer(f: I => S => S): PartiallyApplied =
    fromInputHandler(e => _.map(f).flatMap(e.updateState))

  private[cycle] final class PartiallyApplied private[cycle] (handler: emitter.type => InputHandler) {
    def withStateHolder(state: StateHolder[S]): Cycle[I, S, O] = AirCycleFactory.newAirCycle(emitter)(handler, state)

    def withInitialState(s: => S): Cycle[I, S, O] = withStateHolder(StateHolder.fromValue(s))
  }

}
