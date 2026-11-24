package com.masroufi.app.widget

import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.masroufi.app.MainActivity
import com.masroufi.app.R
import com.masroufi.app.core.inAppLanguage
import com.masroufi.app.data.repo.RepeatChip
import com.masroufi.app.notify.EXTRA_OPEN_MATCH
import com.masroufi.app.ui.components.HIDDEN_AMOUNT
import com.masroufi.app.ui.theme.Tz
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import java.time.LocalDate

/** Widgets are not created by Hilt, so they reach the singletons through this. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun summaryStore(): DaySummaryStore
    fun punch(): WidgetPunch
    fun clock(): Clock
}

private fun Context.entry() = EntryPointAccessors.fromApplication(applicationContext, WidgetEntryPoint::class.java)

/** What a widget draws from: the stored summary, today (a summary of another day draws as no ticket) and the app language. */
private class Snapshot(val summary: DaySummary?, val today: LocalDate, val ctx: Context)

private suspend fun snapshot(context: Context): Snapshot {
    val e = context.entry()
    // A widget must always draw something: a failed read looks like "no ticket yet".
    val summary = try {
        e.summaryStore().read()
    } catch (x: Exception) {
        Log.e("Widgets", "reading the summary failed", x)
        null
    }
    return Snapshot(summary, LocalDate.now(e.clock()), context.inAppLanguage())
}

private fun style(color: Color, size: Int, bold: Boolean = false, center: Boolean = false) = TextStyle(
    color = ColorProvider(color),
    fontSize = size.sp,
    fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium,
    textAlign = if (center) TextAlign.Center else null,
)

private fun Context.home() = actionStartActivity(Intent(this, MainActivity::class.java))

private fun Context.match() = actionStartActivity(
    Intent(this, MainActivity::class.java)
        .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        .putExtra(EXTRA_OPEN_MATCH, true),
)

// ---- the small ticket -------------------------------------------------------------------------------------------

class TicketWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snap = snapshot(context)
        provideContent { TicketCard(snap.ctx, ticketView(snap.summary, snap.today), GlanceModifier.fillMaxSize(), 28, small = true) }
    }
}

class TicketWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TicketWidget()
}

private fun TicketView.label(ctx: Context) = ctx.getString(
    when {
        mode == TicketMode.CLOSED -> R.string.widget_closed
        over -> R.string.widget_over
        else -> R.string.home_ticket_amount_label
    },
)

/** The yellow ticket: the small widget draws the holes row, the wide widget's panel the "of" line instead. */
@Composable
private fun TicketCard(ctx: Context, v: TicketView, modifier: GlanceModifier, corner: Int, small: Boolean) {
    Column(modifier = modifier.background(Tz.yellow).cornerRadius(corner.dp).padding(if (small) 14.dp else 12.dp).clickable(ctx.home())) {
        Text(v.label(ctx), style = style(Tz.yellowInk2, 11, bold = true))
        Spacer(GlanceModifier.defaultWeight())
        Row(verticalAlignment = Alignment.Bottom) {
            Text(v.number, style = style(Tz.night, if (small) 52 else 44, bold = true))
            if (v.mode != TicketMode.EMPTY) {
                Spacer(GlanceModifier.width(4.dp))
                Text(ctx.getString(R.string.widget_pound), style = style(Tz.night, 16, bold = true))
            }
        }
        Spacer(GlanceModifier.defaultWeight())
        if (small) {
            Row { repeat(HOLE_SLOTS) { i -> Hole(filled = i < v.filled); Spacer(GlanceModifier.width(5.dp)) } }
        } else if (v.mode != TicketMode.EMPTY) {
            Text(ctx.getString(R.string.widget_of_holes, v.of, v.holes.toString()), style = style(Tz.yellowInk2, 10, bold = true))
        }
    }
}

/** Glance has no dashed border: an open hole is a ring (a darker yellow disc with the ticket yellow inside). */
@Composable
private fun Hole(filled: Boolean) {
    if (filled) {
        Box(GlanceModifier.size(14.dp).cornerRadius(7.dp).background(Tz.night)) {}
    } else {
        Box(GlanceModifier.size(14.dp).cornerRadius(7.dp).background(Tz.yellowRule).padding(2.dp)) {
            Box(GlanceModifier.fillMaxSize().cornerRadius(5.dp).background(Tz.yellow)) {}
        }
    }
}

// ---- the small match --------------------------------------------------------------------------------------------

class MatchWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snap = snapshot(context)
        val rtl = snap.ctx.resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL
        provideContent { MatchCard(snap.ctx, matchView(snap.summary), rtl) }
    }
}

class MatchWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MatchWidget()
}

@Composable
private fun MatchCard(ctx: Context, v: MatchWidgetView, rtl: Boolean) {
    Column(modifier = GlanceModifier.fillMaxSize().background(Tz.turquoiseDim).cornerRadius(28.dp).padding(14.dp).clickable(ctx.match())) {
        Text(ctx.getString(R.string.match_title), style = style(Tz.turquoise, 11, bold = true))
        Spacer(GlanceModifier.defaultWeight())
        // The score reads "last week : you" left to right in both languages, so the order follows the layout direction.
        Row(modifier = GlanceModifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalAlignment = Alignment.CenterVertically) {
            if (rtl) Score(v.you, ctx.getString(R.string.match_you), Tz.yellow) else Score(v.last, ctx.getString(R.string.match_last_week), Tz.text2)
            Spacer(GlanceModifier.width(8.dp))
            Text(":", style = style(Tz.turquoise, 28, bold = true))
            Spacer(GlanceModifier.width(8.dp))
            if (rtl) Score(v.last, ctx.getString(R.string.match_last_week), Tz.text2) else Score(v.you, ctx.getString(R.string.match_you), Tz.yellow)
        }
        Spacer(GlanceModifier.defaultWeight())
        if (v.hatNear) {
            Text(ctx.getString(R.string.match_hat_nudge_title), modifier = GlanceModifier.fillMaxWidth(), style = style(Tz.turquoiseDimText, 11, center = true))
        }
    }
}

@Composable
private fun Score(number: String, caption: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(number, style = style(color, 52, bold = true))
        Text(caption, style = style(Tz.turquoiseDimText, 9))
    }
}

// ---- the wide widget --------------------------------------------------------------------------------------------

class WideWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snap = snapshot(context)
        provideContent { WideCard(snap.ctx, wideView(snap.summary, snap.today)) }
    }
}

class WideWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WideWidget()
}

@Composable
private fun WideCard(ctx: Context, v: WideView) {
    Row(modifier = GlanceModifier.fillMaxSize().background(Tz.night).cornerRadius(28.dp).padding(14.dp)) {
        TicketCard(ctx, v.ticket, GlanceModifier.width(130.dp).fillMaxHeight(), 20, small = false)
        Spacer(GlanceModifier.width(12.dp))
        if (v.locked) {
            Column(modifier = GlanceModifier.defaultWeight().fillMaxHeight().clickable(ctx.home()), verticalAlignment = Alignment.CenterVertically) {
                Text(HIDDEN_AMOUNT, style = style(Tz.text, 22, bold = true))
                Text(ctx.getString(R.string.widget_locked), style = style(Tz.text2, 11))
            }
        } else {
            // The chips, then the "+" (null) in the last cell, two to a row.
            val cells: List<ChipView?> = v.chips + listOf(null)
            Column(modifier = GlanceModifier.defaultWeight().fillMaxHeight()) {
                cells.chunked(2).forEachIndexed { row, pair ->
                    if (row > 0) Spacer(GlanceModifier.size(8.dp))
                    Row(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
                        pair.forEachIndexed { col, cell ->
                            if (col > 0) Spacer(GlanceModifier.width(8.dp))
                            if (cell != null) ChipButton(cell, GlanceModifier.defaultWeight().fillMaxHeight()) else PlusButton(ctx, GlanceModifier.defaultWeight().fillMaxHeight())
                        }
                        if (pair.size == 1) {
                            Spacer(GlanceModifier.width(8.dp))
                            Spacer(GlanceModifier.defaultWeight())
                        }
                    }
                }
            }
        }
    }
}

private val CHIP_CATEGORY = ActionParameters.Key<Long>("categoryId")
private val CHIP_AMOUNT = ActionParameters.Key<Long>("amount")
private val CHIP_POCKET = ActionParameters.Key<Long>("pocketId")
private val CHIP_NAME = ActionParameters.Key<String>("name")

@Composable
private fun ChipButton(c: ChipView, modifier: GlanceModifier) {
    val tap = actionRunCallback<RepeatPunchAction>(
        actionParametersOf(
            CHIP_CATEGORY to c.chip.categoryId, CHIP_AMOUNT to c.chip.amount, CHIP_POCKET to c.chip.pocketId, CHIP_NAME to c.chip.categoryName,
        ),
    )
    Column(
        modifier = modifier.background(Tz.surface).cornerRadius(16.dp).clickable(tap),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(c.chip.categoryName, style = style(Tz.text, 11, center = true), maxLines = 1)
        Text(c.amount, style = style(Tz.yellow, 18, bold = true, center = true))
    }
}

@Composable
private fun PlusButton(ctx: Context, modifier: GlanceModifier) {
    Box(modifier = modifier.background(Tz.yellow).cornerRadius(16.dp).clickable(ctx.home()), contentAlignment = Alignment.Center) {
        Text("+", style = style(Tz.night, 28, bold = true, center = true))
    }
}

/** The one-tap repeat: forwards the tapped chip to [WidgetPunch], which logs Home's entry; the summary then redraws every widget. */
class RepeatPunchAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val chip = RepeatChip(
            categoryId = parameters[CHIP_CATEGORY] ?: return,
            categoryName = parameters[CHIP_NAME] ?: return,
            amount = parameters[CHIP_AMOUNT] ?: return,
            pocketId = parameters[CHIP_POCKET] ?: return,
        )
        context.entry().punch().punch(chip)
    }
}
