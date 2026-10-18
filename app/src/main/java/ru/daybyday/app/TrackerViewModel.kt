package ru.daybyday.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.daybyday.app.data.AppSettings
import ru.daybyday.app.data.LocalHistory
import ru.daybyday.app.domain.TrackerDomain
import ru.daybyday.app.ui.AppUiState
import ru.daybyday.app.ui.UiAchievement
import ru.daybyday.app.ui.UiCelebration
import ru.daybyday.app.ui.UiLevel
import ru.daybyday.app.ui.UiNotificationSettings
import ru.daybyday.app.ui.UiPeriod

class TrackerViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as DayByDayApplication).repository
    private val today = MutableStateFlow(LocalDate.now())
    private val error = MutableStateFlow<String?>(null)
    private val celebration = MutableStateFlow<UiCelebration?>(null)
    private val permission = MutableStateFlow(true)
    private val loaded = MutableStateFlow(false)
    private var midnightJob: Job? = null
    private var previousLevel: Int? = null
    private var previousAwardIds: Set<Long>? = null

    private val events = combine(error, celebration, permission, loaded) { e, c, p, l -> Events(e, c, p, l) }
    val state = combine(repository.history, repository.settings.settings, today, events) { history, settings, date, event ->
        toUiState(history, settings, date).copy(
            isLoading = !event.loaded,
            error = event.error,
            celebration = event.celebration,
            notificationsAllowed = event.permission,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, AppUiState(isLoading = true))

    init {
        viewModelScope.launch {
            combine(repository.history, today) { history, date -> history to date }.collect { (history, date) ->
                val progress = TrackerDomain.snapshot(history.periods, history.awards, date)
                val awardIds = history.awards.map { it.threshold }.toSet()
                val newAward = previousAwardIds?.let { previous ->
                    history.awards.filter { it.threshold !in previous }.maxByOrNull { it.threshold }
                }
                if (newAward != null) {
                    val definition = TrackerDomain.achievements.first { it.threshold == newAward.threshold }
                    celebration.value = UiCelebration(
                        "Новое достижение", definition.title,
                        UiAchievement(definition.threshold.toString(), definition.threshold, definition.title, definition.description, newAward.date),
                    )
                } else if (previousLevel != null && progress.level.number > previousLevel!!) {
                    celebration.value = UiCelebration("Новый уровень", "Уровень ${progress.level.number} · ${progress.level.title}")
                }
                previousLevel = progress.level.number
                previousAwardIds = awardIds
            }
        }
        viewModelScope.launch {
            combine(repository.settings.settings, repository.history) { settings, history -> settings to history.periods.isNotEmpty() }
                .collect { (settings, hasPeriod) ->
                    ru.daybyday.app.notifications.NotificationScheduler.schedule(getApplication(), settings, hasPeriod)
                }
        }
        refreshDate()
    }

    fun refreshDate() {
        today.value = LocalDate.now()
        viewModelScope.launch {
            try {
                val awards = repository.synchronize(today.value)
                val settings = repository.settings.settings.first()
                ru.daybyday.app.notifications.NotificationCoordinator.checkProgress(getApplication(), awards, settings)
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) {
                error.value = e.message ?: "Не удалось прочитать сохранённые данные"
            } finally { loaded.value = true }
        }
        midnightJob?.cancel()
        midnightJob = viewModelScope.launch {
            val now = ZonedDateTime.now()
            val nextMidnight = now.toLocalDate().plusDays(1).atStartOfDay(now.zone)
            delay(Duration.between(now, nextMidnight).toMillis().coerceAtLeast(1_000))
            refreshDate()
        }
    }

    fun start(date: LocalDate) = perform { repository.initialize(date, LocalDate.now()) }
    fun editStart(date: LocalDate) = perform {
        repository.editStart(date, LocalDate.now())
        ru.daybyday.app.notifications.NotificationCoordinator.reconcileAfterDateCorrection(getApplication())
    }
    fun newPeriod(end: LocalDate, start: LocalDate) = perform {
        repository.startNewPeriod(end, start, LocalDate.now())
        ru.daybyday.app.notifications.NotificationCoordinator.reconcileAfterDateCorrection(getApplication())
        celebration.value = UiCelebration("Новый период", "Твой путь продолжается. Всё, что пройдено, остаётся с тобой.")
    }
    fun setTheme(dark: Boolean) = perform { repository.settings.update { it.copy(darkTheme = dark) } }
    fun setNotifications(value: UiNotificationSettings) = perform {
        repository.settings.update { it.copy(
            dailyEnabled = value.dailyEnabled,
            achievementEnabled = value.achievementEnabled,
            recordEnabled = value.recordEnabled,
            hour = value.dailyHour,
            minute = value.dailyMinute,
            privateNotifications = value.privateNotifications,
        ) }
    }
    fun setPermission(allowed: Boolean) { permission.value = allowed }
    fun clearAll() = perform {
        repository.clear()
        ru.daybyday.app.notifications.NotificationScheduler.cancel(getApplication())
        celebration.value = null
        previousAwardIds = null
        previousLevel = null
    }
    fun dismissError() { error.value = null }
    fun dismissCelebration() { celebration.value = null }

    private fun perform(block: suspend () -> Unit) {
        viewModelScope.launch {
            try { block(); today.value = LocalDate.now() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { error.value = e.message ?: "Не удалось сохранить изменения. Попробуйте ещё раз." }
        }
    }

    private fun toUiState(history: LocalHistory, settings: AppSettings, date: LocalDate): AppUiState {
        val p = TrackerDomain.snapshot(history.periods, history.awards, date)
        val awards = history.awards.associateBy { it.threshold }
        val achievements = TrackerDomain.achievements.map { a ->
            UiAchievement(a.threshold.toString(), a.threshold, a.title, a.description, awards[a.threshold]?.date)
        }
        return AppUiState(
            today = date, startDate = p.currentPeriod?.start,
            earliestStartDate = history.periods.mapNotNull { it.endExclusive }.maxOrNull(),
            fullDays = p.completedDays, currentDay = p.currentDay, bestDays = p.bestDays,
            totalDays = p.totalDays, completedPeriods = p.completedPeriods,
            level = UiLevel(p.level.number, p.level.title, p.levelProgress, TrackerDomain.levels.getOrNull(p.level.number)?.minDays),
            achievements = achievements,
            nextAchievement = p.nextAchievement?.let { next -> achievements.first { it.days == next.threshold } },
            periods = history.periods.map { UiPeriod(it.id, it.start, it.endExclusive?.minusDays(1), TrackerDomain.completedDays(it, date)) },
            notifications = UiNotificationSettings(settings.dailyEnabled, settings.achievementEnabled, settings.recordEnabled, settings.hour, settings.minute, settings.privateNotifications),
            darkTheme = settings.darkTheme,
            motivation = TrackerDomain.messages[Math.floorMod(date.toEpochDay(), TrackerDomain.messages.size.toLong()).toInt()],
        )
    }
    private data class Events(val error: String?, val celebration: UiCelebration?, val permission: Boolean, val loaded: Boolean)
}
