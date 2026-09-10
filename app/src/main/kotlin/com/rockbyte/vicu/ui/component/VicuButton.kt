package com.rockbyte.vicu.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.style.Style
import androidx.compose.foundation.style.rememberUpdatedStyleState
import androidx.compose.foundation.style.styleable
import androidx.compose.foundation.style.then
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.rockbyte.vicu.ui.theme.VicuTheme

/**
 * 设计系统按钮（Styles API）。默认为基础主按钮样式（黑 pill）；
 * 传入 [VicuTheme.styles.secondaryButton] 等样式时按 then 语义整体覆盖（后者优先）。
 */
@Composable
fun VicuButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: Style = Style,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val styleState = rememberUpdatedStyleState(source) {
        it.isEnabled = enabled
    }
    Row(
        modifier = modifier
            .clickable(
                onClick = onClick,
                enabled = enabled,
                interactionSource = source,
                indication = null,
            )
            .styleable(styleState, VicuTheme.styles.primaryButton then style),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}
