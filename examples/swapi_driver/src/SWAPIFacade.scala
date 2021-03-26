package example.swapi_driver

import scala.scalajs.js

object SWAPIFacade {
  object types {

    @js.native
    trait Person extends js.Object {
      val name: String = js.native
    }

  }

  import types._
  @js.native
  @js.annotation.JSGlobal("swapiModule")
  private object swapiModule extends js.Object {
    def getPerson(id: Int): js.Promise[Person]              = js.native
    def getPeople(obj: js.Dynamic): js.Promise[Seq[Person]] = js.native
  }

  object ops {

    def getPerson(id: Int): js.Promise[Person] = swapiModule.getPerson(id)
    def findPeople(search: String): js.Promise[Seq[Person]] = {
      swapiModule.getPeople(
        js.Dynamic.literal(
          search = search
        )
      )
    }

  }

}
