package com.masroufi.app.data.prefs

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.masroufi.app.core.Payday
import com.masroufi.app.domain.LeftoverDestination
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SettingsStoreTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun TestScope.store(name: String = "settings.preferences_pb") =
        SettingsStore(PreferenceDataStoreFactory.create(scope = backgroundScope) { File(tmp.root, name) })

    @Test fun defaults() = runTest {
        val s = store().current()
        assertEquals(Payday.Day(25), s.payday)
        assertEquals(LeftoverDestination.JAR, s.leftoverDefault)
        assertEquals(0, s.roundUpStep)
        assertFalse(s.onboarded)
    }

    @Test fun paydayRoundTrip() = runTest {
        val store = store()
        for (p in listOf(Payday.Day(1), Payday.Day(31), Payday.LastDay, Payday.NotFixed)) {
            store.setPayday(p)
            assertEquals(p, store.current().payday)
        }
    }

    @Test fun otherSettersRoundTrip() = runTest {
        val store = store()
        store.setLeftoverDefault(LeftoverDestination.EMERGENCY)
        store.setRoundUpStep(500)
        store.setOnboarded(true)
        val s = store.current()
        assertEquals(LeftoverDestination.EMERGENCY, s.leftoverDefault)
        assertEquals(500, s.roundUpStep)
        assertEquals(true, s.onboarded)
    }

    @Test fun seedIsStableAcrossReads() = runTest {
        val store = store()
        val first = store.current().proverbSeed
        store.setPayday(Payday.LastDay)
        assertEquals(first, store.current().proverbSeed)
    }
}
