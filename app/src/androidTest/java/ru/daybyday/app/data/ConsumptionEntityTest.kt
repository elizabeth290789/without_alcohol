package ru.daybyday.app.data

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ConsumptionEntityTest {
    private lateinit var database: TrackerDatabase

    @Before
    fun prepare() = runBlocking {
        val context: Context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, TrackerDatabase::class.java).build()
        database.trackerDao().upsertPeriods(
            listOf(
                PeriodEntity(1, 10, 10),
                PeriodEntity(2, 11, 20),
                PeriodEntity(3, 21, 30),
                PeriodEntity(4, 31, null),
            ),
        )
        // A synthetic zero-length coverage boundary retains its original anchor.
        database.trackerDao().upsertConsumptionEvents(
            listOf(ConsumptionEventEntity(1, 10, 1, 2, 11)),
        )
    }

    @After
    fun cleanUp() {
        database.close()
    }

    @Test
    fun eventDateAndEachPeriodLinkAreUniqueAtDatabaseLevel() {
        rejectsConstraint {
            execute("INSERT INTO consumption_events(id,dateEpochDay,previousPeriodId,nextPeriodId) VALUES(2,10,3,4)")
        }
        rejectsConstraint {
            execute("INSERT INTO consumption_events(id,dateEpochDay,previousPeriodId,nextPeriodId) VALUES(3,11,1,3)")
        }
        rejectsConstraint {
            execute("INSERT INTO consumption_events(id,dateEpochDay,previousPeriodId,nextPeriodId) VALUES(4,12,3,2)")
        }
        assertEventCount(1)
    }

    @Test
    fun bothEventLinksRequireExistingPeriods() {
        rejectsConstraint {
            execute("INSERT INTO consumption_events(id,dateEpochDay,previousPeriodId,nextPeriodId) VALUES(2,20,99,4)")
        }
        rejectsConstraint {
            execute("INSERT INTO consumption_events(id,dateEpochDay,previousPeriodId,nextPeriodId) VALUES(2,20,3,99)")
        }
        assertEventCount(1)
    }

    @Test
    fun deletingLinkedPeriodFailsInsteadOfCascadingAwayTheMarker() {
        rejectsConstraint { execute("DELETE FROM periods WHERE id=1") }
        rejectsConstraint { execute("DELETE FROM periods WHERE id=2") }
        assertEventCount(1)
        database.openHelper.writableDatabase.query("SELECT COUNT(*) FROM periods").use { cursor ->
            cursor.moveToFirst()
            assertEquals(4, cursor.getInt(0))
        }
    }

    @Test
    fun nullableCoverageAnchorsAndAllEventFieldsSurviveRoomRelationReads() = runBlocking {
        val second = ConsumptionEventEntity(2, 20, 2, 3, null)
        database.trackerDao().upsertConsumptionEvents(listOf(second))
        val expected = listOf(ConsumptionEventEntity(1, 10, 1, 2, 11), second)
        val queried = database.trackerDao().readHistory().flatMap { it.consumptionEvents }.sortedBy { it.id }
        val observed = database.trackerDao().observeHistory().first()
            .flatMap { it.consumptionEvents }.sortedBy { it.id }
        assertEquals(expected, queried)
        assertEquals(expected, observed)
    }

    private fun execute(sql: String) {
        database.openHelper.writableDatabase.execSQL(sql)
    }

    private fun assertEventCount(expected: Int) {
        database.openHelper.writableDatabase.query("SELECT COUNT(*) FROM consumption_events").use { cursor ->
            cursor.moveToFirst()
            assertEquals(expected, cursor.getInt(0))
        }
    }

    private fun rejectsConstraint(block: () -> Unit) {
        try {
            block()
            fail("Expected a SQLite uniqueness or foreign-key constraint failure")
        } catch (_: SQLiteConstraintException) {
            // A real SQLite constraint, rather than repository validation, enforces this invariant.
        }
    }
}
