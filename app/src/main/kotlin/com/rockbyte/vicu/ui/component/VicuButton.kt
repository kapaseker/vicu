package com.rockbyte.vicu.ui.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.style.Style
import androidx.compose.foundation.style.rememberUpdatedStyleState
import androidx.compose.foundation.style.styleable
import androidx.compose.foundation.style.then
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import com.rockbyte.vicu.R
import com.rockbyte.vicu.ui.theme.VicuTheme
import com.rockbyte.vicu.ui.theme.vicuRipple

/**
 * 设计系统按钮容器（Styles API）。默认为基础主按钮样式（黑 pill）；
 * 传入 [VicuTheme.styles.secondaryButton] 等样式时按 then 语义整体覆盖（后者优先）。
 * 修饰符顺序为 clip → clickable → styleable：clickable 覆盖含 contentPadding 的整个按钮，
 * ripple 由 indication 节点叠画在 styleable 背景之上并裁进 pill 形状
 * （styleable 若在 clickable 之前，contentPadding 会收缩 clickable 边界导致 ripple 铺不满）。
 * [rippleColor] 默认深色主按钮取 onPrimary；浅底样式（outlineButton）取 onSurface。
 * 文字色/字重由样式的 contentColor/textStyle 传播（isInheritedTextStyleEnabled），无需显式指定。
 */
@Composable
fun VicuButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: Style = Style,
    enabled: Boolean = true,
    rippleColor: Color = VicuTheme.colors.onPrimary,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable () -> Unit,
) {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val styleState = rememberUpdatedStyleState(source) {
        it.isEnabled = enabled
    }
    Box(
        modifier = modifier
            .clip(VicuTheme.shapes.full)
            .clickable(
                onClick = onClick,
                enabled = enabled,
                interactionSource = source,
                indication = vicuRipple(rippleColor),
            )
            .styleable(styleState, VicuTheme.styles.primaryButton then style),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

/** 主按钮预设：黑底白字（primaryButton），纯文字。 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    VicuButton(onClick = onClick, modifier = modifier, enabled = enabled) {
        BasicText(text)
    }
}

/** 描边按钮预设：白底黑边黑字（outlineButton），用于完成态的次级动作。 */
@Composable
fun OutlineButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    VicuButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        style = VicuTheme.styles.outlineButton,
        rippleColor = VicuTheme.colors.onSurface,
    ) {
        BasicText(text)
    }
}

/** 主按钮 + 图标预设：黑底白字，icon 居文字左侧并 tint 成 onPrimary。 */
@Composable
fun PrimaryIconButton(
    icon: Int,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    VicuButton(onClick = onClick, modifier = modifier, enabled = enabled) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(VicuTheme.dimensions.spacingUnit),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(icon),
                contentDescription = null,
                modifier = Modifier.size(VicuTheme.dimensions.iconMedium),
                colorFilter = ColorFilter.tint(VicuTheme.colors.onPrimary),
            )
            BasicText(text)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PrimaryButtonPreview() {
    VicuTheme {
        PrimaryButton(text = "Primary Button", onClick = {})
    }
}

@Preview(showBackground = true)
@Composable
private fun PrimaryButtonDisabledPreview() {
    VicuTheme {
        PrimaryButton(text = "Primary Button", onClick = {}, enabled = false)
    }
}

@Preview(showBackground = true)
@Composable
private fun OutlineButtonPreview() {
    VicuTheme {
        OutlineButton(text = "Outline Button", onClick = {})
    }
}

@Preview(showBackground = true)
@Composable
private fun OutlineButtonDisabledPreview() {
    VicuTheme {
        OutlineButton(text = "Outline Button", onClick = {}, enabled = false)
    }
}

@Preview(showBackground = true)
@Composable
private fun PrimaryIconButtonPreview() {
    VicuTheme {
        PrimaryIconButton(
            icon = R.drawable.ic_export,
            text = "Export Audio",
            onClick = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun PrimaryIconButtonDisabledPreview() {
    VicuTheme {
        PrimaryIconButton(
            icon = R.drawable.ic_export,
            text = "Export Audio",
            onClick = {},
            enabled = false,
        )
    }
}
