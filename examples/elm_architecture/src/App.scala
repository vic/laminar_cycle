package example.cycle_counter

import java.time.Instant

import cycle._
import com.raquo.laminar.api.L._
import org.scalajs.dom

import scala.util.Random

object Example {

  case class Sample(
      value: Int,
      sampledAt: Instant,
      ellapsedMillis: Long
  )

  case class State(
      probingSince: Option[Instant],
      samples: Seq[Sample]
  )

  def initialState: State = State(None, Nil)

  trait Intent
  trait Action      extends Intent
  case object Probe extends Action
  case class AppendSample(since: Instant, sampledAt: Instant, value: Int)
      extends Action

  trait Effect extends Intent
  case class WaitForSample(since: Instant, lastSample: Option[Sample])
      extends Effect

  def performAction(action: Action, state: State): (State, Option[Intent]) =
    action match {
      case Probe =>
        val since = Instant.now
        state.copy(probingSince = Some(since)) -> Some(
          WaitForSample(since = since, state.samples.lastOption)
        )

      case AppendSample(since, sampledAt, value) =>
        val sample =
          Sample(value, sampledAt, sampledAt.toEpochMilli - since.toEpochMilli)

        val newState = state.copy(probingSince = None, state.samples :+ sample)
        newState -> None

      case _ =>
        state -> None
    }

  def performEffect(
      effect: Effect,
      state: State
  ): EventStream[(State, Option[Intent])] = effect match {
    case WaitForSample(since, lastSample) =>
      EventStream
        .fromValue(lastSample.map(_.value).getOrElse(0), emitOnce = true)
        .delay(Random.nextInt(666))
        .map { min =>
          Random.nextInt(10000) match {
            case value if value <= min =>
              // Recursive effectful action
              state -> Some(WaitForSample(since, lastSample))
            case value =>
              // We could return a Right(state) or
              // perform another action (a pure one in this case)
              state -> Some(AppendSample(since, Instant.now, value))
          }
        }
    case _ =>
      EventStream.fromValue(state -> None, emitOnce = true)
  }

  val tea = TEA[State, Intent, Action, Effect](
    EMO[State](initialState),
    EIO[Intent],
    selectPure = _.collect[Action] { case a: Action   => a },
    selectEffect = _.collect[Effect] { case e: Effect => e },
    performAction _,
    performEffect _
  )

  def main: Div = {
    div(
      tea { io => view(io: Mem[State], io: Out[Intent]) }
    )
  }

  def viewSample(
      key: Instant,
      initial: Sample,
      signal: Signal[Sample]
  ): Div = {
    div(
      p(
        "At ",
        small(child.text <-- signal.map(_.sampledAt.toString)),
        " sampled value ",
        strong(child.text <-- signal.map(_.value.toString)),
        " sample took ms: ",
        code(child.text <-- signal.map(_.ellapsedMillis.toString))
      )
    )
  }

  def view(state: Signal[State], actions: Observer[Action]): Div = {
    val samples = state.map(_.samples.toList).split(_.sampledAt)(viewSample)
    val probing = state.map(_.probingSince.isDefined)
    div(
      "Samples",
      button(
        "Probe Sample",
        disabled <-- probing,
        onClick.mapTo(Probe) --> actions
      ),
      children <-- samples
    )
  }

}

object Main extends App {
  render(dom.document.getElementById("app"), Example.main)
}
