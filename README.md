# Laminar.cycle

![Main workflow](https://github.com/vic/laminar_cycle/workflows/Main%20workflow/badge.svg?branch=master)
[![Jipack](https://jitpack.io/v/vic/laminar_cycle.svg)](https://jitpack.io/#vic/laminar_cycle)

![Everything is a Stream][everything-is-a-stream]

[Cycle] style apps using [Laminar] on [ScalaJS]

## Installation

> Artifact: `com.github.vic.laminar_cycle::cycle::VERSION`

Each release artifacts are available from [JitPack][JitPack]

## Intro

[Laminar] is an awesome tool for creating [Functional Reactive][FRP] interfaces enterely based on [Streams][Airstream].

This repository offers a [tiny library][laminar-cycle-source] built on Laminar that can help you
build applications using [Cycle's dialogue abstraction][cycle-dialogue].

[cycle API][laminar-cycle-javadoc]

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

#### CycleIO

The type `CIO[I, O]` stands for `CycleIO` and models senses of type `I` and actuators of type `O`.
> And yes, it's also a nod to the [ZIO] data type ;D -- [@vic]

As an example of _Senses_ and _Actuators_, suppose you need to interact with
some external API by sending it `Request`s and receiving `Response`es from it.

Sidenote: once you finish reading this guide, you might want to look at the [SWAPIDriver][swapi-driver-source]
example to see how to implement a [Cycle driver][cycle-driver] in Laminar.

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

def computer(api: cycle.CIO[Response, Request]) = {
  // define the behavior of `actuators` somehow
}
```

The `cycle.CIO[Sense, Actuator]` type is defined something like:

```scala
trait CIO[I, O] {
  val input:  EventStream[I]
  val output: WriteBus[O]
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

def computer(states: cycle.EIO[State], actions: cycle.EIO[Action]): Mod[Element] = {
  // Initialize the computer's internal state and keep it on a Signal[State]
  // in order to always have a *current value*
  val stateSignal: Signal[State] = states.startWith(State(0, 0))
  
  // Now, whenever we sense a user Action, we have to update our current state
  val updatedState: EventStream[State] = 
     actions.withCurrentValueOf(stateSignal).map(Function.tupled(performAction))
 
  updatedState --> states
}

def performAction(action: Action, state: State): State = action match {
 case Increment =>
   state.copy(state.value + 1, state.numberOfInteractions + 1)
 case Decrement =>
   state.copy(state.value - 1, state.numberOfInteractions + 1)
}
```

Let's explore our previous example code.

* The `cycle.EIO[E]` type is just an alias for `cycle.CIO[E, E]`. 

  EIO stands for EqualIO, meaning that both, senses and actuators have the same type `E`.
  It's equivalent to [Airstream]'s `EventBus[E]`

* Our previous example does not render anything (we will get to producing views later).

  Yet, it's a fully working example of how to create a cycle function that reads and writes
  from both: `actions` and `states`.

* The return type is `Mod[Element]`. 

  The `updatedState --> states` produces Laminar modifier that manages the event subscriptions. 
  
  Read more about modifiers and subscription ownership at [LaminarDocs].
  
  All of this is part of Laminar's [memory safety and glitch free guarantees][LaminarSafety].


#### Producing reactive views

Now, we will refactor our `computer` function to actually render a user interface.

For brevity sake, we will add `???` for previously seen code.

```scala
def computer(states: cycle.EIO[State], actions: cycle.EIO[Action]): Div = {
  val stateSignal: Signal[State] = ???
  val updatedState: EventStream[State] = ???
 
  div(
    counterView(stateSignal),
    actionControls(actions),
    updatedState --> state
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
  
## Drivers  

A [Driver][cycle-driver] is the Cycle-way to interpret effects.

> All drivers artifact: `com.github.vic.laminar_cycle::all-drivers::VERSION`

Available Drivers:
* [FetchDriver][fetch-driver-javadoc] ([source][fetch-driver-source])
  > Artifact: `com.github.vic.laminar_cycle::fetch-driver::VERSION`

  A cycle driver around `fetch` for executing HTTP requests.

* [ZIODriver][zio-driver-javadoc] ([source][zio-driver-source])
  > Artifact: `com.github.vic.laminar_cycle::zio-driver::VERSION`

  - `ZIODriver.unsafeEither` runs fallible-effects into `Either[E,A]` values.
  - `ZIODriver.unsafeFuture` runs incoming effects with `runtime.unsafeRunToFuture`
  

## Examples

* [Counter] ([source][counter-source])

  Runnable implementation of the counter example on README.md.
  
  Shows basics of using `cycle.InOut` types, handling user actions to update 
  the current state and update a view based on it.

* [SWAPIDriver] ([source][swapi-driver-source])

  Search StartWars characters by name.
  
  Shows how a [Cycle driver][cycle-driver] looks like with Laminar.
  
  The SWAPIDriver makes http requests to a the [SWAPI] database via REST.
  

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
[cycle-driver]: https://cycle.js.org/drivers.html

[Counter]: https://vic.github.io/laminar_cycle/examples/cycle_counter/src/index.html
[counter-source]: examples/cycle_counter/src

[SWAPI]: https://swapi.dev/
[SWAPIDriver]: https://vic.github.io/laminar_cycle/examples/swapi_driver/src/index.html
[swapi-driver-source]: examples/swapi_driver/src

[laminar-cycle-javadoc]: https://vic.github.io/laminar_cycle/out/cycle/docJar/dest/javadoc/index.html
[laminar-cycle-source]: cycle/src/Cycle.scala

[fetch-driver-javadoc]: https://vic.github.io/laminar_cycle/out/drivers/fetch/docJar/dest/javadoc/index.html
[fetch-driver-source]: drivers/fetch/src

[zio-driver-javadoc]: https://vic.github.io/laminar_cycle/out/drivers/zio/docJar/dest/javadoc/index.html
[zio-driver-source]: drivers/zio/src

[ZIO]: https://zio.dev/
[@vic]: https://twitter.com/oeiuwq
