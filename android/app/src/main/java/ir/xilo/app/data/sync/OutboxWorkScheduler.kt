package ir.xilo.app.data.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

@Singleton
class OutboxWorkScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    fun enqueueNow() {
        WorkManager.getInstance(context).enqueueUniqueWork(
            IMMEDIATE_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            drainRequest(initialDelayMs = 0L)
        )
    }

    fun enqueueColdStartRecovery() {
        WorkManager.getInstance(context).enqueueUniqueWork(
            COLD_START_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<OutboxColdStartWorker>()
                .addTag(OUTBOX_WORK_TAG)
                .build()
        )
    }

    fun scheduleSuccessor(runAtMillis: Long, nowMillis: Long) {
        val delayMs = max(MIN_SUCCESSOR_DELAY_MS, runAtMillis - nowMillis)
        WorkManager.getInstance(context).enqueueUniqueWork(
            SUCCESSOR_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            drainRequest(initialDelayMs = delayMs)
        )
    }

    private fun drainRequest(initialDelayMs: Long) =
        OneTimeWorkRequestBuilder<OutboxDrainWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                MIN_BACKOFF_SECONDS,
                TimeUnit.SECONDS
            )
            .setInitialDelay(initialDelayMs, TimeUnit.MILLISECONDS)
            .addTag(OUTBOX_WORK_TAG)
            .build()

    companion object {
        const val IMMEDIATE_WORK_NAME = "xilo-chat-outbox-drain-now"
        const val SUCCESSOR_WORK_NAME = "xilo-chat-outbox-drain-successor"
        const val COLD_START_WORK_NAME = "xilo-chat-outbox-cold-start"
        const val OUTBOX_WORK_TAG = "xilo-chat-outbox"
        private const val MIN_BACKOFF_SECONDS = 10L
        private const val MIN_SUCCESSOR_DELAY_MS = 1_000L
    }
}
