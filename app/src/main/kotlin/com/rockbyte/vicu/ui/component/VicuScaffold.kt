package com.rockbyte.vicu.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.style.MutableStyleState
import androidx.compose.foundation.style.styleable
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.rockbyte.vicu.ui.theme.VicuTheme

/**
 * 通用页面骨架：Level-0 底色（styles.screen）+ 72dp 标题栏（styles.header）+ 内容区。
 * 内容区边距与装饰（渐变 orb 等）由调用方决定。
 */
@Composable
fun VicuScaffold(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val screenState = remember { MutableStyleState(null) }
    Box(
        modifier
            .fillMaxSize()
            .styleable(screenState, VicuTheme.styles.screen)
    ) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            val headerState = remember { MutableStyleState(null) }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .styleable(headerState, VicuTheme.styles.header),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicText(title, style = VicuTheme.typography.h3)
            }
            content()
        }
    }
}
