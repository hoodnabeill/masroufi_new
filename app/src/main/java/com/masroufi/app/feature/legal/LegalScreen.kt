package com.masroufi.app.feature.legal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.masroufi.app.R
import com.masroufi.app.ui.components.MasroufiTopBar
import com.masroufi.app.ui.components.edgeFade
import com.masroufi.app.ui.theme.LocalGlass

/**
 * The privacy policy or terms, read from the same text files that are published for Google Play (docs/legal), in
 * the app's language. "# " lines are headings and "- " lines bullets; everything else is a paragraph.
 */
@Composable
fun LegalScreen(doc: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val arabic = LocalConfiguration.current.locales[0].language == "ar"
    val res = when (doc) {
        "terms" -> if (arabic) R.raw.terms_ar else R.raw.terms_en
        else -> if (arabic) R.raw.privacy_ar else R.raw.privacy_en
    }
    val lines = remember(res) { context.resources.openRawResource(res).bufferedReader(Charsets.UTF_8).use { it.readLines() } }
    val glass = LocalGlass.current
    Scaffold(topBar = { MasroufiTopBar(stringResource(if (doc == "terms") R.string.legal_terms else R.string.legal_privacy), onBack = onBack) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).edgeFade().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // The first heading is the document title, already in the top bar.
            lines.drop(1).forEach { line ->
                when {
                    line.isBlank() -> Unit
                    line.startsWith("# ") -> Text(
                        line.removePrefix("# "),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 14.dp).semantics { heading() },
                    )
                    line.startsWith("- ") -> Row {
                        Text("•", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                        androidx.compose.foundation.layout.Spacer(Modifier.width(8.dp))
                        Text(line.removePrefix("- "), style = MaterialTheme.typography.bodyMedium, color = glass.textSecondary)
                    }
                    else -> Text(line, style = MaterialTheme.typography.bodyMedium, color = glass.textSecondary)
                }
            }
        }
    }
}
