package cycle.pattern

import com.raquo.laminar.api.L.*
import com.raquo.laminar.nodes.ReactiveElement

import scala.annotation.targetName

object BindWhileSignal:

  private def bindWhileSignal[E <: Element](active: SignalSource[Boolean])(dyn: DynamicOwner => DynamicSubscription)
      : Binder[E] =
    Binder[E] { el =>
      val dynOwner = new DynamicOwner(() => ())
      def activate(a: Boolean): Unit =
        if (a && !dynOwner.isActive) dynOwner.activate()
        else if (!a && dynOwner.isActive) dynOwner.deactivate()
      val activations = active.toObservable.composeAll(_.map(activate), _.map(activate))
      val dynSub      = dyn(dynOwner)
      val dynBind     = Binder[E](_ => dynSub)
      ReactiveElement.bindSubscription(el.amend(dynBind, activations --> Observer.empty)) { ctx =>
        new Subscription(ctx.owner, cleanup = () => activate(false))
      }
    }

  implicit class RichSource[A](private val source: Source[A]) extends AnyVal:
    def bindWhile(active: SignalSource[Boolean]): PartiallyApplied[A] = new PartiallyApplied(source, active)

  private[BindWhileSignal] class PartiallyApplied[A] private[BindWhileSignal] (
      source: Source[A],
      active: SignalSource[Boolean]
  ):
    def -->(sink: Sink[A]): Binder[ReactiveElement.Base] =
      bindWhileSignal(active)(DynamicSubscription.subscribeSink(_, source.toObservable, sink))

    def -->(onNext: A => Unit): Binder[ReactiveElement.Base] =
      bindWhileSignal(active)(DynamicSubscription.subscribeFn(_, source.toObservable, onNext))
