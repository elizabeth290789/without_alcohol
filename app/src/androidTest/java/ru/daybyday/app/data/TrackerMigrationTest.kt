package ru.daybyday.app.data

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TrackerMigrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        TrackerDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @After
    fun cleanUp() {
        context.deleteDatabase(DATABASE_NAME)
    }

    @Test
    fun actualV1DatabaseMigratesWithoutChangingSavedPeriodsOrAwards() = runBlocking {
        helper.createDatabase(DATABASE_NAME, 1).apply {
            // IDs, gaps, award dates and provenance must survive the actual SQL migration.
            execSQL("INSERT INTO periods(id,startEpochDay,endExclusiveEpochDay) VALUES(11,100,110)")
            execSQL("INSERT INTO periods(id,startEpochDay,endExclusiveEpochDay) VALUES(42,125,NULL)")
            execSQL("INSERT INTO earned_awards(threshold,earnedEpochDay,periodId) VALUES(1,101,11)")
            execSQL("INSERT INTO earned_awards(threshold,earnedEpochDay,periodId) VALUES(7,107,11)")
            execSQL("INSERT INTO earned_awards(threshold,earnedEpochDay,periodId) VALUES(14,139,42)")
            close()
        }

        helper.runMigrationsAndValidate(DATABASE_NAME, 2, true, MIGRATION_1_2).use { migrated ->
            assertEquals(2, migrated.version)
            migrated.query("SELECT COUNT(*) FROM consumption_events").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(0, cursor.getInt(0))
            }
            migrated.query("PRAGMA foreign_key_check").use { cursor ->
                assertFalse(cursor.moveToFirst())
            }
        }

        val database = Room.databaseBuilder(context, TrackerDatabase::class.java, DATABASE_NAME)
            .addMigrations(MIGRATION_1_2)
            .build()
        try {
            // Read through the v2 Room relation graph, not only raw migration SQL.
            val rows = database.trackerDao().readHistory()
            assertEquals(
                listOf(PeriodEntity(11, 100, 110), PeriodEntity(42, 125, null)),
                rows.map { it.period },
            )
            assertEquals(
                listOf(AwardEntity(1, 101, 11), AwardEntity(7, 107, 11), AwardEntity(14, 139, 42)),
                rows.flatMap { it.awards }.sortedBy { it.threshold },
            )
            // The preserved gap [110,125) must not become invented consumption data.
            assertTrue(rows.flatMap { it.consumptionEvents }.isEmpty())
        } finally {
            database.close()
        }
    }

    private companion object {
        const val DATABASE_NAME = "migration-v1-to-v2-test"
    }
}
