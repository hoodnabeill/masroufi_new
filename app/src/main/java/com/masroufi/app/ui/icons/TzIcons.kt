package com.masroufi.app.ui.icons

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.masroufi.app.ui.theme.Tz
import java.util.concurrent.ConcurrentHashMap

/** Stroke draws the line only; FillStroke also fills; Ink is a detail drawn in the surface colour on a filled icon. */
enum class PartMode { Stroke, FillStroke, Ink }

class Part(val d: String, val mode: PartMode = PartMode.Stroke)

/** One icon: a 24 grid outline and an optional filled (selected) drawing. */
class TzIconDef(val name: String, val outline: List<Part>, val filled: List<Part>? = null) {
    private val cache = ConcurrentHashMap<String, ImageVector>()

    /** Tinted layer: the outline, or the filled drawing's Stroke and FillStroke parts. */
    fun vector(stroke: Float, filled: Boolean = false): ImageVector {
        val useFilled = filled && this.filled != null
        val parts = if (useFilled) this.filled!! else outline
        return cache.computeIfAbsent("v$stroke$useFilled") {
            build(parts.filter { it.mode != PartMode.Ink }, stroke)
        }
    }

    /** Ink layer of the filled drawing, or null when it has none. */
    fun ink(stroke: Float): ImageVector? {
        val parts = filled?.filter { it.mode == PartMode.Ink }.orEmpty()
        if (parts.isEmpty()) return null
        return cache.computeIfAbsent("i$stroke") { build(parts, stroke) }
    }

    fun allVectors(): List<ImageVector> = TzIcons.strokes.flatMap { s ->
        listOfNotNull(vector(s), if (filled != null) vector(s, true) else null, ink(s))
    }

    private fun build(parts: List<Part>, stroke: Float): ImageVector {
        val b = ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        )
        parts.forEach {
            b.addPath(
                pathData = PathParser().parsePathString(it.d).toNodes(),
                fill = if (it.mode == PartMode.FillStroke) SolidColor(Color.Black) else null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = stroke,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
        return b.build()
    }
}

/**
 * The icon family (03-DESIGN-SYSTEM.md, "Icons"): paths copied from `screens/*.dc.html`, 24 grid, round caps and
 * joins. Rects, circles and dashes are expanded to path data. Drawn black; [TzIcon] tints.
 */
object TzIcons {
    const val Stroke = 1.9f
    const val StrokeSmall = 2.2f
    val strokes = listOf(Stroke, StrokeSmall)

    /** Main/Envelopes tab bar (dashes expanded to segments). */
    val today = TzIconDef(
        "today",
        listOf(
            Part("M3 8a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2v2a2 2 0 0 0 0 4v2a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-2a2 2 0 0 0 0-4z"),
            Part("M14 6v2M14 10v2M14 14v2"),
        ),
        listOf(
            Part("M3 8a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2v2a2 2 0 0 0 0 4v2a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-2a2 2 0 0 0 0-4z", PartMode.FillStroke),
            Part("M14 7.5v2M14 11.5v2M14 15.5v1", PartMode.Ink),
        ),
    )

    /** Main/Roll tab bar. */
    val roll = TzIconDef(
        "roll",
        listOf(
            Part("M5 4h11a3 3 0 0 1 3 3v13H8a3 3 0 0 1-3-3z"),
            Part("M9 9h6M9 13h6"),
        ),
        listOf(
            Part("M5 4h11a3 3 0 0 1 3 3v13H8a3 3 0 0 1-3-3z", PartMode.FillStroke),
            Part("M9 9h6M9 13h6", PartMode.Ink),
        ),
    )

    /** Main tab bar (envelope); filled from Envelopes. */
    val plan = TzIconDef(
        "plan",
        listOf(
            Part("M5 5h14a2 2 0 0 1 2 2v10a2 2 0 0 1 -2 2h-14a2 2 0 0 1 -2 -2v-10a2 2 0 0 1 2 -2z"),
            Part("m3 7 9 7 9-7"),
        ),
        listOf(
            Part("M5 5h14a2 2 0 0 1 2 2v10a2 2 0 0 1 -2 2h-14a2 2 0 0 1 -2 -2v-10a2 2 0 0 1 2 -2z", PartMode.FillStroke),
            Part("m4.5 8 7.5 6 7.5-6", PartMode.Ink),
        ),
    )

    /** Main tab bar; filled from People. */
    val people = TzIconDef(
        "people",
        listOf(
            Part("M5.8 8a3.2 3.2 0 1 0 6.4 0a3.2 3.2 0 1 0 -6.4 0z"),
            Part("M3 20c0-3.3 2.7-6 6-6s6 2.7 6 6"),
            Part("M15.1 9a2.4 2.4 0 1 0 4.8 0a2.4 2.4 0 1 0 -4.8 0z"),
            Part("M16 14.2c2.8.2 5 2.6 5 5.8"),
        ),
        listOf(
            Part("M5.8 8a3.2 3.2 0 1 0 6.4 0a3.2 3.2 0 1 0 -6.4 0z", PartMode.FillStroke),
            Part("M3 20c0-3.3 2.7-6 6-6s6 2.7 6 6", PartMode.FillStroke),
            Part("M15.1 9a2.4 2.4 0 1 0 4.8 0a2.4 2.4 0 1 0 -4.8 0z", PartMode.FillStroke),
            Part("M16 14.2c2.8.2 5 2.6 5 5.8", PartMode.FillStroke),
        ),
    )

    /** Main tab bar (plus); filled DERIVED (same plus, nothing closed to fill). */
    val add = TzIconDef(
        "add",
        listOf(
            Part("M12 5v14M5 12h14"),
        ),
        listOf(
            Part("M12 5v14M5 12h14"),
        ),
    )

    /** Album (outline), Lock (filled). */
    val lock = TzIconDef(
        "lock",
        listOf(
            Part("M7 11h10a2 2 0 0 1 2 2v5a2 2 0 0 1 -2 2h-10a2 2 0 0 1 -2 -2v-5a2 2 0 0 1 2 -2z"),
            Part("M8 11V8a4 4 0 0 1 8 0v3"),
        ),
        listOf(
            Part("M7 11h10a3 3 0 0 1 3 3v4a3 3 0 0 1 -3 3h-10a3 3 0 0 1 -3 -3v-4a3 3 0 0 1 3 -3z", PartMode.FillStroke),
            Part("M8 11V8a4 4 0 0 1 8 0v3"),
        ),
    )

    /** Main. */
    val flame = TzIconDef(
        "flame",
        listOf(
            Part("M12 3c1 3 4 4.5 4 9a4 4 0 0 1-8 0c0-1.5.5-2.5 1.5-3.5C10 10 11 7 12 3z"),
        ),
    )

    /** Main. */
    val mic = TzIconDef(
        "mic",
        listOf(
            Part("M12 3h0a3 3 0 0 1 3 3v5a3 3 0 0 1 -3 3h0a3 3 0 0 1 -3 -3v-5a3 3 0 0 1 3 -3z"),
            Part("M5 11a7 7 0 0 0 14 0M12 18v3"),
        ),
    )

    /** Main. */
    val jar = TzIconDef(
        "jar",
        listOf(
            Part("M8 3h8v3l2 3v9a3 3 0 0 1-3 3H9a3 3 0 0 1-3-3V9l2-3z"),
            Part("M8 6h8"),
        ),
    )

    /** Main (moon). */
    val season = TzIconDef(
        "season",
        listOf(
            Part("M20 14.5A8 8 0 1 1 9.5 4a6.5 6.5 0 0 0 10.5 10.5z"),
        ),
    )

    /** Roll. */
    val search = TzIconDef(
        "search",
        listOf(
            Part("M4.5 11a6.5 6.5 0 1 0 13 0a6.5 6.5 0 1 0 -13 0z"),
            Part("m16 16 4.5 4.5"),
        ),
    )

    /** Album row end (points left). */
    val chevron = TzIconDef(
        "chevron",
        listOf(
            Part("m15 6-6 6 6 6"),
        ),
    )

    /** Roll. */
    val chevronDown = TzIconDef(
        "chevronDown",
        listOf(
            Part("m6 9 6 6 6-6"),
        ),
    )

    /** AddPunch. */
    val close = TzIconDef(
        "close",
        listOf(
            Part("M6 6l12 12M18 6 6 18"),
        ),
    )

    /** Album/Settings back button (points right, RTL). */
    val back = TzIconDef(
        "back",
        listOf(
            Part("m9 6 6 6-6 6"),
        ),
    )

    /** Backup. */
    val check = TzIconDef(
        "check",
        listOf(
            Part("m5 12.5 4.5 4.5L19 7.5"),
        ),
    )

    /** Lock. */
    val fingerprint = TzIconDef(
        "fingerprint",
        listOf(
            Part("M12 10a2 2 0 0 0-2 2c0 1.02-.1 2.51-.26 4"),
            Part("M14 13.12c0 2.38 0 6.38-1 8.88"),
            Part("M17.29 21.02c.12-.6.43-2.3.5-3.02"),
            Part("M2 12a10 10 0 0 1 18-6"),
            Part("M2 16h.01"),
            Part("M21.8 16c.2-2 .131-5.354 0-6"),
            Part("M5 19.5C5.5 18 6 15 6 12a6 6 0 0 1 .34-2"),
            Part("M8.65 22c.21-.66.45-1.32.57-2"),
            Part("M9 6.8a6 6 0 0 1 9 5.2v2"),
        ),
    )

    /** AddPunch keypad backspace (Lock's variant is the same drawing). */
    val delete = TzIconDef(
        "delete",
        listOf(
            Part("M9 5h10a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H9l-6-7z"),
            Part("m12 9.5 5 5M17 9.5l-5 5"),
        ),
    )

    /** Gameya. */
    val share = TzIconDef(
        "share",
        listOf(
            Part("M4 12v7h16v-7M12 3v12M8 7l4-4 4 4"),
        ),
    )

    /** Album. */
    val settings = TzIconDef(
        "settings",
        listOf(
            Part("M9 12a3 3 0 1 0 6 0a3 3 0 1 0 -6 0z"),
            Part("M12 3v3M12 18v3M3 12h3M18 12h3M5.6 5.6l2.1 2.1M16.3 16.3l2.1 2.1M5.6 18.4l2.1-2.1M16.3 7.7l2.1-2.1"),
        ),
    )

    /** Gameya. */
    val swap = TzIconDef(
        "swap",
        listOf(
            Part("M7 7h13M17 4l3 3-3 3M17 17H4M7 14l-3 3 3 3"),
        ),
    )

    /** Profile. */
    val minus = TzIconDef(
        "minus",
        listOf(
            Part("M5 12h14"),
        ),
    )

    /** WalletDetail. */
    val edit = TzIconDef(
        "edit",
        listOf(
            Part("M4 20h4L19 9l-4-4L4 16zM13.5 6.5l4 4"),
        ),
    )

    /** WalletDetail. */
    val arrowUp = TzIconDef(
        "arrowUp",
        listOf(
            Part("M12 19V5M6 11l6-6 6 6"),
        ),
    )

    /** Backup. */
    val download = TzIconDef(
        "download",
        listOf(
            Part("M12 4v11"),
            Part("m7 11 5 5 5-5"),
            Part("M5 20h14"),
        ),
    )

    /** WalletDetail. */
    val refresh = TzIconDef(
        "refresh",
        listOf(
            Part("M20 12a8 8 0 1 1-2.3-5.7M20 4v4h-4"),
        ),
    )

    /** WalletDetail (cash withdrawal). */
    val withdraw = TzIconDef(
        "withdraw",
        listOf(
            Part("M5 4h14a2 2 0 0 1 2 2v4a2 2 0 0 1 -2 2h-14a2 2 0 0 1 -2 -2v-4a2 2 0 0 1 2 -2z"),
            Part("M7 12v6h10v-6M12 14v3"),
        ),
    )

    /** Wallets. */
    val bank = TzIconDef(
        "bank",
        listOf(
            Part("M3 10 12 4l9 6M5 10v8M9.5 10v8M14.5 10v8M19 10v8M3 20h18"),
        ),
    )

    /** Wallets. */
    val grid = TzIconDef(
        "grid",
        listOf(
            Part("M4 3h6a1 1 0 0 1 1 1v6a1 1 0 0 1 -1 1h-6a1 1 0 0 1 -1 -1v-6a1 1 0 0 1 1 -1z"),
            Part("M14 3h6a1 1 0 0 1 1 1v6a1 1 0 0 1 -1 1h-6a1 1 0 0 1 -1 -1v-6a1 1 0 0 1 1 -1z"),
            Part("M4 13h6a1 1 0 0 1 1 1v6a1 1 0 0 1 -1 1h-6a1 1 0 0 1 -1 -1v-6a1 1 0 0 1 1 -1z"),
            Part("M14 13h6a1 1 0 0 1 1 1v6a1 1 0 0 1 -1 1h-6a1 1 0 0 1 -1 -1v-6a1 1 0 0 1 1 -1z"),
        ),
    )

    /** Wallets. */
    val list = TzIconDef(
        "list",
        listOf(
            Part("M4 6h16M4 12h10M4 18h7"),
        ),
    )

    /** AddPunch (punch the ticket); dot is filled. */
    val punch = TzIconDef(
        "punch",
        listOf(
            Part("M4 12a8 8 0 1 0 16 0a8 8 0 1 0 -16 0z"),
            Part("M9.5 12a2.5 2.5 0 1 0 5 0a2.5 2.5 0 1 0 -5 0z", PartMode.FillStroke),
        ),
    )

    /** AddPunch. */
    val coffee = TzIconDef(
        "coffee",
        listOf(
            Part("M5 8h11v5a5 5 0 0 1-5 5h-1a5 5 0 0 1-5-5zM16 10h2a2 2 0 0 1 0 4h-2"),
        ),
    )

    /** AddPunch. */
    val transport = TzIconDef(
        "transport",
        listOf(
            Part("M7 4h10a3 3 0 0 1 3 3v7a3 3 0 0 1 -3 3h-10a3 3 0 0 1 -3 -3v-7a3 3 0 0 1 3 -3z"),
            Part("M4 11h16M8 20v-3M16 20v-3"),
        ),
    )

    /** AddPunch. */
    val food = TzIconDef(
        "food",
        listOf(
            Part("M7 3v8M5 3v4a2 2 0 0 0 4 0V3M7 11v10M16 3c-2 2-2 6 0 8v10"),
        ),
    )

    /** AddPunch. */
    val groceries = TzIconDef(
        "groceries",
        listOf(
            Part("M4 5h2l2 10h9l2-7H7"),
            Part("M8.6 19a1.4 1.4 0 1 0 2.8 0a1.4 1.4 0 1 0 -2.8 0z"),
            Part("M14.6 19a1.4 1.4 0 1 0 2.8 0a1.4 1.4 0 1 0 -2.8 0z"),
        ),
    )

    /** Installments. */
    val glasses = TzIconDef(
        "glasses",
        listOf(
            Part("M3 13a4 4 0 1 0 8 0a4 4 0 1 0 -8 0z"),
            Part("M13 13a4 4 0 1 0 8 0a4 4 0 1 0 -8 0z"),
            Part("M11 13h2M3 13 5 6M21 13l-2-7"),
        ),
    )

    /** Installments. */
    val phone = TzIconDef(
        "phone",
        listOf(
            Part("M8.5 2h7a2.5 2.5 0 0 1 2.5 2.5v15a2.5 2.5 0 0 1 -2.5 2.5h-7a2.5 2.5 0 0 1 -2.5 -2.5v-15a2.5 2.5 0 0 1 2.5 -2.5z"),
            Part("M11 18h2"),
        ),
    )

    /** Installments. */
    val fridge = TzIconDef(
        "fridge",
        listOf(
            Part("M8 2h8a2 2 0 0 1 2 2v16a2 2 0 0 1 -2 2h-8a2 2 0 0 1 -2 -2v-16a2 2 0 0 1 2 -2z"),
            Part("M6 10h12M9 5.5v2M9 13v3"),
        ),
    )

    /** People. */
    val shop = TzIconDef(
        "shop",
        listOf(
            Part("M4 9h16l-1-4H5zM5 9v10h14V9M9 19v-5h6v5"),
        ),
    )

    /** DRAWN, not from a mock. */
    val wallet = TzIconDef(
        "wallet",
        listOf(
            Part("M17 8V6a1 1 0 0 0-1-1H5a2 2 0 0 0-2 2v11a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-8a2 2 0 0 0-2-2H5"),
            Part("M16 14h2"),
        ),
    )

    /** DRAWN, not from a mock. */
    val calendar = TzIconDef(
        "calendar",
        listOf(
            Part("M5 5h14a2 2 0 0 1 2 2v12a2 2 0 0 1 -2 2h-14a2 2 0 0 1 -2 -2v-12a2 2 0 0 1 2 -2z"),
            Part("M3 10h18M8 3v4M16 3v4"),
        ),
    )

    /** DRAWN, not from a mock. */
    val bell = TzIconDef(
        "bell",
        listOf(
            Part("M6 17v-6a6 6 0 0 1 12 0v6l2 2H4zM10 21.5h4"),
        ),
    )

    /** DRAWN, not from a mock. */
    val gold = TzIconDef(
        "gold",
        listOf(
            Part("M3 18l2.5-7h13L21 18zM9 11l1-4h4l1 4"),
        ),
    )

    /** Same drawing as [plan]. */
    val envelope = plan


    val all: List<TzIconDef> = listOf(
        today,
        roll,
        plan,
        people,
        add,
        lock,
        flame,
        mic,
        jar,
        season,
        search,
        chevron,
        chevronDown,
        close,
        back,
        check,
        fingerprint,
        delete,
        share,
        settings,
        swap,
        minus,
        edit,
        arrowUp,
        download,
        refresh,
        withdraw,
        bank,
        grid,
        list,
        punch,
        coffee,
        transport,
        food,
        groceries,
        glasses,
        phone,
        fridge,
        shop,
        wallet,
        calendar,
        bell,
        gold
    ).distinct()
}

/**
 * Draws [icon] tinted [tint]; uses stroke 2.2 at 18 dp and below, 1.9 above. [filled] is the selected state; its
 * ink details take [inkColor], which must match the surface behind the icon.
 */
@Composable
fun TzIcon(
    icon: TzIconDef,
    size: Dp,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = Tz.text2,
    filled: Boolean = false,
    inkColor: Color = Tz.surface,
) {
    val stroke = if (size <= 18.dp) TzIcons.StrokeSmall else TzIcons.Stroke
    val showFilled = filled && icon.filled != null
    Box(modifier.size(size)) {
        Icon(icon.vector(stroke, showFilled), contentDescription, Modifier.size(size), tint)
        if (showFilled) icon.ink(stroke)?.let { Icon(it, null, Modifier.size(size), inkColor) }
    }
}
