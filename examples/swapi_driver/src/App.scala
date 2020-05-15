package example.swapi_driver

import com.raquo.laminar.api.L._
import org.scalajs.dom

object Example {

  def searchForm(current: Observable[String], search: Observer[String], submit: Observer[Unit]): Div =
    div(
      form(
        "Search an StartWars character:",
        input(
          tpe := "text",
          value <-- current,
          inContext { input => input.events(onKeyUp).delay(666).mapTo(input.ref.value) --> search }
        ),
        button(tpe := "submit", "Search"),
        inContext { form => form.events(onSubmit.preventDefault.stopPropagation).mapTo(()) --> submit }
      )
    )

  def renderFoundPeople(req: SWAPI.FindPeople, res: SWAPI.FoundPeople): Div = {
    div(
      h5("Search Results for ", req.search),
      ol(
        res.people.map { person => li(strong(person.name)) }
      )
    )
  }

  def cycled(swapi: SWAPI.InOut, input: cycle.IO[String], submit: cycle.IO[Unit]): Mod[Element] = {
    val currentInput = input.in.startWith("")

    val findPeopleReqs: EventStream[SWAPI.FindPeople] = submit.in
      .withCurrentValueOf(currentInput)
      .map(_._2.trim)
      .filterNot(_.isEmpty)
      .map(SWAPI.FindPeople(_))

    val viewSearchResults: EventStream[Div] = swapi.in.collect {
      case (req: SWAPI.FindPeople, res: SWAPI.FoundPeople) => req -> res
    }.map2(renderFoundPeople)

    div(
      cls := "app",
      searchForm(currentInput, input.out, submit.out),
      child <-- viewSearchResults,
      findPeopleReqs --> swapi
    )
  }

  def apply(): Div = {
    import scala.concurrent.ExecutionContext.Implicits.global
    val inputBus  = new EventBus[String]
    val submitBus = new EventBus[Unit]
    div(
      SWAPIDriver { swapi => cycled(swapi, inputBus, submitBus) }
    )
  }
}

object Main extends App {
  render(dom.document.getElementById("app"), Example())
}
