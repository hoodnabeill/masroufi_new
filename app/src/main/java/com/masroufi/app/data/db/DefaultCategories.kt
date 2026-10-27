package com.masroufi.app.data.db

import com.masroufi.app.data.model.Category
import com.masroufi.app.data.model.CategoryKind

object DefaultCategories {
    private data class Seed(val key: String, val kind: CategoryKind, val icon: String, val color: Int)

    private val seeds = listOf(
        Seed("food", CategoryKind.EXPENSE, "restaurant", 0),
        Seed("groceries", CategoryKind.EXPENSE, "cart", 1),
        Seed("transport", CategoryKind.EXPENSE, "car", 2),
        Seed("bills", CategoryKind.EXPENSE, "bolt", 3),
        Seed("rent", CategoryKind.EXPENSE, "home", 4),
        Seed("mobile", CategoryKind.EXPENSE, "phone", 5),
        Seed("health", CategoryKind.EXPENSE, "health", 6),
        Seed("education", CategoryKind.EXPENSE, "school", 7),
        Seed("shopping", CategoryKind.EXPENSE, "shopping", 8),
        Seed("entertainment", CategoryKind.EXPENSE, "movie", 9),
        Seed("family", CategoryKind.EXPENSE, "family", 10),
        Seed("gifts", CategoryKind.EXPENSE, "gift", 11),
        Seed("charity", CategoryKind.EXPENSE, "charity", 0),
        Seed("other_expense", CategoryKind.EXPENSE, "category", 1),
        Seed("salary", CategoryKind.INCOME, "work", 2),
        Seed("freelance", CategoryKind.INCOME, "payments", 3),
        Seed("business", CategoryKind.INCOME, "trending", 4),
        Seed("gift_income", CategoryKind.INCOME, "gift", 5),
        Seed("other_income", CategoryKind.INCOME, "category", 6),
    )

    /** Stable ids ("sys_food") let backups from different installs merge seeded categories instead of duplicating them. */
    fun build(now: Long): List<Category> = seeds.mapIndexed { index, seed ->
        Category(
            id = "sys_${seed.key}",
            name = seed.key,
            kind = seed.kind,
            iconKey = seed.icon,
            colorIndex = seed.color,
            systemKey = seed.key,
            sortOrder = index,
            updatedAt = now,
        )
    }
}
