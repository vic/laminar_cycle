package cycle.pattern

final case class RequestResponse[Tag, RQ, RS](
    req: (Tag, RQ),
    res: (Tag, RS)
)

object RequestResponse {}
