package example.cycle_counter

import cycle._

import com.raquo.laminar.api.L._
import org.scalajs.dom

object Counter {

  case class State(
      value: Int,
      interactions: Int
  )
  val initialState: State = State(0, 0)

  sealed trait Action
  case object Reset     extends Action
  case object Increment extends Action
  case object Decrement extends Action

  def performAction(action: Action, state: State): State = action match {
    case Reset     => state.copy(initialState.value, state.interactions + 1)
    case Increment => state.copy(state.value + 1, state.interactions + 1)
    case Decrement => state.copy(state.value - 1, state.interactions + 1)
  }

  def actionControls(actions: Observer[Action]): Div = {
    div(
      button(
        "Increment",
        onClick.mapTo(Increment) --> actions
      ),
      button(
        "Decrement",
        onClick.mapTo(Decrement) --> actions
      ),
      button(
        "Reset",
        onClick.mapTo(Reset) --> actions
      )
    )
  }

  def counterView(state: Observable[State]): Div = {
    div(
      h2("Counter value: ", child.text <-- state.map(_.value.toString)),
      h2("Interactions: ", child.text <-- state.map(_.interactions.toString))
    )
  }

  def apply(): Div = {
    val counterCycle = Cycle[Action, State, Nothing]
      .fromStateReducer(performAction.curried)
      .withInitialState(initialState)

    div(
      counterView(counterCycle.stateSignal),
      actionControls(counterCycle.toObserver),
      counterCycle.toModifier
    )
  }

}

object Main {
  def main(args: Array[String]): Unit = {
    render(dom.document.getElementById("app"), Counter())
  }
}
