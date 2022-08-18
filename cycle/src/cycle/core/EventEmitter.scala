package cycle.core

import com.raquo.airstream.core.EventStream

private[core] final class EventEmitter[I, S, O] private[core] (val eventTypes: EventTypes[I, S, O]) {
  import eventTypes.*

  def noop: EventStream[Event] = emit(Noop)

  def input(i: I): EventStream[Event]  = emit(In(i))
  def output(o: O): EventStream[Event] = emit(Out(o))

  def withCurrentState(f: S => EventStream[Event]): EventStream[Event] = emit(CurrentState(f))

  def setState(s: S): EventStream[Event] = emit(SetState(s))

  def withCurrentHandler(f: InputHandler => EventStream[Event]): EventStream[Event] = emit(CurrentHandler(f))

  def updateState(f: S => S): EventStream[Event] = withCurrentState(s => setState(f(s)))

  def setHandler(h: InputHandler): EventStream[Event] = emit(SetHandler(h))

  def updateHandler(f: InputHandler => InputHandler): EventStream[Event] = withCurrentHandler(h => setHandler(f(h)))

  private def emit[X](x: => X): EventStream[X] =
    EventStream.fromValue((), emitOnce = true).mapTo(x)
}
