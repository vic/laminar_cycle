package cycle

import zio.*
import zio.stream.*

object Cycle {
  def create[In, State, Out] = {
    val x =
      ???
  }
}

//trait Cycle[-I, +S, +O] {
//  def ins: Cycle.Writer[I]
//  def outs: Cycle.Reader[O]
//  def state: Cycle.Signal[S]
//}
//
//object Cycle {
//
//  sealed trait Signal[S] {
//    def current: UIO[S]
//    def changes: Reader[S]
//  }
//
//  sealed trait Reader[O] {
//    def asQueue: Dequeue[O]
//    def asStream: UStream[O]
//  }
//
//  sealed trait Writer[I] {
//    def asQueue: Enqueue[I]
//  }
//
//  sealed trait Event
//  object Event {
//    final case class Value[V](v: V)                        extends Event
//    final case class In[I](in: I)                          extends Event
//    final case class Out[O](out: O)                        extends Event
//    final case class WithState[S](f: Ref[S] => UIO[Event]) extends Event
//
//    final case class WithHandler[R, E, I](f: Ref[ZPipeline[R, E, I, Event]] => UIO[Event]) extends Event
//  }
//
//  def create[R, E, S, I, O](
//      makeState: UIO[Ref[S]],
//      makeHandler: UIO[Ref[ZPipeline[R, E, I, Event]]]
//  ): ZIO[Any, Nothing, Cycle[I, S, O]] = {
//    import Event.*
//    for {
//      stateRef: Ref[S]                           <- makeState
//      handlerRef: Ref[ZPipeline[R, E, I, Event]] <- makeHandler
//
//      hub: Hub[Event] <- Hub.unbounded[Event]
//
//      outs: ZStream[R, E, O]          <- hub.subscribe.map(ZStream.fromQueue(_).collect { case Out(o: O) => o })
//      vals: ZStream[R, E, Value[Any]] <- hub.subscribe.map(ZStream.fromQueue(_).collectType[Value[Any]])
//
//      ins: ZStream[R, E, I] <- hub.subscribe.map(ZStream.fromQueue(_).collect { case In(i: I) => i })
//      loopbackFromInput: ZStream[R, E, Event] = ???
////        ins.mapZIO(in => handlerRef.get.map(_ -> in)).flatMap { case (x, y) => }
//
//      withStates: ZStream[R, E, WithState[S]] <- hub.subscribe.map(ZStream.fromQueue(_).collectType[WithState[S]])
//      loopbackFromWithStates: ZStream[R, E, Event] = withStates.mapZIO(w => w.f(stateRef))
//
//      loopback: ZStream[R, E, Event] = loopbackFromInput.merge(loopbackFromWithStates)
//
//      x <- loopback.run(ZSink.fromHub(hub)).fork
//
//    } yield ???
//  }
//
//}
