package com.masroufi.app.feature.plan

import com.masroufi.app.feature.pockets.LoadingRows
import com.masroufi.app.ui.motion.pressClickable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.masroufi.app.R
import com.masroufi.app.data.repo.PlusFeature
import com.masroufi.app.ui.components.poundsText
import com.masroufi.app.feature.add.AmountInput
import com.masroufi.app.feature.pockets.CircleButton
import com.masroufi.app.feature.pockets.PushedHeader
import com.masroufi.app.ui.components.PlusLocked
import com.masroufi.app.ui.components.PrimaryButton
import com.masroufi.app.ui.components.TzCard
import com.masroufi.app.ui.haptics.HapticLevel
import com.masroufi.app.ui.haptics.LocalHaptics
import com.masroufi.app.ui.icons.TzIcon
import com.masroufi.app.ui.icons.TzIcons
import com.masroufi.app.ui.theme.Tz
import com.masroufi.app.ui.theme.TzType

/** حاسبة الزكاة (Plus): zakat on money against a nisab the user confirms, and zakat al-fitr from people x amount. */
@Composable
fun ZakatScreen(viewModel: ZakatViewModel, onBack: () -> Unit) {
    val ui by viewModel.state.collectAsStateWithLifecycle()
    ZakatContent(
        ui, onBack,
        ZakatActions(viewModel::open, viewModel::dismiss, viewModel::setZakatable, viewModel::setNisab, viewModel::setPerPerson, viewModel::stepPeople),
    )
}

/** What the zakat page can do; the view model's functions in the app, no-ops in the audit. */
internal class ZakatActions(
    val open: (ZakatSheet) -> Unit,
    val dismiss: () -> Unit,
    val setZakatable: (Long) -> Unit,
    val setNisab: (Long) -> Unit,
    val setPerPerson: (Long) -> Unit,
    val stepPeople: (Int) -> Unit,
) {
    companion object { val None = ZakatActions({}, {}, {}, {}, {}, {}) }
}

@Composable
internal fun ZakatContent(ui: ZakatUi, onBack: () -> Unit, vm: ZakatActions) {
    val body: @Composable () -> Unit = { ZakatBody(ui, vm) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        PushedHeader(stringResource(R.string.zakat_title), onBack)
        if (ui.loading) LoadingRows()
        else if (ui.isPlus) body() else PlusLocked(R.string.zakat_lock, PlusFeature.ZAKAT, body)
    }
    when (ui.sheet) {
        ZakatSheet.Money -> ValueSheet(R.string.zakat_held, ui.zakatable, vm.dismiss, vm.setZakatable)
        ZakatSheet.Nisab -> ValueSheet(R.string.zakat_nisab, ui.nisab ?: 0, vm.dismiss, vm.setNisab)
        ZakatSheet.PerPerson -> ValueSheet(R.string.zakat_per_person, ui.perPerson, vm.dismiss, vm.setPerPerson)
        null -> Unit
    }
}

@Composable
private fun ZakatBody(ui: ZakatUi, vm: ZakatActions) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        TzCard {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                ValueRow(stringResource(R.string.zakat_held), poundsText(ui.zakatable)) { vm.open(ZakatSheet.Money) }
                ValueRow(stringResource(R.string.zakat_nisab), ui.nisab?.let { poundsText(it) } ?: stringResource(R.string.zakat_nisab_prompt)) { vm.open(ZakatSheet.Nisab) }
                Text(stringResource(R.string.zakat_nisab_note), style = TzType.caption, color = Tz.text2)
            }
        }
        ui.zakat?.let { zakat ->
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Tz.yellow).padding(20.dp)) {
                Text(
                    if (zakat > 0) stringResource(R.string.zakat_result, poundsText(zakat)) else stringResource(R.string.zakat_none),
                    style = TzType.titleTab, color = Tz.night,
                )
            }
        }
        TzCard {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.zakat_fitr_title), style = TzType.cardHeading, color = Tz.text)
                Row(Modifier.fillMaxWidth().defaultMinSize(minHeight = 56.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.zakat_people), style = TzType.body.copy(fontSize = 15.sp), color = Tz.text, modifier = Modifier.weight(1f))
                    CircleButton(TzIcons.minus, stringResource(R.string.zakat_people_less)) { vm.stepPeople(-1) }
                    Text(ui.people.toString(), style = TzType.rowNumber, color = Tz.text, modifier = Modifier.defaultMinSize(minWidth = 28.dp))
                    CircleButton(TzIcons.add, stringResource(R.string.zakat_people_more)) { vm.stepPeople(1) }
                }
                ValueRow(stringResource(R.string.zakat_per_person), poundsText(ui.perPerson)) { vm.open(ZakatSheet.PerPerson) }
                Text(stringResource(R.string.zakat_fitr_result, poundsText(ui.fitr)), style = TzType.cardHeading, color = Tz.yellow, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
}

@Composable
private fun ValueRow(label: String, value: String, onClick: () -> Unit) {
    val haptics = LocalHaptics.current
    Row(
        Modifier.fillMaxWidth().pressClickable { haptics.perform(HapticLevel.Tap); onClick() }.defaultMinSize(minHeight = 56.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = TzType.body.copy(fontSize = 15.sp), color = Tz.text, modifier = Modifier.weight(1f))
        Text(value, style = TzType.rowNumber, color = Tz.text)
        TzIcon(TzIcons.chevron, 18.dp, null)
    }
}

/** The keypad sheet for one typed amount; [onSave] gets piastres. */
@Composable
private fun ValueSheet(title: Int, initial: Long, onDismiss: () -> Unit, onSave: (Long) -> Unit) {
    val haptics = LocalHaptics.current
    AmountSheet(stringResource(title), onDismiss, AmountInput.of(initial)) { amount ->
        PrimaryButton(stringResource(R.string.jar_save), { haptics.perform(HapticLevel.Confirm); onSave(amount) }, Modifier.fillMaxWidth(), height = 52.dp)
    }
}
