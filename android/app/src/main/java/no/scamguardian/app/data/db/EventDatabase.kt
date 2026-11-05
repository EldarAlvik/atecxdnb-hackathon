package no.scamguardian.app.data.db

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/**
 * One board event. `epoch` goes up when the board's event log starts over (its `last_seq` fell
 * below what the phone already has), so old and new sequence numbers never clash.
 */
@Entity(tableName = "events", primaryKeys = ["epoch", "seq"])
data class EventEntity(
    val epoch: Int,
    val seq: Long,
    val ts: Long,
    val key: String,
    val eventType: String,
    val valueJson: String,
    val receivedAt: Long,
)

/** Events the board no longer had when the phone synced (`since < oldest_seq - 1`), or a log restart. */
@Entity(tableName = "gaps")
data class GapEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val epoch: Int,
    val fromSeq: Long,
    val toSeq: Long,
    val reason: String,
    val detectedAt: Long,
)

/** How far /events has been read: every logged event with seq <= cursor is in the table. */
@Entity(tableName = "sync_state")
data class SyncStateEntity(
    @PrimaryKey val id: Int = 0,
    val epoch: Int,
    val cursor: Long,
)

@Dao
interface EventDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(events: List<EventEntity>)

    @Insert
    suspend fun insertGap(gap: GapEntity)

    @Query("SELECT * FROM sync_state WHERE id = 0")
    suspend fun syncState(): SyncStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putSyncState(state: SyncStateEntity)

    @Query("SELECT * FROM events ORDER BY epoch DESC, seq DESC LIMIT :limit")
    fun recent(limit: Int): Flow<List<EventEntity>>

    @Query("SELECT * FROM gaps ORDER BY epoch DESC, toSeq DESC LIMIT :limit")
    fun gaps(limit: Int): Flow<List<GapEntity>>

    @Query("SELECT COUNT(*) FROM events")
    suspend fun count(): Int

    @Query(
        "DELETE FROM events WHERE rowid IN (SELECT rowid FROM events ORDER BY epoch ASC, seq ASC LIMIT :n)",
    )
    suspend fun deleteOldest(n: Int)

    @Query("DELETE FROM events")
    suspend fun clearEvents()

    @Query("DELETE FROM gaps")
    suspend fun clearGaps()

    @Query("DELETE FROM sync_state")
    suspend fun clearSyncState()

    @Transaction
    suspend fun saveSynced(events: List<EventEntity>, state: SyncStateEntity) {
        insert(events)
        putSyncState(state)
    }

    @Transaction
    suspend fun clearAll() {
        clearEvents()
        clearGaps()
        clearSyncState()
    }
}

@Database(entities = [EventEntity::class, GapEntity::class, SyncStateEntity::class], version = 1, exportSchema = true)
abstract class EventDatabase : RoomDatabase() {
    abstract fun events(): EventDao
}
