package com.masroufi.app.feature.plan

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.masroufi.app.R
import com.masroufi.app.core.formatPounds
import com.masroufi.app.data.repo.GoldView
import com.masroufi.app.feature.add.AmountInput
import com.masroufi.app.ui.components.ChipStyle
import com.masroufi.app.ui.components.EmptyState
import com.masroufi.app.ui.components.Odometer
import com.masroufi.app.ui.components.PlusComingSheet
import com.masroufi.app.ui.components.PlusLocked
import com.masroufi.app.ui.components.PrimaryButton
import com.masroufi.app.ui.components.SecondaryButton
import com.masroufi.app.ui.components.TextLink
import com.masroufi.app.ui.components.TzCard
import com.masroufi.app.ui.components.TzChip
import com.masroufi.app.ui.components.TzProgressBar
import com.masroufi.app.ui.components.TzSheet
import com.masroufi.app.ui.components.TzToggle
import com.masroufi.app.ui.haptics.HapticLevel
import com.masroufi.app.ui.haptics.LocalHaptics
import com.masroufi.app.ui.motion.TzSpring
import com.masroufi.app.ui.motion.motionSpec
import com.masroufi.app.ui.paper.EmptyJar
import com.masroufi.app.ui.theme.Tz
import com.masroufi.app.ui.theme.TzType
import java.time.format.DateTimeFormatter

private val RoundSteps = listOf(5, 10, 50)
private const val GLASS_PATH = "M30 8h60v16l14 18v96a14 14 0 0 1-14 14H30a14 14 0 0 1-14-14V42l14-18z"
private const val RIM_PATH = "M30 24h60"

// Coin centres in the mock's 120 x 160 viewBox, bottom row first; a coin shows once the level has risen to it.
private val Coins = listOf(
    38 to 136, 62 to 138, 86 to 135, 50 to 118, 75 to 119, 34 to 104, 62 to 100, 88 to 103,
    44 to 86, 70 to 84, 92 to 88, 36 to 70, 62 to 68, 86 to 70, 50 to 52, 74 to 52,
)

/** الحصّالة: the jar view inside the Plan tab (Jar.dc.html). */
@Composable
fun JarSection(viewModel: JarViewModel, onNewGoal: () -> Unit, onToday: () -> Unit, onSeasons: () -> Unit) {
    val ui by viewModel.state.collectAsStateWithLifecycle()
    val jar = ui.jar ?: return
    if (ui.empty) {
        EmptyState({ EmptyJar() }, R.string.empty_jar_title, R.string.empty_jar_line, R.string.empty_jar_action, onToday, primaryAction = true)
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            JarHero(jar.total, jar.monthGrowth, ui.goals.maxOfOrNull { it.view.goal.target } ?: 0L)
            val gold: @Composable () -> Unit = { GoldCard(jar.gold) { viewModel.open(JarSheet.Gold) } }
            if (ui.isPlus) gold() else PlusLocked(R.string.jar_lock_gold, { viewModel.open(JarSheet.Plus) }, gold)
            val round: @Composable () -> Unit = { RoundUpCard(ui.roundUpStep, viewModel::setRoundUpStep) }
            if (ui.isPlus) round() else PlusLocked(R.string.jar_lock_round, { viewModel.open(JarSheet.Plus) }, round)
            SourcesCard(jar.sources.leftoverCount, jar.sources.leftoverSum, jar.sources.roundUpSum, jar.sources.manualSum)
            GoalsCard(ui, onNewGoal, { viewModel.open(JarSheet.Goal(it)) }, onSeasons)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryButton(stringResource(R.string.jar_withdraw), { viewModel.open(JarSheet.Withdraw) }, Modifier.weight(1f), height = 56.dp)
                PrimaryButton(stringResource(R.string.jar_deposit), { viewModel.open(JarSheet.Deposit) }, Modifier.weight(1f), height = 56.dp)
            }
        }
    }
    JarSheets(ui, viewModel)
}

@Composable
private fun JarHero(total: Long, growth: Long, biggestGoal: Long) {
    val level = (total.toFloat() / maxOf(biggestGoal, total, 1L).toFloat()).coerceIn(0f, 1f)
    TzCard(padding = 20.dp) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            JarDrawing(level)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.jar_total_label), style = TzType.label, color = Tz.text2)
                Odometer(total, TzType.hero)
                if (growth > 0) Text(stringResource(R.string.jar_growth, formatPounds(growth)), style = TzType.label.copy(fontWeight = FontWeight.SemiBold), color = Tz.turquoise)
                Text(stringResource(R.string.jar_honesty), style = TzType.caption.copy(lineHeight = 19.sp), color = Tz.text2, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}

@Composable
private fun JarDrawing(level: Float) {
    val shown by animateFloatAsState(level, motionSpec(TzSpring.Move), label = "jarLevel")
    val glass = remember { PathParser().parsePathString(GLASS_PATH).toPath() }
    val rim = remember { PathParser().parsePathString(RIM_PATH).toPath() }
    Canvas(Modifier.size(120.dp, 160.dp)) {
        scale(size.width / 120f, pivot = Offset.Zero) {
            drawPath(glass, Tz.night)
            val line = shown * 116f
            Coins.forEach { (x, y) ->
                if (y >= 150f - line) {
                    drawCircle(Tz.yellow, 12f, Offset(x.toFloat(), y.toFloat()))
                    drawCircle(Tz.night, 12f, Offset(x.toFloat(), y.toFloat()), style = Stroke(2f))
                }
            }
            drawPath(glass, Tz.turquoise, style = Stroke(3f, join = StrokeJoin.Round))
            drawPath(rim, Tz.turquoise, style = Stroke(3f))
            drawLine(Tz.turquoise, Offset(52f, 2f), Offset(68f, 2f), 4f, StrokeCap.Round)
        }
    }
}

@Composable
private fun GoldCard(gold: GoldView?, onEdit: () -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    if (gold == null) {
        Row(
            Modifier.fillMaxWidth().clip(shape).background(Tz.yellow).clickable(role = Role.Button, onClick = onEdit).padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.jar_gold_title), style = TzType.body.copy(fontWeight = FontWeight.Bold), color = Tz.night)
            Text(stringResource(R.string.jar_gold_prompt), style = TzType.label, color = Tz.yellowInk, modifier = Modifier.weight(1f).padding(start = 10.dp))
        }
        return
    }
    Column(Modifier.fillMaxWidth().clip(shape).background(Tz.yellow).padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.jar_gold_title), style = TzType.body.copy(fontWeight = FontWeight.Bold), color = Tz.night)
            Box(
                Modifier.height(36.dp).clip(RoundedCornerShape(18.dp)).border(2.dp, Tz.night, RoundedCornerShape(18.dp))
                    .clickable(role = Role.Button, onClick = onEdit).padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) { Text(stringResource(R.string.jar_gold_edit), style = TzType.caption.copy(fontWeight = FontWeight.Bold), color = Tz.night) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
            val g = gold.gramsHundredths
            Text("${g / 100}.${(g % 100).toString().padStart(2, '0')}", style = TzType.hero.copy(fontSize = 64.sp, lineHeight = 64.sp), color = Tz.night)
            Text(stringResource(R.string.jar_gold_unit), style = TzType.titlePushed, color = Tz.night, modifier = Modifier.padding(bottom = 6.dp))
        }
        val bar = RoundedCornerShape(5.dp)
        Box(Modifier.fillMaxWidth().height(10.dp).clip(bar).background(Tz.yellowRule2)) {
            Box(Modifier.fillMaxWidth((gold.gramsHundredths % 100) / 100f).fillMaxHeight().background(Tz.night, bar))
        }
        val left = gold.leftToFirstGram?.let { stringResource(R.string.jar_gold_left, formatPounds(it)) }
        val label = stringResource(R.string.jar_gold_price_label)
        val price = stringResource(R.string.jar_gold_per_gram, formatPounds(gold.pricePerGram))
        Text(
            buildAnnotatedString {
                if (left != null) append("$left ")
                append("$label ")
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(price) }
            },
            style = TzType.label, color = Tz.yellowInk,
        )
    }
}

@Composable
private fun RoundUpCard(step: Int, onStep: (Int) -> Unit) {
    val title = stringResource(R.string.jar_round_title)
    TzCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(title, style = TzType.cardHeading, color = Tz.text)
                    Text(stringResource(R.string.jar_round_line, if (step > 0) step else RoundSteps.first()), style = TzType.caption.copy(lineHeight = 19.sp), color = Tz.text2)
                }
                TzToggle(step > 0, { onStep(if (it) RoundSteps.first() else 0) }, title)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RoundSteps.forEach { n ->
                    TzChip(stringResource(R.string.jar_round_to, n), step == n, { onStep(n) }, Modifier.weight(1f), style = ChipStyle.Choice)
                }
            }
        }
    }
}

@Composable
private fun SourcesCard(leftoverCount: Int, leftovers: Long, roundUps: Long, manual: Long) {
    val rows = listOf(
        Triple(stringResource(R.string.jar_src_tickets, leftoverCount), leftovers, Tz.yellow),
        Triple(stringResource(R.string.jar_src_roundup), roundUps, Tz.turquoise),
        Triple(stringResource(R.string.jar_src_manual), manual, Tz.sky),
    )
    TzCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.jar_sources_title), style = TzType.cardHeading, color = Tz.text)
            Row(Modifier.fillMaxWidth().height(12.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                rows.filter { it.second > 0 }.forEach { (_, v, c) -> Box(Modifier.weight(v.toFloat()).fillMaxHeight().clip(RoundedCornerShape(6.dp)).background(c)) }
            }
            rows.forEach { (label, v, c) ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).background(c, CircleShape))
                    Text(label, style = TzType.body.copy(fontSize = 14.sp), color = Tz.text, modifier = Modifier.weight(1f))
                    Text(formatPounds(v), style = TzType.rowNumber.copy(fontSize = 18.sp), color = Tz.text)
                }
            }
        }
    }
}

@Composable
private fun GoalsCard(ui: JarUi, onNew: () -> Unit, onGoal: (Long) -> Unit, onSeasons: () -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    val month = remember(locale) { DateTimeFormatter.ofPattern("MMMM", locale) }
    TzCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.jar_goals_title), style = TzType.cardHeading, color = Tz.text)
                TextLink(stringResource(R.string.jar_goal_new), onNew)
            }
            ui.goals.forEach { g ->
                val goal = g.view.goal
                Column(Modifier.fillMaxWidth().clickable(role = Role.Button) { onGoal(goal.id) }, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(goal.name, style = TzType.body.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold), color = Tz.text)
                        Text(stringResource(R.string.jar_goal_amounts, formatPounds(g.view.earmarked), formatPounds(goal.target)), style = TzType.body.copy(fontSize = 14.sp), color = Tz.text2)
                    }
                    TzProgressBar(listOf(g.view.earmarked.toFloat() / goal.target to Tz.yellow))
                    g.eta?.let { Text(stringResource(R.string.jar_goal_eta, month.format(it)), style = TzType.caption, color = Tz.text2) }
                }
            }
            ui.season?.let { s ->
                if (ui.goals.isNotEmpty()) Box(Modifier.fillMaxWidth().height(1.dp).background(Tz.line))
                Column(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onSeasons), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(s.season.name, style = TzType.body.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold), color = Tz.text)
                        Text(stringResource(R.string.jar_season_link, formatPounds(s.saved), formatPounds(s.season.target)), style = TzType.label, color = Tz.yellow)
                    }
                    TzProgressBar(listOf(s.saved.toFloat() / s.season.target to Tz.turquoise))
                }
            }
        }
    }
}

@Composable
private fun JarSheets(ui: JarUi, vm: JarViewModel) {
    val haptics = LocalHaptics.current
    val jar = ui.jar ?: return
    when (val sheet = ui.sheet) {
        JarSheet.Plus -> PlusComingSheet(vm::dismiss)
        JarSheet.Deposit -> AmountSheet(stringResource(R.string.jar_deposit_title), vm::dismiss) { amount ->
            PrimaryButton(stringResource(R.string.jar_deposit), { haptics.perform(HapticLevel.Confirm); vm.deposit(amount) }, Modifier.fillMaxWidth(), enabled = amount > 0, height = 52.dp)
        }
        JarSheet.Withdraw -> AmountSheet(stringResource(R.string.jar_withdraw_title), vm::dismiss) { amount ->
            if (amount > jar.free) Text(stringResource(R.string.jar_free_only, formatPounds(jar.free)), style = TzType.label, color = Tz.carnelianText)
            PrimaryButton(stringResource(R.string.jar_withdraw), { haptics.perform(HapticLevel.Confirm); vm.withdraw(amount) }, Modifier.fillMaxWidth(), enabled = amount in 1..jar.free, height = 52.dp)
        }
        JarSheet.Gold -> AmountSheet(stringResource(R.string.jar_gold_prompt), vm::dismiss, AmountInput.of(jar.gold?.pricePerGram ?: 0)) { amount ->
            PrimaryButton(stringResource(R.string.jar_save), { haptics.perform(HapticLevel.Confirm); vm.setGoldPrice(amount) }, Modifier.fillMaxWidth(), enabled = amount > 0, height = 52.dp)
        }
        is JarSheet.Goal -> {
            val g = ui.goals.firstOrNull { it.view.goal.id == sheet.id }?.view
            if (g != null) AmountSheet(g.goal.name, vm::dismiss) { amount ->
                if (amount > jar.free) Text(stringResource(R.string.jar_free_only, formatPounds(jar.free)), style = TzType.label, color = Tz.carnelianText)
                PrimaryButton(stringResource(R.string.jar_goal_put), { haptics.perform(HapticLevel.Confirm); vm.earmark(g.goal.id, amount) }, Modifier.fillMaxWidth(), enabled = amount in 1..jar.free, height = 52.dp)
                if (g.earmarked > 0) SecondaryButton(
                    stringResource(R.string.jar_goal_release),
                    { if (amount in 1..g.earmarked) { haptics.perform(HapticLevel.Confirm); vm.release(g.goal.id, amount) } else haptics.perform(HapticLevel.Reject) },
                    Modifier.fillMaxWidth(), height = 52.dp,
                )
            }
        }
        null -> Unit
    }
}

/** A sheet with a hero amount and the keypad; [actions] gets the typed piastres. */
@Composable
private fun AmountSheet(title: String, onDismiss: () -> Unit, initial: AmountInput = AmountInput(), actions: @Composable ColumnScope.(Long) -> Unit) {
    var input by remember { mutableStateOf(initial) }
    TzSheet(onDismiss) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, style = TzType.cardHeading, color = Tz.text)
            AmountKeypad(input, { input = input.digit(it) }, { input = input.point() }, { input = input.backspace() })
            actions(input.toPiastres())
        }
    }
}
