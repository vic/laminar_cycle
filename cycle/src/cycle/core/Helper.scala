package cycle.core

import com.raquo.laminar.api.L._

private[core] trait Helper {
  def amend[El <: Element](mods: Mod[El]*): Mod[El] =
    inContext[El](_.amend(mods: _*))

  def drain[El <: Element](eventStream: EventStream[_]): Binder[El] =
    eventStream.filter(_ => false).mapTo(()) --> Observer[Unit](_ => ())
}
