package com.masroufi.app.feature.onboarding

import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import com.masroufi.app.ui.components.FitAmount
import com.masroufi.app.ui.components.glassInset
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.masroufi.app.R
import com.masroufi.app.core.Money
import com.masroufi.app.core.newId
import com.masroufi.app.data.model.Wallet
import com.masroufi.app.data.model.WalletType
import com.masroufi.app.data.prefs.SettingsRepository
import com.masroufi.app.data.repo.WalletRepository
import com.masroufi.app.ui.components.AmountField
import com.masroufi.app.ui.components.AnimatedAmount
import com.masroufi.app.ui.components.CurrencyField
import com.masroufi.app.ui.components.GlassButton
import com.masroufi.app.ui.components.GlassStage
import com.masroufi.app.ui.components.IconBadge
import com.masroufi.app.ui.components.MIcons
import com.masroufi.app.ui.components.PressableCard
import com.masroufi.app.ui.components.SegmentedChoice
import com.masroufi.app.ui.components.categoryColor
import com.masroufi.app.ui.components.glass
import com.masroufi.app.ui.components.rememberHaptics
import com.masroufi.app.ui.theme.LocalFinanceColors
import com.masroufi.app.ui.theme.LocalGlass
import com.masroufi.app.ui.theme.LocalReduceMotion
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import java.time.Clock
import javax.inject.Inject
import kotlin.math.PI
import kotlin.math.absoluteValue
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val wallets: WalletRepository,
    private val clock: Clock,
) : ViewModel() {
    fun finish(currency: String, cashName: String, openingMinor: Long) {
        viewModelScope.launch {
            if (!wallets.hasAnyWallet()) {
                val now = clock.millis()
                wallets.save(Wallet(newId(), cashName, WalletType.CASH, currency, openingMinor, createdAt = now, updatedAt = now))
            }
            settings.setOnboardingDone(currency)
        }
    }
}

private const val PAGES = 4

/** Example balance the hero card rolls up to. It is decoration only and never saved. */
private const val DEMO_BALANCE_MINOR = 1_245_000L

/**
 * Onboarding is staged like a title sequence: glass cards are dealt out of the light, the balance rolls up on its
 * odometer, titles rise word by word, and only then do the controls arrive. Swiping drives every layer with
 * parallax, the page indicator is a single liquid drop, and each settled page ticks a haptic.
 */
@Composable
fun OnboardingScreen(viewModel: OnboardingViewModel = hiltViewModel()) {
    val pager = rememberPagerState { PAGES }
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val reduce = LocalReduceMotion.current
    var currency by rememberSaveable { mutableStateOf(Money.DEFAULT_CURRENCY) }
    var opening by rememberSaveable { mutableStateOf("") }
    val cashName = stringResource(R.string.wallet_default_cash)
    val last = pager.currentPage == PAGES - 1
    val chrome = remember { Animatable(if (reduce) 1f else 0f) }
    LaunchedEffect(Unit) {
        delay(1_050)
        chrome.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = 180f))
    }
    LaunchedEffect(pager) { snapshotFlow { pager.settledPage }.drop(1).collect { haptics.tick() } }

    GlassStage(layout = pager.currentPage + 1) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
            Box(Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 12.dp)) {
                // Qualified so the outer ColumnScope overload is not picked up inside this Box.
                androidx.compose.animation.AnimatedVisibility(!last, Modifier.align(Alignment.CenterEnd), enter = fadeIn(), exit = fadeOut()) {
                    TextButton(
                        onClick = { scope.launch { pager.animateScrollToPage(PAGES - 1) } },
                        modifier = Modifier.graphicsLayer { alpha = chrome.value },
                    ) { Text(stringResource(R.string.onboarding_skip)) }
                }
            }
            HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { page ->
                val offset = { pager.currentPage - page + pager.currentPageOffsetFraction }
                val active = pager.currentPage == page
                when (page) {
                    0 -> StoryPage(offset, active, firstDelay = 700, stringResource(R.string.onboarding_title), stringResource(R.string.onboarding_body)) {
                        WalletStack(offset)
                    }
                    1 -> StoryPage(offset, active, firstDelay = 0, stringResource(R.string.onboarding_plan_title), stringResource(R.string.onboarding_plan_body)) {
                        PlanOrbit(active)
                    }
                    2 -> StoryPage(offset, active, firstDelay = 0, stringResource(R.string.onboarding_page2_title), stringResource(R.string.onboarding_privacy)) {
                        PrivacySeal(active)
                    }
                    else -> SetupPage(offset, active, currency, { currency = it }, opening, { opening = it })
                }
            }
            LiquidDrop(pager, Modifier.align(Alignment.CenterHorizontally).graphicsLayer { alpha = chrome.value })
            Spacer(Modifier.height(18.dp))
            GlassButton(
                onClick = {
                    haptics.confirm()
                    if (last) {
                        viewModel.finish(currency, cashName, Money.parse(opening, currency) ?: 0L)
                    } else {
                        scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .height(58.dp)
                    .graphicsLayer {
                        alpha = chrome.value
                        translationY = (1f - chrome.value) * 40.dp.toPx()
                    },
            ) {
                AnimatedContent(
                    targetState = last,
                    transitionSpec = {
                        (fadeIn() + slideInVertically { it / 2 }) togetherWith (fadeOut() + slideOutVertically { -it / 2 })
                    },
                    label = "cta",
                ) { isLast ->
                    Text(stringResource(if (isLast) R.string.onboarding_start else R.string.onboarding_continue), style = MaterialTheme.typography.titleMedium)
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun StoryPage(offset: () -> Float, active: Boolean, firstDelay: Long, title: String, body: String, art: @Composable () -> Unit) {
    // The art is designed at 300dp and scaled down to fit short screens (landscape, large fonts); the page scrolls
    // if the text still does not fit.
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val artSize = minOf(300.dp, maxHeight * 0.46f, maxWidth * 0.86f)
        val artScale = artSize / 300.dp
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight)
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                Modifier.size(artSize).graphicsLayer {
                    val o = offset()
                    translationX = o * size.width * 0.55f
                    alpha = 1f - o.absoluteValue.coerceAtMost(1f)
                    val s = 1f - 0.18f * o.absoluteValue.coerceAtMost(1f)
                    scaleX = s
                    scaleY = s
                },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier.requiredSize(300.dp).graphicsLayer {
                        scaleX = artScale
                        scaleY = artScale
                    },
                    contentAlignment = Alignment.Center,
                ) { art() }
            }
            Spacer(Modifier.height(28.dp))
            Column(
                Modifier.graphicsLayer {
                    val o = offset()
                    translationX = o * size.width * 0.22f
                    alpha = 1f - o.absoluteValue.coerceAtMost(1f)
                },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                KineticText(title, active, firstDelay, MaterialTheme.typography.displaySmall, heading = true)
                Spacer(Modifier.height(14.dp))
                KineticText(body, active, firstDelay + 260, MaterialTheme.typography.bodyLarge, MaterialTheme.colorScheme.onSurfaceVariant, stagger = 14)
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

/**
 * Text that rises into place word by word, each word coming out of a soft blur (Android 12+). Words keep their
 * own shaping, so Arabic stays joined; the flow follows the layout direction. Plays once, the first time its page
 * becomes current.
 */
@Composable
private fun KineticText(
    text: String,
    active: Boolean,
    startDelay: Long,
    style: androidx.compose.ui.text.TextStyle,
    color: Color = Color.Unspecified,
    stagger: Long = 55,
    heading: Boolean = false,
) {
    val reduce = LocalReduceMotion.current
    val words = remember(text) { text.split(' ').filter { it.isNotEmpty() } }
    val progress = remember(text) { words.map { Animatable(if (reduce) 1f else 0f) } }
    var played by remember(text) { mutableStateOf(reduce) }
    LaunchedEffect(active, text) {
        if (active && !played) {
            played = true
            delay(startDelay)
            progress.forEachIndexed { i, anim ->
                launch {
                    delay(i * stagger)
                    anim.animateTo(1f, spring(dampingRatio = 0.75f, stiffness = 170f))
                }
            }
        }
    }
    // Screen readers get the sentence once, whole, not word by word.
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(0.dp, Alignment.CenterHorizontally),
        modifier = Modifier.clearAndSetSemantics {
            this.text = AnnotatedString(text)
            if (heading) heading()
        },
    ) {
        words.forEachIndexed { i, word ->
            Text(
                if (i < words.lastIndex) "$word " else word,
                style = style,
                color = color,
                textAlign = TextAlign.Center,
                modifier = Modifier.graphicsLayer {
                    val p = progress[i].value
                    alpha = p.coerceIn(0f, 1f)
                    translationY = (1f - p) * 26.dp.toPx()
                    val blur = (1f - p.coerceIn(0f, 1f)) * 14.dp.toPx()
                    renderEffect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && blur > 0.5f) BlurEffect(blur, blur, TileMode.Decal) else null
                },
            )
        }
    }
}

/** Three glass wallet cards dealt out of the light, floating in 3D and tilting with the swipe. */
@Composable
private fun WalletStack(offset: () -> Float) {
    val reduce = LocalReduceMotion.current
    val finance = LocalFinanceColors.current
    val aurora = LocalGlass.current.aurora
    val primary = MaterialTheme.colorScheme.primary
    val deal = remember { List(3) { Animatable(if (reduce) 1f else 0f) } }
    var balance by remember { mutableLongStateOf(if (reduce) DEMO_BALANCE_MINOR else 0L) }
    LaunchedEffect(Unit) {
        deal.forEachIndexed { i, anim ->
            launch {
                delay(180L + i * 120L)
                anim.animateTo(1f, spring(dampingRatio = 0.62f, stiffness = 140f))
            }
        }
        delay(900)
        balance = DEMO_BALANCE_MINOR
    }
    val float = if (reduce) null else rememberInfiniteTransition(label = "float").animateFloat(
        0f, (2 * PI).toFloat(), infiniteRepeatable(tween(7_000, easing = LinearEasing), RepeatMode.Restart), label = "t",
    )
    // Back to front: resting offset (dp), resting tilt (degrees), glass tint, icon.
    val cards = listOf(
        Triple(Offset(-28f, -54f), -11f, aurora[2]) to MIcons.Bank,
        Triple(Offset(26f, -20f), 8f, finance.transfer) to MIcons.Cash,
        Triple(Offset(0f, 34f), -3f, primary) to MIcons.Wallet,
    )
    val shape = MaterialTheme.shapes.large
    // Dark glass carries white text; light glass is pale, so it carries the normal ink colour instead.
    val ink = if (LocalGlass.current.dark) Color.White else MaterialTheme.colorScheme.onSurface
    // Decorative example only: hidden from screen readers so the demo balance is never announced as real money.
    Box(Modifier.fillMaxSize().clearAndSetSemantics { }, contentAlignment = Alignment.Center) {
        cards.forEachIndexed { i, (pose, icon) ->
            val (rest, tilt, tint) = pose
            val depth = i + 1
            Box(
                Modifier
                    .offset(rest.x.dp, rest.y.dp)
                    .size(220.dp, 138.dp)
                    .graphicsLayer {
                        val d = deal[i].value
                        val t = float?.value ?: 0f
                        val o = offset()
                        alpha = d.coerceIn(0f, 1f)
                        translationY = (1f - d) * 420.dp.toPx() + sin(t + i * 1.3f) * 5.dp.toPx()
                        translationX = -o * depth * 18.dp.toPx()
                        rotationZ = tilt * d + (1f - d) * 24f * (if (i % 2 == 0) -1 else 1)
                        rotationY = sin(t + i) * 7f + o * 26f
                        rotationX = cos(t * 0.8f + i) * 5f
                        cameraDistance = 14f * density
                    }
                    .glass(shape, tint = tint.copy(alpha = 0.34f), strong = i == 2)
                    .padding(18.dp),
            ) {
                Icon(icon, contentDescription = null, tint = ink.copy(alpha = 0.92f), modifier = Modifier.size(26.dp))
                if (i == 2) {
                    Text(
                        stringResource(R.string.onboarding_example),
                        style = MaterialTheme.typography.labelMedium,
                        color = ink.copy(alpha = 0.85f),
                        modifier = Modifier.align(Alignment.TopEnd).glassInset(CircleShape).padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                    Column(Modifier.align(Alignment.BottomStart)) {
                        Text(stringResource(R.string.onboarding_demo_wallet), style = MaterialTheme.typography.labelLarge, color = ink.copy(alpha = 0.78f))
                        FitAmount(balance, Money.DEFAULT_CURRENCY, style = MaterialTheme.typography.headlineSmall, color = ink)
                    }
                }
            }
        }
    }
}

/** A glass orb whose ring of spending fills in, circled by category gems drifting on a tilted orbit. */
@Composable
private fun PlanOrbit(active: Boolean) {
    val reduce = LocalReduceMotion.current
    val finance = LocalFinanceColors.current
    val primary = MaterialTheme.colorScheme.primary
    val fill = remember { Animatable(if (reduce) 1f else 0f) }
    LaunchedEffect(active) {
        if (active && fill.value < 1f) fill.animateTo(1f, spring(dampingRatio = 0.9f, stiffness = 40f))
    }
    val spin = if (reduce) null else rememberInfiniteTransition(label = "orbit").animateFloat(
        0f, (2 * PI).toFloat(), infiniteRepeatable(tween(26_000, easing = LinearEasing), RepeatMode.Restart), label = "a",
    )
    val gems = listOf(MIcons.Food, MIcons.Cart, MIcons.Savings, MIcons.Car, MIcons.Gift, MIcons.Cafe)
    val slices = listOf(0.42f to primary, 0.27f to finance.transfer, 0.19f to finance.expense, 0.12f to MaterialTheme.colorScheme.tertiary)
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(Modifier.size(150.dp).glass(CircleShape, strong = true), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(104.dp)) {
                val stroke = 14.dp.toPx()
                val inset = stroke / 2
                val arcSize = Size(size.width - stroke, size.height - stroke)
                var start = -90f
                slices.forEach { (share, color) ->
                    val sweep = 360f * share * fill.value
                    drawArc(color.copy(alpha = 0.22f), start, sweep, false, Offset(inset, inset), arcSize, style = Stroke(stroke * 1.8f, cap = StrokeCap.Round))
                    drawArc(color, start, (sweep - 6f).coerceAtLeast(0f), false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                    start += 360f * share
                }
            }
        }
        gems.forEachIndexed { i, icon ->
            Box(
                Modifier.offset {
                    val a = (spin?.value ?: 0f) + i * (2 * PI / gems.size).toFloat()
                    IntOffset((cos(a) * 128.dp.toPx()).roundToInt(), (sin(a) * 104.dp.toPx()).roundToInt())
                }.graphicsLayer {
                    // Gems on the far side of the orbit shrink and dim, which sells the depth.
                    val a = (spin?.value ?: 0f) + i * (2 * PI / gems.size).toFloat()
                    val near = (sin(a) + 1f) / 2f
                    val s = (0.78f + 0.3f * near) * fill.value.coerceIn(0f, 1f)
                    scaleX = s
                    scaleY = s
                    alpha = 0.55f + 0.45f * near
                },
            ) {
                IconBadge(icon, categoryColor(i), size = 46.dp, modifier = Modifier.glass(RoundedCornerShape(percent = 34)))
            }
        }
    }
}

/** A lock seals shut with a bounce when its page arrives, sending soft ripples outwards. */
@Composable
private fun PrivacySeal(active: Boolean) {
    val reduce = LocalReduceMotion.current
    val haptics = rememberHaptics()
    val primary = MaterialTheme.colorScheme.primary
    val onPrimary = MaterialTheme.colorScheme.onPrimary
    val shut = remember { Animatable(if (reduce) 1f else 0f) }
    LaunchedEffect(active) {
        if (active && shut.value < 1f) {
            delay(380)
            shut.animateTo(1f, spring(dampingRatio = 0.42f, stiffness = 380f))
            haptics.confirm()
        }
    }
    val ripple = if (reduce) null else rememberInfiniteTransition(label = "ripple").animateFloat(
        0f, 1f, infiniteRepeatable(tween(3_200, easing = LinearEasing), RepeatMode.Restart), label = "p",
    )
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val maxR = size.minDimension / 2
            repeat(3) { i ->
                val p = ((ripple?.value ?: 0.5f) + i / 3f) % 1f
                drawCircle(primary.copy(alpha = (1f - p) * 0.45f * shut.value.coerceIn(0f, 1f)), maxR * (0.36f + 0.64f * p), style = Stroke(width = 2.dp.toPx()))
            }
            drawCircle(Brush.radialGradient(listOf(primary.copy(alpha = 0.4f), Color.Transparent)), maxR * 0.62f)
        }
        Box(Modifier.size(124.dp).glass(CircleShape, tint = primary.copy(alpha = 0.7f), strong = true), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(56.dp)) {
                val u = size.minDimension / 24f
                val stroke = Stroke(width = 2.2f * u, cap = StrokeCap.Round)
                // Shackle drops 5 units into the body as the lock closes.
                val drop = (1f - shut.value) * -5f * u
                drawArc(
                    color = onPrimary,
                    startAngle = 180f,
                    sweepAngle = 180f,
                    useCenter = false,
                    topLeft = Offset(7f * u, 3.5f * u + drop),
                    size = Size(10f * u, 10f * u),
                    style = stroke,
                )
                drawLine(onPrimary, Offset(7f * u, 8.5f * u + drop), Offset(7f * u, 11f * u), stroke.width, StrokeCap.Round)
                drawLine(onPrimary, Offset(17f * u, 8.5f * u + drop), Offset(17f * u, 11f * u + drop.coerceAtMost(0f) * 0.6f), stroke.width, StrokeCap.Round)
                drawRoundRect(onPrimary, Offset(4.5f * u, 10.5f * u), Size(15f * u, 11f * u), CornerRadius(3f * u))
                drawCircle(primary, 1.6f * u, Offset(12f * u, 15.5f * u))
            }
        }
    }
}

@Composable
private fun SetupPage(
    offset: () -> Float,
    active: Boolean,
    currency: String,
    onCurrency: (String) -> Unit,
    opening: String,
    onOpening: (String) -> Unit,
) {
    val language = LocalConfiguration.current.locales[0].language
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .graphicsLayer {
                val o = offset()
                translationX = o * size.width * 0.3f
                alpha = 1f - o.absoluteValue.coerceAtMost(1f)
            },
        verticalArrangement = Arrangement.Center,
    ) {
        Spacer(Modifier.height(32.dp))
        KineticText(stringResource(R.string.onboarding_setup_title), active, 0, MaterialTheme.typography.displaySmall, heading = true)
        Spacer(Modifier.height(24.dp))
        PressableCard(onClick = null) {
            Text(stringResource(R.string.settings_language), style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(10.dp))
            SegmentedChoice(
                options = listOf("ar", "en"),
                selected = if (language == "ar") "ar" else "en",
                onSelect = { AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(it)) },
                label = { if (it == "ar") "العربية" else "English" },
            )
            Spacer(Modifier.height(16.dp))
            CurrencyField(selected = currency, onSelect = onCurrency)
            Spacer(Modifier.height(12.dp))
            AmountField(value = opening, onValueChange = onOpening, currency = currency, label = stringResource(R.string.onboarding_cash_label))
        }
        Spacer(Modifier.height(14.dp))
        Text(stringResource(R.string.onboarding_privacy), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * Page indicator as one liquid drop sliding over the dots. It follows the swipe continuously and stretches
 * mid-way between pages, like a droplet pulled across glass. Pager state is read only while drawing.
 */
@Composable
private fun LiquidDrop(pager: PagerState, modifier: Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    val track = LocalGlass.current.strokeTop
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val dot = 8.dp
    val gap = 14.dp
    val drop = 24.dp
    Canvas(modifier.width(drop + (dot + gap) * (PAGES - 1)).height(dot)) {
        val step = (dot + gap).toPx()
        val half = drop.toPx() / 2
        val r = dot.toPx() / 2
        fun x(at: Float) = if (rtl) size.width - (half + at * step) else half + at * step
        repeat(PAGES) { i -> drawCircle(track, r, Offset(x(i.toFloat()), r)) }
        val position = (pager.currentPage + pager.currentPageOffsetFraction).coerceIn(0f, PAGES - 1f)
        val stretch = sin(PI.toFloat() * (position - floor(position)))
        val width = drop.toPx() + step * 0.7f * stretch
        val center = x(position)
        drawRoundRect(primary, Offset(center - width / 2, 0f), Size(width, dot.toPx()), CornerRadius(r))
    }
}
