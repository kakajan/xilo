package ir.xilo.app.data.remote.websocket

import ir.xilo.app.data.remote.dto.NotificationResponse
import ir.xilo.app.data.repository.NotificationRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Applies user-scoped notification realtime events and refetches REST state on reconnect.
 */
@Singleton
class NotificationRealtimeReconciler @Inject constructor(
    private val webSocketManager: WebSocketManager,
    private val notificationRepository: NotificationRepository,
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    init {
        scope.launch {
            webSocketManager.events.collect { event ->
                reconcile(event)
            }
        }
        scope.launch {
            var wasConnected = webSocketManager.isConnected.value
            webSocketManager.isConnected.collect { connected ->
                if (connected && !wasConnected) {
                    notificationRepository.reconcileOnReconnect()
                }
                wasConnected = connected
            }
        }
    }

    internal suspend fun reconcile(event: RealtimeEvent) {
        try {
            when (event) {
                is RealtimeEvent.NotificationNew -> {
                    notificationRepository.applyRealtimeNew(event.notification)
                }
                is RealtimeEvent.NotificationCount -> {
                    notificationRepository.applyRealtimeCount(event.unread)
                }
                else -> Unit
            }
        } catch (_: Exception) {
            // A single bad event must not stop the collector.
        }
    }
}
