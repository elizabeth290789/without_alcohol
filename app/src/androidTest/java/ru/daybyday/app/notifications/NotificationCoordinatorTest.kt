package ru.daybyday.app.notifications

import android.app.NotificationManager
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import ru.daybyday.app.data.AppSettings
import ru.daybyday.app.data.TrackerRepository

@RunWith(AndroidJUnit4::class)
class NotificationCoordinatorTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val repository = TrackerRepository(context)
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val today = LocalDate.now()

    @Before fun prepare() = runBlocking {
        repository.clear()
        NotificationScheduler.cancel(context)
        if (Build.VERSION.SDK_INT >= 33) {
            ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(
                "pm grant ${context.packageName} android.permission.POST_NOTIFICATIONS",
            )).use { it.readBytes() }
        }
        Unit
    }

    @After fun cleanup() = runBlocking {
        repository.clear()
        NotificationScheduler.cancel(context)
    }

    @Test fun enablingDoesNotAnnounceHistoricalAchievementsOrRecords() = runBlocking {
        repository.initialize(today.minusDays(8), today)
        val settings = AppSettings(achievementEnabled = true, recordEnabled = true)
        repository.settings.update(settings)
        NotificationScheduler.schedule(context, settings, true)
        NotificationCoordinator.checkProgress(context, repository.readHistory().awards, settings)
        assertFalse(isPosted(101))
        assertFalse(isPosted(102))
    }

    @Test fun aNewAchievementIsPostedOnceAcrossRepeatedChecks() = runBlocking {
        repository.initialize(today.minusDays(8), today)
        val settings = AppSettings(achievementEnabled = true)
        repository.settings.update(settings)
        NotificationScheduler.schedule(context, settings, true)
        repository.editStart(today.minusDays(15), today)
        NotificationCoordinator.checkProgress(context, emptyList(), settings)
        awaitPosted(101)
        manager.cancel(101)
        awaitRemoved(101)
        NotificationCoordinator.checkProgress(context, repository.readHistory().awards, settings)
        assertFalse(isPosted(101))
    }

    @Test fun recordWatermarkPreventsDuplicateAnnouncements() = runBlocking {
        repository.initialize(today.minusDays(8), today)
        val settings = AppSettings(recordEnabled = true)
        repository.settings.update(settings)
        NotificationScheduler.schedule(context, settings, true)
        repository.editStart(today.minusDays(9), today)
        NotificationCoordinator.checkProgress(context, emptyList(), settings)
        awaitPosted(102)
        manager.cancel(102)
        awaitRemoved(102)
        NotificationCoordinator.checkProgress(context, emptyList(), settings)
        assertFalse(isPosted(102))
    }

    @Test fun aStaleCallerCannotPostAfterNotificationsWereDisabled() = runBlocking {
        repository.initialize(today.minusDays(8), today)
        val settings = AppSettings(achievementEnabled = true)
        repository.settings.update(settings)
        NotificationScheduler.schedule(context, settings, true)
        repository.editStart(today.minusDays(15), today)
        repository.settings.update(settings.copy(achievementEnabled = false))
        NotificationCoordinator.checkProgress(context, repository.readHistory().awards, settings)
        assertFalse(isPosted(101))
    }

    @Test fun correcting100DaysTo5AllowsTheNextRecordAndReearnedAward() = runBlocking {
        repository.initialize(today.minusDays(100), today)
        val settings = AppSettings(achievementEnabled = true, recordEnabled = true)
        repository.settings.update(settings)
        NotificationScheduler.schedule(context, settings, true)

        repository.editStart(today.minusDays(5), today)
        NotificationCoordinator.reconcileAfterDateCorrection(context)
        NotificationCoordinator.checkProgress(context, emptyList(), settings, today)
        assertFalse(isPosted(101))
        assertFalse(isPosted(102))

        val sixthDay = today.plusDays(1)
        NotificationCoordinator.checkProgress(context, repository.synchronize(sixthDay), settings, sixthDay)
        awaitPosted(102)
        assertFalse(isPosted(101))
        manager.cancel(102)
        awaitRemoved(102)

        val seventhDay = today.plusDays(2)
        NotificationCoordinator.checkProgress(context, repository.synchronize(seventhDay), settings, seventhDay)
        awaitPosted(101)
        awaitPosted(102)
        manager.cancel(101)
        manager.cancel(102)
        awaitRemoved(101)
        awaitRemoved(102)
        NotificationCoordinator.checkProgress(context, emptyList(), settings, seventhDay)
        assertFalse(isPosted(101))
        assertFalse(isPosted(102))
    }

    @Test fun correctingCurrentDatesRetainsHistoricalAwardAndRecordWatermarks() = runBlocking {
        repository.initialize(today.minusDays(200), today.minusDays(100))
        repository.startNewPeriod(today.minusDays(101), today.minusDays(100), today)
        val settings = AppSettings(achievementEnabled = true, recordEnabled = true)
        repository.settings.update(settings)
        NotificationScheduler.schedule(context, settings, true)
        repository.editStart(today.minusDays(5), today)
        NotificationCoordinator.reconcileAfterDateCorrection(context)

        val seventhDay = today.plusDays(2)
        NotificationCoordinator.checkProgress(context, repository.synchronize(seventhDay), settings, seventhDay)
        assertFalse(isPosted(101))
        assertFalse(isPosted(102))
    }

    @Test fun shorteningPreviousPeriodPreservesAwardsAndAllowsTheNextActualRecord() = runBlocking {
        repository.initialize(today.minusDays(100), today)
        val earnedAwards = repository.readHistory().awards
        val settings = AppSettings(achievementEnabled = true, recordEnabled = true)
        repository.settings.update(settings)
        NotificationScheduler.schedule(context, settings, true)

        repository.startNewPeriod(today.minusDays(91), today.minusDays(10), today)
        NotificationCoordinator.reconcileAfterDateCorrection(context)
        assertEquals(earnedAwards, repository.readHistory().awards)
        NotificationCoordinator.checkProgress(context, emptyList(), settings, today)
        assertFalse(isPosted(101))
        assertFalse(isPosted(102))

        val eleventhDay = today.plusDays(1)
        NotificationCoordinator.checkProgress(context, repository.synchronize(eleventhDay), settings, eleventhDay)
        awaitPosted(102)
        assertFalse(isPosted(101))
        assertEquals(earnedAwards, repository.readHistory().awards)
    }

    private fun isPosted(id: Int): Boolean = manager.activeNotifications.any { it.id == id }

    private fun awaitPosted(id: Int) {
        repeat(40) {
            if (isPosted(id)) return
            SystemClock.sleep(50)
        }
        assertTrue("Notification $id was not posted", isPosted(id))
    }

    private fun awaitRemoved(id: Int) {
        repeat(40) {
            if (!isPosted(id)) return
            SystemClock.sleep(50)
        }
        assertFalse("Notification $id was not removed", isPosted(id))
    }
}
