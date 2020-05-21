package cycle

import com.raquo.laminar.api.L._
import com.raquo.laminar.nodes.ReactiveElement
import com.raquo.laminar.nodes.ReactiveElement.Base
import org.scalajs.dom.experimental._

object fetch {

  final case class Request(input: RequestInfo, init: RequestInit = null)

  type FetchIO = CIO[(Request, Response), Request]

  def apply(user: User[FetchIO]): ModEl = driver(user)

  val driver: Driver[FetchIO] = { () =>
    val devices = PIO[Request, (Request, Response)]
    val reqRes = devices.flatMap(req =>
      EventStream.fromFuture {
        Fetch.fetch(req.input, req.init).toFuture
      }.map(req -> _)
    )
    binds(reqRes --> devices) -> devices
  }

}
