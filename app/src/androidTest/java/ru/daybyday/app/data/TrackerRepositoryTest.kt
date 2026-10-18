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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TrackerRepositoryTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val today = LocalDate.of(2026, 10, 18)
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
    fun historyAndSettingsRemainAvailableToNewRepository() = runBlocking {
        repository.initialize(today.minusDays(14), today)
        val expectedSettings = AppSettings(
            darkTheme = false,
            dailyEnabled = true,
            achievementEnabled = true,
            recordEnabled = true,
            hour = 9,
            minute = 35,
            privateNotifications = false,
        )
        repository.settings.update(expectedSettings)

        val reopened = TrackerRepository(context)
        val history = reopened.readHistory()
        assertEquals(today.minusDays(14), history.periods.single().start)
        assertEquals(listOf(1L, 3L, 7L, 14L), history.awards.map { it.threshold })
        assertEquals(today, history.awards.single { it.threshold == 14L }.date)
        assertEquals(expectedSettings, reopened.settings.settings.first())
        assertEquals(history, reopened.history.first())
    }

    @Test
    fun synchronizationAcrossMidnightIsIdempotent() = runBlocking {
        repository.initialize(today, today)
        assertTrue(repository.readHistory().awards.isEmpty())

        val newAwards = repository.synchronize(today.plusDays(1))
        assertEquals(listOf(1L), newAwards.map { it.threshold })
        assertEquals(today.plusDays(1), newAwards.single().date)
        assertTrue(repository.synchronize(today.plusDays(1)).isEmpty())
        assertEquals(1, TrackerRepository(context).readHistory().awards.size)
    }

    @Test
    fun correctingCurrentDateRecalculatesItsAwards() = runBlocking {
        repository.initialize(today.minusDays(15), today)
        assertTrue(repository.readHistory().awards.any { it.threshold == 14L })

        repository.editStart(today.minusDays(3), today)

        val corrected = repository.readHistory()
        assertEquals(today.minusDays(3), corrected.periods.single().start)
        assertEquals(listOf(1L, 3L), corrected.awards.map { it.threshold })
        assertEquals(today, corrected.awards.single { it.threshold == 3L }.date)
    }

    @Test
    fun newPeriodPreservesHistoricalAwardsAndDoesNotOverlap() = runBlocking {
        repository.initialize(today.minusDays(17), today)
        val originalAwards = repository.readHistory().awards
        repository.startNewPeriod(today.minusDays(2), today.minusDays(1), today)

        val history = repository.readHistory()
        assertEquals(2, history.periods.size)
        assertEquals(today.minusDays(1), history.periods.first().endExclusive)
        assertEquals(today.minusDays(1), history.periods.last().start)
        assertEquals(originalAwards, history.awards)

        // Editing a current date must never silently rewrite confirmed history.
        rejects { repository.editStart(today.minusDays(2), today) }
        assertEquals(history, repository.readHistory())
        rejects { repository.startNewPeriod(today.minusDays(2), today, today) }
        assertEquals(history, repository.readHistory())
    }

    @Test
    fun correctingNewPeriodKeepsAwardsEarnedInEarlierHistory() = runBlocking {
        repository.initialize(today.minusDays(40), today)
        repository.startNewPeriod(today.minusDays(10), today.minusDays(9), today)
        val historicalAwards = repository.readHistory().awards
        repository.editStart(today.minusDays(1), today)

        assertEquals(historicalAwards, repository.readHistory().awards)
        assertEquals(2, repository.readHistory().periods.size)
    }

    @Test
    fun invalidFutureInitializationDoesNotLeavePartialData() = runBlocking {
        rejects { repository.initialize(today.plusDays(1), today) }
        assertEquals(LocalHistory(), repository.readHistory())
        repository.initialize(today, today)
        assertEquals(1, repository.readHistory().periods.size)
    }

    @Test
    fun resetCannotBeUndoneByConcurrentDateSynchronization() = runBlocking {
        repository.initialize(today.minusDays(1000), today)
        repository.settings.update { it.copy(dailyEnabled = true, darkTheme = false) }

        coroutineScope {
            launch { repeat(10) { repository.synchronize(today.plusDays(1)) } }
            launch { repository.clear() }
        }

        val reopened = TrackerRepository(context)
        assertEquals(LocalHistory(), reopened.readHistory())
        assertEquals(AppSettings(), reopened.settings.settings.first())
        assertTrue(reopened.synchronize(today.plusDays(1)).isEmpty())
        assertFalse(reopened.settings.settings.first().dailyEnabled)
    }

    private suspend fun rejects(block: suspend () -> Unit) {
        var rejected = false
        try {
            block()
        } catch (_: IllegalArgumentException) {
            rejected = true
        }
        assertTrue("Invalid dates must be rejected", rejected)
    }
}
