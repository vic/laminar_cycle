package cycle.core

import cycle.Cycle
import com.raquo.airstream.core.EventStream

private[cycle] trait CycleFactories {
  def apply[I, S, O]: AirCycleFactory[I, S, O] = new AirCycleFactory[I, S, O]
}

private[cycle] final class AirCycleFactory[-I, +S, +O] private[cycle] () {
  type In >: I
  type State <: S
  type Out <: O

  private[cycle] val emitter = new EventEmitter[In, State, Out]
  import emitter._

  def fromStream(handler: Emitter => InputHandler): PartiallyApplied = new PartiallyApplied(handler)

  private[cycle] final class PartiallyApplied(handler: Emitter => InputHandler) {
    def apply(state: StateHolder[State]): Cycle[I, S, O] = new AirCycle[In, State, Out](emitter) {
      override val eventTypes: EventTypes[In, State, Out]           = emitter
      override protected[cycle] def initialHandler: InputHandler    = handler(emitter)
      override protected[cycle] val stateHolder: StateHolder[State] = state
    }
  }
}

//  final class Factories[-I, +S, +O](eventTypes: EventTypes[I, S, O]) {
//    import eventTypes._
//
//    def fromStream(handler: EventEmitter => InputHandler): PartiallyApplied =
//      new PartiallyApplied(handler)
//
//    def fromStateUpdater(updateState: I => S => S)(implicit ev: O =:= Nothing): PartiallyApplied =
//      fromStream { emitter: EventEmitter => (ins: EventStream[I]) =>
//        ins.flatMap { i: I =>
//          emitter.currentState { s =>
//            val newState: S                              = updateState(i)(s)
//            val x: EventStream[emitter.eventTypes.Event] = emitter.setState(newState)
//            x
//          }
//        }
//      }
//
//    private[cycle] final class PartiallyApplied private[cycle] (
//        handler: EventEmitter => InputHandler
//    ) {
//
//      def apply(s: => S): Cycle[I, S, O] = apply(StateHolder.fromValue(s))
//
//      def apply(state: StateHolder[S]): Cycle[I, S, O] = {
//        lazy val emitter = new EventEmitter(eventTypes)
//        new AirCycle[I, S, O](emitter) {
//          override protected val stateHolder: StateHolder[S] = state
//          override protected def initialHandler              = handler
//        }
//      }
//    }
//  }
