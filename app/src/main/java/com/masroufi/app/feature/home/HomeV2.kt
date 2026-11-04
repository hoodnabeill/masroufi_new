package com.masroufi.app.feature.home

import android.content.Intent
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.masroufi.app.R
import androidx.compose.ui.unit.sp
import com.masroufi.app.core.Money
import com.masroufi.app.data.model.Category
import com.masroufi.app.data.model.LastSpend
import com.masroufi.app.data.model.TransactionRow
import com.masroufi.app.domain.Insight
import com.masroufi.app.domain.PayCycle
import com.masroufi.app.domain.ProgressSnapshot
import com.masroufi.app.domain.QuickLog
import com.masroufi.app.domain.QuickLogParser
import com.masroufi.app.feature.progress.levelTitle
import com.masroufi.app.ui.components.CategoryIcons
import com.masroufi.app.ui.components.FitAmount
import com.masroufi.app.ui.components.GlassIconButton
import com.masroufi.app.ui.components.IconBadge
import com.masroufi.app.ui.components.LocalMoneyFormatter
import com.masroufi.app.ui.components.MIcons
import com.masroufi.app.ui.components.MoneyText
import com.masroufi.app.ui.components.TransactionItem
import com.masroufi.app.ui.components.categoryColor
import com.masroufi.app.ui.components.categoryLabel
import com.masroufi.app.ui.components.liquidPress
import com.masroufi.app.ui.components.rememberHaptics
import com.masroufi.app.ui.theme.LocalFinanceColors
import com.masroufi.app.ui.theme.LocalGlass
import com.masroufi.app.ui.theme.LocalReduceMotion
import com.masroufi.app.ui.theme.Motion
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

private const val HIDDEN = "•••"

/** Section title in the handoff style: H2 Readex 18 on the left, an optional violet link on the right. */
@Composable
internal fun HomeSectionTitle(title: String, link: String? = null, onLink: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f).semantics { heading() })
        if (link != null && onLink != null) {
            Text(
                link,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.clip(CircleShape).clickable(role = Role.Button, onClick = onLink).padding(horizontal = 8.dp, vertical = 6.dp),
            )
        }
    }
}

/** Date, greeting, and the amber streak pill that opens Achievements. */
@Composable
internal fun HomeHeader(streak: Int, onOpenAchievements: () -> Unit, onCustomize: () -> Unit) {
    val hour = remember { java.time.LocalTime.now().hour }
    val greeting = stringResource(
        when (hour) {
            in 5..11 -> R.string.home_greeting_morning
            in 12..16 -> R.string.home_greeting_afternoon
            else -> R.string.home_greeting_evening
        },
    )
    val locale = LocalConfiguration.current.locales[0]
    val date = remember(locale) { LocalDate.now().format(DateTimeFormatter.ofPattern(if (locale.language == "ar") "EEEE، d MMMM" else "EEEE, d MMMM", locale)) }
    val amber = LocalFinanceColors.current.streak
    val streakLabel = pluralStringResource(R.plurals.streak_days, streak, streak)
    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(top = 12.dp, start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(date, style = MaterialTheme.typography.bodySmall, color = LocalGlass.current.textTertiary, maxLines = 1)
            Text(greeting, style = MaterialTheme.typography.headlineLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Row(
            Modifier
                .height(44.dp)
                .clip(CircleShape)
                .background(amber.copy(alpha = 0.16f))
                .border(1.dp, amber.copy(alpha = 0.3f), CircleShape)
                .clickable(role = Role.Button, onClick = onOpenAchievements)
                .semantics { contentDescription = streakLabel }
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(MIcons.Flame, contentDescription = null, tint = amber, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(streak.toString(), style = MaterialTheme.typography.titleMedium.copy(fontFamily = MaterialTheme.typography.headlineSmall.fontFamily), color = amber)
        }
        Spacer(Modifier.width(8.dp))
        GlassIconButton(onClick = onCustomize) { Icon(MIcons.Tune, contentDescription = stringResource(R.string.home_customize)) }
    }
}

/**
 * The hero: "تقدر تصرف النهارده" in big lime numbers, the morning balance spread over the days to the next income;
 * a bar for how much of today's number is spent; then balance, month spending and month income. The eye hides
 * every number on Home (kept across launches).
 */
@Composable
internal fun SafeHero(state: HomeUiState, spentToday: Long, hidden: Boolean, onToggleHidden: () -> Unit, onOpenWallets: () -> Unit) {
    val today = remember { LocalDate.now() }
    val payday = state.settings.paydayDay
    val currency = state.currency
    val unit = pow10(Money.fractionDigits(currency))
    // The daily number is shown in whole pounds, rounded down so it never overstates what is safe.
    val safe = PayCycle.safeToday(state.primaryBalance, spentToday, today, payday) / unit * unit
    val days = PayCycle.daysUntilNextIncome(today, payday)
    val lime = MaterialTheme.colorScheme.primary
    val over = LocalFinanceColors.current.over
    val fraction = if (safe > 0) spentToday.toFloat() / safe else if (spentToday > 0) 1f else 0f
    val bar by animateFloatAsState(fraction.coerceIn(0f, 1f), Motion.spatial(LocalReduceMotion.current), label = "todayBar")
    val formatter = LocalMoneyFormatter.current
    val glass = LocalGlass.current
    val cycle = PayCycle.isSet(payday)

    Column(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraLarge)
            .background(glass.fill)
            .border(1.dp, glass.rim, MaterialTheme.shapes.extraLarge)
            .clickable(role = Role.Button, onClick = onOpenWallets)
            .padding(22.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.home_safe_today), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(glass.fillStrong).clickable(role = Role.Button, onClick = onToggleHidden),
                contentAlignment = Alignment.Center,
            ) {
                Icon(if (hidden) MIcons.EyeOff else MIcons.Eye, contentDescription = stringResource(R.string.home_privacy_toggle), tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            }
        }
        if (hidden) {
            Text(HIDDEN, style = HeroType, color = lime)
        } else {
            FitAmount(safe, currency, style = HeroType.copy(fontFeatureSettings = "tnum"), color = if (safe > 0) lime else over)
        }
        Text(
            stringResource(if (cycle) R.string.home_safe_caption_payday else R.string.home_safe_caption_month) + " · " +
                pluralStringResource(R.plurals.home_days_left, days, days),
            style = MaterialTheme.typography.bodySmall,
            color = glass.textTertiary,
        )
        Spacer(Modifier.height(14.dp))
        Box(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(glass.track)) {
            Box(Modifier.fillMaxWidth(bar.coerceAtLeast(if (spentToday > 0) 0.02f else 0f)).height(8.dp).clip(CircleShape).background(if (fraction > 1f) over else lime))
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth()) {
            Text(
                stringResource(R.string.home_spent_today, if (hidden) HIDDEN else formatter.format(spentToday, currency)),
                style = MaterialTheme.typography.bodySmall, color = glass.textTertiary, modifier = Modifier.weight(1f),
            )
            Text(stringResource(R.string.home_limit, if (hidden) HIDDEN else formatter.format(safe, currency)), style = MaterialTheme.typography.bodySmall, color = glass.textTertiary)
        }
        Spacer(Modifier.height(14.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(glass.strokeBottom))
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth()) {
            HeroStat(stringResource(R.string.home_have), state.primaryBalance, currency, hidden, Modifier.weight(1f), divider = false)
            HeroStat(stringResource(if (cycle) R.string.home_spent_cycle else R.string.home_spent_month), state.expenseMinor, currency, hidden, Modifier.weight(1f))
            HeroStat(stringResource(if (cycle) R.string.home_income_cycle else R.string.home_income_month), state.incomeMinor, currency, hidden, Modifier.weight(1f))
        }
    }
}

/** The handoff hero number: 68 sp bold, line height 1.05, -2 px tracking (a notch above displayLarge). */
private val HeroType: androidx.compose.ui.text.TextStyle
    @Composable get() = MaterialTheme.typography.displayLarge.copy(fontSize = 68.sp, lineHeight = 72.sp)

private fun pow10(n: Int): Long = (1..n).fold(1L) { acc, _ -> acc * 10 }

@Composable
private fun HeroStat(label: String, minor: Long, currency: String, hidden: Boolean, modifier: Modifier, divider: Boolean = true) {
    val glass = LocalGlass.current
    Row(modifier) {
        if (divider) Box(Modifier.width(1.dp).height(44.dp).background(glass.strokeBottom))
        Column(Modifier.padding(start = if (divider) 12.dp else 0.dp)) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = glass.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            if (hidden) {
                Text(HIDDEN, style = MaterialTheme.typography.titleLarge)
            } else {
                FitAmount(minor, currency, style = MaterialTheme.typography.headlineSmall.copy(fontFeatureSettings = "tnum"))
            }
        }
    }
}

/** Streak with this week's days (Saturday first, as the Egyptian week runs), points for today, and the level bar. */
@Composable
internal fun StreakCard(snapshot: ProgressSnapshot, onOpen: () -> Unit) {
    val glass = LocalGlass.current
    val amber = LocalFinanceColors.current.streak
    val streak = snapshot.streak
    val today = remember { LocalDate.now() }
    val weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.SATURDAY))
    val names = stringArrayResource(R.array.week_short)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(glass.fill)
            .border(1.dp, glass.rim, MaterialTheme.shapes.large)
            .clickable(role = Role.Button, onClick = onOpen)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(MIcons.Flame, amber, size = 44.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (streak.current > 0) pluralStringResource(R.plurals.streak_days, streak.current, streak.current) else stringResource(R.string.streak_start),
                    style = MaterialTheme.typography.headlineSmall.copy(fontSize = MaterialTheme.typography.titleSmall.fontSize),
                )
                Text(
                    stringResource(if (streak.loggedToday) R.string.streak_done else if (streak.current > 0) R.string.streak_keep else R.string.streak_start),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!streak.loggedToday) {
                Text(
                    stringResource(R.string.home_points, 10),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.primaryContainer).padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }
        Row(Modifier.fillMaxWidth()) {
            (0 until 7).forEach { i ->
                val day = weekStart.plusDays(i.toLong())
                val logged = day in snapshot.logDays
                val isToday = day == today
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        names.getOrElse(i) { "" },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isToday) amber else glass.textTertiary,
                    )
                    Spacer(Modifier.height(6.dp))
                    DayDot(logged = logged, today = isToday, past = day.isBefore(today), amber = amber)
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val level = snapshot.level
            Row(Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.level_label, level.number, levelTitle(level)), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                level.nextXp?.let { next ->
                    Text(stringResource(R.string.home_level_progress, level.xp - level.floorXp, next - level.floorXp, level.number + 1), style = MaterialTheme.typography.labelMedium, color = glass.textTertiary)
                }
            }
            Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(glass.track)) {
                Box(Modifier.fillMaxWidth(level.progress.coerceIn(0.02f, 1f)).height(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondary))
            }
        }
    }
}

@Composable
private fun DayDot(logged: Boolean, today: Boolean, past: Boolean, amber: Color) {
    val glass = LocalGlass.current
    val dashed = remember { PathEffect.dashPathEffect(floatArrayOf(8f, 6f)) }
    Box(
        Modifier.size(32.dp).drawBehind {
            val r = size.minDimension / 2
            when {
                logged -> drawCircle(amber, r)
                today -> drawCircle(amber, r - 1.dp.toPx(), style = Stroke(2.dp.toPx()))
                past -> drawCircle(glass.track, r - 0.5.dp.toPx(), style = Stroke(1.dp.toPx(), pathEffect = dashed))
                else -> drawCircle(glass.fillStrong, r)
            }
            if (today && !logged) drawCircle(amber, 4.dp.toPx())
        },
        contentAlignment = Alignment.Center,
    ) {
        if (logged) Icon(MIcons.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiary, modifier = Modifier.size(16.dp))
    }
}

/**
 * "سجّل في ثانية": type `50 قهوة` (or say it) and the editor opens with the amount, category and note filled in; or
 * tap a chip for a recent category, prefilled with its last amount.
 */
@Composable
internal fun QuickLogSection(
    lastSpends: List<LastSpend>,
    categories: Map<String, Category>,
    currency: String,
    onLog: (amount: String?, categoryId: String?, note: String?) -> Unit,
    onAllCategories: () -> Unit,
) {
    val context = LocalContext.current
    val haptics = rememberHaptics()
    var text by rememberSaveable { mutableStateOf("") }
    val glass = LocalGlass.current
    val violet = MaterialTheme.colorScheme.secondary
    val locale = LocalConfiguration.current.locales[0]

    fun submit(input: String) {
        if (input.isBlank()) return
        val parsed: QuickLog = QuickLogParser.parse(input, currency)
        haptics.confirm()
        onLog(parsed.amountMinor?.let { Money.toPlainString(it, currency) }, parsed.categoryKey?.let { "sys_$it" }?.takeIf { it in categories }, parsed.note.ifBlank { null })
        text = ""
    }

    val voice = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let(::submit)
    }
    val canSpeak = remember { SpeechRecognizer.isRecognitionAvailable(context) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        HomeSectionTitle(stringResource(R.string.home_quick_title), stringResource(R.string.home_all_categories), onAllCategories)
        Row(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(CircleShape)
                .background(glass.fill)
                .border(1.dp, glass.rim, CircleShape)
                .padding(start = 16.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(MIcons.Sparkle, contentDescription = null, tint = violet, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            val label = stringResource(R.string.home_type_label)
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (text.isEmpty()) Text(stringResource(R.string.home_type_hint), style = MaterialTheme.typography.bodyLarge, color = glass.textTertiary, maxLines = 1)
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(violet),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit(text) }),
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = label },
                )
            }
            if (text.isNotBlank()) {
                SmallRound(MIcons.Check, stringResource(R.string.home_type_send), MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary) { submit(text) }
            } else if (canSpeak) {
                SmallRound(MIcons.Mic, stringResource(R.string.home_voice), MaterialTheme.colorScheme.secondaryContainer, violet) {
                    voice.launch(
                        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, if (locale.language == "ar") "ar-EG" else locale.toLanguageTag())
                            // Ask for on-device recognition so nothing has to leave the phone where the device supports it.
                            .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true),
                    )
                }
            }
        }
        val chips = quickChips(lastSpends, categories)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            chips.forEach { (category, last) ->
                val interaction = remember { MutableInteractionSource() }
                Row(
                    Modifier
                        .height(56.dp)
                        .liquidPress(interaction, MaterialTheme.shapes.small, scaleTo = 0.97f)
                        .clip(MaterialTheme.shapes.small)
                        .background(glass.fill)
                        .border(1.dp, glass.rim, MaterialTheme.shapes.small)
                        .clickable(interactionSource = interaction, indication = null, role = Role.Button) {
                            haptics.tick()
                            onLog(last?.let { Money.toPlainString(it.amountMinor, it.currency) }, category.id, null)
                        }
                        .padding(start = 10.dp, end = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconBadge(CategoryIcons.of(category.iconKey), categoryColor(category.colorIndex), size = 36.dp)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(categoryLabel(category), style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                        Text(
                            last?.let { stringResource(R.string.home_last_time, LocalMoneyFormatter.current.formatCompact(it.amountMinor, it.currency)) }
                                ?: stringResource(R.string.home_first_time),
                            style = MaterialTheme.typography.labelSmall,
                            color = glass.textTertiary,
                        )
                    }
                }
            }
        }
    }
}

/** Recent categories with their last amount first, then everyday defaults the user has not used yet. */
private fun quickChips(lastSpends: List<LastSpend>, categories: Map<String, Category>): List<Pair<Category, LastSpend?>> {
    val used = lastSpends.mapNotNull { last -> categories[last.categoryId]?.let { it to last } }
    val fallback = listOf("sys_transport", "sys_food", "sys_groceries", "sys_mobile", "sys_bills")
        .filter { id -> used.none { it.first.id == id } }
        .mapNotNull { id -> categories[id]?.let { it to null } }
    return (used + fallback).take(5)
}

@Composable
private fun SmallRound(icon: ImageVector, description: String, background: Color, tint: Color, onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).clip(CircleShape).background(background).clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(18.dp)) }
}

/** "ملاحظات ذكية": one wide card per insight, toned by kind (coral alert, violet discovery, amber coming up). */
@Composable
internal fun InsightsRow(state: HomeUiState, onOpenAll: () -> Unit, onMakeBudget: () -> Unit) {
    val finance = LocalFinanceColors.current
    val violet = MaterialTheme.colorScheme.secondary
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        HomeSectionTitle(stringResource(R.string.home_insights_title), stringResource(R.string.home_insights_all, state.insights.size), onOpenAll)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            state.insights.take(5).forEach { insight ->
                val (tone, tag, icon) = when (insight) {
                    is Insight.BudgetAlert, is Insight.CategoryRising, is Insight.MonthEndProjection ->
                        Triple(finance.over, R.string.insight_tag_alert, MIcons.Trending)
                    is Insight.UpcomingRecurring -> Triple(finance.warning, R.string.insight_tag_soon, MIcons.Calendar)
                    else -> Triple(violet, R.string.insight_tag_discovery, MIcons.Budgets)
                }
                val alert = tone == finance.over
                Column(
                    Modifier
                        .widthIn(max = 300.dp)
                        .width(300.dp)
                        .heightIn(min = 150.dp)
                        .clip(MaterialTheme.shapes.large)
                        .background(tone.copy(alpha = 0.08f))
                        .border(1.dp, tone.copy(alpha = 0.28f), MaterialTheme.shapes.large)
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(icon, tone, size = 28.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(tag), style = MaterialTheme.typography.labelMedium, color = tone)
                    }
                    Text(insightText(insight, state), style = MaterialTheme.typography.headlineSmall.copy(fontSize = MaterialTheme.typography.titleLarge.fontSize), maxLines = 3, overflow = TextOverflow.Ellipsis)
                    if (alert && insight !is Insight.BudgetAlert) {
                        Text(
                            stringResource(R.string.insight_cta_budget),
                            style = MaterialTheme.typography.labelLarge,
                            color = Color(0xFF1E0D09),
                            modifier = Modifier.clip(CircleShape).background(tone).clickable(role = Role.Button, onClick = onMakeBudget).padding(horizontal = 18.dp, vertical = 10.dp),
                        )
                    }
                }
            }
        }
    }
}

/** "صرف نوفمبر": the month's total, a stacked bar of the top categories, and a row per category with its share. */
@Composable
internal fun MonthSpendCard(state: HomeUiState, hidden: Boolean, onOpenDetails: () -> Unit) {
    val glass = LocalGlass.current
    val locale = LocalConfiguration.current.locales[0]
    val month = remember(locale) { LocalDate.now().format(DateTimeFormatter.ofPattern("MMMM", locale)) }
    val total = state.spending.sumOf { it.second }.coerceAtLeast(1)
    val top = state.spending.take(5)
    val today = remember { LocalDate.now() }
    val days = today.dayOfMonth
    Column(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(glass.fill).border(1.dp, glass.rim, MaterialTheme.shapes.large).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        HomeSectionTitle(stringResource(R.string.home_month_spend, month), stringResource(R.string.home_details), onOpenDetails)
        Row(verticalAlignment = Alignment.Bottom) {
            if (hidden) Text(HIDDEN, style = MaterialTheme.typography.displaySmall) else FitAmount(state.expenseMinor, state.currency, style = MaterialTheme.typography.displaySmall.copy(fontSize = MaterialTheme.typography.displaySmall.fontSize * 1.2f, fontFeatureSettings = "tnum"), modifier = Modifier.weight(1f, fill = false))
            Spacer(Modifier.width(8.dp))
            Text(pluralStringResource(R.plurals.home_in_days, days, days), style = MaterialTheme.typography.bodySmall, color = glass.textTertiary, modifier = Modifier.padding(bottom = 6.dp))
        }
        Row(Modifier.fillMaxWidth().height(12.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            top.forEach { (category, amount) ->
                Box(Modifier.weight(amount.toFloat().coerceAtLeast(1f)).height(12.dp).clip(RoundedCornerShape(6.dp)).background(categoryColor(category?.colorIndex)))
            }
        }
        top.forEach { (category, amount) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(categoryColor(category?.colorIndex)))
                Spacer(Modifier.width(12.dp))
                Text(categoryLabel(category), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1)
                Text("${(amount * 100 / total)}%", style = MaterialTheme.typography.bodySmall, color = glass.textTertiary, modifier = Modifier.width(44.dp))
                if (hidden) Text(HIDDEN, style = MaterialTheme.typography.titleSmall) else MoneyText(amount, state.currency, style = MaterialTheme.typography.titleSmall)
            }
        }
    }
}

/** "آخر حاجات": recent entries grouped by day, each day headed by its name and what was spent. */
@Composable
internal fun RecentByDay(rows: List<TransactionRow>, currency: String, onOpen: (String) -> Unit, onSeeAll: () -> Unit) {
    val glass = LocalGlass.current
    val locale = LocalConfiguration.current.locales[0]
    val zone = remember { ZoneId.systemDefault() }
    val today = remember { LocalDate.now() }
    val formatter = LocalMoneyFormatter.current
    val todayLabel = stringResource(R.string.day_today)
    val yesterdayLabel = stringResource(R.string.day_yesterday)
    val dayFormat = remember(locale) { DateTimeFormatter.ofPattern("EEEE d MMMM", locale) }
    val weekdayFormat = remember(locale) { DateTimeFormatter.ofPattern("EEEE", locale) }
    val groups = rows.groupBy { java.time.Instant.ofEpochMilli(it.transaction.occurredAt).atZone(zone).toLocalDate() }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        HomeSectionTitle(stringResource(R.string.home_recent), stringResource(R.string.action_see_all), onSeeAll)
        Column(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(glass.fill).border(1.dp, glass.rim, MaterialTheme.shapes.large).padding(vertical = 6.dp)) {
            groups.entries.forEachIndexed { index, (day, items) ->
                if (index > 0) Box(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp).height(1.dp).background(glass.strokeBottom))
                val label = when (day) {
                    today -> "$todayLabel · ${day.format(weekdayFormat)}"
                    today.minusDays(1) -> "$yesterdayLabel · ${day.format(weekdayFormat)}"
                    else -> day.format(dayFormat)
                }
                val spent = items.filter { it.transaction.type == com.masroufi.app.data.model.TransactionType.EXPENSE && it.transaction.currency == currency }.sumOf { it.transaction.amountMinor }
                Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 2.dp)) {
                    Text(label, style = MaterialTheme.typography.bodySmall, color = glass.textTertiary, modifier = Modifier.weight(1f))
                    if (spent > 0) Text(stringResource(R.string.home_day_spent, formatter.format(spent, currency)), style = MaterialTheme.typography.bodySmall, color = glass.textTertiary)
                }
                items.forEach { row -> TransactionItem(row, onClick = { onOpen(row.transaction.id) }, showDate = false) }
            }
        }
    }
}

