package com.rockbyte.vicu.ui.theme

import androidx.compose.foundation.style.Style
import androidx.compose.foundation.style.border
import androidx.compose.foundation.style.contentPadding
import androidx.compose.foundation.style.contentPaddingHorizontal
import androidx.compose.foundation.style.disabled
import androidx.compose.foundation.style.size
import androidx.compose.foundation.style.then
import androidx.compose.ui.graphics.shadow.Shadow

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
        contentPaddingHorizontal(dimensions.buttonHorizontalPadding)
        minHeight(dimensions.buttonMinHeight) // DESIGN.md 未规定按钮高度，取 pill 通用高度
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
        contentPaddingHorizontal(dimensions.buttonHorizontalPadding)
        minHeight(dimensions.buttonMinHeight)
        disabled {
            background(colors.surfaceContainerHighest)
        }
    }

    /**
     * 描边按钮：黑边框 + 白底 + 黑字（用户决策：DESIGN.md 无此规格，色值取 YAML token）。
     * 视觉层级介于主按钮与次按钮之间，用于完成态的次级动作（如"回到首页"）。
     */
    val outlineButton: Style = Style {
        background(colors.surfaceContainerLowest)
        contentColor(colors.onSurface)
        border(dimensions.cardBorderWidth, colors.primary)
        shape(shapes.full)
        textStyle(typography.button)
        contentPaddingHorizontal(dimensions.buttonHorizontalPadding)
        minHeight(dimensions.buttonMinHeight)
        disabled {
            background(colors.surfaceContainerHighest)
        }
    }

    /** Level-2 浮起卡片：24dp 圆角、1px 边框、min 32dp 内边距、2% 黑环境光晕。 */
    val card: Style = Style {
        shape(shapes.xl)
        background(colors.surfaceContainerLowest)
        border(dimensions.cardBorderWidth, colors.outlineVariant)
        contentPadding(dimensions.cardPadding)
        dropShadow(
            Shadow(
                radius = dimensions.cardShadowRadius,
                color = colors.primary,
                alpha = alpha.ambientShadow,
            )
        )
    }

    /** Level 0 底色。 */
    val screen: Style = Style {
        background(colors.background)
    }

    /**
     * 功能按钮 tile：卡片视觉（card then 覆盖内边距）。
     * DESIGN.md 卡片 32dp 内边距下限针对整幅卡片；grid 内约 100dp 的 tile 装不下，
     * 降到 16dp。后续功能变多或 tile 尺寸放大时可回到 card 默认。
     */
    val functionTile: Style = card then Style {
        contentPadding(dimensions.functionTilePadding)
    }

    /**
     * 64dp 标准顶栏（M3 TopAppBar 规格，无 Material3 依赖的手工对齐）：
     * 4dp 行内边距 + 48dp 触控区（24dp 图标居中）= 图标距屏幕 16dp；标题起点 56dp 由 VicuTopAppBar 布局。
     */
    val header: Style = Style {
        background(colors.surface)
        height(dimensions.topAppBarHeight)
        contentPaddingHorizontal(dimensions.topAppBarHorizontalPadding)
        contentColor(colors.onSurface)
    }

    /** 状态指示：小圆点 + 文案，而非大面积横幅（DESIGN.md Visual Indicators）。 */
    val statusDot: Style = Style {
        size(dimensions.statusDotSize)
        shape(shapes.full)
        background(colors.secondary)
    }

    /** 媒体 tile：Level-1 panel 底 + 24dp 圆角（缩略图加载态与音频占位共用底色）。 */
    val mediaTile: Style = Style {
        shape(shapes.xl)
        background(colors.surfaceContainerLow)
    }

    /** 媒体类型角标：24dp 白底 base 圆角块 + 1px 边框，承载 16dp tint 图标。 */
    val typeBadge: Style = Style {
        size(dimensions.typeBadgeSize)
        shape(shapes.base)
        background(colors.surfaceContainerLowest)
        border(dimensions.cardBorderWidth, colors.outlineVariant)
    }
}
