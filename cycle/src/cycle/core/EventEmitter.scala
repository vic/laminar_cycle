package cycle.core

import com.raquo.airstream.core.EventStream

private[core] final class EventEmitter[I, S, O] private[core] () extends EventTypes[I, S, O] {
  def noop: EventStream[Event] = emit(Noop)

//  def input[i <: I](i: i): EventStream[Event]  = emit(In(i))
//  def output[o >: O](o: o): EventStream[Event] = emit(Out(o))
//
//  def currentState(f: S => EventStream[Event]): EventStream[Event] = emit(CurrentState(f))
//
//  def setState[s >: S](s: s): EventStream[Event] = emit(SetState(s))
//
//  def currentHandler(f: InputHandler => EventStream[Event]): EventStream[Event] = emit(CurrentHandler(f))
//
//  def setHandler(h: InputHandler): EventStream[Event] = emit(SetHandler(h))
//
  private def emit[X](x: => X): EventStream[X] =
    EventStream.fromValue((), emitOnce = true).mapTo(x)
}
