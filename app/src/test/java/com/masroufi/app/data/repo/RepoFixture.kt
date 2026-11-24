package com.masroufi.app.data.repo

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.masroufi.app.core.ChildMode
import com.masroufi.app.core.PinStore
import com.masroufi.app.core.PlainCipher
import com.masroufi.app.core.SecretCipher
import com.masroufi.app.data.db.TazkaraDatabase
import com.masroufi.app.data.prefs.SettingsStore
import com.masroufi.app.entitlements.DebugEntitlements
import com.masroufi.app.entitlements.PlusRepository
import com.masroufi.app.entitlements.StubBilling
import com.masroufi.app.widget.DaySummaryStore
import com.masroufi.app.widget.DaySummaryWriter
import com.masroufi.app.widget.WidgetRefresher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File
import java.time.Clock
import java.time.Instant
import java.security.GeneralSecurityException
import java.time.ZoneId
import javax.inject.Provider

class TestClock(var instant: Instant, private val zoneId: ZoneId = ZoneId.of("Africa/Cairo")) : Clock() {
    override fun getZone(): ZoneId = zoneId
    override fun withZone(zone: ZoneId): Clock = TestClock(instant, zone)
    override fun instant(): Instant = instant
}

/** Stands in for the widgets: counts how often they were told to redraw. */
class CountingRefresher : WidgetRefresher {
    @Volatile var updates = 0
    override suspend fun update() { updates++ }
}

/** The plain test cipher; [broken] makes every read fail like a lost Keystore key. */
class SwitchCipher : SecretCipher {
    var broken = false
    private val plain = PlainCipher()
    override fun encrypt(plain: ByteArray): ByteArray = this.plain.encrypt(plain)
    override fun decrypt(data: ByteArray): ByteArray = if (broken) throw GeneralSecurityException("key gone") else plain.decrypt(data)
}

/** The repositories over one test database and a DataStore file in `dir`, whose scope is `scope`. */
class RepoFixture(db: TazkaraDatabase, scope: CoroutineScope, dir: File, clock: Clock, plus: DebugEntitlements) {
    val settings = SettingsStore(PreferenceDataStoreFactory.create(scope = scope) { File(dir, "s.preferences_pb") }, clock)
    // Provider: entries is declared below (the Hilt graph breaks the same cycle).
    val recurring: RecurringRepository = RecurringRepository(db.recurringDao(), db.entryDao(), Provider { entries }, clock)
    val stamps = StampAwarder(db.stampDao(), db.jarDao(), db.peopleDao(), clock)
    val story = StoryRepository(
        db.storyDao(), db.dayTicketDao(), db.entryDao(), db.categoryDao(), db.envelopeDao(), db.jarDao(), db.proverbDao(), clock,
    )
    // Its own file, like the PIN's; the widgets are counted, not drawn.
    val summaryStore = DaySummaryStore(PreferenceDataStoreFactory.create(scope = scope) { File(dir, "summary.preferences_pb") })
    val widgets = CountingRefresher()
    val daySummary = DaySummaryWriter(db.dayTicketDao(), db.entryDao(), db.categoryDao(), settings, summaryStore, plus, clock, widgets, CoroutineScope(SupervisorJob() + Dispatchers.IO))
    val tickets: TicketRepository = TicketRepository(
        db, db.pocketDao(), db.entryDao(), db.dayTicketDao(), db.recurringDao(), db.jarDao(), db.proverbDao(),
        db.stampDao(), settings, clock, stamps, Provider { eom }, Provider { installments }, Provider { freelance }, story, daySummary,
    )
    val eom = EndOfMonthRepository(
        settings, tickets, db.dayTicketDao(), db.entryDao(), db.categoryDao(), db.recurringDao(), db.pocketDao(), db.jarDao(),
        db.peopleDao(), stamps, clock,
    )
    val eomMode = EndOfMonthMode(settings, db.dayTicketDao(), clock)
    val entries: EntryRepository = EntryRepository(db, db.entryDao(), db.pocketDao(), db.jarDao(), db.recurringDao(), db.peopleDao(), db.clientJobDao(), tickets, settings, plus, clock, daySummary)
    val home = HomeRepository(
        db.pocketDao(), db.entryDao(), db.categoryDao(), db.dayTicketDao(), db.jarDao(), db.proverbDao(), db.recurringDao(),
        settings, clock, plus,
    )
    val envelopes = EnvelopeRepository(
        db, db.envelopeDao(), db.entryDao(), db.recurringDao(), db.categoryDao(), settings, plus, clock,
    )
    val cipher = SwitchCipher()
    // Its own file: two DataStores on one file would clash. 1,000 iterations keep the hashing quick.
    val pin = PinStore(PreferenceDataStoreFactory.create(scope = scope) { File(dir, "pin.preferences_pb") }, cipher, clock, iterations = 1_000)
    val childMode = ChildMode(settings, pin)
    val kids = KidsRepository(
        db, db.kidsDao(), db.recurringDao(), db.pocketDao(), db.entryDao(), db.envelopeDao(), db.categoryDao(), entries, plus, clock,
    )
    val jar = JarRepository(db, db.jarDao(), db.goalDao(), settings, clock, stamps)
    val installments = InstallmentRepository(
        db, db.recurringDao(), db.entryDao(), db.pocketDao(), db.jarDao(), recurring, jar, settings, clock,
    )
    val freelance = FreelanceRepository(
        db.pocketDao(), db.entryDao(), db.recurringDao(), db.clientJobDao(), Provider { entries }, settings, clock,
    )
    val seasons = SeasonRepository(db, db.seasonDao(), db.jarDao(), clock)
    val subscriptions = SubscriptionRepository(db.recurringDao(), db.dayTicketDao(), clock)
    val people = PeopleRepository(db, db.peopleDao(), db.dutyDao(), entries, clock, stamps)
    val duty = DutyRepository(db, db.dutyDao(), db.peopleDao(), clock)
    val gameyas = GameyaRepository(db, db.gameyaDao(), recurring, plus, clock)
    val profile = ProfileRepository(settings, clock)
    val basket = BasketRepository(db, db.trackedItemDao(), db.entryDao(), db.dayTicketDao(), settings, plus, clock)
    val wishes = WishRepository(
        db, db.wishDao(), db.entryDao(), db.pocketDao(), db.recurringDao(), db.jarDao(), db.dayTicketDao(), jar, seasons, settings, clock,
    )
    val ritual = PaydayRitualRepository(
        db.entryDao(), db.pocketDao(), db.recurringDao(), db.dayTicketDao(), tickets, jar, envelopes, people, settings, clock,
    )
    val roll = RollRepository(db.entryDao(), db.dayTicketDao(), clock)
    val pockets = PocketRepository(db, db.pocketDao(), db.entryDao(), db.recurringDao(), entries, clock)
    val seed = SeedData(db, db.pocketDao(), db.categoryDao(), clock)
    val onboarding = OnboardingRepository(
        db, db.pocketDao(), db.entryDao(), db.dayTicketDao(), db.recurringDao(), tickets, seed, settings, clock,
    )
    /** Debug-flavoured stub billing (the two mock prices); the real entitlement is [plusRepo]. Lazy: nothing runs unless a test asks. */
    val billing = StubBilling(debug = true, clock = clock)
    val plusRepo: PlusRepository by lazy { PlusRepository(billing, settings, db.dayTicketDao(), eomMode, clock, scope) }
    val lossRows: LossRowsRepository by lazy {
        LossRowsRepository(
            db.jarDao(), db.seasonDao(), db.dutyDao(), db.trackedItemDao(), db.entryDao(), db.envelopeDao(), db.dayTicketDao(),
            db.gameyaDao(), db.kidsDao(), db.recurringDao(), settings, clock,
        )
    }
}
