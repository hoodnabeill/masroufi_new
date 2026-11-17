package com.masroufi.app.feature.add

import com.masroufi.app.domain.EntryKind

/** The stored entry the sheet edits: its id, what the form does not show, and what it took from its day's ticket. */
data class EditTarget(val id: Long, val happenedAt: Long, val recurringId: Long?, val roundUp: Long, val punched: Long)

/**
 * What the add sheet opens with: the quick log hands over what it could not log; the roll hands over a stored entry
 * ([edit] set, every field filled).
 */
data class AddPrefill(
    val amount: Long?,
    val categoryId: Long?,
    val note: String?,
    val type: EntryKind? = null,
    val pocketId: Long? = null,
    val toPocketId: Long? = null,
    val fee: Long = 0,
    val edit: EditTarget? = null,
    /** The balance an SMS reported: recorded on the pocket once the entry is saved. */
    val smsBalance: Long? = null,
    /** Paying a fixed cost: the new entry pays this recurring item. */
    val recurringId: Long? = null,
)
