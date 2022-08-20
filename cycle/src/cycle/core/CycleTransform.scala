package cycle.core

import cycle.Cycle
import com.raquo.laminar.api.L.*
import com.raquo.laminar.nodes.ReactiveElement.Base

class CycleTransform[-I, +S, +O] private[cycle] (private val cycle: Cycle[I, S, O]) extends AnyVal:

  def triMap[I0, S0, O0](f: I0 => I, g: S => S0, h: O => O0): Cycle[I0, S0, O0] = new:
    override val stateSignal: Signal[S0]       = cycle.stateSignal.map(g)
    override def toModifier[E <: Base]: Mod[E] = cycle.toModifier
    override def toObservable: EventStream[O0] = cycle.toObservable.map(h)
    override def toObserver: Observer[I0]      = cycle.toObserver.contramap(f)

  @inline def biMap[I0, O0](f: I0 => I, g: O => O0): Cycle[I0, S, O0] =
    triMap(f, identity, g)

  @inline def map[O0](f: O => O0): Cycle[I, S, O0] =
    triMap(identity, identity, f)

  @inline def contramap[I0](f: I0 => I): Cycle[I0, S, O] =
    triMap(f, identity, identity)

  @inline def stateMap[S0](f: S => S0): Cycle[I, S0, O] =
    triMap(identity, f, identity)

  def withModifiers(mods: Mod[Base]*): Cycle[I, S, O] = new:
    override def stateSignal: Signal[S]        = cycle.stateSignal
    override def toModifier[E <: Base]: Mod[E] = Seq[Mod[E]](cycle.toModifier, mods)
    override def toObservable: EventStream[O]  = cycle.toObservable
    override def toObserver: Observer[I]       = cycle.toObserver
