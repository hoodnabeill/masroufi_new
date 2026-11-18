package com.masroufi.app.feature.story

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import android.view.View
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import com.masroufi.app.R
import com.masroufi.app.ui.theme.Tz
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlin.math.ceil

/**
 * Shares one story slide as a PNG drawn on the phone (decision 3). The picture is the slide's [SlideText] and nothing
 * else, so a text built with "hide" on has no amount in it. The frame is the mock's 390 x 844, drawn at 1080 px wide.
 */
object StoryShare {
    private const val W = 1080
    private const val H = 2337
    private const val UNITS_W = 390f
    private const val UNITS_H = 844f
    private const val GUTTER = 20f
    private const val CONTENT_W = UNITS_W - 2 * GUTTER
    private const val GRID_COLUMNS = 8

    fun render(context: Context, t: SlideText): Bitmap {
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.scale(W / UNITS_W, H / UNITS_H)
        val rtl = context.resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL
        val lalezar = ResourcesCompat.getFont(context, R.font.lalezar) ?: Typeface.DEFAULT
        val alexandria = ResourcesCompat.getFont(context, R.font.alexandria) ?: Typeface.DEFAULT
        val ink = Tz.night.toArgb()
        fun paint(face: Typeface, size: Float, color: Int = ink, bold: Boolean = false) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = face; textSize = size; this.color = color; isFakeBoldText = bold
        }
        fun layout(text: String, p: TextPaint, width: Float, spacing: Float = 1f, end: Boolean = false): StaticLayout =
            StaticLayout.Builder.obtain(text, 0, text.length, p, ceil(width).toInt())
                .setAlignment(if (end) Layout.Alignment.ALIGN_OPPOSITE else Layout.Alignment.ALIGN_NORMAL)
                .setTextDirection(if (rtl) TextDirectionHeuristics.RTL else TextDirectionHeuristics.LTR)
                .setLineSpacing(0f, spacing)
                .build()
        fun draw(l: StaticLayout, x: Float, y: Float) {
            c.save(); c.translate(x, y); l.draw(c); c.restore()
        }
        /** x of a box [w] wide at the start edge of the content. */
        fun startX(w: Float) = if (rtl) UNITS_W - GUTTER - w else GUTTER

        c.drawColor(Tz.yellow.toArgb())
        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.5f; color = Tz.yellowRule2.toArgb() }
        for (r in 80..230 step 30) c.drawCircle(60f, 804f, r.toFloat(), ring)

        var y = 40f
        draw(layout(t.header, paint(alexandria, 14f, bold = true), CONTENT_W), startX(CONTENT_W), y)
        y += 48f
        t.kicker?.let {
            draw(layout(it, paint(alexandria, 16f, Tz.yellowInk.toArgb(), bold = true), CONTENT_W), startX(CONTENT_W), y)
            y += 30f
        }
        if (t.big.isNotEmpty()) {
            val size = when {
                t.big.length <= 3 -> 150f
                t.big.length <= 5 -> 110f
                t.big.length <= 7 -> 80f
                else -> 56f
            }
            val bp = paint(lalezar, size)
            val bigW = bp.measureText(t.big) + 4f
            val big = layout(t.big, bp, bigW, 0.9f)
            val bigX = startX(bigW)
            draw(big, bigX, y)
            t.suffix?.let {
                val sl = layout(it, paint(lalezar, 34f), 130f)
                val sw = (0 until sl.lineCount).maxOf { i -> sl.getLineWidth(i) }
                draw(sl, if (rtl) bigX - 10f - sw else bigX + bigW + 10f, y + big.height - sl.height - 6f)
            }
            y += big.height + 14f
        }

        fun line() {
            val text = t.line ?: return
            val l = layout(text, paint(lalezar, 26f), CONTENT_W, 1.35f)
            draw(l, startX(CONTENT_W), y)
            y += l.height + 16f
        }
        if (t.summary) line()

        t.grid?.let { cells ->
            val gap = 6f
            val cell = (CONTENT_W - gap * (GRID_COLUMNS - 1)) / GRID_COLUMNS
            val h = 34f
            val night = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Tz.night.toArgb() }
            val over = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Tz.carnelian.toArgb() }
            val dashed = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE; strokeWidth = 2f; color = Tz.night.toArgb(); pathEffect = DashPathEffect(floatArrayOf(5f, 4f), 0f)
            }
            cells.forEachIndexed { i, ch ->
                val col = i % GRID_COLUMNS
                val x = if (rtl) UNITS_W - GUTTER - (col + 1) * cell - col * gap else GUTTER + col * (cell + gap)
                val top = y + (i / GRID_COLUMNS) * (h + gap)
                val r = RectF(x, top, x + cell, top + h)
                when (ch) {
                    'U' -> c.drawRoundRect(r, 6f, 6f, night)
                    'O' -> c.drawRoundRect(r, 6f, 6f, over)
                    else -> { r.inset(1f, 1f); c.drawRoundRect(r, 6f, 6f, dashed) }
                }
            }
            y += ((cells.length + GRID_COLUMNS - 1) / GRID_COLUMNS) * (h + gap) + 10f
        }

        if (t.legend.isNotEmpty()) {
            val p = paint(alexandria, 12f, bold = true).apply { textAlign = if (rtl) Paint.Align.RIGHT else Paint.Align.LEFT }
            val swatches = listOf(Tz.night.toArgb(), Tz.carnelian.toArgb(), 0)
            var x = if (rtl) UNITS_W - GUTTER else GUTTER
            t.legend.forEachIndexed { i, text ->
                val w = 16f + p.measureText(text)
                val room = if (rtl) x - w >= GUTTER else x + w <= UNITS_W - GUTTER
                if (!room) { y += 20f; x = if (rtl) UNITS_W - GUTTER else GUTTER }
                val left = if (rtl) x - 10f else x
                val sw = RectF(left, y, left + 10f, y + 10f)
                if (swatches[i] != 0) {
                    c.drawRoundRect(sw, 3f, 3f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = swatches[i] })
                } else {
                    sw.inset(0.75f, 0.75f)
                    c.drawRoundRect(sw, 3f, 3f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        style = Paint.Style.STROKE; strokeWidth = 1.5f; color = ink; pathEffect = DashPathEffect(floatArrayOf(3f, 2f), 0f)
                    })
                }
                c.drawText(text, if (rtl) left - 6f else left + 16f, y + 10f, p)
                x = if (rtl) x - w - 14f else x + w + 14f
            }
            y += 30f
        }

        if (t.rows.isNotEmpty()) {
            val rowH = 38f
            val pad = if (t.summary) 16f else 0f
            val h = t.rows.size * rowH + 2 * pad
            if (t.summary) {
                c.drawRoundRect(RectF(GUTTER, y, UNITS_W - GUTTER, y + h), 24f, 24f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Tz.night.toArgb() })
            }
            val fg = if (t.summary) Tz.yellow.toArgb() else ink
            val inner = CONTENT_W - 2 * pad
            val left = GUTTER + pad
            t.rows.forEachIndexed { i, r ->
                val top = y + pad + i * rowH
                val labelBox = layout(r.label, paint(alexandria, 15f, fg, bold = true), inner * 0.6f)
                val valueBox = layout(r.value, paint(lalezar, 22f, fg), inner * 0.4f, end = true)
                // The label sits at the start edge, the value at the end edge.
                draw(labelBox, if (rtl) left + inner - inner * 0.6f else left, top + 4f)
                draw(valueBox, if (rtl) left else left + inner - inner * 0.4f, top)
            }
            y += h + 16f
        }
        if (!t.summary) line()

        val mark = paint(alexandria, 14f, Tz.yellowInk.toArgb()).apply { textAlign = Paint.Align.CENTER }
        c.drawText(context.getString(R.string.app_name), UNITS_W / 2f, 820f, mark)
        return bmp
    }

    /**
     * Renders the slide and writes cacheDir/share/story_<name>.png off the main thread, then opens the chooser on the
     * caller's thread: call it from a scope on Main. Throws [IOException] when the PNG cannot be written.
     */
    suspend fun share(context: Context, name: String, t: SlideText) {
        val chooser = withContext(Dispatchers.Default) { chooser(context, name, t) }
        context.startActivity(chooser)
    }

    private fun chooser(context: Context, name: String, t: SlideText): Intent {
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        val file = File(dir, "story_$name.png")
        val bmp = render(context, t)
        try {
            FileOutputStream(file).use { if (!bmp.compress(Bitmap.CompressFormat.PNG, 100, it)) throw IOException("PNG encode failed") }
        } finally {
            bmp.recycle()
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val send = Intent(Intent.ACTION_SEND)
            .setType("image/png")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val chooser = Intent.createChooser(send, null).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        if (context !is Activity) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return chooser
    }
}
