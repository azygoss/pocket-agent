// SPDX-License-Identifier: GPL-3.0-or-later
package dev.pocketagent.data

import androidx.room.*

// P05: secrets NEVER stored here — credentialRef only.
@Entity(tableName = "connections")
data class ConnectionEntity(
    @PrimaryKey val id: String,
    val name: String,
    val host: String,
    val port: Int,
    val user: String,
    val credentialRef: String,
    val transportOrder: String = "SSH",
    val jumpHost: String? = null,
    val etPort: Int = 2022,
    val agentForward: Boolean = false,
    val lastConnectedAt: Long = 0L,
    val sortOrder: Int = 0,
)

@Entity(tableName = "agent_events")
data class AgentEventEntity(
    @PrimaryKey val eventId: String,
    val sessionId: String,
    val source: String,
    val category: String,
    val title: String,
    val createdAt: Long,
    val expiresAt: Long,
)

@Dao
interface ConnectionDao {
    @Query("SELECT * FROM connections ORDER BY lastConnectedAt DESC, sortOrder, name")
    suspend fun all(): List<ConnectionEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(e: ConnectionEntity)
    @Query("DELETE FROM connections WHERE id = :id")
    suspend fun delete(id: String)
    @Query("UPDATE connections SET lastConnectedAt = :at WHERE id = :id")
    suspend fun touch(id: String, at: Long)
}

@Dao
interface AgentEventDao {
    @Query("SELECT * FROM agent_events WHERE expiresAt > :now ORDER BY createdAt DESC")
    suspend fun active(now: Long): List<AgentEventEntity>
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(e: AgentEventEntity)
    @Query("DELETE FROM agent_events WHERE expiresAt <= :now")
    suspend fun sweepExpired(now: Long): Int
}

@Database(entities = [ConnectionEntity::class, AgentEventEntity::class], version = 3, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun connections(): ConnectionDao
    abstract fun events(): AgentEventDao
}
