package cyle

import com.raquo.airstream.core.EventStream

final class EventEmitter[I, S, O] private[cycle] () {
  import Event._

  def noop: EventStream[Noop.type] = emit(Noop)

  def input(i: I): EventStream[In[I]]   = emit(new In(i))
  def output(o: O): EventStream[Out[O]] = emit(new Out(o))

  def currentState(f: S => EventStream[Event[I, S, O]]): EventStream[CurrentState[I, S, O]] = emit(new CurrentState(f))

  def setState(s: S): EventStream[SetState[S]] = emit(new SetState(s))

  def currentHandler(f: InputHandler[I, S, O] => EventStream[Event[I, S, O]]): EventStream[CurrentState[I, S, O]] = emit(new CurrentHandler(f))

  def setHandler(h: InputHandler[I, S, O]): EventStream[SetHandler[I, S, O]] = emit(new SetHandler(h))

  private def emit[X](x: => X): EventStream[X] =
    EventStream.fromValue((), emitOnce = true).mapTo(x)
}
