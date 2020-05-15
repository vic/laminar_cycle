package cycle

import com.raquo.laminar.api.L._

object ComposeDriver {

  def apply[I1, O1, I2, O2](
      compose: EventStream[I1] => EventStream[I2],
      contracompose: EventStream[O2] => EventStream[O1]
  )(user1: CIO[I1, O1] => Mod[Element])(user2: CIO[I2, O2] => Mod[Element])(cio1: CIO[I1, O1]): Mod[Element] = {
    val (cio2: CIO[I2, O2], onMount) = cio1.composeIO[I2, O2, Element](compose, contracompose)
    amend(
      onMount,
      user2(cio2)
    )
  }

}
