package ir.xilo.app.data.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CancellationException

class OutboxColdStartWorker(
    appContext: Context,
    workerParameters: WorkerParameters
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result {
        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            OutboxWorkerEntryPoint::class.java
        )
        return try {
            entryPoint.processor().resetAllInFlight()
            entryPoint.processor().purgeExpiredPermanentFailures()
            entryPoint.scheduler().enqueueNow()
            Result.success()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            Result.retry()
        }
    }
}
