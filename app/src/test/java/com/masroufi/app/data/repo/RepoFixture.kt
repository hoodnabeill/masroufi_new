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
import javax.inject.Provider

class TestClock(var instant: Instant, private val zoneId: ZoneId = ZoneId.of("Africa/Cairo")) : Clock() {
    override fun getZone(): ZoneId = zoneId
    override fun withZone(zone: ZoneId): Clock = TestClock(instant, zone)
    override fun instant(): Instant = instant
}

/** The repositories over one test database and a DataStore file in `dir`, whose scope is `scope`. */
class RepoFixture(db: TazkaraDatabase, scope: CoroutineScope, dir: File, clock: Clock, plus: DebugEntitlements) {
    val settings = SettingsStore(PreferenceDataStoreFactory.create(scope = scope) { File(dir, "s.preferences_pb") }, clock)
    // Provider: entries is declared below (the Hilt graph breaks the same cycle).
    val recurring: RecurringRepository = RecurringRepository(db.recurringDao(), db.entryDao(), Provider { entries }, clock)
    val stamps = StampAwarder(db.stampDao(), db.jarDao(), db.peopleDao(), clock)
    val tickets: TicketRepository = TicketRepository(
        db, db.pocketDao(), db.entryDao(), db.dayTicketDao(), db.recurringDao(), db.jarDao(), db.proverbDao(),
        db.stampDao(), settings, clock, stamps,
    )
    val entries: EntryRepository = EntryRepository(db, db.entryDao(), db.pocketDao(), db.jarDao(), db.recurringDao(), db.peopleDao(), tickets, settings, plus, clock)
    val home = HomeRepository(
        db.pocketDao(), db.entryDao(), db.categoryDao(), db.dayTicketDao(), db.jarDao(), db.proverbDao(), db.recurringDao(),
        settings, clock,
    )
    val envelopes = EnvelopeRepository(
        db, db.envelopeDao(), db.entryDao(), db.recurringDao(), db.categoryDao(), settings, plus, clock,
    )
    val jar = JarRepository(db, db.jarDao(), db.goalDao(), settings, clock, stamps)
    val seasons = SeasonRepository(db, db.seasonDao(), db.jarDao(), clock)
    val subscriptions = SubscriptionRepository(db.recurringDao(), db.dayTicketDao(), clock)
    val people = PeopleRepository(db, db.peopleDao(), db.dutyDao(), entries, clock, stamps)
    val duty = DutyRepository(db, db.dutyDao(), db.peopleDao(), clock)
    val gameyas = GameyaRepository(db, db.gameyaDao(), recurring, plus, clock)
    val ritual = PaydayRitualRepository(
        db.entryDao(), db.pocketDao(), db.recurringDao(), db.dayTicketDao(), tickets, jar, envelopes, people, settings, clock,
    )
    val roll = RollRepository(db.entryDao(), db.dayTicketDao(), clock)
    val pockets = PocketRepository(db, db.pocketDao(), db.entryDao(), db.recurringDao(), entries, clock)
    val seed = SeedData(db, db.pocketDao(), db.categoryDao(), clock)
    val onboarding = OnboardingRepository(
        db, db.pocketDao(), db.entryDao(), db.dayTicketDao(), db.recurringDao(), tickets, seed, settings, clock,
    )
}
