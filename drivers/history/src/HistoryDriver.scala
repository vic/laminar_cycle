package cycle

import com.raquo.domtypes.jsdom.defs.events.PageTransitionEvent
import com.raquo.laminar.api.L._
import org.scalajs.dom
import org.scalajs.dom.PopStateEvent

import scala.scalajs.js

object History {

  final case class State(
      data: js.Any = dom.window.history.state,
      title: String = dom.window.document.title,
      href: String = dom.window.location.href
  )

  sealed trait Action
  case object ReadState                 extends Action
  case class Go(delta: Int)             extends Action
  case object GoForward                 extends Action
  case object GoBack                    extends Action
  case class PushState(state: State)    extends Action
  case class ReplaceState(state: State) extends Action

  sealed trait Event {
    val state: State
  }
  case class Read private (state: State = readState)     extends Event
  case class Pushed private (state: State = readState)   extends Event
  case class Replaced private (state: State = readState) extends Event
  case class Popped private (state: State = readState)   extends Event
  case class Hidden private (state: State = readState)   extends Event
  case class Shown private (state: State = readState)    extends Event

  private def readState: State = State(data = dom.window.history.state)

  type IO = MIO[State, Event, Action]

  def driver[El <: Element]: Driver[IO, El] = {
    val pio         = PIO[Action, Event]
    val state       = new EventBus[State]
    val stateSignal = state.events.toSignal(readState)

    val onPopState: EventStream[PopStateEvent] =
      windowEvents.onPopState.eventStream
    val onPageHide: EventStream[PageTransitionEvent] =
      windowEvents.onPageHide.eventStream
    val onPageShow: EventStream[PageTransitionEvent] =
      windowEvents.onPageShow.eventStream

    val exec = pio
      .collect[Option[Action]] {
        case ReadState =>
          Some(ReadState)
        case a @ PushState(state) =>
          dom.window.history.pushState(state.data, state.title, state.href)
          Some(a)
        case a @ ReplaceState(state) =>
          dom.window.history.replaceState(state.data, state.title, state.href)
          Some(a)
        case Go(direction) =>
          dom.window.history.go(direction)
          None
        case GoBack =>
          dom.window.history.back()
          None
        case GoForward =>
          dom.window.history.forward()
          None
      }
      .collect { case Some(action) => action }

    val reads: EventStream[Event] = EventStream.merge(
      exec.collect {
        case ReadState       => Read()
        case _: PushState    => Pushed()
        case _: ReplaceState => Replaced()
      },
      onPopState.mapTo(Popped()),
      onPageShow.mapTo(Shown()),
      onPageHide.mapTo(Hidden())
    )

    Driver(
      MIO(stateSignal, PairedIO.in2(pio), PairedIO.out2(pio)),
      reads --> pio,
      reads.map(_.state) --> state.writer
    )
  }

}
