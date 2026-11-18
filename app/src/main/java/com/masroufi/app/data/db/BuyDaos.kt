package com.masroufi.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface WishDao {
    @Insert suspend fun insert(wish: WishEntity): Long
    @Update suspend fun update(wish: WishEntity)
    @Query("SELECT * FROM wish WHERE id = :id") suspend fun byId(id: Long): WishEntity?

    /** Waiting ones first (soonest due first), then the answered, newest first. */
    @Query("SELECT * FROM wish ORDER BY (answer != 'WAITING'), CASE WHEN answer = 'WAITING' THEN parkedAt END, answeredAt DESC, id DESC")
    fun all(): Flow<List<WishEntity>>

    /** What the user decided not to buy, so far. */
    @Query("SELECT COALESCE(SUM(price), 0) FROM wish WHERE answer = 'CHANGED_MIND'") fun changedMindTotal(): Flow<Long>
}

@Dao
interface TrackedItemDao {
    @Insert suspend fun insert(item: TrackedItemEntity): Long
    @Update suspend fun update(item: TrackedItemEntity)
    @Query("SELECT * FROM tracked_item WHERE normalisedName = :normalised LIMIT 1") suspend fun byNormalised(normalised: String): TrackedItemEntity?
    @Query("SELECT * FROM tracked_item ORDER BY id") fun all(): Flow<List<TrackedItemEntity>>
    @Query("SELECT * FROM tracked_item ORDER BY id") suspend fun allNow(): List<TrackedItemEntity>
}
