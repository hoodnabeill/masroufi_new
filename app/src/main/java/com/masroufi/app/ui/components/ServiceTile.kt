package com.masroufi.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * A service or wallet as a liquid-glass app icon in the Midnight Ledger style: the brand colour as a calm, slightly
 * lit fill, a light-catching rim, and the mark in whichever ink reads best on it. Monogram services use their
 * initials in the display face.
 */
@Composable
fun ServiceTile(service: Service, modifier: Modifier = Modifier, size: Dp = 48.dp) {
    val base = service.color ?: categoryColor(service.key.hashCode())
    val shape = RoundedCornerShape(percent = 30)
    val ink = if (base.luminance() > 0.6f) Color(0xFF15131D) else Color.White
    val lit = Color.White.copy(alpha = 0.12f).compositeOver(base)
    Box(
        modifier
            .size(size)
            .clip(shape)
            .background(Brush.verticalGradient(0f to lit, 0.6f to base))
            .border(1.dp, Brush.verticalGradient(0f to Color.White.copy(alpha = 0.45f), 0.5f to Color.White.copy(alpha = 0.04f)), shape)
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        val logo = service.logo
        if (logo != null) {
            Icon(logo, contentDescription = null, tint = ink, modifier = Modifier.size(size * 0.52f))
        } else {
            val mono = service.monogram.orEmpty()
            Text(
                mono,
                color = ink,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = (size.value * if (mono.length > 2) 0.26f else 0.36f).sp,
                    letterSpacing = (-0.02f * size.value).sp,
                ),
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
    }
}

/** Top of the subscription editor: the service's tile, large, with its name, so the form reads as "this one". */
@Composable
fun ServiceHero(service: Service, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ServiceTile(service, size = 84.dp)
        Text(service.title, style = MaterialTheme.typography.headlineSmall)
    }
}
