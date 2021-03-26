package example.hello

import scala.scalajs.js
import org.scalajs.dom
import com.raquo.laminar.api.L._


object Main {

  def main(args: Array[String]): Unit = {
    dom.console.log("HELLO WORLD!")
    render(dom.document.getElementById("app"), div("HELLO WORLD!!"))
  }

}

