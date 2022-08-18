package example.hello

import scala.scalajs.js
import org.scalajs.dom
import com.raquo.laminar.api.L.*
import cycle.Cycle
import cycle.core.EventEmitter

object Main {

  val reverseStringCycle: Cycle[Any, String, Nothing] =
    Cycle[Any, String, Nothing]
      .fromStateReducer(_ => _.reverse.capitalize)
      .withInitialState("Hello")

  val helloView: Div = {
    div(
      reverseStringCycle,
      h1(child.text <-- reverseStringCycle.stateSignal),
      button("Reverse", onClick --> reverseStringCycle)
    )
  }

  def main(args: Array[String]): Unit = {
    render(dom.document.getElementById("app"), helloView)
  }

}
