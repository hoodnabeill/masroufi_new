package com.masroufi.app.ui.paper

import androidx.compose.ui.graphics.Color
import com.masroufi.app.domain.ProverbSet
import com.masroufi.app.ui.theme.Tz

/** Card colour per set (ruling: the Proverb mock uses turquoise for saving and patience). */
fun ProverbSet.color(): Color = when (this) {
    ProverbSet.SAVING, ProverbSet.PATIENCE -> Tz.turquoise
    ProverbSet.SPENDING -> Tz.yellow
    ProverbSet.DEBT -> Tz.carnelian
    ProverbSet.WORK -> Tz.sky
    ProverbSet.PEOPLE -> Tz.lilac
}
