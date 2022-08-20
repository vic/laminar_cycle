package cycle

import com.raquo.laminar.api.L.*
import com.raquo.laminar.nodes.ReactiveElement
import cycle.core.*

trait Cycle[-In, +State, +Out] extends Sink[In] with EventSource[Out]:
  def stateSignal: Signal[State]
  def toModifier[E <: ReactiveElement.Base]: Mod[E]

object Cycle extends CycleFactories with CycleImplicits
