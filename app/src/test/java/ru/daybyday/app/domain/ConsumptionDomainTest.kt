package ru.daybyday.app.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ConsumptionDomainTest {
    private val today = LocalDate.of(2026, 11, 9)
    private fun date(value: String) = LocalDate.parse(value)
    private fun current(start: String = "2026-10-18") = listOf(Period(1, date(start)))
    private fun period(change: ConsumptionChange, id: Long) = change.periods.single { it.id == id }
    private fun progress(change: ConsumptionChange, now: LocalDate = today) =
        TrackerDomain.snapshot(change.periods, emptyList(), now)

    @Test fun yesterdayClosesExactly21DaysAndExcludesTheConsumptionDate() {
        val change = ConsumptionDomain.record(current(), emptyList(), date("2026-11-08"), today, 2, 1)
        assertEquals(date("2026-11-08"), period(change, 1).endExclusive)
        assertEquals(date("2026-11-09"), period(change, 2).start)
        assertEquals(21L, TrackerDomain.completedDays(period(change, 1), today))
        assertEquals(0L, progress(change).completedDays)
        assertEquals(1L, progress(change).currentDay)
        assertEquals(21L, progress(change).bestDays)
        assertEquals(21L, progress(change).totalDays)
        assertEquals(1, progress(change).completedPeriods)
        assertEquals(DayStatus.UNKNOWN, TrackerDomain.calendarDay(date("2026-11-08"), change.periods, emptyList(), today).status)
    }

    @Test fun todayPreserves21DaysAndStartsTomorrowWithoutClaimingDayOne() {
        val change = ConsumptionDomain.record(current("2026-10-19"), emptyList(), today, today, 2, 1)
        assertEquals(today, period(change, 1).endExclusive)
        assertEquals(today.plusDays(1), period(change, 2).start)
        assertEquals(21L, progress(change).bestDays)
        assertEquals(21L, progress(change).totalDays)
        assertEquals(0L, progress(change).completedDays)
        assertEquals(0L, progress(change).currentDay)
    }

    @Test fun recordingOnFirstDayKeepsAnInternalZeroPeriodWithoutInventingProgress() {
        val change = ConsumptionDomain.record(current("2026-11-09"), emptyList(), today, today, 2, 1)
        assertEquals(today, period(change, 1).start)
        assertEquals(today, period(change, 1).endExclusive)
        assertEquals(0L, progress(change).totalDays)
        assertEquals(0L, progress(change).bestDays)
        assertEquals(0, progress(change).completedPeriods)
        ConsumptionDomain.validate(change.periods, change.events)
    }

    @Test fun consecutiveConsumptionDatesKeepBothMarkersAndNoEmptyPeriodStatistics() {
        val first = ConsumptionDomain.record(current("2026-11-08"), emptyList(), today.minusDays(1), today, 2, 1)
        val second = ConsumptionDomain.record(first.periods, first.events, today, today, 3, 2)
        assertEquals(2, second.events.size)
        assertEquals(3, second.periods.size)
        assertEquals(0L, progress(second).totalDays)
        assertEquals(0, progress(second).completedPeriods)
        assertEquals(today.plusDays(1), period(second, 3).start)
        assertEquals(0L, progress(second).currentDay)
    }

    @Test fun duplicateDateIsANoopBeforeActivePeriodOrNewIdChecks() {
        val first = ConsumptionDomain.record(current(), emptyList(), today, today, 2, 1)
        val allClosed = first.periods.map { if (it.endExclusive == null) it.copy(endExclusive = it.start) else it }
        val repeated = ConsumptionDomain.record(allClosed, first.events, today, today, 1, 1)
        assertSame(allClosed, repeated.periods)
        assertSame(first.events, repeated.events)
        assertEquals(first.event, repeated.event)
    }

    @Test fun duplicateStoredDateSurvivesAClockRollbackWithoutCreatingAnything() {
        val first = ConsumptionDomain.record(current(), emptyList(), today, today, 2, 1)
        val repeated = ConsumptionDomain.record(first.periods, first.events, today, today.minusDays(2), 1, 1)
        assertEquals(first, repeated)
    }

    @Test fun aNewFutureDateIsRejectedWithoutChangingInputs() {
        val initial = current()
        expectInvalid { ConsumptionDomain.record(initial, emptyList(), today.plusDays(1), today, 2, 1) }
        assertEquals(listOf(Period(1, date("2026-10-18"))), initial)
    }

    @Test fun newIdsMustBeUnusedWhenTheyCreateRows() {
        expectInvalid { ConsumptionDomain.record(current(), emptyList(), today, today, 1, 1) }
        val first = ConsumptionDomain.record(current(), emptyList(), today.minusDays(1), today, 2, 1)
        expectInvalid { ConsumptionDomain.record(first.periods, first.events, today, today, 3, 1) }
    }

    @Test fun arbitraryHistoricalInsertionBeforeTheAllowedBoundaryIsRejected() {
        expectInvalid { ConsumptionDomain.record(current(), emptyList(), date("2026-10-16"), today, 2, 1) }
        val first = ConsumptionDomain.record(current(), emptyList(), date("2026-11-07"), today, 2, 1)
        expectInvalid { ConsumptionDomain.record(first.periods, first.events, date("2026-11-06"), today, 3, 2) }
    }

    @Test fun minimumDateIsThePreStartDayOnlyForAnUnlinkedCurrentPeriod() {
        assertEquals(date("2026-10-17"), ConsumptionDomain.minimumRecordDate(current(), emptyList()))
        val change = ConsumptionDomain.record(current(), emptyList(), today, today, 2, 1)
        assertEquals(today.plusDays(1), ConsumptionDomain.minimumRecordDate(change.periods, change.events))
        assertNull(ConsumptionDomain.minimumRecordDate(emptyList(), emptyList()))
    }

    @Test fun anEarlierEventEditOnlyChangesItsTwoLinkedPeriods() {
        val archived = Period(10, date("2025-01-01"), date("2025-01-05"))
        val first = ConsumptionDomain.record(listOf(archived) + current(), emptyList(), date("2026-10-25"), today, 2, 1)
        val second = ConsumptionDomain.record(first.periods, first.events, date("2026-11-01"), today, 3, 2)
        val changed = ConsumptionDomain.edit(second.periods, second.events, 1, date("2026-10-27"), today)
        assertEquals(date("2026-10-27"), period(changed, 1).endExclusive)
        assertEquals(date("2026-10-28"), period(changed, 2).start)
        assertEquals(date("2026-11-01"), period(changed, 2).endExclusive)
        assertSame(archived, period(changed, 10))
        assertSame(period(second, 3), period(changed, 3))
        assertSame(second.events.single { it.id == 2L }, changed.events.single { it.id == 2L })
        assertEquals(setOf(1L, 2L, 3L, 10L), changed.periods.map { it.id }.toSet())
    }

    @Test fun editingOldDatesCannotCrossAdjacentMarkersOrDuplicateAnotherDate() {
        val first = ConsumptionDomain.record(current(), emptyList(), date("2026-10-25"), today, 2, 1)
        val second = ConsumptionDomain.record(first.periods, first.events, date("2026-11-01"), today, 3, 2)
        assertEquals(
            ConsumptionDateBounds(date("2026-10-18"), date("2026-10-31")),
            ConsumptionDomain.bounds(second.periods, second.events, 1, today),
        )
        expectInvalid { ConsumptionDomain.edit(second.periods, second.events, 1, date("2026-11-02"), today) }
        expectInvalid { ConsumptionDomain.edit(second.periods, second.events, 1, date("2026-11-01"), today) }
        expectInvalid { ConsumptionDomain.edit(second.periods, second.events, 2, date("2026-10-24"), today) }
        expectInvalid { ConsumptionDomain.edit(second.periods, second.events, 2, today.plusDays(1), today) }
    }

    @Test fun adjacentEditBoundsPermitEmptyIntervalsButNeverOverlappingDates() {
        val first = ConsumptionDomain.record(current(), emptyList(), date("2026-10-25"), today, 2, 1)
        val second = ConsumptionDomain.record(first.periods, first.events, date("2026-11-01"), today, 3, 2)
        val moved = ConsumptionDomain.edit(second.periods, second.events, 1, date("2026-10-31"), today)
        assertEquals(date("2026-11-01"), period(moved, 2).start)
        assertEquals(date("2026-11-01"), period(moved, 2).endExclusive)
        ConsumptionDomain.validate(moved.periods, moved.events)
        val earliest = ConsumptionDomain.edit(moved.periods, moved.events, 1, date("2026-10-18"), today)
        assertEquals(0L, TrackerDomain.completedDays(period(earliest, 1), today))
        ConsumptionDomain.validate(earliest.periods, earliest.events)
    }

    @Test fun correctingTodayToYesterdayStartsTodayWithZeroCompletedDays() {
        val first = ConsumptionDomain.record(current("2026-10-19"), emptyList(), today, today, 2, 1)
        val edited = ConsumptionDomain.edit(first.periods, first.events, 1, today.minusDays(1), today)
        assertEquals(today, period(edited, 2).start)
        assertEquals(0L, progress(edited).completedDays)
        assertEquals(1L, progress(edited).currentDay)
    }

    @Test fun preStartAttachmentKeepsTheExistingActiveIdentityAndUsesAnEmptyPrevious() {
        val active = Period(1, today)
        val change = ConsumptionDomain.record(listOf(active), emptyList(), today.minusDays(1), today, 2, 1)
        assertSame(active, period(change, 1))
        assertEquals(1L, change.event.nextPeriodId)
        assertEquals(2L, change.event.previousPeriodId)
        assertEquals(today, change.event.previousCoverageStart)
        assertEquals(today.minusDays(1), period(change, 2).start)
        assertEquals(today.minusDays(1), period(change, 2).endExclusive)
        assertEquals(0L, progress(change).totalDays)
    }

    @Test fun syntheticEightToNineCorrectionDoesNotInventTheEighthAsSoberHistory() {
        val first = ConsumptionDomain.record(current("2026-11-09"), emptyList(), date("2026-11-08"), today, 2, 1)
        val edited = ConsumptionDomain.edit(first.periods, first.events, 1, date("2026-11-09"), today)
        assertEquals(today, period(edited, 2).start)
        assertEquals(today, period(edited, 2).endExclusive)
        assertEquals(today.plusDays(1), period(edited, 1).start)
        assertEquals(0L, progress(edited).totalDays)
        assertEquals(DayStatus.UNKNOWN, TrackerDomain.calendarDay(date("2026-11-08"), edited.periods, emptyList(), today).status)
    }

    @Test fun syntheticCoverageAnchorAllowsMovingForwardAndBackWithoutCoverageDrift() {
        val now = date("2026-11-11")
        val initial = ConsumptionDomain.record(current("2026-11-09"), emptyList(), date("2026-11-08"), now, 2, 1)
        val forward = ConsumptionDomain.edit(initial.periods, initial.events, 1, date("2026-11-10"), now)
        assertEquals(date("2026-11-09"), period(forward, 2).start)
        assertEquals(date("2026-11-10"), period(forward, 2).endExclusive)
        assertEquals(1L, TrackerDomain.completedDays(period(forward, 2), now))
        assertEquals(date("2026-11-08"), ConsumptionDomain.bounds(forward.periods, forward.events, 1, now).minDate)
        expectInvalid { ConsumptionDomain.edit(forward.periods, forward.events, 1, date("2026-11-07"), now) }
        val back = ConsumptionDomain.edit(forward.periods, forward.events, 1, date("2026-11-08"), now)
        assertEquals(initial.periods, back.periods)
        assertEquals(initial.events, back.events)
    }

    @Test fun attachmentDoesNotExtendAnUnknownGapOrChangeUnrelatedHistory() {
        val archived = Period(10, date("2026-10-01"), date("2026-10-04"))
        val active = Period(1, today)
        val change = ConsumptionDomain.record(listOf(archived, active), emptyList(), today.minusDays(1), today, 2, 1)
        assertSame(archived, period(change, 10))
        assertSame(active, period(change, 1))
        assertEquals(3L, progress(change).totalDays)
        assertEquals(DayStatus.UNKNOWN, TrackerDomain.calendarDay(date("2026-11-07"), change.periods, emptyList(), today).status)
        assertEquals(0L, TrackerDomain.completedDays(period(change, 2), today))
    }

    @Test fun attachmentReusesAClosedPeriodEndingAtTheConsumptionDate() {
        val archived = Period(10, date("2026-11-01"), date("2026-11-08"))
        val active = Period(1, today)
        val change = ConsumptionDomain.record(listOf(archived, active), emptyList(), date("2026-11-08"), today, 2, 1)
        assertEquals(2, change.periods.size)
        assertEquals(archived, period(change, 10))
        assertSame(active, period(change, 1))
        assertEquals(10L, change.event.previousPeriodId)
        assertNull(change.event.previousCoverageStart)
        assertEquals(7L, progress(change).totalDays)
    }

    @Test fun attachmentShortensAContiguousLegacyPeriodSoTheConsumptionDateIsExcluded() {
        val archived = Period(10, date("2026-11-01"), today)
        val active = Period(1, today)
        val change = ConsumptionDomain.record(listOf(archived, active), emptyList(), today.minusDays(1), today, 2, 1)
        assertEquals(2, change.periods.size)
        assertEquals(today.minusDays(1), period(change, 10).endExclusive)
        assertSame(active, period(change, 1))
        assertEquals(7L, progress(change).totalDays)
        assertEquals(7L, progress(change).bestDays)
        assertNull(change.event.previousCoverageStart)
    }

    @Test fun aOneDayContiguousLegacyPeriodCanBecomeZeroWithoutLosingItsId() {
        val archived = Period(10, today.minusDays(1), today)
        val change = ConsumptionDomain.record(listOf(archived, Period(1, today)), emptyList(), today.minusDays(1), today, 2, 1)
        assertEquals(today.minusDays(1), period(change, 10).start)
        assertEquals(today.minusDays(1), period(change, 10).endExclusive)
        assertEquals(0, progress(change).completedPeriods)
        assertEquals(0L, progress(change).totalDays)
    }

    @Test fun aPreStartMarkerDoesNotResetAnExistingPositiveCurrentCount() {
        val active = Period(1, date("2026-10-19"))
        val change = ConsumptionDomain.record(listOf(active), emptyList(), date("2026-10-18"), today, 2, 1)
        assertSame(active, period(change, 1))
        assertEquals(21L, progress(change).completedDays)
        assertEquals(22L, progress(change).currentDay)
        assertEquals(21L, progress(change).totalDays)
    }

    @Test fun noOpEditsRetainTheOriginalObjectsWhenTheDateIsAllowed() {
        val first = ConsumptionDomain.record(current(), emptyList(), today, today, 2, 1)
        val repeated = ConsumptionDomain.edit(first.periods, first.events, 1, today, today)
        assertSame(first.periods, repeated.periods)
        assertSame(first.events, repeated.events)
        assertEquals(first.event, repeated.event)
    }

    @Test fun missingRowsBrokenLinksAndDuplicateLedgerIdsAreRejected() {
        val first = ConsumptionDomain.record(current(), emptyList(), today, today, 2, 1)
        expectInvalid { ConsumptionDomain.validate(first.periods, listOf(first.event.copy(previousPeriodId = 99))) }
        expectInvalid { ConsumptionDomain.validate(first.periods, listOf(first.event.copy(nextPeriodId = 99))) }
        expectInvalid { ConsumptionDomain.validate(first.periods, listOf(first.event.copy(date = today.minusDays(1)))) }
        expectInvalid { ConsumptionDomain.validate(first.periods, first.events + first.event) }
        expectInvalid { ConsumptionDomain.validate(first.periods + first.periods.first(), first.events) }
        expectInvalid { ConsumptionDomain.edit(first.periods, first.events, 99, today, today) }
        expectInvalid { ConsumptionDomain.bounds(first.periods, first.events, 99, today) }
    }

    @Test fun overlappingIntervalsAndForgedSyntheticAnchorsAreRejected() {
        expectInvalid { ConsumptionDomain.validate(listOf(Period(1, date("2026-10-18")), Period(2, date("2026-11-01"))), emptyList()) }
        expectInvalid { ConsumptionDomain.validate(listOf(Period(1, today, today.minusDays(1))), emptyList()) }
        val first = ConsumptionDomain.record(current("2026-11-09"), emptyList(), today.minusDays(1), today, 2, 1)
        expectInvalid {
            ConsumptionDomain.validate(
                first.periods,
                listOf(first.event.copy(previousCoverageStart = today.plusDays(2))),
            )
        }
    }

    @Test fun clockRollbackLeavesStoredEventsReadableAndReturnsEmptyBounds() {
        val first = ConsumptionDomain.record(current("2026-11-09"), emptyList(), today.minusDays(1), today, 2, 1)
        val rolledBackToday = today.minusDays(2)
        val allowed = ConsumptionDomain.bounds(first.periods, first.events, 1, rolledBackToday)
        assertEquals(today.minusDays(1), allowed.minDate)
        assertEquals(rolledBackToday, allowed.maxDate)
        assertTrue(allowed.minDate.isAfter(allowed.maxDate))
        ConsumptionDomain.validate(first.periods, first.events)
        expectInvalid { ConsumptionDomain.edit(first.periods, first.events, 1, rolledBackToday, rolledBackToday) }
        expectInvalid { ConsumptionDomain.edit(first.periods, first.events, 1, first.event.date, rolledBackToday) }
        assertEquals(today.minusDays(1), first.event.date)
        assertEquals(0L, progress(first, rolledBackToday).completedDays)
        assertEquals(0L, progress(first, rolledBackToday).currentDay)
    }

    @Test fun neitherAnActivePeriodNorPriorDataIsInventedWhenThereIsNoCurrentPeriod() {
        assertNull(ConsumptionDomain.minimumRecordDate(emptyList(), emptyList()))
        expectInvalid { ConsumptionDomain.record(emptyList(), emptyList(), today, today, 1, 1) }
        val closed = listOf(Period(1, date("2026-10-18"), date("2026-11-01")))
        assertNull(ConsumptionDomain.minimumRecordDate(closed, emptyList()))
        expectInvalid { ConsumptionDomain.record(closed, emptyList(), today, today, 2, 1) }
    }

    @Test fun calendarConsumptionTakesPriorityAndRetainsOriginalAwardReceiptDates() {
        val change = ConsumptionDomain.record(current("2026-10-19"), emptyList(), today, today, 2, 1)
        val received = Award(21, today, 1)
        val marked = TrackerDomain.calendarDay(today, change.periods, listOf(received), today, change.events)
        assertEquals(DayStatus.CONSUMPTION, marked.status)
        assertEquals(change.event, marked.event)
        assertEquals(listOf(received), marked.awards)
        val clockRollback = TrackerDomain.calendarDay(today, change.periods, listOf(received), today.minusDays(1), change.events)
        assertEquals(DayStatus.CONSUMPTION, clockRollback.status)
        assertEquals(listOf(received), clockRollback.awards)
        assertEquals(DayStatus.UNKNOWN, TrackerDomain.calendarDay(today, change.periods, listOf(received), today).status)
        assertEquals(DayStatus.CONFIRMED, TrackerDomain.calendarDay(today.minusDays(1), change.periods, emptyList(), today, change.events).status)
        assertEquals(DayStatus.FUTURE, TrackerDomain.calendarDay(today.plusDays(1), change.periods, emptyList(), today, change.events).status)
    }

    private fun expectInvalid(action: () -> Unit) {
        try {
            action()
            throw AssertionError("Expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
            // Reject before replacing any source-of-truth rows.
        }
    }
}
