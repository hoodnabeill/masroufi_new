package com.masroufi.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.masroufi.app.data.db.PocketEntity
import com.masroufi.app.data.db.PocketKind
import com.masroufi.app.data.db.TazkaraDatabase
import com.masroufi.app.data.repo.ProfileRepository
import com.masroufi.app.data.repo.RepoFixture
import com.masroufi.app.data.repo.TestClock
import com.masroufi.app.domain.HomeCard
import com.masroufi.app.domain.MainGoal
import com.masroufi.app.domain.WorkKind
import com.masroufi.app.entitlements.DebugEntitlements
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

@RunWith(AndroidJUnit4::class)
class ProfileAndWipeTest {
    @get:Rule val tmp = TemporaryFolder()

    private val zone = ZoneId.of("Africa/Cairo")
    private val today = LocalDate.of(2026, 11, 12)
    private val clock = TestClock(ZonedDateTime.of(today, LocalTime.of(9, 0), zone).toInstant(), zone)
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var db: TazkaraDatabase

    @Before fun open() {
        db = Room.inMemoryDatabaseBuilder(context, TazkaraDatabase::class.java).allowMainThreadQueries().build()
    }

    @After fun close() = db.close()

    @Test fun profileFieldsAreOptionalAndRoundTrip() = runTest {
        val f = RepoFixture(db, backgroundScope, tmp.root, clock, DebugEntitlements())
        val s0 = f.settings.current()
        assertNull(s0.profileName)
        assertNull(s0.birthday)
        assertNull(s0.mainGoal)
        val profile = ProfileRepository(f.settings, clock)
        profile.setName("  يوسف ")
        profile.setBirthday(LocalDate.of(1998, 3, 14))
        profile.setWorkKind(WorkKind.FREELANCE)
        profile.setHousehold(4, 2)
        profile.setMainGoal(MainGoal.SAVE)
        val s = f.settings.current()
        assertEquals(listOf<Any?>("يوسف", LocalDate.of(1998, 3, 14), WorkKind.FREELANCE, 4, 2, MainGoal.SAVE),
            listOf(s.profileName, s.birthday, s.workKind, s.householdSize, s.inSchool, s.mainGoal))
        profile.setName(null)
        assertNull(f.settings.current().profileName)
    }

    @Test fun homeCardsFollowTheMainGoal() = runTest {
        val f = RepoFixture(db, backgroundScope, tmp.root, clock, DebugEntitlements())
        val profile = ProfileRepository(f.settings, clock)
        assertEquals(HomeCard.WEEK, profile.homeCards().first().first())
        profile.setMainGoal(MainGoal.LAST_THE_MONTH)
        assertEquals(HomeCard.CYCLE, profile.homeCards().first().first())
    }

    @Test fun birthdayPlanUsesTheTypedTargetOrTheDefault() = runTest {
        val f = RepoFixture(db, backgroundScope, tmp.root, clock, DebugEntitlements())
        val profile = ProfileRepository(f.settings, clock)
        assertNull(profile.birthdayPlan().first())
        profile.setBirthday(LocalDate.of(1998, 3, 14))
        assertEquals(820L, profile.birthdayPlan().first()!!.perDay)
        profile.setBirthdayTarget(244_000)
        assertEquals(2_000L, profile.birthdayPlan().first()!!.perDay)
    }

    @Test fun envelopeSuggestionsScaleOnlyTheSuggestion() = runTest {
        val f = RepoFixture(db, backgroundScope, tmp.root, clock, DebugEntitlements())
        f.settings.setHousehold(6, 2)
        // GROCERY_BASE is 0 (no invented amounts), so scaling keeps every suggested amount at 0.
        assertTrue(f.envelopes.suggestDefaults().all { it.monthly == 0L })
    }

    @Test fun wipeClearsTablesSettingsAndV1Files() = runTest {
        val f = RepoFixture(db, backgroundScope, tmp.root, clock, DebugEntitlements())
        db.pocketDao().insert(PocketEntity(name = "cash", kind = PocketKind.CASH, color = 0))
        f.settings.setOnboarded(true)
        f.settings.setProfileName("يوسف")
        f.settings.markMilestoneSeen("level:2")
        val v1Db = context.getDatabasePath(V1_DATABASE).also { it.parentFile!!.mkdirs(); it.writeText("v1") }
        val v1Settings = File(context.filesDir, V1_SETTINGS_FILE).also { it.parentFile!!.mkdirs(); it.writeText("v1") }

        DataWipe(context, db, f.settings).wipe()

        assertTrue(db.pocketDao().all().first().isEmpty())
        val s = f.settings.current()
        assertFalse(s.onboarded)
        assertNull(s.profileName)
        assertTrue(s.milestonesSeen.isEmpty())
        assertFalse(v1Db.exists())
        assertFalse(v1Settings.exists())
    }
}
