package cycle.core

private[core] trait Core {

  /**
    * A User function takes devices of type `D` and returns a `B` which can be
    * either a Laminar view or just Laminar binders.
    *
    * @tparam D The type of devices this function consumes
    * @tparam B The type of view or result this function produces.
    */
  trait User[-D, +B] {
    def apply(device: D): B
  }

  /**
    * A Cycle function takes a User function and invokes it with some `D` devices
    * in order to produce a `V` result.
    *
    * @tparam D
    * @tparam B
    */
  trait Cycle[-D, +B, -U <: User[D, B]] {
    def apply(user: U): B
  }

}
