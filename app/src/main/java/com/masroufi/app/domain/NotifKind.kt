package com.masroufi.app.domain

/**
 * The kinds of notification, in the fixed priority order the two-a-day cap uses (first = posted first).
 * [defaultOn] is the switch's value until the user touches it; the evening reminder is on exactly when a reminder hour is set.
 */
enum class NotifKind(val defaultOn: Boolean = true) {
    EVENING, BILL, GAMEYA_DUE, TRIAL_SUB, TRIAL_PLUS, LOW_BALANCE, STREAK, GAMEYA_TURN, OCCASION, WISH, MORNING(defaultOn = false),
}
