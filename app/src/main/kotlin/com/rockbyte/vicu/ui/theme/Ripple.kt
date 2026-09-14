package com.rockbyte.vicu.ui.theme

import androidx.compose.foundation.Indication
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.material.ripple.RippleAlpha
import androidx.compose.material.ripple.createRippleModifierNode
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorProducer
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.unit.Dp

/**
 * 按压反馈：DESIGN.md 未定义 ripple，按 M3 默认规格落地——
 * bounded ripple + 内容色低透明度叠层，保持 Auralis 单色、低干扰基调。
 * material-ripple 1.12 官方路径：自定义设计系统用 createRippleModifierNode 构建自有 Indication。
 * [VicuTheme] 将其 provide 为全局 LocalIndication 默认值；深色底需显式传 onPrimary 覆盖。
 */
private val VicuRippleAlpha = RippleAlpha(
    pressedAlpha = 0.12f,
    focusedAlpha = 0.12f,
    hoveredAlpha = 0.08f,
    draggedAlpha = 0.12f,
)

private class VicuRipple(private val color: Color) : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode =
        createRippleModifierNode(
            interactionSource = interactionSource,
            bounded = true,
            radius = Dp.Unspecified,
            color = ColorProducer { color },
            rippleAlpha = { VicuRippleAlpha },
        )

    override fun equals(other: Any?): Boolean = other is VicuRipple && other.color == color
    override fun hashCode(): Int = color.hashCode()
}

/** 深色底（如黑色主按钮）传 onPrimary，浅底传 onSurface。 */
@Composable
fun vicuRipple(color: Color = VicuTheme.colors.onSurface): Indication =
    remember(color) { VicuRipple(color) }
