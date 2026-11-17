package com.masroufi.app.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.masroufi.app.R
import com.masroufi.app.core.PayCycle
import java.time.LocalDate

/** 07-COPY "Greetings on Home". */
sealed interface Greeting {
    data object Morning : Greeting
    data object Afternoon : Greeting
    data object Evening : Greeting
    data object Payday : Greeting
    data class Close(val daysLeft: Int) : Greeting
}

/** Payday first, then the last 3 days before it, then the hour: 5-11, 12-16, 17-4. */
fun greetingFor(today: LocalDate, hour: Int, cycle: PayCycle): Greeting {
    val daysLeft = cycle.daysLeft(today)
    return when {
        today == cycle.start -> Greeting.Payday
        daysLeft <= 3 -> Greeting.Close(daysLeft)
        hour in 5..11 -> Greeting.Morning
        hour in 12..16 -> Greeting.Afternoon
        else -> Greeting.Evening
    }
}

@Composable
fun greetingText(g: Greeting): String = when (g) {
    Greeting.Morning -> stringResource(R.string.home_greeting_morning)
    Greeting.Afternoon -> stringResource(R.string.home_greeting_afternoon)
    Greeting.Evening -> stringResource(R.string.home_greeting_evening)
    Greeting.Payday -> stringResource(R.string.home_greeting_payday)
    is Greeting.Close -> stringResource(R.string.home_greeting_close, g.daysLeft.toString())
}
