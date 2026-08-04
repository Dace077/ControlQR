package com.controlqr.acceso.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {

    @Query("SELECT * FROM users WHERE username = :username LIMIT 1")
    suspend fun byUsername(username: String): UserEntity?

    @Query("SELECT * FROM users WHERE id = :id LIMIT 1")
    suspend fun byId(id: Long): UserEntity?

    @Query("SELECT * FROM users ORDER BY role, displayName")
    fun observeAll(): Flow<List<UserEntity>>

    @Query("SELECT COUNT(*) FROM users")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM users WHERE role = 'MASTER' AND active = 1")
    suspend fun activeMasterCount(): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(user: UserEntity): Long

    @Update
    suspend fun update(user: UserEntity)

    @Query("DELETE FROM users WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface PassDao {

    @Upsert
    suspend fun upsert(pass: PassEntity)

    @Upsert
    suspend fun upsertAll(passes: List<PassEntity>)

    @Query("SELECT * FROM passes WHERE tokenId = :tokenId LIMIT 1")
    suspend fun byToken(tokenId: Int): PassEntity?

    @Query("SELECT * FROM passes ORDER BY issuedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int = 300): Flow<List<PassEntity>>

    @Query("SELECT * FROM passes WHERE status = :status ORDER BY entryAt DESC")
    fun observeByStatus(status: PassStatus): Flow<List<PassEntity>>

    @Query(
        """
        SELECT * FROM passes
        WHERE fullName LIKE '%' || :query || '%'
           OR carrier  LIKE '%' || :query || '%'
           OR plate    LIKE '%' || :query || '%'
           OR folio    LIKE '%' || :query || '%'
        ORDER BY issuedAt DESC
        LIMIT 200
        """
    )
    fun search(query: String): Flow<List<PassEntity>>

    /** Ocupación en tiempo real: cuántos pases están en estado DENTRO. */
    @Query("SELECT COUNT(*) FROM passes WHERE status = 'DENTRO'")
    fun observeInsideCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM passes WHERE status = 'EMITIDO' AND validUntil >= :now")
    fun observeActiveCount(now: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM passes WHERE entryAt BETWEEN :from AND :to")
    fun observeEntriesBetween(from: Long, to: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM passes WHERE exitAt BETWEEN :from AND :to")
    fun observeExitsBetween(from: Long, to: Long): Flow<Int>

    /** Base de los reportes: se agrega por día/semana/mes en memoria para respetar la zona horaria local. */
    @Query(
        """
        SELECT * FROM passes
        WHERE (entryAt BETWEEN :from AND :to)
           OR (exitAt  BETWEEN :from AND :to)
           OR (issuedAt BETWEEN :from AND :to)
        """
    )
    suspend fun inRange(from: Long, to: Long): List<PassEntity>

    @Query("SELECT * FROM passes")
    suspend fun all(): List<PassEntity>

    @Query("SELECT COUNT(*) FROM passes")
    suspend fun total(): Int

    @Query("UPDATE passes SET status = 'REVOCADO', revokedAt = :at, revokedReason = :reason, updatedAt = :at WHERE tokenId = :tokenId")
    suspend fun revoke(tokenId: Int, at: Long, reason: String)

    @Query("DELETE FROM passes WHERE tokenId = :tokenId")
    suspend fun delete(tokenId: Int)
}

@Dao
interface ScanEventDao {

    @Insert
    suspend fun insert(event: ScanEventEntity): Long

    @Insert
    suspend fun insertAll(events: List<ScanEventEntity>)

    @Query("SELECT * FROM scan_events ORDER BY at DESC LIMIT :limit")
    fun observeRecent(limit: Int = 200): Flow<List<ScanEventEntity>>

    @Query("SELECT * FROM scan_events WHERE at BETWEEN :from AND :to ORDER BY at DESC")
    suspend fun inRange(from: Long, to: Long): List<ScanEventEntity>

    @Query("SELECT * FROM scan_events")
    suspend fun all(): List<ScanEventEntity>

    @Query("SELECT COUNT(*) FROM scan_events WHERE accepted = 0 AND at >= :since")
    fun observeDeniedSince(since: Long): Flow<Int>
}
