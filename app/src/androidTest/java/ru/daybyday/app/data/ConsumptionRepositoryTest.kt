package ru.daybyday.app.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.LocalDate
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ConsumptionRepositoryTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val today = LocalDate.of(2026, 11, 9)
    private lateinit var repository: TrackerRepository

    @Before
    fun prepare() = runBlocking {
        repository = TrackerRepository(context)
        repository.clear()
    }

    @After
    fun cleanUp() = runBlocking {
        repository.clear()
    }

    @Test
    fun markingTodayExcludesEventDayAndPersistsSuccessorAndOriginalAwards() = runBlocking {
        repository.initialize(today.minusDays(5), today)
        val originalAwards = repository.readHistory().awards
        val event = repository.recordConsumption(today, today)

        val history = TrackerRepository(context).history.first()
        assertEquals(2, history.periods.size)
        val previous = history.periods.single { it.id == event.previousPeriodId }
        val next = history.periods.single { it.id == event.nextPeriodId }
        assertEquals(today, previous.endExclusive)
        assertEquals(today.plusDays(1), next.start)
        assertEquals(null, next.endExclusive)
        assertEquals(listOf(event), history.consumptionEvents)
        assertEquals(originalAwards, history.awards)
    }

    @Test
    fun duplicateTodayReturnsSameMarkerEvenWhenActivePeriodStartsTomorrow() = runBlocking {
        repository.initialize(today.minusDays(5), today)
        val event = repository.recordConsumption(today, today)
        val before = repository.readHistory()

        assertEquals(event, repository.recordConsumption(today, today))

        assertEquals(before, repository.readHistory())
        assertEquals(1, repository.readHistory().consumptionEvents.size)
    }

    @Test
    fun syntheticLegacyAttachmentPreservesCoverageAnchorAcrossReopeningAndEdits() = runBlocking {
        val originalStart = today.minusDays(5)
        repository.initialize(originalStart, today)
        val originalAwards = repository.readHistory().awards
        val event = repository.recordConsumption(originalStart.minusDays(1), today)
        assertEquals(originalStart, event.previousCoverageStart)

        val reopened = TrackerRepository(context)
        assertEquals(event, reopened.readHistory().consumptionEvents.single())
        reopened.editConsumption(event.id, originalStart.plusDays(2), today)
        val changed = TrackerRepository(context).readHistory()
        assertEquals(originalStart, changed.consumptionEvents.single().previousCoverageStart)
        assertEquals(originalStart, changed.periods.single { it.id == event.previousPeriodId }.start)

        reopened.editConsumption(event.id, event.date, today)
        val restored = TrackerRepository(context).readHistory()
        val synthetic = restored.periods.single { it.id == event.previousPeriodId }
        assertEquals(event.date, synthetic.start)
        assertEquals(event.date, synthetic.endExclusive)
        assertEquals(originalStart, restored.periods.single { it.id == event.nextPeriodId }.start)
        assertEquals(originalAwards, restored.awards)
    }

    @Test
    fun editingMarkerChangesBothBoundariesAndRetainsAwardDates() = runBlocking {
        repository.initialize(today.minusDays(10), today)
        val event = repository.recordConsumption(today, today)
        val before = repository.readHistory()
        val changedDate = today.minusDays(8)

        repository.editConsumption(event.id, changedDate, today)

        val after = TrackerRepository(context).readHistory()
        assertEquals(before.periods.map { it.id }.toSet(), after.periods.map { it.id }.toSet())
        assertEquals(changedDate, after.periods.single { it.id == event.previousPeriodId }.endExclusive)
        assertEquals(changedDate.plusDays(1), after.periods.single { it.id == event.nextPeriodId }.start)
        assertEquals(event.copy(date = changedDate), after.consumptionEvents.single())
        assertEquals(before.awards, after.awards)
    }

    @Test
    fun adjacentEventsCanLeaveAnInternalZeroLengthPeriod() = runBlocking {
        repository.initialize(today.minusDays(5), today)
        val first = repository.recordConsumption(today.minusDays(2), today)
        val second = repository.recordConsumption(today, today)
        repository.editConsumption(first.id, today.minusDays(1), today)

        val history = repository.readHistory()
        assertEquals(2, history.consumptionEvents.size)
        assertEquals(first.nextPeriodId, second.previousPeriodId)
        val internal = history.periods.single { it.id == first.nextPeriodId }
        assertEquals(today, internal.start)
        assertEquals(today, internal.endExclusive)
    }

    @Test
    fun conflictingOrOutOfBoundsEditsRollBackAllLinkedRows() = runBlocking {
        repository.initialize(today.minusDays(5), today)
        val first = repository.recordConsumption(today.minusDays(2), today)
        val second = repository.recordConsumption(today, today)
        val before = repository.readHistory()

        rejects { repository.editConsumption(first.id, today, today) }
        assertEquals(before, repository.readHistory())
        rejects { repository.editConsumption(second.id, today.minusDays(4), today) }
        assertEquals(before, repository.readHistory())
    }

    @Test
    fun genericStartEditingCannotDetachAnEventLinkedCurrentPeriod() = runBlocking {
        repository.initialize(today.minusDays(5), today)
        repository.recordConsumption(today, today)
        val before = repository.readHistory()

        rejects { repository.editStart(today, today) }

        assertEquals(before, repository.readHistory())
    }

    @Test
    fun concurrentSynchronizationCannotRecreateConsumptionHistoryAfterReset() = runBlocking {
        repository.initialize(today.minusDays(40), today)
        repository.recordConsumption(today, today)

        coroutineScope {
            launch { repeat(10) { repository.synchronize(today.plusDays(1)) } }
            launch { repository.clear() }
        }

        assertEquals(LocalHistory(), repository.readHistory())
        assertTrue(repository.synchronize(today.plusDays(1)).isEmpty())
    }

    private suspend fun rejects(block: suspend () -> Unit) {
        var rejected = false
        try {
            block()
        } catch (_: IllegalArgumentException) {
            rejected = true
        }
        assertTrue("Invalid event changes must be rejected without partial writes", rejected)
    }
}
