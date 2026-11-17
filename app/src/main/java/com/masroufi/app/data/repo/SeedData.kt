package com.masroufi.app.data.repo

import androidx.room.withTransaction
import com.masroufi.app.data.db.CategoryDao
import com.masroufi.app.data.db.CategoryEntity
import com.masroufi.app.data.db.PocketDao
import com.masroufi.app.data.db.PocketEntity
import com.masroufi.app.data.db.PocketKind
import com.masroufi.app.data.db.TazkaraDatabase
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

/** Name, TzIcons name, essential (11-DATA "End-of-month mode": rent, bills, groceries, transport, health, education). */
internal val DEFAULT_CATEGORIES = listOf(
    Triple("قهوة", "coffee", false),
    Triple("مواصلات", "transport", true),
    Triple("أكل", "food", false),
    Triple("بقالة", "groceries", true),
    Triple("فواتير", "bell", true),
    Triple("إيجار", "shop", true),
    Triple("صحة", "grid", true),
    Triple("تعليم", "glasses", true),
    Triple("حاجة تانية", "grid", false),
)

// ARGB copies of the Tz data colours: the data layer does not import UI (HomeRepositoryTest pins them to Tz).
private val YELLOW = 0xFFFFC72C.toInt() // Tz.yellow

/** Category colours cycle through the five data colours in this order. */
private val CATEGORY_COLORS = listOf(
    0xFF8FB4FF.toInt(), // Tz.sky
    0xFFE7A6FF.toInt(), // Tz.lilac
    0xFF35E0D0.toInt(), // Tz.turquoise
    YELLOW,
    0xFFFF6B4A.toInt(), // Tz.carnelian
)

@Singleton
class SeedData @Inject constructor(
    private val db: TazkaraDatabase,
    private val pocketDao: PocketDao,
    private val categoryDao: CategoryDao,
    private val clock: Clock,
) {
    /**
     * First launch only (no pocket yet): the cash pocket and the default categories. The check runs inside the
     * transaction, so concurrent calls seed once.
     */
    suspend fun ensureSeeded() {
        db.withTransaction<Unit> {
            if (pocketDao.count() > 0) return@withTransaction
            val now = clock.millis()
            pocketDao.insert(
                PocketEntity(
                    name = "الكاش", kind = PocketKind.CASH, openingBalance = 0, color = YELLOW,
                    countsInTicket = true, updatedAt = now,
                ),
            )
            DEFAULT_CATEGORIES.forEachIndexed { i, (name, icon, essential) ->
                val color = CATEGORY_COLORS[i % CATEGORY_COLORS.size]
                categoryDao.insert(CategoryEntity(name = name, icon = icon, color = color, essential = essential, updatedAt = now))
            }
        }
    }
}
