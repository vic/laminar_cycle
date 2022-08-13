package cyle

import com.raquo.airstream.core.EventStream

trait InputHandler[-I, +S, +O] extends (EventStream[I] => EventStream[Event[I, S, O]])
