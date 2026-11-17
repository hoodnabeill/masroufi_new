package com.masroufi.app.data.repo

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.masroufi.app.data.db.TazkaraDatabase
import com.masroufi.app.data.prefs.SettingsStore
import com.masroufi.app.entitlements.DebugEntitlements
import kotlinx.coroutines.CoroutineScope
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

class TestClock(var instant: Instant, private val zoneId: ZoneId = ZoneId.of("Africa/Cairo")) : Clock() {
    override fun getZone(): ZoneId = zoneId
    override fun withZone(zone: ZoneId): Clock = TestClock(instant, zone)
    override fun instant(): Instant = instant
}

/** The repositories over one test database and a DataStore file in `dir`, whose scope is `scope`. */
class RepoFixture(db: TazkaraDatabase, scope: CoroutineScope, dir: File, clock: Clock, plus: DebugEntitlements) {
    val settings = SettingsStore(PreferenceDataStoreFactory.create(scope = scope) { File(dir, "s.preferences_pb") }, clock)
    val tickets = TicketRepository(
        db, db.pocketDao(), db.entryDao(), db.dayTicketDao(), db.recurringDao(), db.jarDao(), db.proverbDao(),
        db.stampDao(), settings, clock,
    )
    val entries = EntryRepository(db, db.entryDao(), db.pocketDao(), db.jarDao(), db.recurringDao(), tickets, settings, plus, clock)
    val home = HomeRepository(
        db.pocketDao(), db.entryDao(), db.categoryDao(), db.dayTicketDao(), db.jarDao(), db.proverbDao(), db.recurringDao(),
        settings, clock,
    )
    val seed = SeedData(db, db.pocketDao(), db.categoryDao(), clock)
    val onboarding = OnboardingRepository(
        db, db.pocketDao(), db.entryDao(), db.dayTicketDao(), db.recurringDao(), tickets, seed, settings, clock,
    )
}
