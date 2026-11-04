package com.masroufi.app.feature.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.masroufi.app.R
import com.masroufi.app.core.Money
import com.masroufi.app.core.applyAppLanguage
import com.masroufi.app.core.newId
import com.masroufi.app.data.model.Transaction
import com.masroufi.app.data.model.TransactionType
import com.masroufi.app.data.model.Wallet
import com.masroufi.app.data.model.WalletType
import com.masroufi.app.data.prefs.SettingsRepository
import com.masroufi.app.data.repo.TransactionRepository
import com.masroufi.app.data.repo.WalletRepository
import com.masroufi.app.domain.PayCycle
import com.masroufi.app.feature.transactions.SheetKeypad
import com.masroufi.app.feature.transactions.applyAmountKey
import com.masroufi.app.feature.transactions.groupTyped
import com.masroufi.app.ui.components.GlassButton
import com.masroufi.app.ui.components.GlassStage
import com.masroufi.app.ui.components.LocalMoneyFormatter
import com.masroufi.app.ui.components.MIcons
import com.masroufi.app.ui.components.PocketMark
import com.masroufi.app.ui.components.rememberHaptics
import com.masroufi.app.ui.navigation.StartRequest
import com.masroufi.app.ui.theme.LocalFinanceColors
import com.masroufi.app.ui.theme.LocalGlass
import com.masroufi.app.ui.theme.LocalReduceMotion
import com.masroufi.app.ui.theme.Motion
import com.masroufi.app.work.LogReminderWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    @ApplicationContext private val context: android.content.Context,
    private val settings: SettingsRepository,
    private val wallets: WalletRepository,
    private val transactions: TransactionRepository,
    private val clock: Clock,
) : ViewModel() {
    private var firstWalletId: String? = null

    /** Creates the wallets (the balance goes into the first) and the payday, once; safe to call again. */
    fun setup(currency: String, places: List<Pair<String, WalletType>>, openingMinor: Long, paydayDay: Int) {
        viewModelScope.launch {
            if (!wallets.hasAnyWallet()) {
                val now = clock.millis()
                places.forEachIndexed { i, (name, type) ->
                    val id = newId()
                    if (i == 0) firstWalletId = id
                    wallets.save(Wallet(id, name, type, currency, if (i == 0) openingMinor else 0L, createdAt = now, updatedAt = now))
                }
            }
            settings.setPayday(paydayDay)
        }
    }

    /** The first entry, logged for real so the streak and points start today. */
    fun logFirst(categoryId: String, amountMinor: Long, currency: String) {
        val wallet = firstWalletId ?: return
        viewModelScope.launch {
            val now = clock.millis()
            runCatching {
                transactions.save(
                    Transaction(
                        id = newId(), type = TransactionType.EXPENSE, amountMinor = amountMinor, currency = currency,
                        walletId = wallet, categoryId = categoryId, occurredAt = now, createdAt = now, updatedAt = now,
                    ),
                )
            }
        }
    }

    /** [reminderHour] is the daily nudge's hour, or null to skip it. Marks onboarding done, which opens the app. */
    fun complete(currency: String, reminderHour: Int?) {
        viewModelScope.launch {
            val hour = reminderHour ?: -1
            settings.setLogReminderHour(hour)
            LogReminderWorker.schedule(context, hour)
            settings.setOnboardingDone(currency)
        }
    }
}

private const val STEPS = 8

/** The ways people get paid, mapped onto the payday setting (0 = no fixed day: the month's end). */
private enum class PayKind(val label: Int, val sub: Int, val day: Int) {
    FIRST(R.string.ob_pay_first, R.string.ob_pay_first_sub, 1),
    TWENTY_FIFTH(R.string.ob_pay_25, R.string.ob_pay_25_sub, 25),
    WEEKLY(R.string.ob_pay_weekly, R.string.ob_pay_weekly_sub, 0),
    IRREGULAR(R.string.ob_pay_irregular, R.string.ob_pay_irregular_sub, 0),
}

private data class Place(val key: String, val label: Int, val type: WalletType)

private val places = listOf(
    Place("cash", R.string.wallet_default_cash, WalletType.CASH),
    Place("vodafone", R.string.wallet_preset_vodafone, WalletType.DIGITAL),
    Place("instapay", R.string.wallet_preset_instapay, WalletType.DIGITAL),
    Place("bank", R.string.wallet_preset_bank, WalletType.BANK),
)

private val goals = listOf(R.string.ob_goal_where, R.string.ob_goal_month, R.string.ob_goal_save, R.string.ob_goal_stop, R.string.ob_goal_gameya)

/** First-log choices: label and the system category each logs to. */
private val firstLogs = listOf(R.string.cat_transport to "sys_transport", R.string.ob_log_coffee to "sys_food", R.string.cat_food to "sys_food", R.string.cat_groceries to "sys_groceries")
private val firstAmounts = listOf(15L, 25L, 50L, 100L)

/** Reminder times: the hour and its label (بعد الشغل، بالليل، قبل النوم). */
private val reminders = listOf(18 to R.string.ob_remind_after_work, 21 to R.string.ob_remind_evening, 23 to R.string.ob_remind_bed)

/**
 * Eight short steps that end with the user's first real entry (README: welcome → goals → balance → next income →
 * daily number reveal → first log → reminder → done). Each step slides in from the reading direction.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OnboardingScreen(viewModel: OnboardingViewModel = hiltViewModel()) {
    val haptics = rememberHaptics()
    val reduce = LocalReduceMotion.current
    val glass = LocalGlass.current
    val money = LocalMoneyFormatter.current
    val currency = Money.DEFAULT_CURRENCY
    val unit = Money.unit(currency)
    val today = remember { LocalDate.now() }
    val uiLanguage = LocalConfiguration.current.locales[0].language
    val locale = LocalConfiguration.current.locales[0]
    val dayMonth = remember(locale) { DateTimeFormatter.ofPattern("d MMMM", locale) }

    var step by rememberSaveable { mutableIntStateOf(0) }
    var forward by remember { mutableStateOf(true) }
    var chosenGoals by rememberSaveable { mutableStateOf(listOf<Int>()) }
    var amount by rememberSaveable { mutableStateOf("") }
    var chosenPlaces by rememberSaveable { mutableStateOf(listOf("cash")) }
    var pay by rememberSaveable { mutableStateOf(PayKind.FIRST) }
    var logCat by rememberSaveable { mutableIntStateOf(0) }
    var logAmount by rememberSaveable { mutableStateOf(firstAmounts[1]) }
    var logged by rememberSaveable { mutableStateOf(false) }
    var reminder by rememberSaveable { mutableIntStateOf(1) }
    var reminderOn by rememberSaveable { mutableStateOf(false) }

    val balanceMinor = Money.parse(amount, currency) ?: 0L
    val days = PayCycle.daysUntilNextIncome(today, pay.day)
    val payDate = today.plusDays(days.toLong())
    val daily = balanceMinor / days.coerceAtLeast(1) / unit * unit
    val placeNames = places.associate { it.key to stringResource(it.label) }

    fun go(to: Int) {
        forward = to > step
        haptics.tick()
        step = to.coerceIn(0, STEPS - 1)
    }
    BackHandler(enabled = step in 1..6) { go(step - 1) }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { go(7) }

    fun finish() {
        // Pin the language the user saw, so Arabic gets Latin digits even when it came from the system locale.
        if (AppCompatDelegate.getApplicationLocales().isEmpty) applyAppLanguage(uiLanguage)
        viewModel.complete(currency, reminders[reminder].first.takeIf { reminderOn })
    }

    GlassStage(layout = step + 1) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 20.dp)) {
            TopBar(step, onBack = { go(step - 1) }, onLanguage = { applyAppLanguage(if (uiLanguage == "ar") "en" else "ar") }, arabic = uiLanguage == "ar")
            AnimatedContent(
                targetState = step,
                transitionSpec = {
                    val dir = if (forward) 1 else -1
                    (slideInHorizontally(Motion.spatial(reduce)) { it / 3 * dir } + fadeIn(Motion.effects(reduce))) togetherWith
                        (slideOutHorizontally(Motion.spatial(reduce)) { -it / 3 * dir } + fadeOut(Motion.snappy(reduce)))
                },
                modifier = Modifier.weight(1f).fillMaxWidth(),
                label = "onboardingStep",
            ) { s ->
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    when (s) {
                        0 -> Welcome()
                        1 -> {
                            Title(R.string.ob_goals_title, R.string.ob_goals_body)
                            goals.forEach { g ->
                                val on = g in chosenGoals
                                CheckRow(stringResource(g), on) { chosenGoals = if (on) chosenGoals - g else chosenGoals + g }
                            }
                            Text(
                                when (chosenGoals.size) {
                                    0 -> stringResource(R.string.ob_goals_hint_none)
                                    1 -> stringResource(R.string.ob_goals_hint_one)
                                    else -> stringResource(R.string.ob_goals_hint_many, chosenGoals.size)
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = glass.textTertiary,
                            )
                        }
                        2 -> {
                            Title(R.string.ob_balance_title, R.string.ob_balance_body)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.Bottom) {
                                Text(groupTyped(amount), style = MaterialTheme.typography.displayLarge, color = if (amount.isEmpty()) glass.textTertiary else MaterialTheme.colorScheme.onSurface)
                                Spacer(Modifier.width(8.dp))
                                Text(money.symbol(currency), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 12.dp))
                            }
                            Text(stringResource(R.string.ob_where), style = MaterialTheme.typography.bodySmall, color = glass.textTertiary)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                places.forEach { p ->
                                    val on = p.key in chosenPlaces
                                    Pill(stringResource(p.label), on) {
                                        chosenPlaces = if (on) (chosenPlaces - p.key).ifEmpty { chosenPlaces } else chosenPlaces + p.key
                                    }
                                }
                            }
                            SheetKeypad(allowDecimal = false, onKey = { k -> haptics.tick(); amount = applyAmountKey(amount, k, currency) }, onClear = { amount = "" })
                        }
                        3 -> {
                            Title(R.string.ob_pay_title, R.string.ob_pay_body)
                            PayKind.entries.forEach { k -> RadioRow(stringResource(k.label), stringResource(k.sub), k == pay) { pay = k } }
                            Timeline(days, today.format(dayMonth), payDate.format(dayMonth))
                        }
                        4 -> Reveal(daily, balanceMinor, days, currency, payDate.format(dayMonth))
                        5 -> FirstLog(
                            daily = daily, currency = currency, cat = logCat, onCat = { logCat = it }, amount = logAmount, onAmount = { logAmount = it }, logged = logged,
                        )
                        6 -> {
                            Title(R.string.ob_remind_title, R.string.ob_remind_body)
                            reminders.forEachIndexed { i, (hour, label) ->
                                RadioRow(stringResource(R.string.ob_remind_time, if (hour > 12) hour - 12 else hour), stringResource(label), i == reminder) { reminder = i }
                            }
                            NotificationPreview(
                                if (logged) stringResource(R.string.remind_log_title, money.format((daily - logAmount * unit).coerceAtLeast(0), currency))
                                else stringResource(R.string.remind_log_title, money.format(daily, currency)),
                                stringResource(if (logged) R.string.ob_notif_body_logged else R.string.remind_log_body_start),
                            )
                            InfoCard(MIcons.Flame, R.string.ob_freeze_title, R.string.ob_freeze_body)
                        }
                        else -> Done(
                            daily = money.format(daily, currency),
                            reminder = if (reminderOn) stringResource(R.string.ob_remind_time, reminders[reminder].first - 12) + " · " + stringResource(reminders[reminder].second) else stringResource(R.string.ob_remind_off),
                            goal = chosenGoals.firstOrNull()?.let { stringResource(it) } ?: "—",
                            logged = logged,
                        )
                    }
                }
            }
            // The step's action, pinned at the bottom.
            val primary: Pair<Int, () -> Unit> = when (step) {
                0 -> R.string.ob_start to { go(1) }
                1 -> R.string.onboarding_continue to { go(2) }
                2 -> R.string.onboarding_continue to { go(3) }
                3 -> R.string.ob_calc to {
                    viewModel.setup(currency, places.filter { it.key in chosenPlaces }.map { placeNames.getValue(it.key) to it.type }, balanceMinor, pay.day)
                    go(4)
                }
                4 -> R.string.ob_try to { go(5) }
                5 -> R.string.ob_log to {
                    if (!logged) {
                        haptics.confirm()
                        viewModel.logFirst(firstLogs[logCat].second, logAmount * unit, currency)
                        logged = true
                    }
                }
                6 -> R.string.ob_remind_on to {
                    reminderOn = true
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS) else go(7)
                }
                else -> R.string.ob_done to { haptics.confirm(); finish() }
            }
            // After the first log, the streak burst plays briefly and the flow moves on by itself.
            if (step == 5 && logged) LaunchedEffect(Unit) { delay(1_400); go(6) }
            GlassButton(
                onClick = primary.second,
                enabled = when (step) {
                    1 -> chosenGoals.isNotEmpty()
                    2 -> balanceMinor > 0
                    5 -> !logged
                    else -> true
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                Text(stringResource(primary.first), style = MaterialTheme.typography.titleMedium)
                if (step == 5) {
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.home_points, 10), style = MaterialTheme.typography.labelMedium)
                }
            }
            val secondary: Pair<Int, () -> Unit>? = when (step) {
                0 -> R.string.ob_have_backup to { StartRequest.backup = true; finish() }
                5 -> R.string.ob_later to { go(6) }
                6 -> R.string.ob_not_now to { reminderOn = false; go(7) }
                else -> null
            }
            Box(Modifier.fillMaxWidth().height(52.dp), contentAlignment = Alignment.Center) {
                secondary?.let { (label, action) -> TextButton(onClick = action) { Text(stringResource(label), color = MaterialTheme.colorScheme.secondary) } }
            }
        }
    }
}

@Composable
private fun TopBar(step: Int, onBack: () -> Unit, onLanguage: () -> Unit, arabic: Boolean) {
    val glass = LocalGlass.current
    Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
        if (step in 1..6) {
            Box(Modifier.size(44.dp).clip(CircleShape).background(glass.fill).clickable(role = Role.Button, onClick = onBack), contentAlignment = Alignment.Center) {
                Icon(MIcons.Back, contentDescription = stringResource(R.string.action_back), modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            // Six segments for the six middle steps; welcome and done stand on their own.
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(6) { i ->
                    Box(Modifier.weight(1f).height(4.dp).clip(CircleShape).background(if (i < step) MaterialTheme.colorScheme.primary else glass.track))
                }
            }
            Spacer(Modifier.width(56.dp))
        } else if (step == 0) {
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onLanguage) { Text(if (arabic) "English" else "العربية") }
        }
    }
}

@Composable
private fun Title(title: Int, body: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 8.dp)) {
        Text(stringResource(title), style = MaterialTheme.typography.displaySmall)
        Text(stringResource(body), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** فلوسك في إيدك: the coin drops into the pocket, then the promise and three plain facts. */
@Composable
private fun Welcome() {
    val reduce = LocalReduceMotion.current
    val drop = remember { Animatable(if (reduce) 0f else 60f) }
    LaunchedEffect(Unit) { drop.animateTo(0f, spring(dampingRatio = 0.55f, stiffness = 140f)) }
    val glass = LocalGlass.current
    Column(Modifier.fillMaxWidth().padding(top = 24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        PocketMark(Modifier.size(160.dp), coinDrop = { drop.value })
        Text(stringResource(R.string.ob_welcome_title), style = MaterialTheme.typography.displaySmall, textAlign = TextAlign.Center)
        Text(stringResource(R.string.ob_welcome_body), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        listOf(MIcons.Check to R.string.ob_fact_no_math, MIcons.Cloud to R.string.ob_fact_offline, MIcons.Lock to R.string.ob_fact_yours).forEach { (icon, text) ->
            Row(
                Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(glass.fill).border(1.dp, glass.rim, MaterialTheme.shapes.medium).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Text(stringResource(text), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun CheckRow(label: String, on: Boolean, onClick: () -> Unit) {
    val glass = LocalGlass.current
    val lime = MaterialTheme.colorScheme.primary
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(if (on) lime.copy(alpha = 0.10f) else glass.fill)
            .border(1.dp, if (on) lime else glass.strokeBottom, MaterialTheme.shapes.medium)
            .clickable(role = Role.Checkbox, onClick = onClick)
            .semantics { selected = on }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Box(Modifier.size(24.dp).clip(CircleShape).background(if (on) lime else Color.Transparent).border(1.5.dp, if (on) lime else glass.textTertiary, CircleShape), contentAlignment = Alignment.Center) {
            if (on) Icon(MIcons.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun RadioRow(label: String, sub: String, on: Boolean, onClick: () -> Unit) {
    val glass = LocalGlass.current
    val lime = MaterialTheme.colorScheme.primary
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(if (on) lime.copy(alpha = 0.10f) else glass.fill)
            .border(1.dp, if (on) lime else glass.strokeBottom, MaterialTheme.shapes.medium)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics { selected = on }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleSmall)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = glass.textTertiary)
        }
        Box(Modifier.size(22.dp).clip(CircleShape).border(2.dp, if (on) lime else glass.textTertiary, CircleShape), contentAlignment = Alignment.Center) {
            if (on) Box(Modifier.size(10.dp).clip(CircleShape).background(lime))
        }
    }
}

@Composable
private fun Pill(label: String, on: Boolean, onClick: () -> Unit) {
    val glass = LocalGlass.current
    val lime = MaterialTheme.colorScheme.primary
    Text(
        label,
        style = MaterialTheme.typography.labelLarge,
        color = if (on) lime else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .heightIn(min = 40.dp)
            .clip(CircleShape)
            .background(if (on) lime.copy(alpha = 0.12f) else glass.fill)
            .border(1.dp, if (on) lime else glass.strokeBottom, CircleShape)
            .clickable(role = Role.Checkbox, onClick = onClick)
            .semantics { selected = on }
            .padding(horizontal = 16.dp, vertical = 10.dp),
    )
}

/** Today to the next money, as a bar with the day count in the middle. */
@Composable
private fun Timeline(days: Int, todayLabel: String, payLabel: String) {
    val glass = LocalGlass.current
    Column(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(glass.fill).border(1.dp, glass.rim, MaterialTheme.shapes.large).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
            Box(Modifier.weight(1f).height(3.dp).padding(horizontal = 6.dp).background(glass.track))
            Text(stringResource(R.string.ob_days_count, days), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Box(Modifier.weight(1f).height(3.dp).padding(horizontal = 6.dp).background(glass.track))
            Box(Modifier.size(10.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondary))
        }
        Row {
            Text(stringResource(R.string.ob_today_label, todayLabel), style = MaterialTheme.typography.labelSmall, color = glass.textTertiary, modifier = Modifier.weight(1f))
            Text(payLabel, style = MaterialTheme.typography.labelSmall, color = glass.textTertiary)
        }
    }
}

/** رقمك اليومي جاهز: the ring fills while the number counts up, then the sum that made it. */
@Composable
private fun Reveal(daily: Long, balance: Long, days: Int, currency: String, payDate: String) {
    val money = LocalMoneyFormatter.current
    val glass = LocalGlass.current
    val lime = MaterialTheme.colorScheme.primary
    val reduce = LocalReduceMotion.current
    val t = remember { Animatable(if (reduce) 1f else 0f) }
    LaunchedEffect(Unit) { t.animateTo(1f, tween(1_400, easing = FastOutSlowInEasing)) }
    val track = glass.fillStrong
    Column(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(stringResource(R.string.ob_reveal_label), style = MaterialTheme.typography.labelLarge, color = lime)
        Box(Modifier.size(220.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = 14.dp.toPx()
                val arc = Size(size.width - stroke, size.height - stroke)
                drawArc(track, -90f, 360f, false, Offset(stroke / 2, stroke / 2), arc, style = Stroke(stroke))
                drawArc(lime, -90f, 360f * t.value, false, Offset(stroke / 2, stroke / 2), arc, style = Stroke(stroke, cap = StrokeCap.Round))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(money.formatCompact((daily * t.value).toLong(), currency), style = MaterialTheme.typography.displayLarge, color = lime)
                Text(stringResource(R.string.ob_reveal_caption, money.symbol(currency)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(
            "${money.format(balance, currency)} ÷ ${stringResource(R.string.ob_days_count, days)} = ${money.format(daily, currency)}",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.graphicsLayer { alpha = t.value },
        )
        Text(stringResource(R.string.ob_until, payDate), style = MaterialTheme.typography.bodySmall, color = glass.textTertiary)
        Text(stringResource(R.string.ob_reveal_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FirstLog(daily: Long, currency: String, cat: Int, onCat: (Int) -> Unit, amount: Long, onAmount: (Long) -> Unit, logged: Boolean) {
    val money = LocalMoneyFormatter.current
    val glass = LocalGlass.current
    val unit = Money.unit(currency)
    val streak = LocalFinanceColors.current.streak
    Title(R.string.ob_log_title, R.string.ob_log_body)
    Column(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(glass.fill).border(1.dp, glass.rim, MaterialTheme.shapes.large).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.ob_log_preview), style = MaterialTheme.typography.bodySmall, color = glass.textTertiary)
        Text(money.format((daily - amount * unit).coerceAtLeast(0), currency), style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary)
        Text(money.format(daily, currency), style = MaterialTheme.typography.bodySmall, color = glass.textTertiary)
    }
    Text(stringResource(R.string.add_on_what), style = MaterialTheme.typography.bodySmall, color = glass.textTertiary)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        firstLogs.forEachIndexed { i, (label, _) -> Pill(stringResource(label), i == cat) { onCat(i) } }
    }
    Text(stringResource(R.string.ob_how_much), style = MaterialTheme.typography.bodySmall, color = glass.textTertiary)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        firstAmounts.forEach { a -> Pill(money.formatCompact(a * unit, currency), a == amount) { onAmount(a) } }
    }
    if (logged) {
        // Streak burst: the flame pops and day 1 lands.
        val pop = remember { Animatable(0.4f) }
        LaunchedEffect(Unit) { pop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 300f)) }
        Row(
            Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(streak.copy(alpha = 0.14f)).padding(16.dp).graphicsLayer { scaleX = pop.value; scaleY = pop.value },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(MIcons.Flame, contentDescription = null, tint = streak, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(12.dp))
            Text(stringResource(R.string.ob_streak_started), style = MaterialTheme.typography.titleMedium, color = streak)
        }
    }
}

/** How tonight's reminder will look, in the shape of a notification. */
@Composable
private fun NotificationPreview(title: String, body: String) {
    val glass = LocalGlass.current
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.ob_notif_preview), style = MaterialTheme.typography.labelSmall, color = glass.textTertiary)
        Row(
            Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(glass.fillStrong).border(1.dp, glass.rim, MaterialTheme.shapes.large).padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PocketMark(Modifier.size(32.dp))
            Spacer(Modifier.width(12.dp))
            Column {
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.labelSmall, color = glass.textTertiary)
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun InfoCard(icon: androidx.compose.ui.graphics.vector.ImageVector, title: Int, body: Int) {
    val glass = LocalGlass.current
    val blue = LocalFinanceColors.current.transfer
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(blue.copy(alpha = 0.10f)).padding(16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(icon, contentDescription = null, tint = blue, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Column {
            Text(stringResource(title), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(body), style = MaterialTheme.typography.bodySmall, color = glass.textTertiary)
        }
    }
}

/** جاهز: what was set up, the points earned and, after a first log, the first badge. */
@Composable
private fun Done(daily: String, reminder: String, goal: String, logged: Boolean) {
    val glass = LocalGlass.current
    val lime = MaterialTheme.colorScheme.primary
    val reduce = LocalReduceMotion.current
    val pop = remember { Animatable(if (reduce) 1f else 0.6f) }
    LaunchedEffect(Unit) { pop.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 220f)) }
    Column(Modifier.fillMaxWidth().padding(top = 24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(120.dp).graphicsLayer { scaleX = pop.value; scaleY = pop.value }.clip(CircleShape).background(lime.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
            Icon(if (logged) MIcons.Trophy else MIcons.Check, contentDescription = null, tint = lime, modifier = Modifier.size(52.dp))
        }
        Text(stringResource(R.string.ob_done_title), style = MaterialTheme.typography.displaySmall)
        Text(stringResource(R.string.ob_done_body), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(
            Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(glass.fill).border(1.dp, glass.rim, MaterialTheme.shapes.large).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SummaryRow(stringResource(R.string.ob_sum_daily), daily)
            SummaryRow(stringResource(R.string.ob_sum_reminder), reminder)
            SummaryRow(stringResource(R.string.ob_sum_goal), goal)
            SummaryRow(stringResource(R.string.ob_sum_streak), stringResource(if (logged) R.string.ob_sum_streak_day1 else R.string.ob_sum_streak_start))
        }
        Text(
            stringResource(R.string.home_points, if (logged) 30 else 20) + if (logged) " · " + stringResource(R.string.ob_badge_first) else "",
            style = MaterialTheme.typography.titleSmall,
            color = lime,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = LocalGlass.current.textTertiary, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
