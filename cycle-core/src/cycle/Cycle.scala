package cycle

import com.raquo.laminar.api.L.*
import com.raquo.laminar.nodes.ReactiveElement
import cycle.core.*

/*
md:+cycle.readme+

A Cycle component holds a `State`, consumes `In` and produces `Out` stimuli.

```scala
// The Cycle interface is defined as:
<%= source.code("cycle.trait") %>
```

The `In` stimuli comes from the outside world - e.g. from user interactions
or other Cycle components -, and the cycle's internal handler can process
those events and choose to update it's state or produce `Out` events.

It's possible to have components that don't consume any input -their `In`
type would be `Nothing`- but still can produce `Out` stimuli. An example
is a clock that is triggered internally by some periodic `EventStream`.

Simple cycle components may not need to produce `Out` events, in that
case their `Out` type would be `Nothing`. An example of this are
redux-style components that just update their internal state from inputs.

```scala
// This example reverses it's internal state on every input and produces no output.
<%= source("/examples/hello/src/Hello.scala").code("hello.cycle") %>
```

md:-cycle.readme-
 */

// code:+cycle.trait+
trait Cycle[-In, +State, +Out] extends Sink[In] with EventSource[Out]:
  def stateSignal: Signal[State]
  // code:-cycle.trait-
  def toModifier[E <: ReactiveElement.Base]: Mod[E]

object Cycle extends CycleFactories with CycleImplicits
