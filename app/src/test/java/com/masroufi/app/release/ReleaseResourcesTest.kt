package com.masroufi.app.release

import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.AdaptiveIconDrawable
import androidx.core.content.res.ResourcesCompat
import androidx.test.core.app.ApplicationProvider
import com.masroufi.app.BuildConfig
import com.masroufi.app.MainActivity
import com.masroufi.app.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ReleaseResourcesTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()

    private fun adaptive(id: Int): AdaptiveIconDrawable {
        val d = ResourcesCompat.getDrawable(ctx.resources, id, null)
        assertTrue(d is AdaptiveIconDrawable)
        return d as AdaptiveIconDrawable
    }

    @Test fun launcherAndRoundIconsAreAdaptiveWithAMonochromeLayer() {
        for (id in listOf(R.mipmap.ic_launcher, R.mipmap.ic_launcher_round)) {
            val icon = adaptive(id)
            assertNotNull(icon.background)
            assertNotNull(icon.foreground)
            assertNotNull(icon.monochrome)
        }
    }

    @Test fun monochromeAndSplashDrawablesResolve() {
        assertNotNull(ResourcesCompat.getDrawable(ctx.resources, R.drawable.ic_launcher_monochrome, null))
        assertNotNull(ResourcesCompat.getDrawable(ctx.resources, R.drawable.splash_ticket, null))
    }

    @Test fun launcherActivityUsesTheSplashTheme() {
        val info = ctx.packageManager.getActivityInfo(ComponentName(ctx, MainActivity::class.java), 0)
        assertEquals(R.style.Theme_Masroufi_Splash, info.theme)
    }

    @Test fun versionNameIs2() {
        assertEquals("2.0.0", BuildConfig.VERSION_NAME)
    }
}
