package com.rockbyte.vicu.ui.theme

import androidx.compose.foundation.style.Style
import androidx.compose.foundation.style.border
import androidx.compose.foundation.style.contentPadding
import androidx.compose.foundation.style.contentPaddingHorizontal
import androidx.compose.foundation.style.disabled
import androidx.compose.foundation.style.size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.unit.dp

/**
 * DESIGN.md Components/Elevation 规格的组件样式。
 * 正文中的 panel/border hex 映射到最近 YAML token（用户决策：YAML 为唯一色值来源）：
 * panel → secondaryContainer，border → outlineVariant，base → background。
 */
object VicuStyles {

    /** 主按钮：全黑 pill、白字（DESIGN.md Components）。 */
    val primaryButton: Style = Style {
        background(colors.primary)
        contentColor(colors.onPrimary)
        shape(shapes.full)
        textStyle(typography.button)
        contentPaddingHorizontal(24.dp)
        minHeight(52.dp) // DESIGN.md 未规定按钮高度，取 pill 通用高度
        disabled {
            background(colors.outlineVariant)
        }
    }

    /** 次按钮：panel 色填充 + panel 文本色（YAML：secondaryContainer/onSecondaryContainer）。 */
    val secondaryButton: Style = Style {
        background(colors.secondaryContainer)
        contentColor(colors.onSecondaryContainer)
        shape(shapes.full)
        textStyle(typography.button)
        contentPaddingHorizontal(24.dp)
        minHeight(52.dp)
        disabled {
            background(colors.surfaceContainerHighest)
        }
    }

    /** Level-2 浮起卡片：24dp 圆角、1px 边框、min 32dp 内边距、2% 黑环境光晕。 */
    val card: Style = Style {
        shape(shapes.xl)
        background(colors.surfaceContainerLowest)
        border(1.dp, colors.outlineVariant)
        contentPadding(VicuSpacing.cardPadding)
        dropShadow(
            Shadow(
                radius = 40.dp,
                color = Color.Black,
                alpha = 0.02f,
            )
        )
    }

    /** Level 0 底色。 */
    val screen: Style = Style {
        background(colors.background)
    }

    /** 72dp 固定顶栏；玻璃效果在 minSdk 29 下降级为实色 surface（Modifier.blur 需 API 31+）。 */
    val header: Style = Style {
        background(colors.surface)
        height(VicuSpacing.headerHeight)
        contentPaddingHorizontal(VicuSpacing.marginX)
        contentColor(colors.onSurface)
    }

    /** 状态指示：小圆点 + 文案，而非大面积横幅（DESIGN.md Visual Indicators）。 */
    val statusDot: Style = Style {
        size(8.dp)
        shape(shapes.full)
        background(colors.secondary)
    }
}
