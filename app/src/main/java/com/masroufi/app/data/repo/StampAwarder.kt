package com.masroufi.app.data.repo

import com.masroufi.app.data.db.JarDao
import com.masroufi.app.data.db.PeopleDao
import com.masroufi.app.data.db.Pot
import com.masroufi.app.data.db.StampDao
import com.masroufi.app.data.db.StampEntity
import com.masroufi.app.domain.StampKind
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Writes the stamps the close-day path does not (decision 4): first gram and settled. Idempotent: the stamp table's
 * key is the kind and the insert ignores a second one, so the first date stays. Tight fist is earned by the end-of-month mode.
 */
@Singleton
class StampAwarder @Inject constructor(
    private val stampDao: StampDao,
    private val jarDao: JarDao,
    private val peopleDao: PeopleDao,
    private val clock: Clock,
) {
    /** The jar total reaches one gram at the typed [goldPrice]; nothing without a price. */
    suspend fun checkFirstGram(goldPrice: Long?) {
        if (goldPrice == null || goldPrice <= 0) return
        if (jarDao.totalNow(Pot.JAR) >= goldPrice) award(StampKind.FIRST_GRAM)
    }

    /** At least one debt was ever recorded and none is open now. */
    suspend fun checkSettled() {
        if (peopleDao.debtCount() > 0 && peopleDao.openDebts().isEmpty()) award(StampKind.SETTLED)
    }

    /** Two no-spend days in end-of-month mode; true only the first time. */
    suspend fun checkTightFist(noSpendDays: Int): Boolean {
        if (noSpendDays < 2 || StampKind.TIGHT_FIST in stampDao.kinds()) return false
        award(StampKind.TIGHT_FIST)
        return true
    }

    private suspend fun award(kind: StampKind) =
        stampDao.insert(StampEntity(kind = kind, earnedOn = LocalDate.now(clock), updatedAt = clock.millis()))
}
