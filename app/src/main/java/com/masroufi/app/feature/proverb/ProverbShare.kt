package com.masroufi.app.feature.proverb

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import com.masroufi.app.R
import com.masroufi.app.domain.Proverb
import com.masroufi.app.domain.ProverbCatalog
import com.masroufi.app.ui.paper.color
import com.masroufi.app.ui.theme.Tz
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/** Shares a card as a PNG (14-PROVERBS "Sharing"): the proverb, its number and a small app mark. No amounts, name or streak. */
object ProverbShare {
    private const val W = 1080
    private const val H = 1440

    /** Everything the image says, nothing more. */
    fun lines(context: Context, p: Proverb): List<String> =
        listOf(p.text, "${p.number} / ${ProverbCatalog.all.size}", context.getString(R.string.app_name))

    fun render(context: Context, p: Proverb): Bitmap {
        val (text, counter, mark) = lines(context, p)
        val color = p.set.color()
        val ink = (if (color == Tz.turquoise) Tz.turquoiseInk else Tz.night).toArgb()
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Tz.night.toArgb())

        // The mock's 300 x 400 card at 3x.
        val card = RectF(90f, 120f, W - 90f, H - 120f)
        c.drawRoundRect(card, 84f, 84f, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color.toArgb() })
        c.save()
        c.clipPath(Path().apply { addRoundRect(card, 84f, 84f, Path.Direction.CW) })
        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 4.2f
            this.color = ink
            alpha = 64
        }
        for (r in 150..420 step 54) c.drawCircle(card.left + 180f, card.top + 1110f, r.toFloat(), ring)
        c.restore()

        val lalezar = ResourcesCompat.getFont(context, R.font.lalezar) ?: Typeface.DEFAULT
        val alexandria = ResourcesCompat.getFont(context, R.font.alexandria) ?: Typeface.DEFAULT
        val pad = 66f
        c.drawText(counter, card.left + pad, card.top + pad + 54f, TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = lalezar; textSize = 54f; this.color = ink
        })
        val body = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = lalezar; textSize = 132f; this.color = ink }
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, body, (card.width() - 2 * pad).toInt())
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setTextDirection(TextDirectionHeuristics.RTL)
            .setLineSpacing(0f, 1.25f)
            .build()
        c.save()
        c.translate(card.left + pad, card.centerY() - layout.height / 2f)
        layout.draw(c)
        c.restore()
        c.drawText(mark, card.centerX(), card.bottom - pad, TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = alexandria; textSize = 48f; this.color = ink; textAlign = Paint.Align.CENTER
        })
        return bmp
    }

    /** Writes the card to cacheDir/share and opens the chooser. Throws [IOException] when the PNG cannot be written. */
    fun share(context: Context, p: Proverb) {
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        val file = File(dir, "proverb_${p.number}.png")
        val bmp = render(context, p)
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
        context.startActivity(chooser)
    }
}
