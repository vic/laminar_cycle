package cycle

import com.raquo.laminar.api.L._

object ComposeDriver {

  private type ModEl = Mod[Element]

  private type FwdBwd[I1, O1, I2, O2] = ((I1 => I2), (O2 => O1))

  private type User[I, O]   = CIO[I, O] => ModEl
  private type Driver[I, O] = User[I, O] => ModEl

  private type User2[I1, O1, I2, O2]   = (CIO[I1, O1], CIO[I2, O2]) => ModEl
  private type Driver2[I1, O1, I2, O2] = User2[I1, O1, I2, O2] => ModEl

  private type User3[I1, O1, I2, O2, I3, O3]   = (CIO[I1, O1], CIO[I2, O2], CIO[I3, O3]) => ModEl
  private type Driver3[I1, O1, I2, O2, I3, O3] = User3[I1, O1, I2, O2, I3, O3] => ModEl

  private type User4[I1, O1, I2, O2, I3, O3, I4, O4]   = (CIO[I1, O1], CIO[I2, O2], CIO[I3, O3], CIO[I4, O4]) => ModEl
  private type Driver4[I1, O1, I2, O2, I3, O3, I4, O4] = User4[I1, O1, I2, O2, I3, O3, I4, O4] => ModEl

  implicit class DriverOps[I1, O1](val driver1: Driver[I1, O1]) extends AnyVal {

    def &[I2, O2](driver2: Driver[I2, O2]): Driver2[I1, O1, I2, O2] = join(driver2)

    def join[I2, O2](driver2: Driver[I2, O2]): Driver2[I1, O1, I2, O2] =
      (user: User2[I1, O1, I2, O2]) => driver1 { cio1 => driver2 { cio2 => user(cio1, cio2) } }

//    def &[I2, O2, I3, O3](driver2: Driver2[I2, O2, I3, O3]): Driver3[I1, O1, I2, O2, I3, O3] = join(driver2)
//
//    def join[I2, O2, I3, O3](driver2: Driver2[I2, O2, I3, O3]): Driver3[I1, O1, I2, O2, I3, O3] =
//      (user: User3[I1, O1, I2, O2, I3, O3]) => driver1 { cio1 => driver2 { (cio2, cio3) => user(cio1, cio2, cio3) } }
//
//    def &[I2, O2, I3, O3, I4, O4](driver2: Driver3[I2, O2, I3, O3, I4, O4]): Driver4[I1, O1, I2, O2, I3, O3, I4, O4] =
//      join(driver2)
//
//    def join[I2, O2, I3, O3, I4, O4](
//        driver2: Driver3[I2, O2, I3, O3, I4, O4]
//    ): Driver4[I1, O1, I2, O2, I3, O3, I4, O4] =
//      (user: User4[I1, O1, I2, O2, I3, O3, I4, O4]) =>
//        driver1 { cio1 => driver2 { (cio2, cio3, cio4) => user(cio1, cio2, cio3, cio4) } }

    def map[I2, O2](fwdBwd: FwdBwd[I1, O1, I2, O2]): Driver[I2, O2] = {
      map(fwdBwd._1, fwdBwd._2)
    }

    def map[I2, O2](map: I1 => I2, contramap: O2 => O1): Driver[I2, O2] = {
      ComposeDriver.map[I1, O1, I2, O2](map, contramap)(driver1)
    }

    def compose[I2, O2](
        fwdBwd: FwdBwd[EventStream[I1], EventStream[O1], EventStream[I2], EventStream[O2]]
    ): Driver[I2, O2] = {
      compose(fwdBwd._1, fwdBwd._2)
    }

    def compose[I2, O2](
        compose: EventStream[I1] => EventStream[I2],
        contracompose: EventStream[O2] => EventStream[O1]
    ): Driver[I2, O2] = {
      ComposeDriver.compose[I1, O1, I2, O2](compose, contracompose)(driver1)
    }

  }

  def map[I1, O1, I2, O2](
      map: I1 => I2,
      contramap: O2 => O1
  )(
      driver1: Driver[I1, O1]
  )(user2: CIO[I2, O2] => ModEl): ModEl = {
    compose[I1, O1, I2, O2](_.map(map), _.map(contramap))(driver1)(user2)
  }

  def compose[I1, O1, I2, O2](
      compose: EventStream[I1] => EventStream[I2],
      contracompose: EventStream[O2] => EventStream[O1]
  )(
      driver1: Driver[I1, O1]
  )(user2: User[I2, O2]): ModEl = {
    driver1 { cio1: CIO[I1, O1] =>
      val (cio2, onMount) = cio1.composeIO[I2, O2, Element](compose, contracompose)
      amend(
        onMount,
        user2(cio2)
      )
    }
  }

  def mapIO[I1, O1, I2, O2](
      map: I1 => I2,
      contramap: O2 => O1
  )(
      cio1: CIO[I1, O1]
  )(
      user2: User[I2, O2]
  ): ModEl = {
    composeIO[I1, O1, I2, O2](_.map(map), _.map(contramap))(cio1)(user2)
  }

  def composeIO[I1, O1, I2, O2](
      compose: EventStream[I1] => EventStream[I2],
      contracompose: EventStream[O2] => EventStream[O1]
  )(
      cio1: CIO[I1, O1]
  )(
      user2: User[I2, O2]
  ): ModEl = {
    val (cio2, onMount) = cio1.composeIO[I2, O2, Element](compose, contracompose)
    amend(onMount, user2(cio2))
  }

}
