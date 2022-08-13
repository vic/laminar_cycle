package cyle

import com.raquo.laminar.api.L._
import com.raquo.laminar.nodes.ReactiveElement

import scala.util.Failure
import scala.util.Success
import scala.util.Try

private[cycle] trait AirCycle[-I, +S, +O] extends Cycle[I, S, O] {

  final type Event        = cyle.Event[_ >: I, S, _ <: O]
  final type InputHandler = cyle.InputHandler[_ >: I, S, _ <: O]

  protected[cycle] val stateHolder: StateHolder[_ <: S]
  protected[cycle] def initialHandler: InputHandler

  override lazy val stateSignal: Signal[S] = stateHolder.toObservable

  override def toObserver: Observer[I]      = eventBus.writer.contramap(new Event.In(_))
  override def toObservable: EventStream[O] = outStream

  val eventBus: EventBus[Event] = new EventBus()

  val loopbackFromCurrentState: EventStream[Event] =
    eventBus.events
      .collect { case e: Event.CurrentState[_ >: I, S, _ <: O] => e.f }
      .withCurrentValueOf(stateSignal)
      .flatMap { case (withState, state) => withState(state) }

  val updatedState: EventStream[S] =
    eventBus.events
      .collect { case e: Event.SetState[S] => e.s }

  val outStream: EventStream[O] =
    eventBus.events.collect { case e: Event.Out[O] => e.out }

  val handlerSignal: Signal[InputHandler] =
    eventBus.events
      .collect { case e: Event.SetHandler[_ >: I, S, _ <: O] => e.h }
      .foldLeftRecover(Try(initialHandler)) {
        case (_, Success(updated))   => Success(updated)
        case (_, Failure(exception)) => Failure(exception)
        case (current, _)            => current
      }

  val loopBackFromInput: EventStream[Event] =
    eventBus.events
      .collect { case e: Event.In[I] => e.in }
      .compose { ins =>
        val firstHandler  = EventStream.fromValue((), emitOnce = true).sample(handlerSignal)
        val handlerStream = EventStream.merge(firstHandler, handlerSignal.changes)
        handlerStream.flatMap(handler => handler(ins))
      }

  val noopEvents: EventStream[Unit] =
    eventBus.events
      .collect { case Event.Noop => () }

  override def bind[E <: ReactiveElement.Base]: Mod[E] = Seq(
    updatedState --> stateHolder,
    loopBackFromInput --> eventBus.writer,
    loopbackFromCurrentState --> eventBus.writer,

    // we just want to make sure all sources are connected even if user does not consumes ie, outStream.
    noopEvents --> Observer.empty,
    outStream --> Observer.empty,
    stateSignal --> Observer.empty
  )

}
