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

  def globalView(global: EMO[Global]): Div = {
    val nameLayer = onion[Global, String](global)(_.globalName) {
      case (globalName, global) => global.copy(globalName = globalName)
    }

    div(
      "Global state",
      borderStyle := "dotted",
      borderColor := "red",
      nameLayer { nameIO =>
        nameInput(
          "Global name",
          nameIO,
          nameIO
        )
      }
    )
  }

  def localView(local: EMO[Local]): Div = {
    val nameLayer = onion[Local, String](local)(_.localName) {
      case (localName, local) => local.copy(localName = localName)
    }

    div(
      "Local state",
      borderStyle := "dotted",
      borderColor := "blue",
      nameLayer { nameIO =>
        nameInput(
          "Local name",
          nameIO,
          nameIO
        )
      }
    )
  }

  def nameInput(
      label: String,
      values: Observable[String],
      update: Observer[String]
  ): Div =
    div(
      label,
      input(
        placeholder := label,
        value <-- values,
        inContext { input =>
          input.events(onKeyUp).throttle(666).mapTo(input.ref.value) --> update
        }
      )
    )

  def apply(): Div = {
    div(
      state[Global](Global("World", None)) { global =>
        amend(
          globalView(global),
          onion(bijGlobalToLocal)(global)(localView)
        )
      }
    )
  }
  implicit val bijGlobalToLocal: MemBijection[Global, Local] = implicitly

  implicit def globalToLocal(global: Signal[Global]): Signal[Local] = {
    global.composeChangesAndInitial[Local](
      operator = globalStream =>
        globalStream.map(_.local).collect { case Some(local) => local },
      initialOperator = tryGlobal =>
        tryGlobal.map(_.local).collect { case Some(local) => local }
    )
  }

  implicit def updateLocalToGlobal(
      updates: EventStream[(Local, Global)]
  ): EventStream[Global] = {
    updates.map {
      case (local, global) => global.copy(local = Some(local))
    }
  }

}

object Main extends App {
  render(dom.document.getElementById("app"), Example())
}
