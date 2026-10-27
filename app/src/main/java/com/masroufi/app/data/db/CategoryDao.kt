package com.masroufi.app.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.masroufi.app.data.model.Category
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories WHERE deleted = 0 ORDER BY sortOrder, name")
    fun observeAll(): Flow<List<Category>>

    /** Categories ordered by how recently the user picked them, so quick entry shows likely choices first. */
    @Query(
        """
        SELECT c.* FROM categories c
        LEFT JOIN (SELECT categoryId, MAX(occurredAt) AS lastUsed FROM transactions WHERE deleted = 0 GROUP BY categoryId) u
          ON u.categoryId = c.id
        WHERE c.deleted = 0
        ORDER BY u.lastUsed IS NULL, u.lastUsed DESC, c.sortOrder
        """,
    )
    fun observeByRecentUse(): Flow<List<Category>>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun get(id: String): Category?

    @Upsert
    suspend fun upsert(category: Category)

    @Query("SELECT * FROM categories")
    suspend fun all(): List<Category>

    @Upsert
    suspend fun upsertAll(items: List<Category>)

    @Query("DELETE FROM categories")
    suspend fun clear()
}
