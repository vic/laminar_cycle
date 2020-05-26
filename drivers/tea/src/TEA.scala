package cycle

import com.raquo.laminar.api.L._

object TEA {

  type Devices[State, Action] = (EMO[State], EIO[Action])

  def apply[State, Action, Pure <: Action, Effect <: Action, El <: Element](
      state: EMO[State],
      actions: EIO[Action],
      selectPure: EventStream[Action] => EventStream[Pure],
      selectEffect: EventStream[Action] => EventStream[Effect],
      performPure: (Pure, State) => (State, Option[Action]),
      performEffect: Effect => EventStream[Action]
  ): Driver[Devices[State, Action], El] = {
    val pures   = actions.compose(selectPure)
    val effects = actions.compose(selectEffect)

    val fromPure =
      pures.withCurrentValueOf(state).map2(performPure)
    val fromEffect = effects.flatMap(performEffect)

    val newStates = fromPure.map(_._1)
    val newActions = EventStream.merge(
      fromPure.map(_._2).collect { case Some(action) => action },
      fromEffect
    )

    Driver(
      (state, actions),
      newStates --> state,
      newActions --> actions
    )
  }
}
