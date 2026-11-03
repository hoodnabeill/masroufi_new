package com.masroufi.app.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.masroufi.app.MainActivity
import com.masroufi.app.R
import com.masroufi.app.core.MoneyFormatter
import com.masroufi.app.data.prefs.SettingsRepository
import com.masroufi.app.data.repo.ProgressRepository
import com.masroufi.app.data.repo.WalletRepository
import com.masroufi.app.domain.PayCycle
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import java.time.LocalDate

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun settings(): SettingsRepository
    fun wallets(): WalletRepository
    fun progress(): ProgressRepository
}

/**
 * Home-screen widget: what you have, days to payday, your streak, and one-tap "spend" / "income" buttons that open
 * the editor straight away. With the app lock on, the balance is hidden, because the home screen is public.
 */
class MasroufiWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entry = EntryPointAccessors.fromApplication(context.applicationContext, WidgetEntryPoint::class.java)
        val settings = entry.settings().settings.first()
        val wallets = entry.wallets().observeWallets().first()
        val streak = entry.progress().observe().first().streak
        val currency = settings.primaryCurrency
        val balance = wallets.filter { !it.wallet.archived && it.wallet.currency == currency }.sumOf { it.balanceMinor }
        val resources = context.resources
        val amount = if (settings.lockEnabled) "••••••" else MoneyFormatter(resources.configuration.locales[0]).format(balance, currency)
        val payday = settings.paydayDay.takeIf { PayCycle.isSet(it) }?.let { day ->
            val left = PayCycle.daysUntilPayday(LocalDate.now(), day)
            resources.getQuantityString(R.plurals.home_days_to_payday, left, left)
        }
        val streakText = if (streak.current > 0) resources.getQuantityString(R.plurals.streak_days, streak.current, streak.current) else context.getString(R.string.streak_start)
        val title = context.getString(R.string.home_total_balance)
        val spend = context.getString(R.string.type_expense)
        val income = context.getString(R.string.type_income)

        provideContent {
            Column(
                GlanceModifier
                    .fillMaxSize()
                    .background(ImageProvider(R.drawable.widget_background))
                    .cornerRadius(28.dp)
                    .padding(16.dp)
                    .clickable(actionStartActivity(openApp(context, null))),
            ) {
                Text(title, style = TextStyle(color = ColorProvider(Color.White.copy(alpha = 0.75f)), fontSize = 13.sp))
                Text(amount, style = TextStyle(color = ColorProvider(Color.White), fontSize = 26.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                Spacer(GlanceModifier.height(4.dp))
                Text(payday ?: streakText, style = TextStyle(color = ColorProvider(Color.White.copy(alpha = 0.85f)), fontSize = 12.sp), maxLines = 1)
                Spacer(GlanceModifier.defaultWeight())
                Row(GlanceModifier.fillMaxWidth()) {
                    QuickButton("+ $spend", openApp(context, "EXPENSE"), GlanceModifier.defaultWeight())
                    Spacer(GlanceModifier.width(8.dp))
                    QuickButton("+ $income", openApp(context, "INCOME"), GlanceModifier.defaultWeight())
                }
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun QuickButton(label: String, intent: Intent, modifier: GlanceModifier) {
        Box(
            modifier
                .height(40.dp)
                .background(ImageProvider(R.drawable.widget_button))
                .cornerRadius(20.dp)
                .clickable(actionStartActivity(intent)),
            contentAlignment = Alignment.Center,
        ) {
            Text(label, style = TextStyle(color = ColorProvider(Color(0xFF12160A)), fontSize = 14.sp, fontWeight = FontWeight.Medium), maxLines = 1)
        }
    }

    private fun openApp(context: Context, type: String?): Intent =
        if (type == null) {
            Intent(context, MainActivity::class.java)
        } else {
            // Handled by the editor's deep link; a fresh task so the editor opens on top of Home.
            Intent(Intent.ACTION_VIEW, Uri.parse("masroufi://add?type=$type"), context, MainActivity::class.java)
        }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)

    companion object {
        /** Refreshes every placed widget; called when the app goes to the background and by the daily worker. */
        suspend fun refresh(context: Context) = runCatching { MasroufiWidget().updateAll(context) }
    }
}

class MasroufiWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MasroufiWidget()
}
