package com.masroufi.app.core

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DecimalStyle
import java.util.Locale

/** Whole pounds (floored), Western digits, "," thousands: 312000 -> "3,120". */
fun formatPounds(piastres: Long): String = String.format(Locale.US, "%,d", Math.floorDiv(piastres, PIASTRES))

/** "الخميس 12 نوفمبر" in Arabic; Western digits in every locale. */
fun formatDayDate(date: LocalDate, locale: Locale = Locale("ar")): String =
    DateTimeFormatter.ofPattern("EEEE d MMMM", locale).withDecimalStyle(DecimalStyle.STANDARD).format(date)

/** "27 سبتمبر" in Arabic; Western digits in every locale. */
fun formatDayMonth(date: LocalDate, locale: Locale = Locale("ar")): String =
    DateTimeFormatter.ofPattern("d MMMM", locale).withDecimalStyle(DecimalStyle.STANDARD).format(date)

/** "27 سبتمبر 2027" in Arabic; Western digits in every locale. */
fun formatDate(date: LocalDate, locale: Locale = Locale("ar")): String =
    DateTimeFormatter.ofPattern("d MMMM yyyy", locale).withDecimalStyle(DecimalStyle.STANDARD).format(date)
