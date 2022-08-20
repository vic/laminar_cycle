package cycle.core

import cycle.Cycle
import com.raquo.laminar.api.L.*
import com.raquo.laminar.nodes.ReactiveElement.Base

private[cycle] trait CycleImplicits:

  @inline implicit def toModifier[E <: Base, I, S, O](cycle: Cycle[I, S, O]): Mod[E] = cycle.toModifier

  @inline implicit def transform[I, S, O](cycle: Cycle[I, S, O]): CycleTransform[I, S, O] = new CycleTransform(cycle)
