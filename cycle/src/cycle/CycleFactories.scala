package cyle

private[cycle] trait CycleFactories {
  import CycleFactories.PartiallyApplied

  def fromStream[I, S, O](handler: EventEmitter[I, S, O] => InputHandler[I, S, O]): PartiallyApplied[I, S, O] =
    new PartiallyApplied[I, S, O](handler(new EventEmitter))

  def fromStateUpdater[I, S](updateState: I => S => S): PartiallyApplied[I, S, Nothing] =
    fromStream[I, S, Nothing] { emitter =>
      _.flatMap { i => emitter.currentState(s => emitter.setState(updateState(i)(s))) }
    }
}

private[cycle] object CycleFactories {

  final class PartiallyApplied[-I, S, +O] private[cycle] (handler: => InputHandler[I, S, O]) {
    def apply(s: => S): Cycle[I, S, O] = apply(StateHolder.fromValue(s))

    def apply(state: StateHolder[S]): Cycle[I, S, O] =
      new AirCycle[I, S, O] {
        override protected val stateHolder: StateHolder[S]  = state
        override protected def initialHandler: InputHandler = handler
      }
  }

}
