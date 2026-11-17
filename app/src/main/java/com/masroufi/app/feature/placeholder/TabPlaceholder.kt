package com.masroufi.app.feature.placeholder

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.masroufi.app.BuildConfig
import com.masroufi.app.ui.components.TextLink
import com.masroufi.app.ui.paper.Qersh
import com.masroufi.app.ui.paper.QershMood
import com.masroufi.app.ui.theme.TzType

/** Title plus a sleepy Qersh until the tab's real screen exists. [onGallery] shows only in debug builds. */
@Composable
fun TabPlaceholder(@StringRes title: Int, modifier: Modifier = Modifier, onGallery: (() -> Unit)? = null) {
    Box(modifier.fillMaxSize()) {
        Text(
            stringResource(title),
            style = TzType.titleTab,
            modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(horizontal = 16.dp),
        )
        Qersh(QershMood.Sleepy, 150.dp, Modifier.align(Alignment.Center))
        if (BuildConfig.DEBUG && onGallery != null) {
            TextLink("Gallery", onGallery, Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp))
        }
    }
}
