package cycle.core

import cycle.Cycle
import com.raquo.laminar.api.L._
import com.raquo.laminar.nodes.ReactiveElement.Base

private[cycle] trait CycleImplicits {

  @inline implicit def toModifier[E <: Base, I, S, O](cycle: Cycle[I, S, O]): Mod[E] = cycle.bind

  @inline implicit def mapInstance[I, S, O](cycle: Cycle[I, S, O]): CycleMap[I, S, O] = new CycleMap(cycle)

}
