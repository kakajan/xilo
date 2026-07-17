package ir.xilo.app.data.sync

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
open class OutboxClock @Inject constructor() {
    open fun nowMillis(): Long = System.currentTimeMillis()
}
