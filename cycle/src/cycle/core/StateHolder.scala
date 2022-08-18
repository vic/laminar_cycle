package cycle.core

import com.raquo.airstream.core.Sink
import com.raquo.airstream.core.Source.SignalSource
import com.raquo.laminar.api.L._

trait StateHolder[S] extends SignalSource[S] with Sink[S] { self =>
  def bimap[A](sourceMap: S => A)(sinkMap: A => S): StateHolder[A] =
    new StateHolder[A] {
      override def toObservable: Signal[A] = self.toObservable.map(sourceMap)
      override def toObserver: Observer[A] = self.toObserver.contramap(sinkMap)
    }
}

object StateHolder {
  def fromValue[S](s: => S): StateHolder[S] = new StateHolder[S] {
    val stateBus: EventBus[S]            = new EventBus
    val stateSignal: Signal[S]           = stateBus.events.toSignal(s)
    override def toObservable: Signal[S] = stateSignal
    override def toObserver: Observer[S] = stateBus.toObserver
  }

  def fromSourceSink[S](source: SignalSource[S], sink: Sink[S]): StateHolder[S] =
    new StateHolder[S] {
      override def toObservable: Signal[S] = source.toObservable
      override def toObserver: Observer[S] = sink.toObserver
    }
}
