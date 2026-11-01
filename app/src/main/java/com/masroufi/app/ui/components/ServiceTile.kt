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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * A service as a liquid-glass app icon: the brand colour poured as a glossy gradient, a curved specular sheen across
 * the top, a lit rim, and the white mark floating on it. Monogram services use a palette colour and their initials.
 */
@Composable
fun ServiceTile(service: Service, modifier: Modifier = Modifier, size: Dp = 48.dp) {
    val base = service.color ?: categoryColor(service.key.hashCode())
    val shape = RoundedCornerShape(percent = 28)
    val top = Color.White.copy(alpha = 0.22f).compositeOver(base)
    val bottom = Color.Black.copy(alpha = 0.28f).compositeOver(base)
    Box(
        modifier
            .size(size)
            .shadow(size * 0.18f, shape, ambientColor = base, spotColor = base)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(top, base, bottom)))
            .drawWithContent {
                drawContent()
                // Specular sheen: a soft elliptical highlight across the upper half, like light on a glass bead.
                drawOval(
                    Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.42f), Color.White.copy(alpha = 0f)), startY = 0f, endY = this.size.width * 0.55f),
                    topLeft = Offset(-this.size.width * 0.15f, -this.size.width * 0.42f),
                    size = androidx.compose.ui.geometry.Size(this.size.width * 1.3f, this.size.width * 0.95f),
                )
            }
            .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.6f), Color.White.copy(alpha = 0.06f))), shape)
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        val logo = service.logo
        if (logo != null) {
            Icon(logo, contentDescription = null, tint = Color.White, modifier = Modifier.size(size * 0.5f))
        } else {
            Text(
                service.monogram.orEmpty(),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = (size.value * if ((service.monogram?.length ?: 0) > 2) 0.24f else 0.34f).sp,
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
