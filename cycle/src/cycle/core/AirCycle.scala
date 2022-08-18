package cycle.core

import com.raquo.laminar.api.L._
import com.raquo.laminar.nodes.ReactiveElement

import scala.util.Failure
import scala.util.Success
import scala.util.Try

private[cycle] trait AirCycle[I, S, O] extends cycle.Cycle[I, S, O] {

  protected[cycle] type Event
  protected[cycle] final type InputHandler = EventStream[I] => EventStream[Event]
  protected[cycle] val withStateFilter: EventStream[Event] => EventStream[S => EventStream[Event]]
  protected[cycle] val setStateFiler: EventStream[Event] => EventStream[S]
  protected[cycle] val outFilter: EventStream[Event] => EventStream[O]
  protected[cycle] val withHandlerFilter: EventStream[Event] => EventStream[InputHandler => EventStream[Event]]
  protected[cycle] val setHandlerFilter: EventStream[Event] => EventStream[InputHandler]
  protected[cycle] val inputFilter: EventStream[Event] => EventStream[I]
  protected[cycle] val noopFilter: EventStream[Event] => EventStream[Any]

  protected[cycle] def inEvent(i: I): Event

  protected[cycle] val stateHolder: StateHolder[S]
  protected[cycle] val initialHandler: InputHandler

  override def stateSignal: Signal[S] = stateHolder.toObservable

  override def toObserver: Observer[I] = eventBus.writer.contramap(inEvent)

  override def toObservable: EventStream[O] = outStream

  val eventBus: EventBus[Event] = new EventBus()

  val loopbackFromCurrentState: EventStream[Event] =
    eventBus.events
      .compose(withStateFilter)
      .withCurrentValueOf(stateSignal)
      .flatMap { case (f, state) => f(state) }

  val updatedState: EventStream[S] =
    eventBus.events.compose(setStateFiler)

  val outStream: EventStream[O] =
    eventBus.events.compose(outFilter)

  val handlerSignal: Signal[InputHandler] =
    eventBus.events
      .compose(setHandlerFilter)
      .foldLeftRecover(Try(initialHandler)) {
        case (_, updated) => updated
      }

  val loopbackFromCurrentHandler: EventStream[Event] =
    eventBus.events
      .compose(withHandlerFilter)
      .withCurrentValueOf(handlerSignal)
      .flatMap { case (f, handler) => f(handler) }

  val loopBackFromInput: EventStream[Event] =
    eventBus.events
      .compose(inputFilter)
      .compose { ins =>
        val firstHandler =
          EventStream.fromValue((), emitOnce = true).sample(handlerSignal)
        val handlerStream =
          EventStream.merge(firstHandler, handlerSignal.changes)
        handlerStream.flatMap(handler => handler(ins))
      }

  val noopStream: EventStream[Unit] =
    eventBus.events.compose(noopFilter).mapToStrict(())

  override def bind[E <: ReactiveElement.Base]: Mod[E] = Seq(
    updatedState --> stateHolder.toObserver,
    loopBackFromInput --> eventBus.writer,
    loopbackFromCurrentState --> eventBus.writer,
    loopbackFromCurrentHandler --> eventBus.writer,
    // we just want to make sure all sources are connected even if user does not consumes ie, outStream.
    noopStream --> Observer.empty,
    outStream --> Observer.empty,
    stateSignal --> Observer.empty
  )

}
