package cycle

import com.raquo.laminar.api.L._
import org.scalajs.dom.experimental._

import scala.concurrent.ExecutionContext

case class fetch(user: User[fetch.FetchIO])
object fetch {

  final case class Request(input: RequestInfo, init: RequestInit = null)

  private type Input  = Request
  private type Output = (Request, Response)

  type FetchIO = CIO[Output, Input]

  implicit def toMod(f: fetch)(implicit ec: ExecutionContext): ModEl = {
    import f.user
    val pio = PIO[Input, Output]
    val reqRes = pio.flatMap { req =>
      EventStream.fromFuture {
        Fetch.fetch(req.input, req.init).toFuture.map(res => req -> res)
      }
    }
    amend(
      reqRes --> pio,
      user(pio)
    )
  }
}
