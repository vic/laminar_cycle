package example.cycle_counter

import cycle._
import com.raquo.laminar.api.L._
import org.scalajs.dom

object Example {

  case class Global(
      globalName: String,
      local: Option[Local]
  )

  case class Local(
      localName: String
  )

  def initialState: Global = Global(globalName = "Global", local = None)

  val globalNameLens: EMO[Global] => stateSlice[Global, String] =
    stateSlice[Global, String](
      memBijection(
        fwd = _.map(_.globalName),
        bwd = _.map {
          case (newName, global) =>
            global.copy(globalName = newName)
        }
      )
    )(_)

  def nameView(label: String, name: EMO[String]): Div = {
    div(
      h1(label),
      child.text <-- name,
      input(
        placeholder := label,
        inContext { el =>
          el.events(onKeyUp).throttle(666).mapTo(el.ref.value) --> name
        }
      )
    )
  }

  def apply(): Div = {
    div(
      h1("Onion State"),
      hr(),
      state(initialState) { state: EMO[Global] =>
        div(
          globalNameLens(state)(nameView("Global name", _)),
          span("Global name", child.text <-- mem(state).map(_.globalName))
        )
      }
    )
  }
}

object Main extends App {
  render(dom.document.getElementById("app"), Example())
}
