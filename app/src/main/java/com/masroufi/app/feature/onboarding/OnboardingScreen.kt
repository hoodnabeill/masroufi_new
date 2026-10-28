package com.masroufi.app.feature.onboarding

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
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
import com.masroufi.app.ui.components.AuroraBackground
import com.masroufi.app.ui.components.CurrencyField
import com.masroufi.app.ui.components.PressableCard
import com.masroufi.app.ui.components.SegmentedChoice
import com.masroufi.app.ui.components.rememberHaptics
import com.masroufi.app.ui.theme.LocalFinanceColors
import com.masroufi.app.ui.theme.LocalGlass
import com.masroufi.app.ui.theme.LocalReduceMotion
import com.masroufi.app.ui.theme.Motion
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import java.time.Clock
import javax.inject.Inject
import kotlin.math.PI
import kotlin.math.absoluteValue
import kotlin.math.cos
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

private const val PAGES = 3

@Composable
fun OnboardingScreen(viewModel: OnboardingViewModel = hiltViewModel()) {
    val pager = rememberPagerState { PAGES }
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    var currency by rememberSaveable { mutableStateOf(Money.DEFAULT_CURRENCY) }
    var opening by rememberSaveable { mutableStateOf("") }
    val cashName = stringResource(R.string.wallet_default_cash)
    val last = pager.currentPage == PAGES - 1

    Box(Modifier.fillMaxSize()) {
        AuroraBackground(layout = pager.currentPage + 1)
        Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
            HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { page ->
                // Parallax: art drifts and fades with the swipe offset, text follows a little behind.
                val offset = pager.currentPage - page + pager.currentPageOffsetFraction
                when (page) {
                    0 -> StoryPage(offset, stringResource(R.string.onboarding_title), stringResource(R.string.onboarding_body)) { CoinOrbit() }
                    1 -> StoryPage(offset, stringResource(R.string.onboarding_page2_title), stringResource(R.string.onboarding_privacy)) { PrivacyRipple() }
                    else -> SetupPage(offset, currency, { currency = it }, opening, { opening = it })
                }
            }
            PageDots(pager, Modifier.align(Alignment.CenterHorizontally))
            Spacer(Modifier.height(18.dp))
            Button(
                onClick = {
                    haptics.confirm()
                    if (last) {
                        viewModel.finish(currency, cashName, Money.parse(opening, currency) ?: 0L)
                    } else {
                        scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).height(58.dp),
            ) {
                Text(stringResource(if (last) R.string.onboarding_start else R.string.onboarding_continue), style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun StoryPage(offset: Float, title: String, body: String, art: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.size(260.dp).graphicsLayer {
                translationX = offset * size.width * 0.5f
                alpha = 1f - offset.absoluteValue.coerceAtMost(1f)
                val s = 1f - 0.15f * offset.absoluteValue.coerceAtMost(1f)
                scaleX = s
                scaleY = s
            },
            contentAlignment = Alignment.Center,
        ) { art() }
        Spacer(Modifier.height(36.dp))
        Column(
            Modifier.graphicsLayer {
                translationX = offset * size.width * 0.25f
                alpha = 1f - offset.absoluteValue.coerceAtMost(1f)
            },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(title, style = MaterialTheme.typography.displaySmall, textAlign = TextAlign.Center)
            Spacer(Modifier.height(14.dp))
            Text(body, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}

/** A glowing coin with three smaller coins on tilted orbits; drawn in one Canvas so the motion is a single redraw. */
@Composable
private fun CoinOrbit() {
    val reduce = LocalReduceMotion.current
    val finance = LocalFinanceColors.current
    val primary = MaterialTheme.colorScheme.primary
    val gold = MaterialTheme.colorScheme.tertiary
    val angle = if (reduce) null else rememberInfiniteTransition(label = "orbit").animateFloat(
        0f, (2 * PI).toFloat(), infiniteRepeatable(tween(14_000, easing = LinearEasing), RepeatMode.Restart), label = "angle",
    )
    Canvas(Modifier.fillMaxSize()) {
        val c = center
        val r = size.minDimension / 2
        drawCircle(Brush.radialGradient(listOf(primary.copy(alpha = 0.55f), Color.Transparent), c, r), r, c)
        drawCircle(Brush.linearGradient(listOf(gold, primary), Offset(c.x - r * 0.3f, c.y - r * 0.3f), Offset(c.x + r * 0.3f, c.y + r * 0.3f)), r * 0.28f, c)
        drawCircle(Color.White.copy(alpha = 0.35f), r * 0.28f, c, style = Stroke(width = 2.dp.toPx()))
        val a = angle?.value ?: 0.6f
        listOf(Triple(0.62f, 1f, gold), Triple(0.82f, -0.7f, finance.transfer), Triple(0.95f, 0.45f, finance.income)).forEachIndexed { i, (radius, speed, color) ->
            val t = a * speed + i * 2.1f
            // Flattened ellipse reads as a tilted orbit plane.
            val p = Offset(c.x + cos(t) * r * radius, c.y + sin(t) * r * radius * 0.38f)
            drawCircle(color.copy(alpha = 0.18f), r * 0.11f, p)
            drawCircle(color, r * 0.065f, p)
        }
    }
}

/** A lock that sends out soft ripples, like a seal settling into place. */
@Composable
private fun PrivacyRipple() {
    val reduce = LocalReduceMotion.current
    val primary = MaterialTheme.colorScheme.primary
    val progress = if (reduce) null else rememberInfiniteTransition(label = "ripple").animateFloat(
        0f, 1f, infiniteRepeatable(tween(3_200, easing = LinearEasing), RepeatMode.Restart), label = "p",
    )
    Box(contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(260.dp)) {
            val maxR = size.minDimension / 2
            repeat(3) { i ->
                val p = ((progress?.value ?: 0.5f) + i / 3f) % 1f
                drawCircle(primary.copy(alpha = (1f - p) * 0.5f), maxR * (0.35f + 0.65f * p), style = Stroke(width = 2.dp.toPx()))
            }
            drawCircle(Brush.radialGradient(listOf(primary.copy(alpha = 0.45f), Color.Transparent)), maxR * 0.6f)
        }
        Box(
            Modifier.size(96.dp).clip(CircleShape).background(Brush.linearGradient(listOf(primary, primary.copy(alpha = 0.6f)))),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(44.dp))
        }
    }
}

@Composable
private fun SetupPage(offset: Float, currency: String, onCurrency: (String) -> Unit, opening: String, onOpening: (String) -> Unit) {
    val language = LocalConfiguration.current.locales[0].language
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .graphicsLayer {
                translationX = offset * size.width * 0.3f
                alpha = 1f - offset.absoluteValue.coerceAtMost(1f)
            },
        verticalArrangement = Arrangement.Center,
    ) {
        Spacer(Modifier.height(48.dp))
        Text(stringResource(R.string.onboarding_setup_title), style = MaterialTheme.typography.displaySmall)
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

/** The active dot stretches into a pill and the width follows the swipe continuously, not just on page change. */
@Composable
private fun PageDots(pager: PagerState, modifier: Modifier) {
    val glass = LocalGlass.current
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(PAGES) { i ->
            val distance = (pager.currentPage + pager.currentPageOffsetFraction - i).absoluteValue.coerceAtMost(1f)
            val width by animateDpAsState(8.dp + 18.dp * (1f - distance), Motion.snappy(LocalReduceMotion.current), label = "dot")
            Box(
                Modifier
                    .height(8.dp)
                    .width(width)
                    .clip(CircleShape)
                    .background(if (distance < 0.5f) MaterialTheme.colorScheme.primary else glass.strokeTop),
            )
        }
    }
}
