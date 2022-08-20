package cycle.core

import com.raquo.airstream.core.Sink
import com.raquo.airstream.core.Source.SignalSource
import com.raquo.laminar.api.L.*

trait StateHolder[S] extends SignalSource[S] with Sink[S]:
  def bimap[A](sourceMap: S => A)(sinkMap: A => S): StateHolder[A] = new:
    override def toObservable: Signal[A] = StateHolder.this.toObservable.map(sourceMap)
    override def toObserver: Observer[A] = StateHolder.this.toObserver.contramap(sinkMap)

object StateHolder:
  def fromValue[S](s: => S): StateHolder[S] = new:
    val stateBus: EventBus[S]            = new EventBus
    val stateSignal: Signal[S]           = stateBus.events.toSignal(s)
    override def toObservable: Signal[S] = stateSignal
    override def toObserver: Observer[S] = stateBus.toObserver

  def fromSourceSink[S](source: SignalSource[S], sink: Sink[S]): StateHolder[S] = new:
    override def toObservable: Signal[S] = source.toObservable
    override def toObserver: Observer[S] = sink.toObserver
