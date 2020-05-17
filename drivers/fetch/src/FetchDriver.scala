package cycle

import com.raquo.laminar.api.L._
import org.scalajs.dom.experimental._

import scala.concurrent.ExecutionContext

object FetchDriver {

  final case class Request(input: RequestInfo, init: RequestInit = null)

  private type Input  = Request
  private type Output = (Request, Response)

  def apply(implicit ec: ExecutionContext): Cycle[CIO[Output, Input], ModEl] = {
    user: User[CIO[Output, Input], ModEl] =>
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
