package com.rockbyte.vicu.ui.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.rockbyte.vicu.R
import com.rockbyte.vicu.ui.theme.VicuTheme

/**
 * 48dp 触控区 + 24dp 图标的返回按钮（M3 TopAppBar 规格触控区）。
 * VicuScaffold 标题栏与全屏播放页悬浮控制层共用；浅色底用默认 tint，深色底显式传色。
 */
@Composable
fun BackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = VicuTheme.colors.onSurface,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .size(VicuTheme.dimensions.navigationTouchSize)
            .clip(CircleShape)
            .clickable(
                interactionSource = interactionSource,
                enabled = enabled,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_left),
            contentDescription = stringResource(R.string.back),
            modifier = Modifier
                .size(VicuTheme.dimensions.iconMedium)
                .alpha(if (enabled) VicuTheme.alpha.full else VicuTheme.alpha.disabled),
            colorFilter = ColorFilter.tint(tint),
        )
    }
}
