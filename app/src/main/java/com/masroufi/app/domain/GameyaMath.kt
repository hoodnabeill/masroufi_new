package com.masroufi.app.domain

import com.masroufi.app.data.model.Gameya
import com.masroufi.app.data.model.GameyaMember
import com.masroufi.app.data.model.GameyaPayment
import com.masroufi.app.data.model.GameyaPayout
import java.time.LocalDate

enum class ContributionStatus { PAID, OVERDUE, DUE_TODAY, SCHEDULED }

data class GameyaRound(
    val round: Int,
    val date: LocalDate,
    val recipient: GameyaMember?,
    val potMinor: Long,
    val paidMemberIds: Set<String>,
    val payout: GameyaPayout?,
    val myStatus: ContributionStatus?,
) {
    val isMyPayout: Boolean get() = recipient?.isMe == true
}

data class GameyaSummary(
    val rounds: List<GameyaRound>,
    val potMinor: Long,
    val myTotalObligationMinor: Long,
    val myPaidMinor: Long,
    val myRemainingMinor: Long,
    val myPayoutRound: GameyaRound?,
    val missedContributions: Int,
    val completedRounds: Int,
)

object GameyaMath {
    fun paymentId(gameyaId: String, memberId: String, round: Int) = "$gameyaId:$memberId:$round"
    fun payoutId(gameyaId: String, round: Int) = "$gameyaId:$round"

    fun roundDate(gameya: Gameya, round: Int): LocalDate {
        val start = LocalDate.ofEpochDay(gameya.startEpochDay)
        val index = (round - 1).toLong()
        return if (gameya.monthly) start.plusMonths(index) else start.plusDays(index * gameya.intervalDays.coerceAtLeast(1))
    }

    fun summarize(
        gameya: Gameya,
        members: List<GameyaMember>,
        payments: List<GameyaPayment>,
        payouts: List<GameyaPayout>,
        today: LocalDate,
    ): GameyaSummary {
        val ordered = members.sortedBy { it.payoutOrder }
        val roundCount = ordered.size
        val pot = gameya.contributionMinor * roundCount
        val me = ordered.firstOrNull { it.isMe }
        val memberIds = ordered.map { it.id }.toSet()
        val paidByRound = payments.groupBy { it.round }.mapValues { (_, list) -> list.map { it.memberId }.toSet() }
        val payoutByRound = payouts.associateBy { it.round }
        var missed = 0
        val rounds = (1..roundCount).map { round ->
            val date = roundDate(gameya, round)
            // Payments of members removed from the group no longer count towards a round.
            val paid = paidByRound[round].orEmpty().intersect(memberIds)
            if (date.isBefore(today)) missed += ordered.count { it.id !in paid }
            GameyaRound(
                round = round,
                date = date,
                recipient = ordered.getOrNull(round - 1),
                potMinor = pot,
                paidMemberIds = paid,
                payout = payoutByRound[round],
                myStatus = me?.let { status(it.id in paid, date, today) },
            )
        }
        val myPaid = if (me == null) 0 else payments.filter { it.memberId == me.id }.sumOf { it.amountMinor }
        val myObligation = if (me == null) 0 else gameya.contributionMinor * roundCount
        return GameyaSummary(
            rounds = rounds,
            potMinor = pot,
            myTotalObligationMinor = myObligation,
            myPaidMinor = myPaid,
            myRemainingMinor = (myObligation - myPaid).coerceAtLeast(0),
            myPayoutRound = rounds.firstOrNull { it.isMyPayout },
            missedContributions = missed,
            completedRounds = rounds.count { it.payout != null },
        )
    }

    private fun status(paid: Boolean, date: LocalDate, today: LocalDate) = when {
        paid -> ContributionStatus.PAID
        date.isBefore(today) -> ContributionStatus.OVERDUE
        date == today -> ContributionStatus.DUE_TODAY
        else -> ContributionStatus.SCHEDULED
    }
}
