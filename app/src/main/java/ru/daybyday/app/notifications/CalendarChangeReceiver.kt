package ru.daybyday.app.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import ru.daybyday.app.data.TrackerRepository

/** Re-evaluates local dates without a polling timer when the system clock changes. */
class CalendarChangeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in acceptedActions) return
        val pending = goAsync()
        val app = context.applicationContext
        receiverScope.launch {
            try {
                val repository = TrackerRepository(app)
                val settings = repository.settings.settings.first()
                val hasPeriod = repository.readHistory().periods.isNotEmpty()
                if (hasPeriod && (settings.dailyEnabled || settings.achievementEnabled || settings.recordEnabled)) {
                    val awards = repository.synchronize()
                    NotificationCoordinator.checkProgress(app, awards, settings)
                }
                // TIME_SET can leave the zone unchanged, so force a new wall-clock delay.
                NotificationScheduler.schedule(app, settings, hasPeriod, forceReschedule = true)
            } catch (_: Exception) {
                // Foreground refresh and persisted WorkManager work remain fallbacks.
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val acceptedActions = setOf(
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_DATE_CHANGED,
        )
    }
}
