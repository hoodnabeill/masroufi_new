package com.masroufi.app.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Telephony
import com.masroufi.app.MainActivity
import com.masroufi.app.R
import com.masroufi.app.core.Money
import com.masroufi.app.core.MoneyFormatter
import com.masroufi.app.data.model.TransactionType
import com.masroufi.app.data.prefs.SettingsRepository
import com.masroufi.app.data.repo.WalletRepository
import com.masroufi.app.domain.SmsParser
import com.masroufi.app.feature.wallets.guessWallet
import com.masroufi.app.work.Notifier
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Locale

@EntryPoint
@InstallIn(SingletonComponent::class)
interface SmsEntryPoint {
    fun settings(): SettingsRepository
    fun wallets(): WalletRepository
    fun notifier(): Notifier
}

/**
 * Incoming SMS, when the user turned on "Read wallet and bank messages": a money message (an amount from a wallet,
 * InstaPay or a bank) becomes a notification that opens the quick-add sheet filled in. Everything happens on the
 * device; the text is not stored, other messages are ignored, and nothing is saved until the user confirms.
 */
class MoneySmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        // Multi-part messages arrive as pieces of one body.
        val body = Telephony.Sms.Intents.getMessagesFromIntent(intent).orEmpty().joinToString("") { it.messageBody.orEmpty() }
        val parsed = SmsParser.parse(body)?.takeIf { it.source != null } ?: return
        val entry = EntryPointAccessors.fromApplication(context.applicationContext, SmsEntryPoint::class.java)
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                if (!entry.settings().current().smsAutoLog) return@launch
                val wallet = guessWallet(parsed.source, entry.wallets().observeWallets().first())
                val currency = wallet?.wallet?.currency ?: Money.DEFAULT_CURRENCY
                val amount = MoneyFormatter(Locale.getDefault()).format(Money.parse(parsed.amount, currency) ?: 0, currency)
                val uri = Uri.Builder().scheme("masroufi").authority("add")
                    .appendQueryParameter("type", parsed.type.name)
                    .appendQueryParameter("amount", parsed.amount)
                    .apply { wallet?.let { appendQueryParameter("walletId", it.wallet.id) } }
                    .apply { parsed.counterparty?.let { appendQueryParameter("note", it) } }
                    .build()
                val open = Intent(Intent.ACTION_VIEW, uri, context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                val title = context.getString(
                    when (parsed.type) {
                        TransactionType.INCOME -> R.string.sms_notify_income
                        TransactionType.TRANSFER -> R.string.sms_notify_cash
                        TransactionType.EXPENSE -> R.string.sms_notify_spent
                    },
                    amount,
                )
                entry.notifier().show(body.hashCode(), title, context.getString(R.string.sms_notify_body, wallet?.wallet?.name ?: parsed.counterparty.orEmpty()), open)
            } finally {
                pending.finish()
            }
        }
    }
}
