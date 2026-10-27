package com.masroufi.app.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

enum class CategoryKind { EXPENSE, INCOME }

/** Seeded categories carry a [systemKey] so their label follows the app language; user categories use [name]. */
@Serializable
@Entity(tableName = "categories", indices = [Index("parentId")])
data class Category(
    @PrimaryKey val id: String,
    val name: String,
    val kind: CategoryKind,
    val parentId: String? = null,
    val iconKey: String = "category",
    val colorIndex: Int = 0,
    val systemKey: String? = null,
    val sortOrder: Int = 0,
    val updatedAt: Long,
    val deleted: Boolean = false,
)
