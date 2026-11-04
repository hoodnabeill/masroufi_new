package com.masroufi.app.feature.recap

import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.masroufi.app.R
import com.masroufi.app.core.DateWindow
import com.masroufi.app.data.model.Category
import com.masroufi.app.data.model.TransactionType
import com.masroufi.app.data.prefs.SettingsRepository
import com.masroufi.app.data.repo.CategoryRepository
import com.masroufi.app.data.repo.ProgressRepository
import com.masroufi.app.data.repo.TransactionRepository
import com.masroufi.app.domain.Badge
import com.masroufi.app.domain.HealthMath
import com.masroufi.app.domain.RecapStats
import com.masroufi.app.ui.components.GlassButton
import com.masroufi.app.ui.components.GlassSwitch
import com.masroufi.app.ui.components.LocalMoneyFormatter
import com.masroufi.app.ui.components.MIcons
import com.masroufi.app.ui.components.categoryLabel
import com.masroufi.app.ui.theme.LocalReduceMotion
import com.masroufi.app.ui.theme.Motion
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import javax.inject.Inject

data class RecapState(
    val stats: RecapStats,
    val currency: String,
    val topCategory: Category?,
    val bestStreak: Int,
    val loggedDays: Set<LocalDate>,
    val greenMonth: Boolean,
)

@HiltViewModel
class RecapViewModel @Inject constructor(
    transactions: TransactionRepository,
    categories: CategoryRepository,
    progress: ProgressRepository,
    settings: SettingsRepository,
    clock: Clock,
) : ViewModel() {
    private val today = LocalDate.now(clock)

    /** The month just finished during the first week of a new one; otherwise the month so far. */
    val month: YearMonth = YearMonth.from(today).let { if (today.dayOfMonth <= 7) it.minusMonths(1) else it }
    private val window = DateWindow.month(month, clock.zone)

    val state: StateFlow<RecapState?> = combine(
        transactions.observeDailyExpense(window.startMillis),
        transactions.observeExpenseByCategory(window.startMillis, window.endMillis),
        transactions.observeTypeTotals(window.startMillis, window.endMillis),
        combine(categories.observeAll(), progress.observe(), ::Pair),
        settings.settings,
    ) { daily, byCategory, totals, (cats, p), s ->
        val currency = s.primaryCurrency
        val byId = cats.associateBy { it.id }
        // Sub-category spending counts towards its parent, like budgets do.
        val top = byCategory.filter { it.currency == currency && it.categoryId != null }
            .groupBy { t -> byId[t.categoryId]?.let { it.parentId ?: it.id } ?: t.categoryId!! }
            .mapValues { (_, rows) -> rows.sumOf { it.total } }
        val stats = HealthMath.recap(
            month,
            daily.filter { it.currency == currency }.associate { LocalDate.parse(it.day) to it.total },
            top,
            totals.filter { it.type == TransactionType.INCOME && it.currency == currency }.sumOf { it.total },
            today,
        )
        RecapState(
            stats = stats,
            currency = currency,
            topCategory = stats.topCategoryId?.let(byId::get),
            bestStreak = p.streak.best,
            loggedDays = p.logDays.filterTo(HashSet()) { YearMonth.from(it) == month },
            greenMonth = p.earned.contains(Badge.GREEN_MONTH),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

private const val SLIDES = 5
private const val SLIDE_MILLIS = 4_600

/** Each slide's colour and ink, from the handoff (violet, ink, coral, amber, lime). */
private val looks = listOf(
    Color(0xFFA99BFF) to Color(0xFF17132E),
    Color(0xFF0B0A10) to Color(0xFFF4F2FA),
    Color(0xFFFF8C73) to Color(0xFF1E0D09),
    Color(0xFFFFB648) to Color(0xFF1A1206),
    Color(0xFFC8F169) to Color(0xFF12160A),
)

/** The monthly story: five slides that advance on their own (or on a tap), ending in a shareable summary. */
@Composable
fun RecapScreen(onClose: () -> Unit, viewModel: RecapViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val s = state ?: return
    var slide by rememberSaveable { mutableIntStateOf(0) }
    val reduce = LocalReduceMotion.current
    val progress = remember { Animatable(0f) }
    LaunchedEffect(slide) {
        progress.snapTo(0f)
        if (slide < SLIDES - 1) {
            progress.animateTo(1f, tween(SLIDE_MILLIS, easing = LinearEasing))
            slide++
        } else {
            progress.snapTo(1f)
        }
    }
    val (bg, ink) = looks[slide]
    val locale = LocalConfiguration.current.locales[0]
    val monthName = s.stats.month.month.getDisplayName(TextStyle.FULL_STANDALONE, locale)
    Box(
        Modifier
            .fillMaxSize()
            .background(bg)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.Button) { if (slide < SLIDES - 1) slide++ },
    ) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(20.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(SLIDES) { i ->
                    Box(Modifier.weight(1f).height(3.dp).clip(CircleShape).background(ink.copy(alpha = 0.25f))) {
                        val fill = when {
                            i < slide -> 1f
                            i == slide -> progress.value
                            else -> 0f
                        }
                        Box(Modifier.fillMaxWidth(fill).fillMaxHeight().background(ink))
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.recap_title, monthName), style = MaterialTheme.typography.labelLarge, color = ink, modifier = Modifier.weight(1f))
                val close = stringResource(R.string.add_close)
                Box(
                    Modifier.size(44.dp).clip(CircleShape).background(ink.copy(alpha = 0.12f)).clickable(role = Role.Button, onClick = onClose).semantics { contentDescription = close },
                    contentAlignment = Alignment.Center,
                ) { Icon(MIcons.Close, contentDescription = null, tint = ink, modifier = Modifier.size(18.dp)) }
            }
            AnimatedContent(
                targetState = slide,
                transitionSpec = { (fadeIn(Motion.effects(reduce)) + scaleIn(Motion.spatial(reduce), initialScale = 0.94f)) togetherWith fadeOut(Motion.snappy(reduce)) },
                modifier = Modifier.weight(1f).fillMaxWidth(),
                label = "recapSlide",
            ) { current ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    when (current) {
                        0 -> Intro(monthName, ink)
                        1 -> Spent(s, ink)
                        2 -> TopCategory(s, ink, locale)
                        3 -> Streak(s, ink)
                        else -> Saved(s, ink, onClose)
                    }
                }
            }
        }
    }
}

@Composable
private fun Big(text: String, ink: Color) = Text(text, style = MaterialTheme.typography.displayLarge, color = ink, textAlign = TextAlign.Center)

@Composable
private fun Small(text: String, ink: Color, alpha: Float = 0.75f) =
    Text(text, style = MaterialTheme.typography.bodyLarge, color = ink.copy(alpha = alpha), textAlign = TextAlign.Center)

@Composable
private fun Intro(monthName: String, ink: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.recap_intro_title, monthName), style = MaterialTheme.typography.displaySmall, color = ink, textAlign = TextAlign.Center)
        Small(stringResource(R.string.recap_intro_body), ink)
        Spacer(Modifier.height(24.dp))
        Small(stringResource(R.string.recap_tap), ink, alpha = 0.55f)
    }
}

@Composable
private fun Spent(s: RecapState, ink: Color) {
    val money = LocalMoneyFormatter.current
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Small(stringResource(R.string.recap_spent), ink)
        Big(money.format(s.stats.spentMinor, s.currency), ink)
        Small(stringResource(R.string.recap_per_day, money.format(s.stats.perDayMinor, s.currency), money.format(s.stats.dailyNumberMinor, s.currency)), ink)
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.recap_days_under, s.stats.daysUnder, s.stats.days), style = MaterialTheme.typography.titleMedium, color = ink, textAlign = TextAlign.Center)
    }
}

@Composable
private fun TopCategory(s: RecapState, ink: Color, locale: java.util.Locale) {
    val money = LocalMoneyFormatter.current
    val share = if (s.stats.spentMinor > 0) s.stats.topCategoryMinor * 100 / s.stats.spentMinor else 0
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Small(stringResource(R.string.recap_top), ink)
        Text(s.topCategory?.let { categoryLabel(it) } ?: "—", style = MaterialTheme.typography.displaySmall, color = ink, textAlign = TextAlign.Center)
        Small("${money.format(s.stats.topCategoryMinor, s.currency)} · $share%", ink)
        s.stats.busiestWeekday?.let {
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.recap_busiest, it.getDisplayName(TextStyle.FULL, locale)), style = MaterialTheme.typography.titleMedium, color = ink, textAlign = TextAlign.Center)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Streak(s: RecapState, ink: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Big(androidx.compose.ui.res.pluralStringResource(R.plurals.recap_streak_days, s.bestStreak, s.bestStreak), ink)
        Small(stringResource(R.string.recap_streak_best), ink)
        Spacer(Modifier.height(12.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), maxItemsInEachRow = 7) {
            (1..s.stats.month.lengthOfMonth()).forEach { d ->
                val logged = s.stats.month.atDay(d) in s.loggedDays
                Box(Modifier.size(22.dp).clip(CircleShape).background(if (logged) ink else ink.copy(alpha = 0.2f)))
            }
        }
    }
}

/** The last slide: what was saved against the daily number, then a share image with the numbers optionally hidden. */
@Composable
private fun Saved(s: RecapState, ink: Color, onClose: () -> Unit) {
    val context = LocalContext.current
    val money = LocalMoneyFormatter.current
    val scope = rememberCoroutineScope()
    val layer = rememberGraphicsLayer()
    var hide by rememberSaveable { mutableStateOf(false) }
    val (cardBg, cardInk) = looks[4]
    val monthName = s.stats.month.month.getDisplayName(TextStyle.FULL_STANDALONE, LocalConfiguration.current.locales[0])
    val shareTitle = stringResource(R.string.recap_share)
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // The share card is drawn into a layer so the same pixels can be exported as the image.
        Column(
            Modifier
                .drawWithContent {
                    layer.record { this@drawWithContent.drawContent() }
                    drawLayer(layer)
                }
                .clip(MaterialTheme.shapes.extraLarge)
                .background(cardBg)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(R.string.recap_title, monthName), style = MaterialTheme.typography.labelLarge, color = cardInk)
            Text(stringResource(R.string.recap_saved), style = MaterialTheme.typography.bodyLarge, color = cardInk.copy(alpha = 0.75f))
            Text(if (hide) "•••" else money.format(s.stats.savedMinor, s.currency), style = MaterialTheme.typography.displayLarge, color = cardInk)
            Text(stringResource(R.string.recap_days_under, s.stats.daysUnder, s.stats.days), style = MaterialTheme.typography.bodyMedium, color = cardInk)
            if (s.greenMonth) Text(stringResource(R.string.recap_green), style = MaterialTheme.typography.labelLarge, color = cardInk)
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.labelSmall, color = cardInk.copy(alpha = 0.6f))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.recap_hide_numbers), style = MaterialTheme.typography.bodyMedium, color = ink, modifier = Modifier.weight(1f))
            GlassSwitch(hide, { hide = it })
        }
        GlassButton(
            onClick = {
                scope.launch {
                    val bitmap = layer.toImageBitmap().asAndroidBitmap()
                    val uri = withContext(Dispatchers.IO) {
                        val dir = File(context.cacheDir, "share").apply { mkdirs() }
                        val file = File(dir, "masroufi-recap.png")
                        file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                        FileProvider.getUriForFile(context, context.packageName + ".files", file)
                    }
                    val send = Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    context.startActivity(Intent.createChooser(send, shareTitle))
                }
            },
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) { Text(shareTitle) }
        TextButton(onClick = onClose) { Text(stringResource(R.string.recap_home), color = ink) }
    }
}

