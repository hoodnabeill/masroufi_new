package com.masroufi.app.feature.kids

import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.masroufi.app.data.repo.CATEGORY_COLORS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChildFormViewModelTest : KidsVmTestBase() {
    private lateinit var vm: ChildFormViewModel
    private val form get() = vm.form.value

    private fun open(id: Long) {
        vm = make(ChildFormViewModel::class, { it.form }) { ChildFormViewModel(f.kids, clock, SavedStateHandle(mapOf("id" to id))) }
        settle("loaded") { form.loaded }
    }

    private fun type(field: ChildAmount, text: String) = text.forEach { if (it == '.') vm.point(field) else vm.digit(field, it.digitToInt()) }

    @Test fun aNewChildNeedsANameAndAWeeklySumAndSavesWithTheJarAndMatch() {
        open(0)
        assertEquals("eight years old to start", 2018, form.birthYear)
        assertFalse(form.canSave)
        vm.setName("ملك")
        assertFalse("no allowance yet", form.canSave)
        type(ChildAmount.WEEKLY, "70")
        assertTrue(form.canSave)
        vm.setJar(true)
        vm.setJarLabel("العجلة")
        type(ChildAmount.TARGET, "1500")
        vm.setMatch(true)
        assertFalse("a match needs both numbers", form.canSave)
        type(ChildAmount.PER, "10")
        type(ChildAmount.ADD, "5")
        vm.setColor(1)
        vm.save()
        settle("saved") { form.saved }
        val c = call { f.kids.view().children.single().child }
        assertEquals(listOf<Any?>("ملك", 2018, CATEGORY_COLORS[1], 7_000L), listOf(c.name, c.birthYear, c.color, c.weekly))
        assertEquals(listOf<Any?>("العجلة", 150_000L, 1_000L, 500L), listOf(c.jarLabel, c.jarTarget, c.matchPer, c.matchAdd))
    }

    @Test fun editingLoadsTheChildAndTurningTheJarOffDropsTheMatch() {
        val id = call { f.kids.addChild("عمر", 2013, CATEGORY_COLORS[2], 15_000, "رحلة", 90_000, 1_000, 500) }
        open(id)
        assertEquals(listOf<Any?>("عمر", 2013, 2, true, true), listOf(form.name, form.birthYear, form.colorIndex, form.jar, form.match))
        vm.setJar(false)
        vm.save()
        settle("saved") { form.saved }
        val c = call { f.kids.view().children.single().child }
        assertEquals(listOf<Any?>(null, null, null, null), listOf(c.jarLabel, c.jarTarget, c.matchPer, c.matchAdd))
        assertEquals(15_000L, c.weekly)
    }

    @Test fun deletingHidesTheChild() {
        val id = call { f.kids.addChild("عمر", 2013, 3, 15_000) }
        open(id)
        vm.delete()
        settle("gone") { vm.gone.value }
        assertEquals(0, call { f.kids.view().children.size })
    }
}
