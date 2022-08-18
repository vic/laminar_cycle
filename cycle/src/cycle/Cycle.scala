package cycle

import com.raquo.airstream.core.Sink
import com.raquo.airstream.core.Source.EventSource
import com.raquo.laminar.api.L._
import com.raquo.laminar.nodes.ReactiveElement
import cycle.core._

trait Cycle[-In, +State, +Out] extends Sink[In] with EventSource[Out] {
  def stateSignal: Signal[State]
  def toModifier[E <: ReactiveElement.Base]: Mod[E]
}

object Cycle extends CycleFactories with CycleImplicits
