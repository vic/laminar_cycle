package example.route_history

import com.raquo.domtypes.jsdom.defs.events.TypedTargetMouseEvent
import com.raquo.laminar.api.L._
import cycle._
import org.scalajs.dom
import urldsl.errors.{SimpleParamMatchingError, SimplePathMatchingError}
import urldsl.language.PathSegment.simplePathErrorImpl._
import urldsl.language.QueryParameters.simpleParamErrorImpl._
import urldsl.language.{PathSegment, PathSegmentWithQueryParams}
import urldsl.vocabulary.Codec

object Routes {
  val driver: DriverEl[Routes] =
    for {
      history <- History()
      router  <- Router(history.in.map(_.state.href))
    } yield {
      new Routes(history)(router)
    }
}

class Routes(history: History.IO)(implicit router: Router) {
  import RouteModel._
  import Router.at
  import Router.urldslEncoder._

  object landing extends at(endOfSegments)

  object user extends at(User.path) {
    object followers extends at(Followers.path(here))
  }

  def pushLinks: Mod[Element] = inContext { el =>
    def isRouteLink(ev: TypedTargetMouseEvent[dom.Element]): Boolean = {
      ev.target.tagName == "A" &&
      ev.target.getAttribute("href").startsWith(router.origin)
    }

    val pushes = el
      .events(
        onClick.collect {
          case ev if isRouteLink(ev) => ev.target.getAttribute("href")
        }.preventDefault.stopPropagation
      )
      .map(href => History.PushState(state = History.State(href = href)))

    pushes --> history.out
  }

}

object RouteModel {

  case class User(username: String)
  object User {
    implicit val codec: Codec[String, User] =
      Codec.factory[String, User](User(_), _.username)

    type Path = PathSegment[User, SimplePathMatchingError]
    def path: Path = segment[String].as[User]
  }

  object Followers {
    case class Params(
        page: Option[Int] = Some(1),
        mutual: Option[Boolean] = None
    )
    object Params {
      implicit val codec =
        Codec.factory[(Option[Int], Option[Boolean]), Params](
          Function.tupled(Params.apply),
          p => (p.page, p.mutual)
        )
    }

    type Path = PathSegmentWithQueryParams[
      User,
      SimplePathMatchingError,
      Params,
      SimpleParamMatchingError
    ]

    def path(userPath: User.Path): Path = {
      { userPath / "followers" } ? {
        param[Int]("page").? &
          param[Boolean]("mutual").?
      }.as[Params]
    }
  }

}

object Example {
  import RouteModel._

  def apply(): Div = {
    div(
      Routes.driver { routes => layout(routes) }
    )
  }

  def layout(routes: Routes): Mod[Element] =
    amend(
      routes.pushLinks,
      h1("Social network layout"),
      div(
        borderStyle := "dotted",
        h3("Main content"),
        child <-- topLevel(routes)
      ),
      footer(
        h4("Footer links"),
        nav(
          li(
            a("Go to Landing page", href := routes.landing())
          ),
          li(
            a(
              "Go to vic's Home",
              href := routes.user(User("vic"))
            )
          )
        )
      )
    )

  def topLevel(routes: Routes): EventStream[Element] = {
    val landing = routes.landing.map { _ => div("Landing page") }

    val home = routes.user.map { user =>
      div(
        h3(user.username, "'s HomePage"),
        a(
          "Mutual followers",
          href := routes.user
            .followers(user, Followers.Params(mutual = Some(true)))
        )
      )
    }

    val followers = routes.user.followers.map {
      case (user, params) =>
        div(
          span(user.username, " has 0 Followers", params.mutual.map {
            case true => " You also follow"
            case _    => " You dont follow"
          })
        )
    }

    EventStream.merge[Element](
      landing,
      home,
      followers
    )
  }

}

object Main extends App {
  render(dom.document.getElementById("app"), Example())
}
