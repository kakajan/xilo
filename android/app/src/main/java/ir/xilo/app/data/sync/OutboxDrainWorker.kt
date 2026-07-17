package ir.xilo.app.data.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

class OutboxDrainWorker(
    appContext: Context,
    workerParameters: WorkerParameters
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result {
        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            OutboxWorkerEntryPoint::class.java
        )
        val processor = entryPoint.processor()
        val scheduler = entryPoint.scheduler()

        processor.resetStaleInFlight()
        processor.purgeExpiredPermanentFailures()

        repeat(MAX_OPERATIONS_PER_RUN) {
            val operationKey = processor.nextReadyOperationKey()
                ?: return scheduleNextOrFinish(processor, scheduler)

            when (processor.process(operationKey)) {
                is OutboxProcessResult.Delivered,
                is OutboxProcessResult.PermanentFailure,
                OutboxProcessResult.Missing -> Unit
                is OutboxProcessResult.RetryScheduled,
                OutboxProcessResult.NotReady,
                OutboxProcessResult.AlreadyInFlight ->
                    return scheduleNextOrFinish(processor, scheduler)
            }
        }
        return scheduleNextOrFinish(processor, scheduler)
    }

    private suspend fun scheduleNextOrFinish(
        processor: ChatOutboxProcessor,
        scheduler: OutboxWorkScheduler
    ): Result {
        processor.nextRecoveryAt()?.let { dueAt ->
            scheduler.scheduleSuccessor(
                runAtMillis = dueAt,
                nowMillis = processor.nowMillis()
            )
        }
        return Result.success()
    }

    private companion object {
        const val MAX_OPERATIONS_PER_RUN = 50
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface OutboxWorkerEntryPoint {
    fun processor(): ChatOutboxProcessor
    fun scheduler(): OutboxWorkScheduler
}
