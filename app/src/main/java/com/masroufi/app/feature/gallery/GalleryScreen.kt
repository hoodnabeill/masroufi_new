package com.masroufi.app.feature.gallery

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.masroufi.app.entitlements.DebugEntitlements
import com.masroufi.app.entitlements.Entitlements
import com.masroufi.app.ui.components.ChipStyle
import com.masroufi.app.ui.components.Odometer
import com.masroufi.app.ui.components.PrimaryButton
import com.masroufi.app.ui.components.SecondaryButton
import com.masroufi.app.ui.components.Segmented
import com.masroufi.app.ui.components.TextLink
import com.masroufi.app.ui.components.TzCard
import com.masroufi.app.ui.components.TzChip
import com.masroufi.app.ui.components.TzProgressBar
import com.masroufi.app.ui.components.TzRow
import com.masroufi.app.ui.components.TzSheet
import com.masroufi.app.ui.components.TzTab
import com.masroufi.app.ui.components.TzTabBar
import com.masroufi.app.ui.components.TzToggle
import com.masroufi.app.ui.haptics.HapticLevel
import com.masroufi.app.ui.haptics.LocalHaptics
import com.masroufi.app.ui.icons.TzIcon
import com.masroufi.app.ui.icons.TzIcons
import com.masroufi.app.ui.paper.EnvelopeCard
import com.masroufi.app.ui.paper.ProverbCard
import com.masroufi.app.ui.paper.Qersh
import com.masroufi.app.ui.paper.QershBubble
import com.masroufi.app.ui.paper.QershMood
import com.masroufi.app.ui.paper.Stamp
import com.masroufi.app.ui.paper.Stub
import com.masroufi.app.ui.paper.Ticket
import com.masroufi.app.ui.theme.Tz
import com.masroufi.app.ui.theme.TzType

// Debug-only screen: section titles and the sample label are developer text, hard-coded on purpose.
private const val SAMPLE = "عيّنة"
private const val TICKET_CAPTION = "كل تسجيلة = خُرم في التذكرة"
private const val TICKET_SUB = "من 240 · صرفت 84 في 3 مرات"
private const val TICKET_ZERO = "لسه مصرفتش حاجة النهارده"
private const val TICKET_OVER = "عدّيت تذكرة النهارده بـ 35. اتوزّعوا على باقي الأيام."

private val progressSets = listOf(
    listOf(0.2f to Tz.yellow, 0.3f to Tz.turquoise),
    listOf(0.5f to Tz.yellow, 0.3f to Tz.turquoise, 0.15f to Tz.carnelian),
    listOf(1f to Tz.carnelian),
    listOf(0f to Tz.yellow),
)
private val chipLabels = listOf("المواصلات", "الأكل", "مرتين")

private fun LazyListScope.section(key: String, title: String, content: @Composable () -> Unit) {
    item(key = key) {
        Column(Modifier.fillMaxWidth().padding(top = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = TzType.cardHeading, color = Tz.text)
            Text(SAMPLE, style = TzType.caption, color = Tz.text3)
            content()
        }
    }
}

// Stub content as in Main.dc.html: two lines plus a night pill with yellow Lalezar text.
@Composable
private fun SampleStub() {
    Stub {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("اقطعها آخر اليوم", style = TzType.body.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold), color = Tz.yellowInk)
                Text("واللي يفضل يروح الحصّالة", style = TzType.caption, color = Tz.yellowInk2)
            }
            Box(Modifier.height(48.dp).clip(RoundedCornerShape(24.dp)).background(Tz.night).padding(horizontal = 20.dp), contentAlignment = Alignment.Center) {
                Text("قفّل اليوم", style = TzType.button.copy(fontSize = 18.sp), color = Tz.yellow)
            }
        }
    }
}

@Composable
private fun SampleTicket(subLine: String, holes: Int, left: Long = 15_600) {
    Ticket(
        label = "تذكرة اليوم · صالحة ليوم واحد", amountLabel = "معاك النهارده", leftPiastres = left, subLine = subLine, holes = holes,
        caption = TICKET_CAPTION, stub = { SampleStub() },
        contentDescription = "معاك النهارده ${maxOf(left, 0) / 100} جنيه",
    )
}

@Composable
fun GalleryScreen(entitlements: Entitlements) {
    val haptics = LocalHaptics.current
    var choice by remember { mutableIntStateOf(0) }
    var filter by remember { mutableIntStateOf(0) }
    var segment by remember { mutableIntStateOf(0) }
    var toggle by remember { mutableStateOf(true) }
    var progressStep by remember { mutableIntStateOf(0) }
    var odometer by remember { mutableLongStateOf(24_000L) }
    var tab by remember { mutableStateOf(TzTab.Today) }
    var sheet by remember { mutableStateOf(false) }
    var punchHoles by remember { mutableIntStateOf(3) }
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    // A real caller fires Confirm once per action; the components fire their own haptics.
    fun confirm(action: () -> Unit) {
        haptics.perform(HapticLevel.Confirm)
        action()
    }


    LazyColumn(
        Modifier.fillMaxSize().background(Tz.night),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = top + 16.dp, bottom = bottom + 32.dp),
    ) {
        if (entitlements is DebugEntitlements) item(key = "plus") {
            val plus by entitlements.isPlus.collectAsState()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Plus (debug)", style = TzType.body, color = Tz.text)
                TzToggle(plus, entitlements::setDebugOverride, "Plus (debug)")
            }
        }
        section("icons", "Icons") {}
        items(TzIcons.all, key = { "icon:${it.name}" }) { icon ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TzIcon(icon, 24.dp, null, tint = Tz.text)
                if (icon.filled != null) TzIcon(icon, 24.dp, null, tint = Tz.yellow, filled = true)
                Text(icon.name, style = TzType.caption, color = Tz.text2)
            }
        }
        section("buttons", "Buttons") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PrimaryButton("قفّل اليوم", {}, Modifier.fillMaxWidth())
                PrimaryButton("قفّل اليوم", {}, Modifier.fillMaxWidth(), enabled = false)
                SecondaryButton("تمام", {}, Modifier.fillMaxWidth())
                TextLink("ظرف جديد", {})
            }
        }
        section("chips", "Chips") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    chipLabels.forEachIndexed { i, l -> TzChip(l, choice == i, { choice = i }, style = ChipStyle.Choice) }
                }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    chipLabels.forEachIndexed { i, l -> TzChip(l, filter == i, { filter = i }, style = ChipStyle.Filter) }
                }
            }
        }
        section("segmented", "Segmented") {
            Segmented(listOf("تمام", "مدبّر", "مقتصد"), segment, { segment = it })
        }
        section("cards", "Cards & rows") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TzCard {
                    Text("ألبومك", style = TzType.cardHeading, color = Tz.text)
                    Text("18 من 60", style = TzType.caption, color = Tz.text2)
                }
                TzCard(onClick = {}) { Text("أمثال الفلوس", style = TzType.body, color = Tz.text) }
                TzRow(
                    { TzIcon(TzIcons.transport, 24.dp, null, tint = Tz.text) }, "المواصلات", "فاضل من 900",
                    { Text("650", style = TzType.rowNumber, color = Tz.text) },
                )
                TzRow(
                    { TzIcon(TzIcons.food, 24.dp, null, tint = Tz.text) }, "الخروجات", "فاضل من 2,400",
                    { Text("2,400", style = TzType.rowNumber, color = Tz.text) }, onClick = {},
                )
            }
        }
        section("toggle", "Toggle") {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                TzToggle(toggle, { toggle = it }, "Toggle on")
                TzToggle(false, {}, "Toggle off")
            }
        }
        section("progress", "Progress bar") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TzProgressBar(progressSets[progressStep % progressSets.size])
                SecondaryButton("Next", { confirm { progressStep++ } }, Modifier.fillMaxWidth())
            }
        }
        section("odometer", "Odometer") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Odometer(odometer, TzType.hero)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SecondaryButton("-35", { confirm { odometer = (odometer - 3_500).coerceAtLeast(0) } }, Modifier.weight(1f))
                    SecondaryButton("+72", { confirm { odometer += 7_200 } }, Modifier.weight(1f))
                    SecondaryButton("+1000", { confirm { odometer += 100_000 } }, Modifier.weight(1f))
                }
            }
        }
        section("tabbar", "Tab bar") {
            TzTabBar(tab, { tab = it }, { sheet = true })
        }
        section("ticket", "Ticket") {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                SampleTicket(TICKET_ZERO, 0, left = 24_000)
                SampleTicket(TICKET_SUB, 3)
                SampleTicket(TICKET_SUB, 11)
                SampleTicket(TICKET_OVER, 5, left = -3_500)
                SampleTicket(TICKET_SUB, punchHoles)
                SecondaryButton("Punch", { confirm { punchHoles++ } }, Modifier.fillMaxWidth())
            }
        }
        section("stub", "Stub") {
            SampleStub()
        }
        section("envelope", "Envelope") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                EnvelopeCard("الخروجات", Tz.carnelian, 14_000, 0.84f, "فاضل من 900", sealed = false, status = "هيخلص يوم 16 بالمعدل ده")
                EnvelopeCard("الإيجار", Tz.sky, 250_000, 1f, "2,500 · يوم 8", sealed = true, status = "الظرف مقفول لحد الشهر الجاي")
            }
        }
        section("stamp", "Stamp") {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Stamp("أول تذكرة", Tz.yellow, subLabel = "27 سبتمبر")
                Stamp("أسبوع كامل", Tz.turquoise, subLabel = "مرتين")
                Stamp("إيد ماسكة", null, subLabel = "يومين من غير صرف")
            }
        }
        section("proverb", "Proverb card") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ProverbCard(
                    "أمثال الفلوس", 17, 60, "على قد لحافك مد رجليك",
                    "اصرف على قد اللي معاك، مش على قد اللي نفسك فيه.", Tz.yellow,
                )
                ProverbCard(
                    "أمثال الفلوس", 18, 60, "القرش الأبيض ينفع في اليوم الأسود",
                    "اللي تحوّشه وانت مرتاح هو اللي يسندك وقت الزنقة.", Tz.turquoise,
                )
            }
        }
        section("qersh", "Qersh") {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    QershMood.entries.forEach { Qersh(it, 64.dp) }
                }
                Qersh(QershMood.Waving, 150.dp)
                Box(Modifier.clip(RoundedCornerShape(24.dp)).background(Tz.yellow).padding(16.dp)) {
                    Qersh(QershMood.Happy, 64.dp, onYellow = true)
                }
            }
        }
        section("bubble", "QershBubble") {
            QershBubble("والنهارده حوّشت 72")
        }
    }

    if (sheet) TzSheet(onDismiss = { sheet = false }) {
        Text(SAMPLE, style = TzType.body, color = Tz.text, modifier = Modifier.padding(24.dp))
    }
}
