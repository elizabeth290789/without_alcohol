package ru.daybyday.app.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Upsert
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "periods")
internal data class PeriodEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startEpochDay: Long,
    val endExclusiveEpochDay: Long? = null,
)

@Entity(
    tableName = "earned_awards",
    foreignKeys = [ForeignKey(
        entity = PeriodEntity::class,
        parentColumns = ["id"],
        childColumns = ["periodId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("periodId")],
)
internal data class AwardEntity(
    @PrimaryKey val threshold: Long,
    val earnedEpochDay: Long,
    val periodId: Long,
)

@Entity(
    tableName = "consumption_events",
    foreignKeys = [
        ForeignKey(
            entity = PeriodEntity::class,
            parentColumns = ["id"],
            childColumns = ["previousPeriodId"],
            onDelete = ForeignKey.NO_ACTION,
            onUpdate = ForeignKey.NO_ACTION,
        ),
        ForeignKey(
            entity = PeriodEntity::class,
            parentColumns = ["id"],
            childColumns = ["nextPeriodId"],
            onDelete = ForeignKey.NO_ACTION,
            onUpdate = ForeignKey.NO_ACTION,
        ),
    ],
    indices = [
        Index(value = ["dateEpochDay"], unique = true),
        Index(value = ["previousPeriodId"], unique = true),
        Index(value = ["nextPeriodId"], unique = true),
    ],
)
internal data class ConsumptionEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dateEpochDay: Long,
    val previousPeriodId: Long,
    val nextPeriodId: Long,
    val previousCoverageStartEpochDay: Long? = null,
)

internal data class PeriodWithAwards(
    @Embedded val period: PeriodEntity,
    @Relation(parentColumn = "id", entityColumn = "periodId")
    val awards: List<AwardEntity>,
    @Relation(parentColumn = "id", entityColumn = "previousPeriodId")
    val consumptionEvents: List<ConsumptionEventEntity>,
)

@Dao
internal interface TrackerDao {
    // Room observes all three tables and reads each emission in one transaction.
    @Transaction
    @Query("SELECT * FROM periods ORDER BY startEpochDay, id")
    fun observeHistory(): Flow<List<PeriodWithAwards>>

    @Transaction
    @Query("SELECT * FROM periods ORDER BY startEpochDay, id")
    suspend fun readHistory(): List<PeriodWithAwards>

    @Upsert
    suspend fun upsertPeriods(periods: List<PeriodEntity>)

    @Upsert
    suspend fun upsertAwards(awards: List<AwardEntity>)

    @Upsert
    suspend fun upsertConsumptionEvents(events: List<ConsumptionEventEntity>)

    @Query("DELETE FROM earned_awards WHERE threshold IN (:thresholds)")
    suspend fun deleteAwards(thresholds: List<Long>)

    @Query("DELETE FROM consumption_events")
    suspend fun clearConsumptionEvents()

    @Query("DELETE FROM earned_awards")
    suspend fun clearAwards()

    @Query("DELETE FROM periods")
    suspend fun clearPeriods()
}

/** Additive migration: old gaps remain unknown, and all original rows stay intact. */
internal val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS consumption_events (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                dateEpochDay INTEGER NOT NULL,
                previousPeriodId INTEGER NOT NULL,
                nextPeriodId INTEGER NOT NULL,
                previousCoverageStartEpochDay INTEGER,
                FOREIGN KEY(previousPeriodId) REFERENCES periods(id) ON UPDATE NO ACTION ON DELETE NO ACTION,
                FOREIGN KEY(nextPeriodId) REFERENCES periods(id) ON UPDATE NO ACTION ON DELETE NO ACTION
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_consumption_events_dateEpochDay ON consumption_events(dateEpochDay)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_consumption_events_previousPeriodId ON consumption_events(previousPeriodId)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_consumption_events_nextPeriodId ON consumption_events(nextPeriodId)")
    }
}

@Database(
    entities = [PeriodEntity::class, AwardEntity::class, ConsumptionEventEntity::class],
    version = 2,
    exportSchema = true,
)
internal abstract class TrackerDatabase : RoomDatabase() {
    abstract fun trackerDao(): TrackerDao

    companion object {
        @Volatile private var instance: TrackerDatabase? = null

        fun getInstance(context: Context): TrackerDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                TrackerDatabase::class.java,
                "day_by_day.db",
            ).addMigrations(MIGRATION_1_2).build().also { instance = it }
        }
    }
}
