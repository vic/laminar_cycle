package example.cycle_counter

import cycle._

import com.raquo.laminar.api.L._
import org.scalajs.dom

object Counter {

  type State = Int
  val initialState: State = 0

  sealed trait Action
  case object Reset extends Action
  case object Increment extends Action
  case object Decrement extends Action

  def apply(state: IO[State], actions: IO[Action]): Div = {
    val currentState: Signal[State] = state.startWith(initialState)

    val updatedState = actions.in.withCurrentValueOf(currentState).map {
      case (Reset, _)     => initialState
      case (Increment, n) => n + 1
      case (Decrement, n) => n - 1
    }

    div(
      state.addOut(updatedState).subscribeOnMount,
      h2("Counter value: ", child.text <-- currentState.map(_.toString)),
      button(
        cls := "btn secondary",
        "Increment",
        onClick.mapTo(Increment) --> actions.out
      ),
      button(
        cls := "btn secondary",
        "Decrement",
        onClick.mapTo(Decrement) --> actions.out
      ),
      button(
        cls := "btn secondary",
        "Reset",
        onClick.mapTo(Reset) --> actions.out
      )
    )
  }

}

object Main extends App {
  val state: IO[Counter.State] = new EventBus[Counter.State]
  val actions: IO[Counter.Action] = new EventBus[Counter.Action]
  render(dom.document.getElementById("app"), Counter(state, actions))
}
