# Laminar.cycle

![Everything is a Stream][everything-is-a-stream]

[Cycle] style apps using [Laminar] on [ScalaJS]

## Installation

Each release artifacts are available from [JitPack][JitPack]

## Intro

[Laminar] is an awesome tool for creating [Functional Reactive][FRP] interfaces enterely based on [Streams][Airstream].

This repository offers a [tiny library][laminar-cycle] built on Laminar that can help you
build applications using [Cycle's dialogue abstraction][cycle-dialogue].

### Senses and Actuators

<img src="https://cycle.js.org/img/actuators-senses.svg" width="400">


In the [Cycle's dialogue abstraction][cycle-dialogue] pictured above, 
both the _Human_ and the _Computer_ can be seen as entities interacting with each
other by means of `Senses` to `Actuators`. 

`Senses` and `Actuators` can be seen as streams of **incoming** and **outgoing** stimuli
that will cause some effect in the other-end actor. 
This way, the Computer _reacts_ to user interactions (like clicks) by *producing* an updated interface,
and the User _reacts_ to the interface on screen they *see* by *operating* on it (clicking again).

In [Cycle.js][Cycle], every UI component and the whole `Computer` can be seen like a function
from senses to actuators.

```javascript
// Javascript
function computer(senses) {
  // define the behavior of `actuators` somehow
  return actuators;
}
```

### Laminar.cycle

Since Laminar is already powerful enough to efficiently create and render stream-based reactive html elements,
all we need now is a function to model the previously seen cycle dialogue abstraction.

#### The `cycle.InOut[I, O]` type models senses and actuators.

As an example of _Senses_ and _Actuators_, suppose you need to interact with
some external API by sending it `Request`s and receiving `Response`es from it.

```scala
object ExternalAPI {
  sealed trait Request
  sealed trait Response
}
```

In the following code snippet, we have a `computer` function that can take
stimuli (`Response`) from the API but also might produce stimuli for it (`Request`).

```scala
import com.raquo.laminar.api.L._
import ExternalAPI.{Request, Response}

def computer(api: cycle.InOut[Request, Response]) = {
  // define the behavior of `actuators` somehow
}
```

The `cycle.InOut[Sense, Actuator]` type is defined something like:

```scala
trait InOut[I, O] {
  val in:  EventStream[I]
  val out: WriteBus[O]
}
```

it provides an incoming `Observable[I]` stream and an outgoing `Observer[O]` write bus.

Pretty much similar to [Airstream]'s own `EventBus[T]` but generic on both input and output types.


#### Combining state and user interaction.

Suppose we want to implement a counter UI that is able to track the current counter value
and the number of times the user has interacted with the counter.
A working example can be found at [Examples](#Examples)

```scala
object Counter {

  case class State(
    value: Int,
    numberOfInteractions: Int
  )
  
  sealed trait Action // Type of the stimuli produced by the user
  case object Increment extends Action
  case object Decrement extends Action

}
```

Having the above types, we could define our `computer` function like:

```scala
import Counter._

def computer(states: cycle.IO[State], actions: cycle.IO[Action]): Mod[Element] = {
  // Initialize the computer's internal state and keep it on a Signal[State]
  // in order to always have a *current value*
  val stateSignal: Signal[State] = states.in.startWith(State(0, 0))
  
  // Now, whenever we sense a user Action, we have to update our current state
  val updatedState: EventStream[State] = 
     actions.in.withCurrentValueOf(stateSignal).map(Function.tupled(performAction))
 
  states.addOut[Element](updatedState)
}

def performAction(action: Action, state: State): State = action match {
 case Increment =>
   state.copy(state.value + 1, state.numberOfInteractions + 1)
 case Decrement =>
   state.copy(state.value - 1, state.numberOfInteractions + 1)
}
```

Let's explore our previous example code.

* The `cycle.IO[T]` type is just an alias for `cycle.InOut[T, T]`. 

  Perhaps @vic should rename it since `IO` is already used in other functional contexts, 
if you can find out a better name, be sure to share it via an [issue](issues).

* Our previous example does not render anything (we will get to producing views later).

  Yet, it's a fully working example of how to create a cycle function that reads and writes
  from both: `actions` and `states`.

* The return type is `Mod[Element]`. 

  The `states.addOut(updateState)` produces an `cycle.SubscribeOnMount[El]` modifier 
  (read more about `Mod[El]` modifiers and ownership at [LaminarDocs]).
  
  The `SubscribeOnMount[El]` modifier will make sure that we write updated states
  for as long as the UI is mounted and the user can interact with it. All of this
  is part of Laminar's [memory safety and glitch free guarantees][LaminarSafety].


#### Producing reactive views

Now, we will refactor our `computer` function to actually render a user interface.

For brevity sake, we will add `???` for previously seen code.

```scala
def computer(states: cycle.IO[State], actions: cycle.IO[Action]): Div = {
  val stateSignal: Signal[State] = ???
  val updatedState: EventStream[State] = ???
 
  div(
    counterView(stateSignal),
    actionControls(actions.out),
    state.addOut[Div](updatedState)
  )
}

def actionControls(actions: Observer[Action]): Mod[Div] = {
  cycle.amend(
    button(
      cls := "btn secondary",
      "Increment",
      onClick.mapTo(Increment) --> actions
    ),
    button(
      cls := "btn secondary",
      "Decrement",
      onClick.mapTo(Decrement) --> actions
    ),
    button(
      cls := "btn secondary",
      "Reset",
      onClick.mapTo(Reset) --> actions
    )
  )
}

def counterView(state: Observable[State]): Div = {
  div(
    h2("Counter value: ", child.text <-- state.map(_.value.toString)),
    h2("Interactions: ", child.text <-- state.map(_.interactions.toString))
  )
}
```

## Examples

* [Counter] ([source][counter-source])
* [NestedIndent] ([source][nested-indent-source])



[JitPack]: https://jitpack.io/#vic/laminar_cycle
[Cycle]: https://cycle.js.org/
[Airstream]: https://github.com/raquo/Airstream
[Laminar]: https://github.com/raquo/Laminar
[LaminarSafety]: https://github.com/raquo/Laminar#safety
[LaminarDocs]: https://github.com/raquo/Laminar/blob/master/docs/Documentation.md
[ScalaJS]: https://www.scala-js.org/
[FRP]: https://gist.github.com/staltz/868e7e9bc2a7b8c1f754
[everything-is-a-stream]: https://camo.githubusercontent.com/e581baffb3db3e4f749350326af32de8d5ba4363/687474703a2f2f692e696d6775722e636f6d2f4149696d5138432e6a7067
[senses-actuators]: https://cycle.js.org/img/actuators-senses.svg
[cycle-dialogue]: https://cycle.js.org/dialogue.html

[Counter]: https://vic.github.io/laminar_cycle/examples/cycle_counter/
[counter-source]: examples/cycle_counter/src
[NestedIndent]: https://vic.github.io/laminar_cycle/examples/nested_indent/
[nested-indent-source]: examples/nested_indent/src

[laminar-cycle]: cycle/src/Cycle.scala
