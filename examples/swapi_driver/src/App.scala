package example.swapi_driver

import com.raquo.laminar.api.L._
import org.scalajs.dom

import scala.concurrent.{ExecutionContext, Future}
import SWAPIFacade.types._
import example.swapi_driver.SWAPI.{FindPeople, FoundPeople}

object SWAPI {

  sealed trait Request
  case class GetPerson(id: Int)         extends Request
  case class FindPeople(search: String) extends Request

  sealed trait Response
  case class GotPerson(person: Person)        extends Response
  case class FoundPeople(people: Seq[Person]) extends Response

  type InOut = cycle.InOut[(Request, Response), Request]

}

object SWAPIDriver {
  import SWAPI._
  import SWAPIFacade.ops._

  def processRequest(request: Request)(implicit ec: ExecutionContext): Future[Response] =
    request match {
      case GetPerson(id) =>
        getPerson(id).toFuture.map(GotPerson(_))
      case FindPeople(search) =>
        findPeople(search).toFuture.map(FoundPeople(_))
    }

  def apply[El <: Element](fn: SWAPI.InOut => Mod[El])(implicit ec: ExecutionContext): Mod[El] = {
    val (io, oi) = cycle.InOut.split[Request, (Request, Response)]

    val reqAndRes: EventStream[(Request, Response)] = io.in.flatMap { req =>
      EventStream
        .fromFuture(processRequest(req))
        .map(res => req -> res)
    }

    cycle.amend[El](
      reqAndRes --> io.out,
      fn(oi)
    )
  }

}

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

  def cycled(swapi: SWAPI.InOut): Mod[Element] = {
    val inputBus  = new EventBus[String]
    val submitBus = new EventBus[Unit]

    val currentInput = inputBus.events.startWith("")

    val findPeopleReqs: EventStream[SWAPI.FindPeople] = submitBus.events
      .withCurrentValueOf(currentInput)
      .map(_._2.trim)
      .filterNot(_.isEmpty)
      .map(SWAPI.FindPeople(_))

    val viewSearchResults: EventStream[Div] = swapi.in.collect {
      case (req: SWAPI.FindPeople, res: SWAPI.FoundPeople) => req -> res
    }.map2(renderFoundPeople)

    div(
      cls := "app",
      searchForm(currentInput, inputBus.writer, submitBus.writer),
      child <-- viewSearchResults,
      findPeopleReqs --> swapi
    )
  }

  def apply(): Div = {
    import scala.concurrent.ExecutionContext.Implicits.global
    div(
      SWAPIDriver { swapi => cycled(swapi) }
    )
  }
}

object Main extends App {
  render(dom.document.getElementById("app"), Example())
}
