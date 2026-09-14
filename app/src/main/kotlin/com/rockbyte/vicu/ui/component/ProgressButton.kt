package com.rockbyte.vicu.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.clipRect
import com.rockbyte.vicu.ui.theme.VicuTheme

/**
 * 进行中的进度按钮：outlineButton 视觉（白底 + 黑边 pill）+ 黑色进度填充；
 * 进度文字双层绘制——底层黑字完整显示，上层白字裁剪到进度边缘，形成「未覆盖黑、已覆盖白」。
 * progress 为 null（源时长未知）时退化为纯文字无填充，文案由调用方给定。
 */
@Composable
fun ProgressButton(
    progress: Float?,
    label: String,
    modifier: Modifier = Modifier,
) {
    val fraction = (progress ?: 0f).coerceIn(0f, 1f)
    val fillColor = VicuTheme.colors.primary
    Box(
        modifier = modifier
            .heightIn(min = VicuTheme.dimensions.buttonMinHeight)
            .clip(VicuTheme.shapes.full)
            .background(VicuTheme.colors.surfaceContainerLowest)
            .drawWithContent {
                if (fraction > 0f) {
                    drawRect(
                        color = fillColor,
                        size = Size(size.width * fraction, size.height),
                    )
                }
                drawContent()
            }
            .border(VicuTheme.dimensions.cardBorderWidth, VicuTheme.colors.primary, VicuTheme.shapes.full),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(label, style = VicuTheme.typography.button.copy(color = VicuTheme.colors.onSurface))
        if (progress != null) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .drawWithContent {
                        clipRect(right = size.width * fraction) { this@drawWithContent.drawContent() }
                    },
            ) {
                BasicText(
                    label,
                    style = VicuTheme.typography.button.copy(color = VicuTheme.colors.onPrimary),
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }
    }
}
