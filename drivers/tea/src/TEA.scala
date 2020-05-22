package cycle

import com.raquo.laminar.api.L._

case class TEA[State, Action, Pure <: Action, Effect <: Action](
    state: EMO[State],
    actions: EIO[Action],
    selectPure: EventStream[Action] => EventStream[Pure],
    selectEffect: EventStream[Action] => EventStream[Effect],
    performPure: (Pure, State) => (State, Option[Action]),
    performEffect: (Effect, State) => EventStream[(State, Option[Action])]
)
object TEA {

  implicit def driver[
      State: Tag,
      Action: Tag,
      Pure <: Action: Tag,
      Effect <: Action: Tag
  ](
      tea: TEA[State, Action, Pure, Effect]
  ): Driver[EMO[State] with EIO[Action]] = {
    import tea._
    val pures   = actions.compose(selectPure)
    val effects = actions.compose(selectEffect)

    val fromPure =
      pures.withCurrentValueOf(state).map2(performPure)
    val fromEffect =
      effects.withCurrentValueOf(state).map2(performEffect).flatten

    val updated = EventStream.merge(fromPure, fromEffect)

    val newStates  = updated.map(_._1)
    val newActions = updated.map(_._2).collect { case Some(action) => action }

    Driver(
      state ++ actions,
      newStates --> state,
      newActions --> actions
    )
  }
}
