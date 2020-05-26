package cycle
import com.raquo.laminar.api.L._
import com.raquo.laminar.nodes.ReactiveElement

object mountDriver {

  case class Devices[El <: Element](
      mounted: EventStream[MountContext[El]],
      unmounted: EventStream[El]
  )

  def apply[El <: Element](): Driver[Devices[El], El] = {
    val mount   = new EventBus[MountContext[El]]
    val unmount = new EventBus[El]

    val binder =
      Binder[El] { el =>
        ReactiveElement.bindSubscription[El](el) { ctx =>
          mount.writer
            .addSource(EventStream.fromValue(ctx, emitOnce = false))(ctx.owner)
          new Subscription(ctx.owner, cleanup = () => {
            unmount.writer.onNext(el)
          })
        }
      }

    Driver(
      Devices(mount.events, unmount.events),
      binder
    )
  }
}
