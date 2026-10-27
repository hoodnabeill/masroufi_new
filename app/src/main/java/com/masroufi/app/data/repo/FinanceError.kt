package com.masroufi.app.data.repo

enum class FinanceError {
    AMOUNT_REQUIRED,
    NAME_REQUIRED,
    WALLET_REQUIRED,
    SAME_WALLET_TRANSFER,
    TARGET_AMOUNT_REQUIRED,
    WITHDRAW_EXCEEDS_SAVED,
    PAYMENT_EXCEEDS_REMAINING,
    WALLET_HAS_TRANSACTIONS,
    MEMBERS_REQUIRED,
    NOT_FOUND,
}

class FinanceException(val error: FinanceError) : Exception(error.name)

internal fun ensure(condition: Boolean, error: FinanceError) {
    if (!condition) throw FinanceException(error)
}
