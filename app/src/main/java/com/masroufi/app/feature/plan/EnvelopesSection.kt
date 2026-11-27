package com.masroufi.app.feature.plan

import com.masroufi.app.feature.pockets.LoadingRows
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.masroufi.app.R
import com.masroufi.app.core.formatPounds
import com.masroufi.app.ui.components.poundsText
import com.masroufi.app.ui.components.EmptyState
import com.masroufi.app.ui.components.RowSkeleton
import com.masroufi.app.ui.components.TzCard
import com.masroufi.app.ui.components.TzProgressBar
import com.masroufi.app.ui.components.isLargeFont
import com.masroufi.app.ui.motion.pressClickable
import com.masroufi.app.ui.haptics.HapticLevel
import com.masroufi.app.ui.haptics.LocalHaptics
import com.masroufi.app.ui.icons.TzIcon
import com.masroufi.app.ui.icons.TzIcons
import com.masroufi.app.ui.paper.Qersh
import com.masroufi.app.ui.paper.QershMood
import com.masroufi.app.feature.pockets.pocketColor
import com.masroufi.app.ui.theme.Tz
import com.masroufi.app.ui.theme.TzType
import java.time.format.TextStyle
import kotlin.math.roundToInt

/** Where an envelope is being dragged: [id] is the dragged envelope, [pointer] the finger in root coordinates. */
private val TipText = androidx.compose.ui.graphics.Color(0xFFF0E3B5)

private class Drag {
    var id by mutableStateOf<Long?>(null)
    var pointer by mutableStateOf(Offset.Zero)
    var over by mutableStateOf<Long?>(null)
}

/** الظروف (Envelopes.dc.html): the split summary, the envelope grid, the new-envelope card and the move tip. */
@Composable
fun EnvelopesSection(
    state: EnvelopesUi,
    onNew: () -> Unit,
    onOpen: (Long) -> Unit,
    onSuggest: () -> Unit,
    onDrop: (from: Long, to: Long) -> Unit,
    onMoveTip: () -> Unit,
    onMoveFrom: (Long) -> Unit,
) {
    when {
        state.loading -> LoadingRows()
        state.cards.isEmpty() -> EmptyState(
            { Qersh(QershMood.Thinking, 120.dp) }, R.string.env_empty_title, R.string.env_empty_line,
            R.string.env_empty_action, onSuggest, primaryAction = true,
        )
        else -> Loaded(state, onNew, onOpen, onDrop, onMoveTip, onMoveFrom)
    }
}

@Composable
private fun Loaded(
    state: EnvelopesUi,
    onNew: () -> Unit,
    onOpen: (Long) -> Unit,
    onDrop: (Long, Long) -> Unit,
    onMoveTip: () -> Unit,
    onMoveFrom: (Long) -> Unit,
) {
    val haptics = LocalHaptics.current
    val drag = remember { Drag() }
    val cardCoords = remember { HashMap<Long, LayoutCoordinates>() }
    var origin by remember { mutableStateOf(Offset.Zero) }
    fun hit(at: Offset, except: Long?): Long? = cardCoords.entries.firstOrNull { (id, c) ->
        id != except && c.isAttached && c.boundsInRoot().contains(at)
    }?.key

    Box(Modifier.onGloballyPositioned { origin = it.positionInRoot() }) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            state.split?.let { SplitCard(it, state.cards) }
            val perRow = if (isLargeFont()) 1 else 2
            state.cards.chunked(perRow).forEach { pair ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    pair.forEach { ui -> key(ui.view.id) {
                        val id = ui.view.id
                        val coords = remember { arrayOfNulls<LayoutCoordinates>(1) }
                        val shape = RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp, bottomEnd = 22.dp, bottomStart = 22.dp)
                        val moveAction = stringResource(R.string.env_move_action)
                        val canDrag = ui.line !is CardLine.Sealed && ui.view.left > 0
                        PlanEnvelopeCard(
                            ui,
                            Modifier.weight(1f)
                                .pressClickable { haptics.perform(HapticLevel.Tap); onOpen(id) }
                                .onGloballyPositioned { cardCoords[id] = it }
                                .alpha(if (drag.id == id) 0.5f else 1f)
                                .then(if (drag.over == id) Modifier.border(2.dp, Tz.yellow, shape) else Modifier)
                                .semantics {
                                    if (canDrag) customActions = listOf(CustomAccessibilityAction(moveAction) { onMoveFrom(id); true })
                                },
                            amountModifier = if (!canDrag) Modifier else Modifier
                                .onGloballyPositioned { coords[0] = it }
                                .pointerInput(id) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = { at ->
                                            haptics.perform(HapticLevel.Tap)
                                            drag.id = id
                                            drag.pointer = coords[0]?.takeIf { it.isAttached }?.localToRoot(at) ?: Offset.Zero
                                            drag.over = null
                                        },
                                        onDrag = { change, amount ->
                                            change.consume()
                                            drag.pointer += amount
                                            drag.over = hit(drag.pointer, id)
                                        },
                                        onDragEnd = {
                                            val to = hit(drag.pointer, id)
                                            drag.id = null
                                            drag.over = null
                                            if (to != null) onDrop(id, to)
                                        },
                                        onDragCancel = { drag.id = null; drag.over = null },
                                    )
                                },
                        )
                    } }
                    if (pair.size == 1 && perRow == 2) Spacer(Modifier.weight(1f))
                }
            }
            NewEnvelopeRow(onNew)
            state.tip?.let { TipCard(it, onMoveTip) }
        }
        // The chip that follows the finger.
        val dragged = state.cards.firstOrNull { it.view.id == drag.id }
        if (dragged != null) {
            Text(
                poundsText(dragged.view.left),
                style = TzType.button.copy(fontSize = 18.sp),
                color = Tz.night,
                modifier = Modifier
                    // Absolute, so the finger's position holds in right-to-left too.
                    .align(AbsoluteAlignment.TopLeft)
                    .absoluteOffset { IntOffset((drag.pointer.x - origin.x).roundToInt() - 40.dp.roundToPx(), (drag.pointer.y - origin.y).roundToInt() - 56.dp.roundToPx()) }
                    .clip(RoundedCornerShape(50)).background(Tz.yellow).padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SplitCard(split: SplitSummary, cards: List<EnvelopeCardUi>) {
    val locale = LocalConfiguration.current.locales[0]
    val month = split.cycleStart.month.getDisplayName(TextStyle.FULL_STANDALONE, locale)
    TzCard {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, itemVerticalAlignment = Alignment.Bottom) {
                Text(stringResource(R.string.env_split_title, month), style = TzType.caption.copy(fontSize = 13.sp), color = Tz.text2)
                Text(poundsText(split.income), style = TzType.cardHeading.copy(fontSize = 22.sp), color = Tz.text)
            }
            val total = split.income.coerceAtLeast(1).toFloat()
            TzProgressBar(
                cards.map { it.view.monthly / total to pocketColor(it.view.color) },
                height = 14.dp,
            )
            if (split.unassigned > 0) {
                val amount = poundsText(split.unassigned)
                val text = stringResource(R.string.env_split_left, amount)
                val at = text.indexOf(amount)
                Text(
                    if (at < 0) buildAnnotatedString { append(text) } else buildAnnotatedString {
                        append(text.substring(0, at))
                        withStyle(SpanStyle(color = Tz.text, fontWeight = FontWeight.SemiBold)) { append(amount) }
                        append(text.substring(at + amount.length))
                    },
                    style = TzType.caption, color = Tz.text2,
                )
            }
        }
    }
}

/** The dashed "ظرف جديد" card (56 dp, radius 22, 1.5 dp dashed Dashed outline). */
@Composable
private fun NewEnvelopeRow(onClick: () -> Unit) {
    val haptics = LocalHaptics.current
    val shape = RoundedCornerShape(22.dp)
    Row(
        Modifier.fillMaxWidth().pressClickable { haptics.perform(HapticLevel.Tap); onClick() }.heightIn(min = 56.dp).clip(shape)
            .drawBehind {
                drawRoundRect(
                    Tz.dashed, cornerRadius = CornerRadius(22.dp.toPx()),
                    style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx()))),
                )
            },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TzIcon(TzIcons.add, 18.dp, null, tint = Tz.text2)
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.env_new_dashed), style = TzType.label.copy(fontSize = 14.sp, fontWeight = FontWeight.Medium), color = Tz.text2)
    }
}

/** The move tip (yellow-dim card, 22 radius): taps open the move sheet with the suggested pair. */
@Composable
private fun TipCard(tip: MoveTip, onClick: () -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    Column(
        Modifier.fillMaxWidth().pressClickable(onClick = onClick).clip(shape).background(Tz.yellowDim)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(stringResource(R.string.env_tip_title), style = TzType.label.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold), color = Tz.yellow)
        Text(
            stringResource(R.string.env_tip_text, tip.toName, tip.fromName, poundsText(tip.amount)),
            style = TzType.body.copy(fontSize = 13.sp, lineHeight = 22.sp), color = TipText,
        )
    }
}
