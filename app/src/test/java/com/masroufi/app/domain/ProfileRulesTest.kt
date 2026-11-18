package com.masroufi.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class ProfileRulesTest {
    @Test fun homeOrderByMainGoal() {
        assertEquals(HomeCard.WEEK, homeCardOrder(null).first())
        assertEquals(HomeCard.CYCLE, homeCardOrder(MainGoal.LAST_THE_MONTH).first())
        assertEquals(HomeCard.JAR, homeCardOrder(MainGoal.SAVE).first())
        assertEquals(HomeCard.PEOPLE, homeCardOrder(MainGoal.PAY_OFF).first())
        assertEquals(HomeCard.SEASONS, homeCardOrder(MainGoal.OCCASION).first())
        // Nothing is dropped or doubled.
        MainGoal.values().forEach { assertEquals(HomeCard.values().size, homeCardOrder(it).toSet().size) }
        assertEquals(5, homeCardOrder(MainGoal.SAVE).size)
    }

    @Test fun birthdayPlanIsTargetOverDaysRoundedUp() {
        val p = birthdayPlan(LocalDate.of(1998, 3, 14), LocalDate.of(2026, 11, 12), 100_000)
        assertEquals(122, p.days)
        assertEquals(820L, p.perDay) // 819.67 up
    }

    @Test fun birthdayDefaultsToOneThousandAndTodayMeansNextYear() {
        val p = birthdayPlan(LocalDate.of(1998, 3, 14), LocalDate.of(2026, 3, 14), null)
        assertEquals(365, p.days)
        assertEquals(DEFAULT_BIRTHDAY_TARGET, p.target)
        assertEquals(274L, p.perDay)
    }

    @Test fun householdScalingOnlyScalesSuggestions() {
        assertEquals(100_000L, scaleForHousehold(100_000, null))
        assertEquals(50_000L, scaleForHousehold(100_000, 2))
        assertEquals(75_000L, scaleForHousehold(100_000, 3))
        assertEquals(0L, scaleForHousehold(0, 5))
        assertEquals(60_000L, scaleForSchool(30_000, 2))
        assertEquals(30_000L, scaleForSchool(30_000, null))
    }
}
