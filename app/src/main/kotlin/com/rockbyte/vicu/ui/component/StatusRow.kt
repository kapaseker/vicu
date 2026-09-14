package com.rockbyte.vicu.ui.component

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.style.MutableStyleState
import androidx.compose.foundation.style.Style
import androidx.compose.foundation.style.then
import androidx.compose.foundation.style.styleable
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import com.rockbyte.vicu.ui.theme.VicuTheme

/**
 * 状态行：小圆点 + 文案（DESIGN.md Visual Indicators），
 * pulsing 时圆点做呼吸动画（用于进行中状态）。
 */
@Composable
fun StatusRow(
    dotColor: Color,
    text: String,
    pulsing: Boolean = false,
    textColor: Color = VicuTheme.colors.onBackground,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(VicuTheme.dimensions.spacingUnit),
    ) {
        val dotAlpha = if (pulsing) {
            val transition = rememberInfiniteTransition(label = "statusPulse")
            transition.animateFloat(
                initialValue = VicuTheme.alpha.statusPulseMinimum,
                targetValue = VicuTheme.alpha.full,
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        durationMillis = VicuTheme.motion.statusPulseDurationMillis,
                    ),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "dotAlpha",
            ).value
        } else {
            VicuTheme.alpha.full
        }
        val dotState = remember { MutableStyleState(null) }
        Box(
            Modifier
                .alpha(dotAlpha)
                .styleable(dotState, VicuTheme.styles.statusDot then Style { background(dotColor) })
        )
        BasicText(text, style = VicuTheme.typography.bodySm.copy(color = textColor))
    }
}
