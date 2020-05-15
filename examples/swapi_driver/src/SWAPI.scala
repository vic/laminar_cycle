package example.swapi_driver

import SWAPIFacade.types.Person

object SWAPI {

  sealed trait Request
  case class GetPerson(id: Int)         extends Request
  case class FindPeople(search: String) extends Request

  sealed trait Response
  case class GotPerson(person: Person)        extends Response
  case class FoundPeople(people: Seq[Person]) extends Response

  type InOut = cycle.InOut[(Request, Response), Request]

}
