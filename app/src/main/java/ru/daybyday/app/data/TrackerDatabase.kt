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

internal data class PeriodWithAwards(
    @Embedded val period: PeriodEntity,
    @Relation(parentColumn = "id", entityColumn = "periodId")
    val awards: List<AwardEntity>,
)

@Dao
internal interface TrackerDao {
    // Room observes both tables and reads every emission in one transaction.
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

    @Query("DELETE FROM earned_awards WHERE threshold IN (:thresholds)")
    suspend fun deleteAwards(thresholds: List<Long>)

    @Query("DELETE FROM earned_awards")
    suspend fun clearAwards()

    @Query("DELETE FROM periods")
    suspend fun clearPeriods()
}

@Database(entities = [PeriodEntity::class, AwardEntity::class], version = 1, exportSchema = true)
internal abstract class TrackerDatabase : RoomDatabase() {
    abstract fun trackerDao(): TrackerDao

    companion object {
        @Volatile private var instance: TrackerDatabase? = null

        fun getInstance(context: Context): TrackerDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                TrackerDatabase::class.java,
                "day_by_day.db",
            ).build().also { instance = it }
        }
    }
}
