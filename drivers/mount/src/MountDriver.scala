package cycle
import com.raquo.laminar.api.L._

object mountDriver {

  case class Devices[El <: Element](
      mounted: EventStream[MountContext[El]],
      unmounted: EventStream[El]
  )

  def apply[El <: Element](): DriverEl[Devices[El], El] = {
    val mount   = new EventBus[MountContext[El]]
    val unmount = new EventBus[El]

    val binder =
      onMountUnmountCallback[El](
        mount = { ctx =>
          mount.writer
            .addSource(EventStream.fromValue(ctx, emitOnce = false))(ctx.owner)
        },
        unmount = { el => unmount.writer.onNext(el) }
      )

    Driver(
      Devices(mount.events, unmount.events),
      binder
    )
  }
}
