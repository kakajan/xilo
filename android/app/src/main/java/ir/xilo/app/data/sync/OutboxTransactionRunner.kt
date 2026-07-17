package ir.xilo.app.data.sync

import androidx.room.withTransaction
import ir.xilo.app.data.local.db.XiloDatabase
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
open class OutboxTransactionRunner @Inject constructor(
    private val database: XiloDatabase
) {
    open suspend fun run(block: suspend () -> Unit) {
        database.withTransaction {
            block()
        }
    }
}
