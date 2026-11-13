package com.masroufi.app.domain

import java.time.LocalDate

enum class TicketStatus { OPEN, CLOSED_BY_HAND, CLOSED_AUTO }

enum class LeftoverDestination { JAR, TOMORROW, EMERGENCY }

data class DayResult(val date: LocalDate, val status: TicketStatus, val ticket: Long, val spent: Long)

fun leftover(ticket: Long, spent: Long): Long = maxOf(0L, ticket - spent)
