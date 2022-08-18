package cycle.core.air

import cycle.Cycle
import cycle.core.*

import com.raquo.laminar.api.L.*
import com.raquo.laminar.nodes.ReactiveElement

import scala.util.Failure
import scala.util.Success
import scala.util.Try

private[air] trait AirCycle[I, S, O] extends Cycle[I, S, O] {

  protected[air] type Event
  protected[air] final type InputHandler = EventStream[I] => EventStream[Event]
  protected[air] val withStateFilter: EventStream[Event] => EventStream[S => EventStream[Event]]
  protected[air] val setStateFiler: EventStream[Event] => EventStream[S]
  protected[air] val outFilter: EventStream[Event] => EventStream[O]
  protected[air] val withHandlerFilter: EventStream[Event] => EventStream[InputHandler => EventStream[Event]]
  protected[air] val setHandlerFilter: EventStream[Event] => EventStream[InputHandler]
  protected[air] val inputFilter: EventStream[Event] => EventStream[I]
  protected[air] val noopFilter: EventStream[Event] => EventStream[Any]

  protected[air] def inEvent(i: I): Event

  protected[air] val stateHolder: StateHolder[S]
  protected[air] val initialHandler: InputHandler

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

  override def toModifier[E <: ReactiveElement.Base]: Mod[E] = Seq(
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
