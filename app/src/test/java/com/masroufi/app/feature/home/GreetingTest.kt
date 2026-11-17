package com.masroufi.app.feature.home

import com.masroufi.app.core.PayCycle
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class GreetingTest {
    private val cycle = PayCycle(LocalDate.of(2026, 10, 25), LocalDate.of(2026, 11, 25))
    private val nov12 = LocalDate.of(2026, 11, 12)

    @Test fun byHour() {
        assertEquals(Greeting.Morning, greetingFor(nov12, 5, cycle))
        assertEquals(Greeting.Morning, greetingFor(nov12, 11, cycle))
        assertEquals(Greeting.Afternoon, greetingFor(nov12, 12, cycle))
        assertEquals(Greeting.Afternoon, greetingFor(nov12, 16, cycle))
        assertEquals(Greeting.Evening, greetingFor(nov12, 17, cycle))
        assertEquals(Greeting.Evening, greetingFor(nov12, 4, cycle))
    }

    @Test fun paydayThenLastThreeDays() {
        assertEquals(Greeting.Payday, greetingFor(cycle.start, 9, cycle))
        assertEquals(Greeting.Close(3), greetingFor(LocalDate.of(2026, 11, 22), 9, cycle))
        assertEquals(Greeting.Morning, greetingFor(LocalDate.of(2026, 11, 21), 9, cycle))
    }
}
