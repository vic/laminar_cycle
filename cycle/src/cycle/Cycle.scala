package cyle

import com.raquo.laminar.api.L._
import com.raquo.laminar.nodes.ReactiveElement

trait Cycle[-In, +State, +Out] extends Sink[In] with EventSource[Out] {
  val stateSignal: Signal[State]
  def bind[E <: ReactiveElement.Base]: Mod[E]
}

object Cycle extends CycleFactories with CycleImplicits
