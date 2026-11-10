package ru.daybyday.app

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.daybyday.app.data.AppSettings
import ru.daybyday.app.data.LocalHistory
import ru.daybyday.app.domain.ConsumptionDomain
import ru.daybyday.app.domain.ConsumptionEvent
import ru.daybyday.app.domain.TrackerDomain
import ru.daybyday.app.ui.AppUiState
import ru.daybyday.app.ui.UiAchievement
import ru.daybyday.app.ui.UiCelebration
import ru.daybyday.app.ui.UiConsumptionEvent
import ru.daybyday.app.ui.UiConsumptionPreview
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
    private val saving = MutableStateFlow(false)
    private var midnightJob: Job? = null
    private var previousLevel: Int? = null
    private var previousAwardIds: Set<Long>? = null
    private val dateFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.forLanguageTag("ru"))

    // Null means no real database snapshot has arrived yet. An empty history is
    // emitted only after Room has actually read an empty database.
    private val historyState: StateFlow<LocalHistory?> = repository.history
        .map<LocalHistory, LocalHistory?> { it }
        .catch { cause ->
            if (cause is CancellationException) throw cause
            error.value = cause.message ?: "Не удалось прочитать сохранённые данные"
            loaded.value = true
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    private val histories = historyState.filterNotNull()

    private val events = combine(error, celebration, permission, loaded, saving) { e, c, p, l, s ->
        Events(e, c, p, l, s)
    }

    val state = combine(historyState, repository.settings.settings, today, events) { history, settings, date, event ->
        val content = if (history == null) {
            AppUiState(isLoading = true, today = date, darkTheme = settings.darkTheme)
        } else {
            toUiState(history, settings, date).copy(isLoading = !event.loaded)
        }
        content.copy(
            error = event.error,
            celebration = event.celebration,
            notificationsAllowed = event.permission,
            isSaving = event.saving,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, AppUiState(isLoading = true))

    init {
        viewModelScope.launch {
            combine(histories, today) { history, date -> history to date }.collect { (history, date) ->
                val progress = TrackerDomain.snapshot(history.periods, history.awards, date)
                val awardIds = history.awards.map { it.threshold }.toSet()
                val newAward = previousAwardIds?.let { previous ->
                    history.awards.filter { it.threshold !in previous }.maxByOrNull { it.threshold }
                }
                if (newAward != null) {
                    val definition = TrackerDomain.achievements.first { it.threshold == newAward.threshold }
                    celebration.value = UiCelebration(
                        "Новое достижение",
                        definition.title,
                        UiAchievement(
                            definition.threshold.toString(),
                            definition.threshold,
                            definition.title,
                            definition.description,
                            newAward.date,
                        ),
                    )
                } else if (previousLevel != null && progress.level.number > previousLevel!!) {
                    celebration.value = UiCelebration(
                        "Новый уровень",
                        "Уровень ${progress.level.number} · ${progress.level.title}",
                    )
                }
                previousLevel = progress.level.number
                previousAwardIds = awardIds
            }
        }
        viewModelScope.launch {
            combine(repository.settings.settings, histories) { settings, history ->
                settings to history.periods.isNotEmpty()
            }.collect { (settings, hasPeriod) ->
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
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error.value = e.message ?: "Не удалось прочитать сохранённые данные"
            } finally {
                loaded.value = true
            }
        }
        midnightJob?.cancel()
        midnightJob = viewModelScope.launch {
            val now = ZonedDateTime.now()
            val nextMidnight = now.toLocalDate().plusDays(1).atStartOfDay(now.zone)
            delay(Duration.between(now, nextMidnight).toMillis().coerceAtLeast(1_000))
            refreshDate()
        }
    }

    fun start(date: LocalDate) = perform {
        repository.initialize(date, LocalDate.now())
    }

    // This legacy entry remains for an unlinked initial period only. The
    // repository rejects independent edits to a start linked to a consumption
    // marker; the UI opens that marker's date editor instead.
    fun editStart(date: LocalDate) = perform {
        repository.editStart(date, LocalDate.now())
        reconcileNotificationsAfterSave("Дата начала сохранена")
    }

    fun newPeriod(end: LocalDate, start: LocalDate) = perform {
        repository.startNewPeriod(end, start, LocalDate.now())
        celebration.value = UiCelebration(
            "Новый период",
            "Твой путь продолжается. Всё, что пройдено, остаётся с тобой.",
            isNeutral = true,
        )
        reconcileNotificationsAfterSave("Новый период сохранён")
    }

    fun recordConsumption(date: LocalDate) = perform {
        val before = repository.readHistory()
        val duplicate = before.consumptionEvents.any { it.date == date }
        val previousCurrentId = before.periods.singleOrNull { it.endExclusive == null }?.id
        val event = repository.recordConsumption(date, LocalDate.now())
        val after = repository.readHistory()
        celebration.value = consumptionCelebration(
            event = event,
            history = after,
            duplicate = duplicate,
            previousCurrentId = previousCurrentId,
            messageToday = LocalDate.now(),
        )
        reconcileNotificationsAfterSave("Отметка сохранена")
    }

    fun editConsumption(eventId: Long, date: LocalDate) = perform {
        repository.editConsumption(eventId, date, LocalDate.now())
        reconcileNotificationsAfterSave("Отметка сохранена")
    }

    /** Notification bookkeeping must not turn a committed history change into a save error. */
    private suspend fun reconcileNotificationsAfterSave(savedResult: String) {
        try {
            ru.daybyday.app.notifications.NotificationCoordinator.reconcileAfterDateCorrection(getApplication())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // The form uses state.error to abandon its pending-save observation.
            // Keep ancillary failures separate so the committed ledger can close it.
            Log.w("TrackerViewModel", "$savedResult, но не удалось обновить состояние уведомлений", e)
        }
    }

    /** Pure projection of the current Room snapshot; never writes preview data. */
    fun previewConsumption(eventId: Long?, date: LocalDate): UiConsumptionPreview {
        val history = historyState.value
            ?: return UiConsumptionPreview(error = "Данные ещё загружаются")
        return try {
            val now = LocalDate.now()
            val beforeCurrentId = history.periods.singleOrNull { it.endExclusive == null }?.id
            val existingId = if (eventId == null) {
                history.consumptionEvents.firstOrNull { it.date == date }?.id
            } else {
                null
            }
            val change = if (eventId == null) {
                ConsumptionDomain.record(
                    history.periods,
                    history.consumptionEvents,
                    date,
                    now,
                    if (existingId == null) nextId(history.periods.asSequence().map { it.id }) else 0L,
                    if (existingId == null) nextId(history.consumptionEvents.asSequence().map { it.id }) else 0L,
                )
            } else {
                ConsumptionDomain.edit(
                    history.periods,
                    history.consumptionEvents,
                    eventId,
                    date,
                    now,
                )
            }
            val previous = requireNotNull(change.periods.firstOrNull { it.id == change.event.previousPeriodId }) {
                "Не удалось найти предыдущий период"
            }
            val next = requireNotNull(change.periods.firstOrNull { it.id == change.event.nextPeriodId }) {
                "Не удалось найти следующий период"
            }
            val afterCurrentId = change.periods.singleOrNull { it.endExclusive == null }?.id
            UiConsumptionPreview(
                previousDays = TrackerDomain.completedDays(previous, now),
                newStart = next.start,
                fullDays = TrackerDomain.completedDays(next, now),
                existingEventId = existingId,
                newPeriod = beforeCurrentId != afterCurrentId,
            )
        } catch (e: IllegalArgumentException) {
            UiConsumptionPreview(error = e.message ?: "Проверь дату отметки")
        }
    }

    fun setTheme(dark: Boolean) = perform {
        repository.settings.update { it.copy(darkTheme = dark) }
    }

    fun setNotifications(value: UiNotificationSettings) = perform {
        repository.settings.update {
            it.copy(
                dailyEnabled = value.dailyEnabled,
                achievementEnabled = value.achievementEnabled,
                recordEnabled = value.recordEnabled,
                hour = value.dailyHour,
                minute = value.dailyMinute,
                privateNotifications = value.privateNotifications,
            )
        }
    }

    fun setPermission(allowed: Boolean) {
        permission.value = allowed
    }

    fun clearAll() = perform {
        repository.clear()
        ru.daybyday.app.notifications.NotificationScheduler.cancel(getApplication())
        celebration.value = null
        previousAwardIds = emptySet()
        previousLevel = 1
    }

    fun dismissError() {
        error.value = null
    }

    fun dismissCelebration() {
        celebration.value = null
    }

    private fun perform(block: suspend () -> Unit) {
        // Set the flag before launching, so two taps in the same frame do not
        // enqueue duplicate mutations. Room also enforces event uniqueness.
        if (saving.value) return
        saving.value = true
        error.value = null
        viewModelScope.launch {
            try {
                block()
                today.value = LocalDate.now()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error.value = e.message ?: "Не удалось сохранить изменения. Попробуйте ещё раз."
            } finally {
                saving.value = false
            }
        }
    }

    private fun toUiState(history: LocalHistory, settings: AppSettings, date: LocalDate): AppUiState {
        val p = TrackerDomain.snapshot(history.periods, history.awards, date)
        val awards = history.awards.associateBy { it.threshold }
        val achievements = TrackerDomain.achievements.map { a ->
            UiAchievement(
                a.threshold.toString(),
                a.threshold,
                a.title,
                a.description,
                awards[a.threshold]?.date,
            )
        }
        val periodsById = history.periods.associateBy { it.id }
        val consumptionEvents = history.consumptionEvents
            .sortedWith(compareByDescending<ConsumptionEvent> { it.date }.thenByDescending { it.id })
            .map { event ->
                val previous = requireNotNull(periodsById[event.previousPeriodId]) {
                    "Не удалось найти предыдущий период"
                }
                val bounds = ConsumptionDomain.bounds(
                    history.periods,
                    history.consumptionEvents,
                    event.id,
                    date,
                )
                UiConsumptionEvent(
                    id = event.id,
                    date = event.date,
                    previousDays = TrackerDomain.completedDays(previous, date),
                    minDate = bounds.minDate,
                    maxDate = bounds.maxDate,
                )
            }
        return AppUiState(
            today = date,
            startDate = p.currentPeriod?.start,
            earliestStartDate = history.periods.mapNotNull { it.endExclusive }.maxOrNull(),
            fullDays = p.completedDays,
            currentDay = p.currentDay,
            bestDays = p.bestDays,
            totalDays = p.totalDays,
            completedPeriods = p.completedPeriods,
            level = UiLevel(
                p.level.number,
                p.level.title,
                p.levelProgress,
                TrackerDomain.levels.getOrNull(p.level.number)?.minDays,
            ),
            achievements = achievements,
            nextAchievement = p.nextAchievement?.let { next ->
                achievements.first { it.days == next.threshold }
            },
            periods = history.periods
                .filter { it.endExclusive == null || TrackerDomain.completedDays(it, date) > 0L }
                .map {
                    UiPeriod(
                        it.id,
                        it.start,
                        it.endExclusive?.minusDays(1),
                        TrackerDomain.completedDays(it, date),
                    )
                },
            notifications = UiNotificationSettings(
                settings.dailyEnabled,
                settings.achievementEnabled,
                settings.recordEnabled,
                settings.hour,
                settings.minute,
                settings.privateNotifications,
            ),
            darkTheme = settings.darkTheme,
            motivation = TrackerDomain.messages[
                Math.floorMod(date.toEpochDay(), TrackerDomain.messages.size.toLong()).toInt()
            ],
            consumptionEvents = consumptionEvents,
            currentStartConsumptionEventId = p.currentPeriod?.id?.let { currentId ->
                history.consumptionEvents.singleOrNull { it.nextPeriodId == currentId }?.id
            },
            consumptionMinDate = ConsumptionDomain.minimumRecordDate(history.periods, history.consumptionEvents),
        )
    }

    private fun consumptionCelebration(
        event: ConsumptionEvent,
        history: LocalHistory,
        duplicate: Boolean,
        previousCurrentId: Long?,
        messageToday: LocalDate,
    ): UiCelebration {
        val dateText = event.date.format(dateFormatter)
        if (duplicate) {
            return UiCelebration(
                "Отметка уже есть",
                "За ${dateText} уже есть отметка. Её дату можно исправить в истории отметок.",
                isNeutral = true,
            )
        }
        val previous = requireNotNull(history.periods.firstOrNull { it.id == event.previousPeriodId })
        val next = requireNotNull(history.periods.firstOrNull { it.id == event.nextPeriodId })
        val days = TrackerDomain.completedDays(previous, messageToday)
        val currentId = history.periods.singleOrNull { it.endExclusive == null }?.id
        val createdNewPeriod = currentId != previousCurrentId
        val resultText = if (days > 0L) {
            "Предыдущий результат — ${days} ${TrackerDomain.dayWord(days)} — сохранён в истории."
        } else {
            "Отметка за ${dateText} сохранена."
        }
        val startText = when {
            next.start == messageToday.plusDays(1) ->
                "Новый период начнётся завтра, ${next.start.format(dateFormatter)}."
            next.start.isAfter(messageToday) ->
                "Новый период начнётся ${next.start.format(dateFormatter)}."
            createdNewPeriod ->
                "Отсчёт идёт с ${next.start.format(dateFormatter)}. Продолжаем день за днём."
            else ->
                "Текущий период идёт с ${next.start.format(dateFormatter)}."
        }
        return UiCelebration(
            title = if (createdNewPeriod && !next.start.isAfter(messageToday)) {
                "Новый период начат"
            } else {
                "Отметка сохранена"
            },
            message = "${resultText} ${startText}",
            isNeutral = true,
        )
    }

    private fun nextId(ids: Sequence<Long>): Long {
        val max = (ids.maxOrNull() ?: 0L).coerceAtLeast(0L)
        require(max < Long.MAX_VALUE) { "Не удалось создать новую отметку" }
        return max + 1L
    }

    private data class Events(
        val error: String?,
        val celebration: UiCelebration?,
        val permission: Boolean,
        val loaded: Boolean,
        val saving: Boolean,
    )
}
