package com.masroufi.app.feature.story

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.core.content.IntentCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.masroufi.app.R
import com.masroufi.app.data.repo.MonthStory
import com.masroufi.app.data.repo.StoryComparison
import com.masroufi.app.data.repo.StoryRule
import com.masroufi.app.data.repo.StorySuggestion
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.Locale

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "ar")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StoryTextTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val ar = Locale("ar")
    private val september = storyMonthName(sepStart, octStart, ar)

    private fun text(story: MonthStory, slide: StorySlide, hide: Boolean = false) = slideText(app, story, slide, hide)
    private fun str(id: Int, vararg a: Any) = app.getString(id, *a)

    @Test fun theFixtureGridMatchesItsCounts() {
        val p = fullPayload()
        assertEquals(listOf(31, 22, 4, 5), listOf(p.grid.length, p.grid.count { it == 'U' }, p.grid.count { it == 'O' }, p.grid.count { it == 'N' }))
    }

    @Test fun aFullStoryIsFiveSlidesAndAThinOneIsTwo() {
        assertEquals(StorySlide.entries, storySlides(fullPayload()))
        assertEquals(listOf(StorySlide.OPENING, StorySlide.JAR), storySlides(thinPayload()))
    }

    @Test fun theGridSlideCountsTheMonth() {
        val t = text(story(fullPayload()), StorySlide.GRID)
        assertEquals(26L, t.bigValue)
        assertEquals("26", t.big)
        assertEquals(str(R.string.story_grid_suffix, "31"), t.suffix)
        assertEquals(31, t.grid!!.length)
        assertEquals(
            listOf(str(R.string.story_legend_under, "22"), str(R.string.story_legend_over, "4"), str(R.string.story_legend_never, "5")),
            t.legend,
        )
    }

    @Test fun theBestMonthLineOnlyWhenItIsTrue() {
        val best = text(story(fullPayload(isBest = true)), StorySlide.GRID)
        assertTrue(best.bestMonth)
        assertEquals(str(R.string.story_cmp_best, september, "19"), best.line)

        val notBest = text(story(fullPayload(isBest = false)), StorySlide.GRID)
        assertFalse(notBest.bestMonth)
        assertEquals(str(R.string.story_cmp_more, september, "19"), notBest.line)
    }

    @Test fun theComparisonLineIsShownOrHidden() {
        val prev = StoryComparison(sepStart.toEpochDay(), octStart.toEpochDay(), 30, null, null, null, false)
        assertEquals(str(R.string.story_cmp_other, september, "30"), text(story(fullPayload(comparison = prev)), StorySlide.GRID).line)
        assertEquals(str(R.string.story_cmp_more, september, "10"), text(story(fullPayload(comparison = prev.copy(prevTorn = 10))), StorySlide.GRID).line)
        // No earlier cycle (or a thin month, which stores none): no line, no Milestone.
        val none = text(story(fullPayload(comparison = null)), StorySlide.GRID)
        assertNull(none.line)
        assertFalse(none.bestMonth)
        assertNull(text(story(fullPayload(comparison = StoryComparison())), StorySlide.GRID).line)
        assertNull(text(story(thinPayload()), StorySlide.JAR).line)
    }

    @Test fun theSuggestionIsWordedPerRule() {
        fun line(g: StorySuggestion) = text(story(fullPayload(suggestion = g)), StorySlide.CLOSING).line!!
        val ranOut = line(StorySuggestion(StoryRule.ENVELOPE_RAN_OUT, "Food", 12, 31, 300_000))
        assertEquals(str(R.string.story_sug_ran_out, "Food", "12", "31"), ranOut)
        val habit = line(StorySuggestion(StoryRule.HABIT_GREW, "Coffee", 321_000, 123_400))
        assertEquals(str(R.string.story_sug_habit, "Coffee", "3,210", "1,234"), habit)
        val weekday = line(StorySuggestion(StoryRule.WEEKDAY_OVER, null, 4, 3, 5))
        assertTrue(weekday.contains("3") && weekday.contains("5"))
        val neutral = line(StorySuggestion(StoryRule.NEUTRAL))
        assertEquals(str(R.string.story_sug_neutral), neutral)
        assertEquals(4, setOf(ranOut, habit, weekday, neutral).size)
    }

    @Test fun theSummaryCardHasTheMonthsNumbers() {
        val t = text(story(fullPayload()), StorySlide.CLOSING)
        assertTrue(t.summary)
        assertEquals(str(R.string.story_sum_of, "26", "31"), t.rows[0].value)
        assertEquals("4", t.rows[2].value)
        assertEquals(str(R.string.story_days, "9"), t.rows[3].value)
    }

    @Test fun maskedSlidesDrawEveryAmountAsDots() {
        val g = StorySuggestion(StoryRule.HABIT_GREW, "Coffee", 321_000, 123_400)
        val s = story(fullPayload(isBest = true, suggestion = g))
        for (slide in StorySlide.entries) {
            val shown = text(s, slide, hide = false).allText()
            val masked = text(s, slide, hide = true)
            assertFalse("$slide masked text leaks an amount", masked.allText().any { t -> storyAmounts.any { t.contains(it) } })
            if (shown.any { t -> storyAmounts.any { t.contains(it) } }) {
                assertTrue("$slide shows dots where an amount was", masked.allText().any { it.contains(AMOUNT_DOTS) })
            }
        }
        // Money slides carry no count-up target when masked.
        assertNull(text(s, StorySlide.MONEY, hide = true).bigValue)
        assertNull(text(s, StorySlide.JAR, hide = true).bigValue)
        // Counts, streaks and the month name stay.
        assertEquals("26", text(s, StorySlide.GRID, hide = true).big)
        val jar = text(s, StorySlide.JAR, hide = true)
        assertEquals(AMOUNT_DOTS, jar.big)
        assertTrue(jar.rows.any { it.value == "4" })
        assertTrue(jar.rows.any { it.value == str(R.string.story_days, "9") })
        assertEquals(storyMonthName(octStart, novStart, ar), text(s, StorySlide.OPENING, hide = true).big)
        // And with the switch off the same slides do show their amounts.
        assertTrue(text(s, StorySlide.MONEY).allText().any { it.contains("12,345") })
        assertTrue(text(s, StorySlide.OPENING).allText().any { it.contains("8,765") })
    }

    @Test fun theSharePictureIsAPngThroughTheFileProvider() {
        val t = text(story(fullPayload(isBest = true)), StorySlide.GRID, hide = true)
        runBlocking { StoryShare.share(app, "1_grid", t) }
        val chooser = shadowOf(app).nextStartedActivity
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        val send = IntentCompat.getParcelableExtra(chooser, Intent.EXTRA_INTENT, Intent::class.java)
        assertNotNull(send)
        assertEquals("image/png", send!!.type)
        assertTrue(send.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        val uri = IntentCompat.getParcelableExtra(send, Intent.EXTRA_STREAM, Uri::class.java)
        assertEquals("content", uri!!.scheme)
        assertTrue(File(app.cacheDir, "share/story_1_grid.png").length() > 0)
    }

    @Test fun everySlideRendersABitmap() {
        for (slide in StorySlide.entries) {
            val bmp = StoryShare.render(app, text(story(fullPayload(isBest = true)), slide, hide = true))
            assertEquals(1080, bmp.width)
            bmp.recycle()
        }
    }
}
