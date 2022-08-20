package example.hello

import com.raquo.laminar.api.L.*
import cycle.Cycle
import cycle.core.EventEmitter
import org.scalajs.dom

import scala.scalajs.js.annotation.JSExportTopLevel

object Hello:

  val reverseStringCycle: Cycle[Any, String, Nothing] =
    Cycle[Any, String, Nothing]
      .fromStateReducer(_ => _.reverse.toLowerCase.capitalize)
      .withInitialState("Hello")

  val helloView: Div =
    div(
      reverseStringCycle,
      h1(child.text <-- reverseStringCycle.stateSignal),
      button("Reverse", onClick --> reverseStringCycle)
    )

@JSExportTopLevel(name = "mount", moduleID = "Hello")
def mount(parent: dom.Element): Unit = render(parent, Hello.helloView)
