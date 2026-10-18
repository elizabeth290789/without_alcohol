package ru.daybyday.app.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import ru.daybyday.app.R
import ru.daybyday.app.data.AppSettings
import ru.daybyday.app.data.LocalHistory
import ru.daybyday.app.data.SettingsRepository
import ru.daybyday.app.data.TrackerRepository
import ru.daybyday.app.domain.Award
import ru.daybyday.app.domain.TrackerDomain

private val Context.notificationState: DataStore<Preferences> by preferencesDataStore("notification_state")
private val notificationMutex = Mutex()

private object Keys {
    val signature = stringPreferencesKey("schedule_signature")
    val achievementEnabled = booleanPreferencesKey("achievement_was_enabled")
    val recordEnabled = booleanPreferencesKey("record_was_enabled")
    val seenAwards = stringSetPreferencesKey("seen_award_thresholds")
    val recordDays = longPreferencesKey("record_days")
    val dailyDate = longPreferencesKey("last_daily_epoch_day")
}

/** Optional, inexact reminders. No always-running service or exact alarm is used. */
object NotificationScheduler {
    private const val WORK_NAME = "day_by_day_next_reminder"

    suspend fun schedule(
        context: Context,
        settings: AppSettings,
        hasPeriod: Boolean,
        forceReschedule: Boolean = false,
    ) =
        withContext(Dispatchers.IO) {
            val app = context.applicationContext
            notificationMutex.withLock {
                val effective = SettingsRepository(app).settings.first()
                if (!hasPeriod || !effective.anyEnabled()) {
                    cancelLocked(app)
                    return@withLock
                }
                val history = TrackerRepository(app).readHistory()
                if (history.periods.isEmpty()) {
                    cancelLocked(app)
                    return@withLock
                }
                prepareWatermarks(app, effective, history)
                val signature = effective.signature()
                val manager = WorkManager.getInstance(app)
                val existing = manager.getWorkInfosForUniqueWork(WORK_NAME).get()
                val saved = app.notificationState.data.first()[Keys.signature]
                // Opening the app must not move an already scheduled reminder forward.
                if (!forceReschedule && saved == signature && existing.any { !it.state.isFinished }) return@withLock
                currentCoroutineContext().ensureActive()
                manager.enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, nextRequest(effective))
                    .result.get()
                app.notificationState.edit { it[Keys.signature] = signature }
            }
        }

    /** Used after clearing history and when every reminder is disabled. */
    suspend fun cancel(context: Context) = withContext(Dispatchers.IO) {
        notificationMutex.withLock { cancelLocked(context.applicationContext) }
    }

    private suspend fun cancelLocked(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME).result.get()
        context.notificationState.edit { it.clear() }
        val manager = NotificationManagerCompat.from(context)
        manager.cancel(DAILY_ID)
        manager.cancel(ACHIEVEMENT_ID)
        manager.cancel(RECORD_ID)
    }

    internal suspend fun enqueueFollowing(context: Context) = withContext(Dispatchers.IO) {
        notificationMutex.withLock {
            val settings = SettingsRepository(context).settings.first()
            val history = TrackerRepository(context).readHistory()
            if (history.periods.isEmpty() || !settings.anyEnabled()) return@withLock
            currentCoroutineContext().ensureActive()
            // Appending lets this worker return SUCCESS before its successor can run.
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                nextRequest(settings),
            ).result.get()
            context.notificationState.edit { it[Keys.signature] = settings.signature() }
        }
    }

    private fun nextRequest(settings: AppSettings) = OneTimeWorkRequestBuilder<NotificationWorker>()
        .setInitialDelay(nextDelayMillis(settings), TimeUnit.MILLISECONDS)
        .build()

    internal fun nextDelayMillis(settings: AppSettings, now: ZonedDateTime = ZonedDateTime.now()): Long {
        val time = if (settings.dailyEnabled) LocalTime.of(settings.hour, settings.minute)
            else LocalTime.of(0, 1)
        var target = now.toLocalDate().atTime(time).atZone(now.zone)
        if (!target.isAfter(now)) target = now.toLocalDate().plusDays(1).atTime(time).atZone(now.zone)
        // atZone handles clock gaps and overlaps using the platform's zone rules.
        return Duration.between(now, target).toMillis().coerceAtLeast(1000L)
    }
}

/** Shared by foreground refreshes and workers; the persisted ledger prevents repeats. */
object NotificationCoordinator {
    /** Explicit edits change historical facts; clock changes alone must not reset dedupe. */
    suspend fun reconcileAfterDateCorrection(context: Context) = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        notificationMutex.withLock {
            val history = TrackerRepository(app).readHistory()
            val validAwards = history.awards.mapTo(mutableSetOf()) { it.threshold.toString() }
            val correctedBest = TrackerDomain.snapshot(history.periods, history.awards, LocalDate.now()).bestDays
            app.notificationState.edit { preferences ->
                preferences[Keys.seenAwards] = preferences[Keys.seenAwards].orEmpty().intersect(validAwards)
                preferences[Keys.recordDays] = correctedBest
            }
        }
    }

    suspend fun checkProgress(
        context: Context,
        newAwards: List<Award>,
        settings: AppSettings,
        today: LocalDate = LocalDate.now(),
    ) =
        withContext(Dispatchers.IO) {
            val app = context.applicationContext
            notificationMutex.withLock {
                val repository = TrackerRepository(app)
                // A switch can change while a worker is synchronizing. Persisted settings
                // are authoritative, so a stale caller cannot re-enable notifications.
                val effective = repository.settings.settings.first()
                val history = repository.readHistory()
                if (history.periods.isEmpty()) return@withLock
                prepareWatermarks(app, effective, history, today)
                val state = app.notificationState.data.first()
                val seen = state[Keys.seenAwards].orEmpty()
                // Include committed-but-unannounced awards after a process restart.
                val requested = newAwards.map { it.threshold }.toSet()
                val fresh = history.awards.filter {
                    it.threshold.toString() !in seen &&
                        (it.threshold in requested || effective.achievementEnabled)
                }
                val progress = TrackerDomain.snapshot(history.periods, history.awards, today)
                val previousRecord = state[Keys.recordDays] ?: progress.bestDays
                val newRecord = effective.recordEnabled && progress.bestDays > previousRecord
                // Write before posting: a killed process must not announce an award twice.
                app.notificationState.edit { preferences ->
                    if (effective.achievementEnabled) {
                        preferences[Keys.seenAwards] = seen + fresh.map { it.threshold.toString() }
                    }
                    if (effective.recordEnabled) {
                        preferences[Keys.recordDays] = maxOf(previousRecord, progress.bestDays)
                    }
                }
                if (effective.achievementEnabled && fresh.isNotEmpty()) {
                    post(app, ACHIEVEMENT_ID, "Новый этап", "В твоей коллекции появилось новое достижение.", effective)
                }
                if (newRecord) {
                    post(app, RECORD_ID, "Личный прогресс", "Ты прошёл новый рубеж. Продолжай в своём темпе.", effective)
                }
            }
        }

    internal suspend fun daily(context: Context) = withContext(Dispatchers.IO) {
        notificationMutex.withLock {
            val settings = SettingsRepository(context).settings.first()
            if (!settings.dailyEnabled || TrackerRepository(context).readHistory().periods.isEmpty()) return@withLock
            val now = ZonedDateTime.now()
            if (now.toLocalTime().isBefore(LocalTime.of(settings.hour, settings.minute))) return@withLock
            val day = now.toLocalDate().toEpochDay()
            val alreadySent = context.notificationState.data.first()[Keys.dailyDate]
            // A clock rollback also cannot repeat an already announced calendar day.
            if (alreadySent != null && alreadySent >= day) return@withLock
            context.notificationState.edit { it[Keys.dailyDate] = day }
            val index = Math.floorMod(day, TrackerDomain.messages.size.toLong()).toInt()
            post(context, DAILY_ID, "День за днём", TrackerDomain.messages[index], settings)
        }
    }
}

class NotificationWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = try {
        val repository = TrackerRepository(applicationContext)
        val settings = repository.settings.settings.first()
        if (settings.anyEnabled() && repository.readHistory().periods.isNotEmpty()) {
            val awards = repository.synchronize()
            NotificationCoordinator.checkProgress(applicationContext, awards, settings)
            NotificationCoordinator.daily(applicationContext)
            NotificationScheduler.enqueueFollowing(applicationContext)
        }
        Result.success()
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        // A transient database or OS failure retains WorkManager's normal backoff.
        Result.retry()
    }
}

private suspend fun prepareWatermarks(
    context: Context,
    settings: AppSettings,
    history: LocalHistory,
    today: LocalDate = LocalDate.now(),
) {
    val best = TrackerDomain.snapshot(history.periods, history.awards, today).bestDays
    context.notificationState.edit { preferences ->
        if (settings.achievementEnabled && preferences[Keys.achievementEnabled] != true) {
            preferences[Keys.seenAwards] = preferences[Keys.seenAwards].orEmpty() +
                history.awards.map { it.threshold.toString() }
        }
        if (settings.recordEnabled && preferences[Keys.recordEnabled] != true) {
            preferences[Keys.recordDays] = maxOf(preferences[Keys.recordDays] ?: 0L, best)
        }
        preferences[Keys.achievementEnabled] = settings.achievementEnabled
        preferences[Keys.recordEnabled] = settings.recordEnabled
    }
}

private fun AppSettings.anyEnabled() = dailyEnabled || achievementEnabled || recordEnabled

private fun AppSettings.signature() = listOf(
    dailyEnabled, achievementEnabled, recordEnabled,
    if (dailyEnabled) hour else 0, if (dailyEnabled) minute else 1,
    ZoneId.systemDefault().id,
).joinToString(":")

private const val CHANNEL_ID = "day_by_day_gentle_reminders"
private const val DAILY_ID = 100
private const val ACHIEVEMENT_ID = 101
private const val RECORD_ID = 102

private fun post(context: Context, id: Int, title: String, text: String, settings: AppSettings) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    ) return
    val manager = NotificationManagerCompat.from(context)
    if (!manager.areNotificationsEnabled()) return
    val systemManager = context.getSystemService(NotificationManager::class.java)
    systemManager.createNotificationChannel(
        NotificationChannel(CHANNEL_ID, "Ненавязчивые напоминания", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Необязательные сообщения о личном прогрессе"
            enableVibration(false)
        },
    )
    val publicVersion = NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle("День за днём")
        .setContentText("У тебя новое сообщение")
        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        .build()
    val builder = NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle(title)
        .setContentText(text)
        .setStyle(NotificationCompat.BigTextStyle().bigText(text))
        .setAutoCancel(true)
        .setOnlyAlertOnce(true)
        .setSilent(true)
        .setVisibility(if (settings.privateNotifications) NotificationCompat.VISIBILITY_PRIVATE else NotificationCompat.VISIBILITY_PUBLIC)
        .setPublicVersion(publicVersion)
    context.packageManager.getLaunchIntentForPackage(context.packageName)?.let { intent ->
        builder.setContentIntent(PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
    }
    try {
        manager.notify(id, builder.build())
    } catch (_: SecurityException) {
        // The user can revoke Android permission between the check and the post.
    }
}
