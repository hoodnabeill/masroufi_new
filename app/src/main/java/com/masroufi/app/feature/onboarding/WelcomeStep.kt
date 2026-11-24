package com.masroufi.app.feature.onboarding

import androidx.compose.foundation.layout.heightIn
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.core.os.LocaleListCompat
import com.masroufi.app.R
import com.masroufi.app.ui.haptics.HapticLevel
import com.masroufi.app.ui.haptics.LocalHaptics
import com.masroufi.app.ui.icons.TzIcon
import com.masroufi.app.ui.icons.TzIcons
import com.masroufi.app.ui.motion.LocalReduceMotion
import com.masroufi.app.ui.motion.PressScale
import com.masroufi.app.ui.motion.pressScale
import com.masroufi.app.ui.paper.Qersh
import com.masroufi.app.ui.paper.QershMood
import com.masroufi.app.ui.theme.Tz
import com.masroufi.app.ui.theme.TzType

/** Onboarding.dc.html: yellow, Qersh waving once, one button, the language switch in the corner. */
@Composable
fun WelcomeStep(actions: OnboardingActions) {
    val haptics = LocalHaptics.current
    val reduce = LocalReduceMotion.current
    val arabic = LocalConfiguration.current.locales[0].language == "ar"
    // His arm turns 20 degrees and back, once, on arrival (05-MOTION).
    val arm = remember { Animatable(0f) }
    LaunchedEffect(reduce) {
        if (reduce) return@LaunchedEffect
        arm.animateTo(20f, tween(300))
        arm.animateTo(0f, tween(300))
    }
    Box(Modifier.fillMaxSize().background(Tz.yellow)) {
        Canvas(Modifier.fillMaxSize()) {
            // The mock's 520 dp guilloche, centred 70 dp from the left and 110 dp from the top.
            for (r in 80..230 step 30) drawCircle(Tz.yellowRule2, r.dp.toPx(), Offset(70.dp.toPx(), 110.dp.toPx()), style = Stroke(1.5.dp.toPx()))
        }
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row {
                val pill = RoundedCornerShape(22.dp)
                Box(
                    Modifier.heightIn(min = 48.dp).clip(pill).border(BorderStroke(2.dp, Tz.night), pill)
                        .clickable(role = Role.Button) {
                            // The activity recreates in the other language; the step is persisted, so it stays.
                            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(if (arabic) "en" else "ar"))
                        }
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(stringResource(R.string.ob_language_other), style = TzType.label.copy(fontWeight = FontWeight.Bold, color = Tz.night))
                }
            }
            Spacer(Modifier.height(12.dp))
            Qersh(
                QershMood.Waving, 180.dp, Modifier.align(Alignment.CenterHorizontally), onYellow = true,
                contentDescription = stringResource(R.string.ob_welcome_cd), armAngle = { arm.value },
            )
            Column(Modifier.align(Alignment.CenterHorizontally), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    stringResource(R.string.ob_welcome_title),
                    style = TzType.hero.copy(fontSize = 48.sp, lineHeight = 1.15.em, color = Tz.night, textAlign = TextAlign.Center),
                )
                Text(
                    stringResource(R.string.ob_welcome_body),
                    Modifier.widthIn(max = 300.dp),
                    style = TzType.body.copy(fontSize = 16.sp, lineHeight = 1.8.em, fontWeight = FontWeight.Medium, color = Tz.yellowInk, textAlign = TextAlign.Center),
                )
            }
            Spacer(Modifier.weight(1f))
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(R.string.ob_welcome_hint),
                    Modifier.fillMaxWidth(),
                    style = TzType.label.copy(fontWeight = FontWeight.SemiBold, color = Tz.yellowInk, textAlign = TextAlign.Center),
                )
                val source = remember { MutableInteractionSource() }
                Box(
                    Modifier.fillMaxWidth().pressScale(source, PressScale.Primary).heightIn(min = 60.dp).clip(RoundedCornerShape(30.dp)).background(Tz.night)
                        .clickable(interactionSource = source, indication = null, role = Role.Button) {
                            haptics.perform(HapticLevel.Tap)
                            actions.next()
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(stringResource(R.string.ob_welcome_go), style = TzType.button.copy(fontSize = 24.sp, color = Tz.yellow))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
                    TzIcon(TzIcons.lock, 14.dp, null, tint = Tz.yellowInk)
                    Text(stringResource(R.string.ob_welcome_privacy), style = TzType.caption.copy(fontWeight = FontWeight.Medium, color = Tz.yellowInk))
                }
            }
        }
    }
}
