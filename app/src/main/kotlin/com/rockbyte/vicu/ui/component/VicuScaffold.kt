package com.rockbyte.vicu.ui.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.style.MutableStyleState
import androidx.compose.foundation.style.styleable
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rockbyte.vicu.R
import com.rockbyte.vicu.ui.theme.VicuTheme
import com.rockbyte.vicu.ui.theme.vicuRipple

/**
 * 通用页面骨架：Level-0 底色（styles.screen）+ 64dp 标准顶栏（styles.header）+ 内容区。
 * 顶栏布局对齐 M3 TopAppBar 规格：4dp 行边距 + 48dp 返回触控区（24dp 图标），
 * 标题自身再留 4dp，标题起点 56dp；无返回按钮时标题起点 16dp。
 * 内容区边距与装饰（渐变 orb 等）由调用方决定。
 */
@Composable
fun VicuScaffold(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val screenState = remember { MutableStyleState(null) }
    Box(
        modifier
            .fillMaxSize()
            .styleable(screenState, VicuTheme.styles.screen)
    ) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            VicuTopAppBar(title = title, onBack = onBack)
            content()
        }
    }
}

/** 无 Material3 依赖、按 M3 small top app bar token 排布的项目标题栏。 */
@Composable
fun VicuTopAppBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
) {
    val headerState = remember { MutableStyleState(null) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .styleable(headerState, VicuTheme.styles.header),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) BackButton(onBack)
        BasicText(
            text = title,
            style = VicuTheme.typography.topAppBarTitle,
            modifier = Modifier.padding(
                start = if (onBack == null) 12.dp else 4.dp,
                end = 4.dp,
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun BackButton(onBack: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = vicuRipple(),
                onClick = onBack,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_left),
            contentDescription = stringResource(R.string.back),
            modifier = Modifier.size(24.dp),
            colorFilter = ColorFilter.tint(VicuTheme.colors.onSurface),
        )
    }
}
