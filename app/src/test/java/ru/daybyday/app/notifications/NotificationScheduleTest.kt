package ru.daybyday.app.notifications

import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Test
import ru.daybyday.app.data.AppSettings

class NotificationScheduleTest {
    @Test fun dailyReminderUsesChosenLocalTime() {
        val now = ZonedDateTime.of(2026, 10, 18, 19, 45, 0, 0, ZoneId.of("Europe/Moscow"))
        val settings = AppSettings(dailyEnabled = true, hour = 20, minute = 0)
        assertEquals(Duration.ofMinutes(15).toMillis(), NotificationScheduler.nextDelayMillis(settings, now))
    }

    @Test fun aReminderAtTheCurrentTimeSchedulesTomorrowWithoutRepeating() {
        val now = ZonedDateTime.of(2026, 10, 18, 20, 0, 0, 0, ZoneId.of("Europe/Moscow"))
        val settings = AppSettings(dailyEnabled = true, hour = 20, minute = 0)
        assertEquals(Duration.ofHours(24).toMillis(), NotificationScheduler.nextDelayMillis(settings, now))
    }

    @Test fun progressOnlyChecksRunAfterLocalMidnight() {
        val now = ZonedDateTime.of(2026, 10, 18, 23, 59, 0, 0, ZoneId.of("Asia/Tokyo"))
        assertEquals(Duration.ofMinutes(2).toMillis(), NotificationScheduler.nextDelayMillis(AppSettings(achievementEnabled = true), now))
    }

    @Test fun springClockChangeKeepsWallClockTimeInsteadOfAdding24Hours() {
        val now = ZonedDateTime.of(2026, 3, 28, 20, 0, 0, 0, ZoneId.of("Europe/Berlin"))
        val settings = AppSettings(dailyEnabled = true, hour = 20, minute = 0)
        assertEquals(Duration.ofHours(23).toMillis(), NotificationScheduler.nextDelayMillis(settings, now))
    }

    @Test fun autumnClockChangeKeepsWallClockTimeInsteadOfAdding24Hours() {
        val now = ZonedDateTime.of(2026, 10, 24, 20, 0, 0, 0, ZoneId.of("Europe/Berlin"))
        val settings = AppSettings(dailyEnabled = true, hour = 20, minute = 0)
        assertEquals(Duration.ofHours(25).toMillis(), NotificationScheduler.nextDelayMillis(settings, now))
    }

    @Test fun nonexistentSpringTimeUsesPlatformZoneGapRules() {
        val now = ZonedDateTime.of(2026, 3, 29, 0, 0, 0, 0, ZoneId.of("Europe/Berlin"))
        val settings = AppSettings(dailyEnabled = true, hour = 2, minute = 30)
        assertEquals(Duration.ofMinutes(150).toMillis(), NotificationScheduler.nextDelayMillis(settings, now))
    }
}
