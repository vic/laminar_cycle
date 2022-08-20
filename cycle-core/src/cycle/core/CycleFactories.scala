package cycle.core

import cycle.Cycle
import air.AirCycleFactory

private[cycle] trait CycleFactories:
  def apply[I, S, O]: AirCycleFactory[I, S, O] = new AirCycleFactory[I, S, O](new EventEmitter(new EventTypes()))
