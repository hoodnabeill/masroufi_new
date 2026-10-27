package com.masroufi.app.data

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.masroufi.app.backup.BackupRepository
import com.masroufi.app.backup.SyncMerger
import com.masroufi.app.data.db.AppDatabase
import com.masroufi.app.data.db.DefaultCategories
import com.masroufi.app.data.model.Debt
import com.masroufi.app.data.model.DebtDirection
import com.masroufi.app.data.model.RecurringRule
import com.masroufi.app.data.model.Transaction
import com.masroufi.app.data.model.TransactionType
import com.masroufi.app.data.model.Wallet
import com.masroufi.app.data.model.WalletType
import com.masroufi.app.data.prefs.SettingsRepository
import com.masroufi.app.data.repo.DebtRepository
import com.masroufi.app.data.repo.FinanceError
import com.masroufi.app.data.repo.FinanceException
import com.masroufi.app.data.repo.GoalRepository
import com.masroufi.app.data.repo.RecurringRepository
import com.masroufi.app.data.repo.TransactionRepository
import com.masroufi.app.data.repo.WalletRepository
import com.masroufi.app.domain.Frequency
import com.masroufi.app.receipts.ReceiptStore
import com.masroufi.app.security.PayloadCrypto
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class RepositoryTest {
    private val clock = Clock.fixed(Instant.parse("2026-03-15T10:00:00Z"), ZoneOffset.UTC)
    private lateinit var db: AppDatabase
    private lateinit var wallets: WalletRepository
    private lateinit var transactions: TransactionRepository
    private lateinit var recurring: RecurringRepository
    private lateinit var debts: DebtRepository
    private lateinit var goals: GoalRepository
    private lateinit var backup: BackupRepository

    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        wallets = WalletRepository(db, clock)
        transactions = TransactionRepository(db, clock, ReceiptStore(context))
        recurring = RecurringRepository(db, clock)
        debts = DebtRepository(db, clock)
        goals = GoalRepository(db, clock)
        backup = BackupRepository(context, db, Json { ignoreUnknownKeys = true; encodeDefaults = true }, clock, SettingsRepository(context))
    }

    @After fun tearDown() = db.close()

    private suspend fun wallet(id: String, currency: String = "EGP", opening: Long = 0) =
        wallets.save(Wallet(id, id, WalletType.CASH, currency, opening, createdAt = 0, updatedAt = 0))

    private fun tx(id: String, type: TransactionType, amount: Long, wallet: String, to: String? = null, toAmount: Long? = null) =
        Transaction(id, type, amount, "EGP", wallet, to, toAmount, occurredAt = clock.millis(), createdAt = 0, updatedAt = 0)

    private suspend fun balance(id: String) = wallets.observeWallets().first().single { it.wallet.id == id }.balanceMinor

    @Test fun incomeExpenseAndTransferUpdateBalances() = runTest {
        wallet("cash", opening = 100_000)
        wallet("bank")
        transactions.save(tx("1", TransactionType.INCOME, 50_000, "cash"))
        transactions.save(tx("2", TransactionType.EXPENSE, 20_000, "cash"))
        transactions.save(tx("3", TransactionType.TRANSFER, 30_000, "cash", to = "bank"))
        assertEquals(100_000L, balance("cash"))
        assertEquals(30_000L, balance("bank"))
        // Saving the same id again is an edit, not a second movement of money.
        transactions.save(tx("3", TransactionType.TRANSFER, 10_000, "cash", to = "bank"))
        assertEquals(120_000L, balance("cash"))
        assertEquals(10_000L, balance("bank"))
        transactions.delete("2")
        assertEquals(140_000L, balance("cash"))
    }

    @Test fun crossCurrencyTransferNeedsReceivedAmount() = runTest {
        wallet("egp", opening = 1_000_000)
        wallet("usd", currency = "USD")
        try {
            transactions.save(tx("t", TransactionType.TRANSFER, 500_000, "egp", to = "usd"))
            fail("expected validation error")
        } catch (e: FinanceException) {
            assertEquals(FinanceError.TARGET_AMOUNT_REQUIRED, e.error)
        }
        transactions.save(tx("t", TransactionType.TRANSFER, 500_000, "egp", to = "usd", toAmount = 10_000))
        assertEquals(500_000L, balance("egp"))
        assertEquals(10_000L, balance("usd"))
    }

    @Test fun recurringProcessingIsIdempotent() = runTest {
        wallet("cash")
        recurring.save(
            RecurringRule(
                id = "rent", type = TransactionType.EXPENSE, amountMinor = 1_000, currency = "EGP", walletId = "cash",
                frequency = Frequency.MONTHLY, startEpochDay = LocalDate.of(2026, 1, 1).toEpochDay(), updatedAt = 0,
            ),
        )
        assertEquals(3, recurring.processDue())
        assertEquals(0, recurring.processDue())
        assertEquals(-3_000L, balance("cash"))
        assertEquals(LocalDate.of(2026, 4, 1).toEpochDay(), recurring.get("rent")!!.nextEpochDay)
    }

    @Test fun debtCannotBeOverRepaid() = runTest {
        debts.save(Debt("d", DebtDirection.I_OWE, "Ali", 10_000, "EGP", createdAt = 0, updatedAt = 0))
        debts.addPayment("d", 6_000, "")
        try {
            debts.addPayment("d", 5_000, "")
            fail("expected over-payment to be rejected")
        } catch (e: FinanceException) {
            assertEquals(FinanceError.PAYMENT_EXCEEDS_REMAINING, e.error)
        }
        debts.addPayment("d", 4_000, "")
        assertEquals(10_000L, debts.observeDebt("d").first()!!.paidMinor)
    }

    @Test fun goalWithdrawalCannotGoNegative() = runTest {
        goals.save(com.masroufi.app.data.model.Goal("g", "Trip", 50_000, "EGP", createdAt = 0, updatedAt = 0))
        goals.addEntry("g", 10_000, "")
        try {
            goals.addEntry("g", -10_001, "")
            fail("expected rejection")
        } catch (e: FinanceException) {
            assertEquals(FinanceError.WITHDRAW_EXCEEDS_SAVED, e.error)
        }
    }

    @Test fun encryptedBackupRoundTripRestoresExactly() = runTest {
        db.categoryDao().upsertAll(DefaultCategories.build(0))
        wallet("cash", opening = 5_000)
        transactions.save(tx("1", TransactionType.EXPENSE, 1_250, "cash"))
        val snapshot = backup.snapshot()
        val bytes = backup.encode(snapshot, "correct horse".toCharArray())

        try {
            backup.decode(bytes, "wrong".toCharArray())
            fail("expected wrong password")
        } catch (e: PayloadCrypto.WrongPasswordException) {
            // expected
        }

        transactions.save(tx("2", TransactionType.EXPENSE, 999, "cash"))
        backup.write(backup.decode(bytes, "correct horse".toCharArray()), replace = true)
        assertEquals(3_750L, balance("cash"))
        assertEquals(snapshot.transactions, backup.snapshot().transactions)
    }

    @Test fun syncMergeNeverDuplicatesAndKeepsNewest() = runTest {
        wallet("cash")
        transactions.save(tx("1", TransactionType.EXPENSE, 100, "cash"))
        val local = backup.snapshot()
        val remote = local.copy(
            transactions = local.transactions.map { it.copy(amountMinor = 200, updatedAt = it.updatedAt + 1) } +
                tx("2", TransactionType.INCOME, 500, "cash").copy(updatedAt = 5),
        )
        val outcome = SyncMerger.merge(local, remote, lastSync = null, now = 0)
        assertEquals(2, outcome.payload.transactions.size)
        assertEquals(200L, outcome.payload.transactions.single { it.id == "1" }.amountMinor)
        assertTrue(outcome.changedLocally)

        backup.write(outcome.payload, replace = false)
        backup.write(outcome.payload, replace = false)
        assertEquals(2, db.transactionDao().all().size)
        assertEquals(300L, balance("cash"))
    }
}
