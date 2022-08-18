package cycle.core

import cycle.Cycle
import com.raquo.airstream.core.EventStream
import com.raquo.laminar.api.L
import air.AirCycleFactory

private[core] trait CycleFactories {
  def apply[I, S, O]: AirCycleFactory[I, S, O] = new AirCycleFactory[I, S, O](new EventEmitter(new EventTypes()))
}
