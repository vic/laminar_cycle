package cycle

import org.scalajs.dom
import com.raquo.laminar.api.L._

class Router(
    val urlStream: EventStream[String],
    val origin: String = dom.window.location.origin.map(_ + "/").getOrElse("/")
)

object Router {

  object urldslEncoder {
    import urldsl.language.{PathSegment, PathSegmentWithQueryParams}

    implicit def pathSegmentEncoder[R, E, A]
        : RouteEncoder[PathSegment[A, E], E, A] = {
      type R = PathSegment[A, E]
      new RouteEncoder[R, E, A] {
        override def matchUrl(
            route: R,
            url: String
        ): Either[E, A] =
          route.matchRawUrl(url)

        override def createUrl(route: R, model: A): String =
          route.createPath(model)
      }
    }

    implicit def pathSegmentWithQueryEncoder[SA, SE, QA, QE]: RouteEncoder[
      PathSegmentWithQueryParams[SA, SE, QA, QE],
      Either[SE, QE],
      (SA, QA)
    ] = {
      type R = PathSegmentWithQueryParams[SA, SE, QA, QE]
      type E = Either[SE, QE]
      type A = (SA, QA)
      new RouteEncoder[R, E, A] {
        override def matchUrl(route: R, url: String): Either[E, A] =
          route.matchRawUrl(url).map(m => m.path -> m.params)

        override def createUrl(route: R, model: A): String =
          route.createUrlString(model._1, model._2)
      }
    }
  }

  trait RouteEncoder[R, E, A] {
    def matchUrl(route: R, url: String): Either[E, A]
    def createUrl(route: R, model: A): String
  }

  object at {
    def apply[PR, PE, PA](aRoute: PR)(
        implicit
        router: Router,
        aEncoder: RouteEncoder[PR, PE, PA]
    ): Route[PA] = new at(aRoute)
  }

  class at[PR, PE, PA](aRoute: PR)(
      implicit
      router: Router,
      aEncoder: RouteEncoder[PR, PE, PA]
  ) extends Route[PA] {
    override type R = PR
    override type E = PE

    override val here: R = aRoute

    override def relativeUrl(model: PA): String =
      aEncoder.createUrl(aRoute, model)

    override def absoluteUrl(model: PA): String =
      router.origin + relativeUrl(model)

    override lazy val events: RouteStream =
      router.urlStream.map { url => aEncoder.matchUrl(aRoute, url) }
  }

  trait Route[A] {
    type R
    type E

    type RouteStream = EventStream[Either[E, A]]

    def relativeUrl(a: A): String
    def absoluteUrl(a: A): String

    def apply(a: A): String = absoluteUrl(a)

    val here: R
    val events: RouteStream

    lazy val success: EventStream[A] = events.collect {
      case Right(model) => model
    }

    lazy val error: EventStream[E] = events.collect {
      case Left(error) => error
    }
  }

  object Route {
    implicit def toRoute(r: Route[_]): r.R                     = r.here
    implicit def toEventStream[A](r: Route[A]): EventStream[A] = r.success
  }

  def apply(urlStream: EventStream[String]): DriverEl[Router] = {
    val router = new Router(urlStream)
    Driver(router)
  }

}
