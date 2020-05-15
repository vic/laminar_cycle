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

  private type Sense    = Request
  private type Actuator = (Request, Response)

  type ActuatorSense         = CIO[Actuator, Sense]
  private type SenseActuator = CIO[Sense, Actuator]

  def apply[El <: Element](fn: ActuatorSense => Mod[El])(implicit ec: ExecutionContext): Mod[El] = {
    val (senseActuator: SenseActuator, actuatorSense: ActuatorSense) = CIO[Sense, Actuator]

    val reqAndRes: EventStream[(Request, Response)] = senseActuator.flatMap { req =>
      EventStream
        .fromFuture(processRequest(req))
        .map(res => req -> res)
    }

    amend[El](
      reqAndRes --> senseActuator,
      fn(actuatorSense)
    )
  }

}
