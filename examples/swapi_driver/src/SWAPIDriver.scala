package example.swapi_driver

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
