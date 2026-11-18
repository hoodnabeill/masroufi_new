package com.masroufi.app.feature.settings

import androidx.annotation.RawRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.masroufi.app.R
import com.masroufi.app.feature.pockets.PushedHeader
import com.masroufi.app.ui.components.Segmented
import com.masroufi.app.ui.theme.Tz
import com.masroufi.app.ui.theme.TzType

/** الخصوصية والشروط: the policy and the terms on one screen, a switch between them (decision 11). */
@Composable
fun LegalScreen(onBack: () -> Unit, supportEmail: String = SUPPORT_EMAIL) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val items = stringArrayResource(if (tab == 0) R.array.legal_privacy else R.array.legal_terms)
    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        PushedHeader(stringResource(R.string.legal_title), onBack)
        Segmented(listOf(stringResource(R.string.legal_tab_privacy), stringResource(R.string.legal_tab_terms)), tab, { tab = it })
        Text(stringResource(R.string.legal_updated), style = TzType.caption, color = Tz.text2)
        items.toList().chunked(2).forEach { (heading, body) -> Section(heading, body) }
        Section(
            stringResource(R.string.legal_contact_title),
            if (supportEmail.isBlank()) stringResource(R.string.legal_contact_none) else stringResource(R.string.legal_contact_email, supportEmail),
        )
    }
}

@Composable
private fun Section(heading: String, body: String) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(heading, style = TzType.cardHeading, color = Tz.text)
        Text(body, style = TzType.body, color = Tz.text2)
    }
}

/** تراخيص الخطوط: the OFL text of each bundled font, as shipped in the repository's licenses folder. */
@Composable
fun LicencesScreen(onBack: () -> Unit) {
    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        PushedHeader(stringResource(R.string.licences_title), onBack)
        Text(stringResource(R.string.licences_intro), style = TzType.body, color = Tz.text2)
        Licence(stringResource(R.string.licences_alexandria), R.raw.ofl_alexandria)
        Licence(stringResource(R.string.licences_lalezar), R.raw.ofl_lalezar)
    }
}

@Composable
private fun Licence(name: String, @RawRes raw: Int) {
    val context = LocalContext.current
    val text = remember(raw) { context.resources.openRawResource(raw).bufferedReader().use { it.readText() } }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(name, style = TzType.cardHeading, color = Tz.text)
        Text(text, style = TzType.caption, color = Tz.text2)
    }
}
