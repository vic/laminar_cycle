package cycle

import com.raquo.laminar.api.L._
import org.scalajs.dom.experimental._

import scala.concurrent.ExecutionContext

case class fetch(user: User[fetch.FetchIO])
object fetch {

  final case class Request(input: RequestInfo, init: RequestInit = null)

  type FetchIO = CIO[(Request, Response), Request] with Has[API]

  trait API {
    def fetch(req: Request): EventStream[Response]
  }

  implicit def toMod(f: fetch)(implicit ec: ExecutionContext): ModEl = {
    import f.user

    val pio = PIO[Request, (Request, Response)]

    val api: API = (req: Request) => EventStream.fromFuture {
      Fetch.fetch(req.input, req.init).toFuture
    }

    val reqRes = pio.flatMap(req => api.fetch(req).map(req -> _))
    amend(
      reqRes --> pio,
      user(pio ++ has(api))
    )
  }
}
