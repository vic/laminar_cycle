package cycle.core.air

import com.raquo.laminar.api.L.*
import com.raquo.laminar.nodes.ReactiveElement
import cycle.Cycle
import cycle.core.*

import scala.util.{Failure, Success, Try}

object AirCycle:
  type Arrow[A, B] = EventStream[A] => EventStream[B]

  def fromEmitter[I, S, O](
      emitter: EventEmitter[I, S, O]
  ): (Arrow[I, Event], StateHolder[S]) => Cycle[I, S, O] =
    import emitter.eventTypes.*
    AirCycle[Event, I, S, O](
      eventFromIn = In(_),
      eventToWithState = _.collect { case CurrentState(f) => f },
      eventToState = _.collect { case SetState(s) => s },
      eventToOut = _.collect { case Out(o) => o },
      eventToWithHandler = _.collect { case CurrentHandler(f) => f },
      eventToHandler = _.collect { case SetHandler(h) => h },
      eventToIn = _.collect { case In(i) => i },
      eventToNoop = _.collect { case Noop => Noop }
    )

  def apply[E, I, S, O](
      eventFromIn: I => E,
      eventToWithState: Arrow[E, S => EventStream[E]],
      eventToState: Arrow[E, S],
      eventToOut: Arrow[E, O],
      eventToWithHandler: Arrow[E, Arrow[I, E] => EventStream[E]],
      eventToHandler: Arrow[E, Arrow[I, E]],
      eventToIn: Arrow[E, I],
      eventToNoop: Arrow[E, Any]
  )(initialHandler: => Arrow[I, E], stateHolder: => StateHolder[S]): Cycle[I, S, O] = new:
    private lazy val stateHolder_ = stateHolder

    override def stateSignal: Signal[S] = stateHolder_.toObservable

    override def toObserver: Observer[I] = eventBus.writer.contramap(eventFromIn)

    override def toObservable: EventStream[O] = outStream

    private val eventBus: EventBus[E] = new EventBus()

    private val loopbackFromCurrentState: EventStream[E] =
      eventBus.events
        .compose(eventToWithState)
        .withCurrentValueOf(stateSignal)
        .flatMap { case (f, state) => f(state) }

    private val updatedState: EventStream[S] =
      eventBus.events.compose(eventToState)

    private val outStream: EventStream[O] =
      eventBus.events.compose(eventToOut)

    private val handlerSignal: Signal[Arrow[I, E]] =
      eventBus.events
        .compose(eventToHandler)
        .foldLeftRecover(Try(initialHandler)) {
          case (_, updated) => updated
        }

    private val loopbackFromCurrentHandler: EventStream[E] =
      eventBus.events
        .compose(eventToWithHandler)
        .withCurrentValueOf(handlerSignal)
        .flatMap { case (f, handler) => f(handler) }

    private val loopBackFromInput: EventStream[E] =
      eventBus.events
        .compose(eventToIn)
        .compose { ins =>
          val firstHandler =
            EventStream.fromValue((), emitOnce = true).sample(handlerSignal)
          val handlerStream =
            EventStream.merge(firstHandler, handlerSignal.changes)
          handlerStream.flatMap(handler => handler(ins))
        }

    private val noopStream: EventStream[Unit] =
      eventBus.events.compose(eventToNoop).mapToStrict(())

    override def toModifier[E <: ReactiveElement.Base]: Mod[E] = Seq(
      updatedState --> stateHolder_.toObserver,
      loopBackFromInput --> eventBus.writer,
      loopbackFromCurrentState --> eventBus.writer,
      loopbackFromCurrentHandler --> eventBus.writer,
      // we just want to make sure all sources are connected even if user does not consumes ie, outStream.
      noopStream --> Observer.empty,
      outStream --> Observer.empty,
      stateSignal --> Observer.empty
    )
