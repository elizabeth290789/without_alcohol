package ru.daybyday.app.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackerDomainTest {
    private val start = LocalDate.of(2026, 10, 18)
    private fun active(date: LocalDate = start) = listOf(Period(1, date))
    private fun progress(days: Long): Progress {
        val today = start.plusDays(days)
        val periods = active()
        return TrackerDomain.snapshot(periods, TrackerDomain.reconcileAwards(periods, emptyList(), today), today)
    }

    @Test fun firstCalendarDayContainsNoCompletedDays() {
        val value = progress(0)
        assertEquals(0L, value.completedDays)
        assertEquals(1L, value.currentDay)
        assertEquals(0L, value.totalDays)
        assertEquals(0, value.unlockedCount)
        assertEquals(1L, value.daysToNext)
    }

    @Test fun nextDateCompletesFirstDayAndUnlocksItsAward() {
        val today = start.plusDays(1)
        val awards = TrackerDomain.reconcileAwards(active(), emptyList(), today)
        assertEquals(listOf(Award(1, today, 1)), awards)
        val value = TrackerDomain.snapshot(active(), awards, today)
        assertEquals(1L, value.completedDays)
        assertEquals(2L, value.currentDay)
    }

    @Test fun datesCrossMonthYearAndLeapDayWithoutElapsedTime() {
        val cases = listOf(
            Triple("2026-10-31", "2026-11-01", 1L),
            Triple("2026-12-31", "2027-01-01", 1L),
            Triple("2024-02-28", "2024-03-01", 2L),
            Triple("2025-02-28", "2025-03-01", 1L),
            Triple("2024-01-31", "2024-03-01", 30L),
        )
        for ((from, to, expected) in cases) {
            assertEquals(expected, TrackerDomain.completedDays(Period(1, LocalDate.parse(from)), LocalDate.parse(to)))
        }
    }

    @Test fun daylightSavingDoesNotChangeCalendarDayCount() {
        val zone = ZoneId.of("Europe/Berlin")
        val from = Instant.parse("2026-03-28T23:30:00Z").atZone(zone).toLocalDate()
        val to = Instant.parse("2026-03-29T22:30:00Z").atZone(zone).toLocalDate()
        assertEquals(1L, TrackerDomain.completedDays(Period(1, from), to))
    }

    @Test fun zoneChangesUseTheCurrentLocalDate() {
        val instant = Instant.parse("2026-10-18T22:30:00Z")
        val utcDate = instant.atZone(ZoneId.of("UTC")).toLocalDate()
        val moscowDate = instant.atZone(ZoneId.of("Europe/Moscow")).toLocalDate()
        assertEquals(0L, TrackerDomain.completedDays(Period(1, start), utcDate))
        assertEquals(1L, TrackerDomain.completedDays(Period(1, start), moscowDate))
    }

    @Test fun clockRollbackClampsCountsAndPreservesEarnedAwards() {
        val awarded = TrackerDomain.reconcileAwards(active(), emptyList(), start.plusDays(7))
        val rollback = start.minusDays(2)
        val saved = TrackerDomain.reconcileAwards(active(), awarded, rollback)
        val value = TrackerDomain.snapshot(active(), saved, rollback)
        assertEquals(0L, value.completedDays)
        assertEquals(0L, value.currentDay)
        assertEquals(awarded, saved)
        assertEquals(3, value.unlockedCount)
    }

    @Test fun reopeningAfterLongAbsenceBackfillsEveryReachedMilestone() {
        val today = start.plusDays(1000)
        val awards = TrackerDomain.reconcileAwards(active(), emptyList(), today)
        assertEquals(16, awards.size)
        assertEquals(TrackerDomain.achievements.map { it.threshold }, awards.map { it.threshold })
        awards.forEach { assertEquals(start.plusDays(it.threshold), it.date) }
        assertNull(TrackerDomain.snapshot(active(), awards, today).nextAchievement)
    }

    @Test fun monthlyMilestonesUseFixedDayThresholds() {
        val january = LocalDate.of(2026, 1, 1)
        val periods = active(january)
        val day29 = TrackerDomain.reconcileAwards(periods, emptyList(), january.plusDays(29))
        assertFalse(day29.any { it.threshold == 30L })
        val day30 = TrackerDomain.reconcileAwards(periods, day29, january.plusDays(30))
        assertEquals(january.plusDays(30), day30.single { it.threshold == 30L }.date)
    }

    @Test fun repeatedRefreshDoesNotIssueDuplicateAwards() {
        val today = start.plusDays(100)
        val first = TrackerDomain.reconcileAwards(active(), emptyList(), today)
        val second = TrackerDomain.reconcileAwards(active(), first, today)
        assertEquals(first, second)
        assertEquals(second.size, second.map { it.threshold }.toSet().size)
    }

    @Test fun everyLevelBoundaryIsInclusiveAndChangesAtTheRightDate() {
        TrackerDomain.levels.forEach { level ->
            assertEquals(level, progress(level.minDays).level)
            level.maxDays?.let { assertEquals(level, progress(it).level) }
        }
        assertEquals(1f, progress(1000).levelProgress, 0f)
        assertEquals(0f, progress(7).levelProgress, 0f)
    }

    @Test fun russianDeclensionHandlesTeensHundredsAndZero() {
        val expected = mapOf(
            0L to "дней", 1L to "день", 2L to "дня", 4L to "дня", 5L to "дней",
            11L to "дней", 12L to "дней", 14L to "дней", 21L to "день",
            22L to "дня", 25L to "дней", 101L to "день", 111L to "дней",
            112L to "дней", 114L to "дней", 121L to "день", 1000L to "дней",
        )
        expected.forEach { (count, word) -> assertEquals(word, TrackerDomain.dayWord(count)) }
    }

    @Test fun rolloverKeepsHistoryAndAwardsAndDoesNotCountTheNewFirstDay() {
        val today = start.plusDays(10)
        val original = active()
        val originalAwards = TrackerDomain.reconcileAwards(original, emptyList(), today)
        val periods = TrackerDomain.startNewPeriod(original, today.minusDays(1), today, today, 2)
        val awards = TrackerDomain.reconcileAwards(periods, originalAwards, today)
        val value = TrackerDomain.snapshot(periods, awards, today)
        assertEquals(start.plusDays(10), periods.first().endExclusive)
        assertEquals(originalAwards, awards)
        assertEquals(0L, value.completedDays)
        assertEquals(1L, value.currentDay)
        assertEquals(10L, value.bestDays)
        assertEquals(10L, value.totalDays)
        assertEquals(1, value.completedPeriods)
        assertEquals(14L, value.nextAchievement?.threshold)
    }

    @Test fun gapsAreNotInventedAndAdjacentPeriodsAreNotDoubleCounted() {
        val today = start.plusDays(20)
        val adjacent = listOf(Period(1, start, start.plusDays(7)), Period(2, start.plusDays(7)))
        assertEquals(20L, TrackerDomain.snapshot(adjacent, emptyList(), today).totalDays)
        val gaps = listOf(Period(1, start, start.plusDays(7)), Period(2, start.plusDays(10)))
        assertEquals(17L, TrackerDomain.snapshot(gaps, emptyList(), today).totalDays)
        assertEquals(DayStatus.UNKNOWN, TrackerDomain.calendarDay(start.plusDays(8), gaps, emptyList(), today).status)
    }

    @Test fun calendarDistinguishesConfirmedCurrentUnknownAndFutureDates() {
        val today = start.plusDays(7)
        val periods = active()
        val awards = TrackerDomain.reconcileAwards(periods, emptyList(), today)
        assertEquals(DayStatus.UNKNOWN, TrackerDomain.calendarDay(start.minusDays(1), periods, awards, today).status)
        assertEquals(DayStatus.CONFIRMED, TrackerDomain.calendarDay(start, periods, awards, today).status)
        assertEquals(DayStatus.CURRENT, TrackerDomain.calendarDay(today, periods, awards, today).status)
        assertEquals(DayStatus.FUTURE, TrackerDomain.calendarDay(today.plusDays(1), periods, awards, today).status)
        assertEquals(listOf(Award(7, today, 1)), TrackerDomain.calendarDay(today, periods, awards, today).awards)
    }

    @Test fun editingStartRecomputesOnlyTheEditedPeriodsAwardProvenance() {
        val today = start.plusDays(50)
        val periods = listOf(Period(1, start, start.plusDays(20)), Period(2, start.plusDays(20)))
        val awards = TrackerDomain.reconcileAwards(periods, emptyList(), today)
        assertEquals(2L, awards.single { it.threshold == 30L }.periodId)
        val edited = TrackerDomain.editStart(periods, today.minusDays(5), today)
        val corrected = TrackerDomain.reconcileAwards(edited, awards, today, setOf(2))
        assertTrue(corrected.none { it.threshold >= 21 })
        assertEquals(awards.filter { it.periodId == 1L }, corrected)
        assertEquals(25L, TrackerDomain.snapshot(edited, corrected, today).totalDays)
    }

    @Test fun correctingEarlierStartAddsMissingAwardsWithTheirTrueDates() {
        val today = start.plusDays(30)
        val original = active(start.plusDays(25))
        val originalAwards = TrackerDomain.reconcileAwards(original, emptyList(), today)
        val edited = TrackerDomain.editStart(original, start, today)
        val corrected = TrackerDomain.reconcileAwards(edited, originalAwards, today, setOf(1))
        assertEquals(start.plusDays(1), corrected.first().date)
        assertEquals(30L, corrected.last().threshold)
    }

    @Test fun correctionReassignsAnAwardToAnotherEligibleHistoricalPeriod() {
        val today = start.plusDays(50)
        val periods = listOf(Period(1, start, start.plusDays(30)), Period(2, start.plusDays(30)))
        val awards = TrackerDomain.reconcileAwards(periods, emptyList(), today)
        val correctedHistory = listOf(periods[0].copy(endExclusive = start.plusDays(3)), periods[1])
        val result = TrackerDomain.reconcileAwards(correctedHistory, awards, today, setOf(1))
        assertEquals(2L, result.single { it.threshold == 7L }.periodId)
        assertEquals(2L, result.single { it.threshold == 14L }.periodId)
        assertFalse(result.any { it.threshold == 30L })
    }

    @Test fun futureStartIsRejected() {
        expectInvalid { TrackerDomain.startFirst(start.plusDays(1), start) }
        expectInvalid { TrackerDomain.editStart(active(), start.plusDays(1), start) }
    }

    @Test fun invalidAndOverlappingIntervalsAreRejected() {
        expectInvalid { TrackerDomain.validatePeriods(listOf(Period(1, start, start.minusDays(1)))) }
        expectInvalid { TrackerDomain.validatePeriods(listOf(Period(1, start), Period(2, start.plusDays(1)))) }
        expectInvalid {
            TrackerDomain.validatePeriods(listOf(Period(1, start, start.plusDays(7)), Period(2, start.plusDays(6))))
        }
        expectInvalid { TrackerDomain.validatePeriods(listOf(Period(1, start), Period(1, start.plusDays(9)))) }
        val history = listOf(Period(1, start, start.plusDays(7)), Period(2, start.plusDays(10)))
        expectInvalid { TrackerDomain.editStart(history, start.plusDays(6), start.plusDays(20)) }
    }

    @Test fun rolloverRejectsEndBeforeStartFutureStartAndSameDayOverlap() {
        expectInvalid { TrackerDomain.startNewPeriod(active(), start.minusDays(1), start, start, 2) }
        expectInvalid { TrackerDomain.startNewPeriod(active(), start, start, start, 2) }
        expectInvalid { TrackerDomain.startNewPeriod(active(), start, start.plusDays(1), start, 2) }
        expectInvalid { TrackerDomain.startNewPeriod(active(), start, start.plusDays(1), start.plusDays(1), 1) }
    }

    @Test fun largeHistoryStaysExactAndPreservesUniqueAwards() {
        val periods = (0L until 1000L).map { id ->
            val date = start.plusDays(id * 11)
            Period(id + 1, date, date.plusDays(10))
        }
        val today = start.plusDays(11000)
        val awards = TrackerDomain.reconcileAwards(periods, emptyList(), today)
        val value = TrackerDomain.snapshot(periods, awards, today)
        assertEquals(10000L, value.totalDays)
        assertEquals(10L, value.bestDays)
        assertEquals(1000, value.completedPeriods)
        assertEquals(3, awards.size)
        assertEquals(1L, awards.first().periodId)
    }

    private fun expectInvalid(action: () -> Unit) {
        try {
            action()
            throw AssertionError("Expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
            // Invalid user input is rejected before any persistent data is replaced.
        }
    }
}
