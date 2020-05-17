package example.swapi_driver

import cycle._
import com.raquo.laminar.api.L._

import scala.concurrent.{ExecutionContext, Future}

object SWAPIDriver {
  import SWAPI._
  import SWAPIFacade.ops._

  private def processRequest(request: Request)(implicit ec: ExecutionContext): Future[Response] =
    request match {
      case GetPerson(id) =>
        getPerson(id).toFuture.map(GotPerson(_))
      case FindPeople(search) =>
        findPeople(search).toFuture.map(FoundPeople(_))
    }

  private type Input  = Request
  private type Output = (Request, Response)

  type UserIO = CIO[Output, Input]

  def apply(user: User[UserIO, ModEl])(implicit ec: ExecutionContext): ModEl =
    driver(ec)(user) // Just swap the implicit parameters to make compiler happy

  def driver(implicit ec: ExecutionContext): Cycle[UserIO, ModEl] = { user =>
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
