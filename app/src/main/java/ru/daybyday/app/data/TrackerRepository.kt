package ru.daybyday.app.data

import android.content.Context
import androidx.room.withTransaction
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.withLock
import ru.daybyday.app.domain.Award
import ru.daybyday.app.domain.Period
import ru.daybyday.app.domain.TrackerDomain

data class LocalHistory(
    val periods: List<Period> = emptyList(),
    val awards: List<Award> = emptyList(),
)

/** Offline source of truth, shared by the UI and WorkManager workers. */
class TrackerRepository(context: Context) {
    private val database = TrackerDatabase.getInstance(context.applicationContext)
    private val dao = database.trackerDao()
    val settings = SettingsRepository(context.applicationContext)

    val history: Flow<LocalHistory> = dao.observeHistory()
        .map { it.toHistory() }
        .distinctUntilChanged()

    suspend fun readHistory(): LocalHistory = dao.readHistory().toHistory()

    suspend fun initialize(start: LocalDate, today: LocalDate = LocalDate.now()) {
        mutate(today) { previous ->
            require(previous.periods.isEmpty()) { "Период уже создан" }
            TrackerDomain.startFirst(start, today)
        }
    }

    suspend fun editStart(newStart: LocalDate, today: LocalDate = LocalDate.now()) {
        storageMutex.withLock {
            database.withTransaction {
                val previous = dao.readHistory().toHistory()
                val periods = TrackerDomain.editStart(previous.periods, newStart, today)
                val currentId = previous.periods.singleOrNull { it.endExclusive == null }?.id
                val awards = TrackerDomain.reconcileAwards(
                    periods,
                    previous.awards,
                    today,
                    correctedPeriodIds = setOfNotNull(currentId),
                )
                persist(previous, periods, awards)
            }
        }
    }

    suspend fun startNewPeriod(
        endInclusive: LocalDate,
        newStart: LocalDate,
        today: LocalDate = LocalDate.now(),
    ) {
        mutate(today) { previous ->
            val nextId = (previous.periods.maxOfOrNull { it.id } ?: 0L) + 1L
            TrackerDomain.startNewPeriod(previous.periods, endInclusive, newStart, today, nextId)
        }
    }

    /** Reconciles date changes without per-day rows or redundant database writes. */
    suspend fun synchronize(today: LocalDate = LocalDate.now()): List<Award> =
        storageMutex.withLock {
            database.withTransaction {
                val previous = dao.readHistory().toHistory()
                val awards = TrackerDomain.reconcileAwards(previous.periods, previous.awards, today)
                persist(previous, previous.periods, awards)
                val priorThresholds = previous.awards.mapTo(mutableSetOf()) { it.threshold }
                awards.filter { it.threshold !in priorThresholds }
            }
        }

    suspend fun clear() {
        storageMutex.withLock {
            // Disable reminders before deleting data; both operations are serialized
            // against workers, date synchronization, and settings changes.
            settings.clearLocked()
            database.withTransaction {
                dao.clearAwards()
                dao.clearPeriods()
            }
        }
    }

    private suspend fun mutate(
        today: LocalDate,
        change: (LocalHistory) -> List<Period>,
    ) {
        storageMutex.withLock {
            database.withTransaction {
                val previous = dao.readHistory().toHistory()
                val periods = change(previous)
                val awards = TrackerDomain.reconcileAwards(periods, previous.awards, today)
                persist(previous, periods, awards)
            }
        }
    }

    private suspend fun persist(previous: LocalHistory, periods: List<Period>, awards: List<Award>) {
        val oldPeriods = previous.periods.associateBy { it.id }
        val changedPeriods = periods.filter { oldPeriods[it.id] != it }
        if (changedPeriods.isNotEmpty()) dao.upsertPeriods(changedPeriods.map { it.toEntity() })

        val thresholds = awards.mapTo(mutableSetOf()) { it.threshold }
        val removed = previous.awards.filter { it.threshold !in thresholds }.map { it.threshold }
        if (removed.isNotEmpty()) dao.deleteAwards(removed)

        val oldAwards = previous.awards.associateBy { it.threshold }
        val changedAwards = awards.filter { oldAwards[it.threshold] != it }
        if (changedAwards.isNotEmpty()) dao.upsertAwards(changedAwards.map { it.toEntity() })
    }
}

private fun List<PeriodWithAwards>.toHistory() = LocalHistory(
    periods = map { row ->
        Period(
            id = row.period.id,
            start = LocalDate.ofEpochDay(row.period.startEpochDay),
            endExclusive = row.period.endExclusiveEpochDay?.let(LocalDate::ofEpochDay),
        )
    },
    awards = flatMap { it.awards }.map { award ->
        Award(award.threshold, LocalDate.ofEpochDay(award.earnedEpochDay), award.periodId)
    }.sortedBy { it.threshold },
)

private fun Period.toEntity() = PeriodEntity(id, start.toEpochDay(), endExclusive?.toEpochDay())
private fun Award.toEntity() = AwardEntity(threshold, date.toEpochDay(), periodId)
