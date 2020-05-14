package example.nested_indent

import cycle._

import com.raquo.laminar.api.L._
import org.scalajs.dom

object Nested {

  type State = Int
  val defaultIndent = 2

  sealed trait Action
  case object Increment extends Action
  case object Decrement extends Action
  case object AddChild extends Action
  case object RemoveChild extends Action

  def apply(initialIndent: Int, state: IO[State], action: IO[Action]): Div = {
    val currentState = state.startWith(initialIndent)

    val nestedState = state.map(_ + defaultIndent)
    val nestedAction = new EventBus[Action]

    val updatedIndent = action.in.withCurrentValueOf(currentState).collect {
      case (Increment, state)                           => state + 1
      case (Decrement, state) if state <= initialIndent => initialIndent
      case (Decrement, state)                           => state - 1
    }

    val nested: EventStream[Option[Div]] =
      action.in.withCurrentValueOf(currentState).collect {
        case (AddChild, indent) =>
          Some(Nested(indent + defaultIndent, nestedState, nestedAction))
        case (RemoveChild, _) =>
          None
      }

    val hasNested = nested.startWith(None).map(_.isDefined)
    val canDecrement = currentState.map(_ <= initialIndent)

    view(currentState, action, hasNested, canDecrement)
      .amend(state.addOut[Div](updatedIndent), child.maybe <-- nested)
  }

  def view(level: Signal[Int],
           action: Observer[Action],
           canDecrement: Signal[Boolean],
           hasChild: Signal[Boolean]): Div = {
    div(
      paddingLeft <-- level.map(level => s"${level}em"),
      child.text <-- level.map(level => s"Indent level ${level}em"),
      button("Increment", onClick.mapTo(Increment) --> action),
      button(
        "Decrement",
        onClick.mapTo(Decrement) --> action,
        disabled <-- canDecrement.map(!_)
      ),
      button(
        "AddChild",
        onClick.mapTo(AddChild) --> action,
        display <-- hasChild.map {
          case true => "none"
          case _    => "inline"
        }
      ),
      button(
        "RemoveChild",
        onClick.mapTo(RemoveChild) --> action,
        display <-- hasChild.map {
          case true => "inline"
          case _    => "none"
        }
      ),
    )
  }

}

object Example {
  import Nested._

  def apply(): Div = {
    val state: IO[State] = new EventBus[State]
    val action: IO[Action] = new EventBus[Action]

    val mounted = cycle.onMount[Div]
    val initialState: EventStream[State] = mounted.mapTo(defaultIndent)

    div(
      mounted,
      state.addOut[Div](initialState),
      h1("Nested intentation levels"),
      Nested(defaultIndent, state, action)
    )
  }
}

object Main extends App {
  render(dom.document.getElementById("app"), Example())
}
