package cycle.core

import com.raquo.laminar.api.L._
import com.raquo.laminar.nodes.ReactiveElement

import scala.util.Failure
import scala.util.Success
import scala.util.Try

private[cycle] abstract class AirCycle[I, S, O] private[cycle] ()
    extends cycle.Cycle[I, S, O] with EventTypes[I, S, O] {

  protected[cycle] val stateHolder: StateHolder[S]
  protected[cycle] def initialHandler: InputHandler

  override def stateSignal: Signal[S] = stateHolder.toObservable

  override def toObserver: Observer[I] =
    eventBus.writer.contramap(new In(_))

  override def toObservable: EventStream[O] = outStream

  val eventBus: EventBus[Event] =
    new EventBus()

  val loopbackFromCurrentState: EventStream[Event] =
    eventBus.events
      .collect { case CurrentState(f) => f }
      .withCurrentValueOf(stateSignal)
      .flatMap { case (withState, state) => withState(state) }

  val updatedState: EventStream[S] =
    eventBus.events
      .collect { case SetState(s: S) => s }

  val outStream: EventStream[O] =
    eventBus.events.collect { case Out(o: O) => o }

  val handlerSignal: Signal[InputHandler] =
    eventBus.events
      .collect { case SetHandler(h: InputHandler) => h }
      .foldLeftRecover(Try(initialHandler)) {
        case (_, updated) => updated
      }

  val loopBackFromInput: EventStream[Event] =
    eventBus.events
      .collect { case In(i: I @unchecked) => i }
      .compose { ins =>
        val firstHandler =
          EventStream.fromValue((), emitOnce = true).sample(handlerSignal)
        val handlerStream =
          EventStream.merge(firstHandler, handlerSignal.changes)
        handlerStream.flatMap(handler => handler(ins))
      }

  val noopEvents: EventStream[Unit] =
    eventBus.events
      .collect { case Noop => () }

  override def bind[E <: ReactiveElement.Base]: Mod[E] = Seq(
    updatedState --> stateHolder.toObserver,
    loopBackFromInput --> eventBus.writer,
    loopbackFromCurrentState --> eventBus.writer,
    // we just want to make sure all sources are connected even if user does not consumes ie, outStream.
    noopEvents --> Observer.empty,
    outStream --> Observer.empty,
    stateSignal --> Observer.empty
  )

}
