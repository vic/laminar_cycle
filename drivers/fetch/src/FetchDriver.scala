package cycle

import java.util.UUID

import com.raquo.laminar.api.L._
import org.scalajs.dom.experimental._

import scala.concurrent.ExecutionContext

object FetchDriver {

  sealed trait Request {
    val input: RequestInfo
    val init: RequestInit
    val uuid: UUID
  }

  object Request {
    def apply(aInput: RequestInfo, aInit: RequestInit = null): Request = new Request {
      val input = aInput
      val init  = aInit
      val uuid  = UUID.randomUUID()
    }
  }

  private type Sense    = Request
  private type Actuator = (Request, Response)

  type ActuatorSense = CIO[Actuator, Sense]

  def apply(user: ActuatorSense => Mod[Element])(implicit ec: ExecutionContext): Mod[Element] = {
    val (io, oi) = CIO[Sense, Actuator]

    val reqRes = io.flatMap { req =>
      EventStream.fromFuture {
        Fetch.fetch(req.input, req.init).toFuture.map(res => req -> res)
      }
    }

    amend(
      reqRes --> io,
      user(oi)
    )
  }

}
