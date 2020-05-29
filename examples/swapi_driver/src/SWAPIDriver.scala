package example.swapi_driver

import cycle._
import com.raquo.laminar.api.L._

import scala.concurrent.{ExecutionContext, Future}

object SWAPIDriver {
  import SWAPI._
  import SWAPIFacade.ops._

  private def processRequest(
      request: Request
  )(implicit ec: ExecutionContext): Future[Response] =
    request match {
      case GetPerson(id) =>
        getPerson(id).toFuture.map(GotPerson(_))
      case FindPeople(search) =>
        findPeople(search).toFuture.map(FoundPeople(_))
    }

  private type Input  = Request
  private type Output = (Request, Response)

  type UserIO = CIO[Output, Input]

  def apply(
      user: UserEl[UserIO]
  )(implicit ec: ExecutionContext): Mod[Element] =
    driver(ec)(user) // Just swap the implicit parameters to make compiler happy

  def driver(
      implicit ec: ExecutionContext
  ): CycleEl[UserIO] = { user =>
    val pio = PIO[Input, Output]

    val reqAndRes: EventStream[Output] = pio.flatMap { req =>
      EventStream
        .fromFuture(processRequest(req))
        .map(res => req -> res)
    }

    amend(
      reqAndRes --> pio,
      user(pio)
    )
  }

}
