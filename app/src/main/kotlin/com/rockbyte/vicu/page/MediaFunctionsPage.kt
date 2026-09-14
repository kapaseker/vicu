package com.rockbyte.vicu.page

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.tooling.preview.Preview
import com.rockbyte.vicu.R
import com.rockbyte.vicu.repo.MediaKind
import com.rockbyte.vicu.repo.SelectedMedia
import com.rockbyte.vicu.ui.component.VicuScaffold
import com.rockbyte.vicu.ui.component.iconRes
import com.rockbyte.vicu.ui.theme.VicuTheme

/**
 * 媒体功能列表页：所有多媒体点击后进入。
 * 标题与功能集随媒体类型变化；视频提供「导出音频」「视频转换」入口，其余类型展示敬请期待占位。
 */
@Composable
fun MediaFunctionsPage(
    media: SelectedMedia,
    onExportAudio: () -> Unit,
    onConvertVideo: () -> Unit,
    onBack: () -> Unit,
) {
    VicuScaffold(
        title = stringResource(media.kind.functionTitleRes()),
        onBack = onBack,
    ) {
        when (media.kind) {
            MediaKind.VIDEO -> VideoFunctionsContent(media.name, onExportAudio, onConvertVideo)
            MediaKind.IMAGE, MediaKind.AUDIO -> ComingSoonContent(media.kind)
        }
    }
}

/**
 * 文件名超长省略：按名称（不含扩展名）计数，超过 10 字符 → 前 5 + "***" + 后 5，再补回扩展名。
 */
internal fun abbreviateMediaFileName(name: String): String {
    val stem = name.substringBeforeLast('.')
    val shown = if (stem.length <= 10) stem else stem.take(5) + "***" + stem.takeLast(5)
    val ext = name.substringAfterLast('.', "")
    return if (ext.isEmpty()) shown else "$shown.$ext"
}

private fun MediaKind.functionTitleRes(): Int = when (this) {
    MediaKind.VIDEO -> R.string.functions_title_video
    MediaKind.IMAGE -> R.string.functions_title_image
    MediaKind.AUDIO -> R.string.functions_title_audio
}

@Composable
private fun VideoFunctionsContent(
    name: String,
    onExportAudio: () -> Unit,
    onConvertVideo: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .padding(VicuTheme.dimensions.screenGutter),
    ) {
        BasicText(
            text = stringResource(R.string.functions_description, abbreviateMediaFileName(name)),
            style = VicuTheme.typography.bodyLg.copy(color = VicuTheme.colors.onSurfaceVariant),
        )
        FunctionGrid(onExportAudio, onConvertVideo)
    }
}

/** 功能按钮 grid：自适应列（tile 最小 96dp，宽屏自动多列），为后续功能扩展预留。 */
@Composable
private fun FunctionGrid(onExportAudio: () -> Unit, onConvertVideo: () -> Unit) {
    val functions = listOf(
        FunctionEntry(R.drawable.ic_audio, R.string.export_audio, onExportAudio),
        FunctionEntry(R.drawable.ic_transfer, R.string.video_convert, onConvertVideo),
    )
    LazyVerticalGrid(
        columns = GridCells.Adaptive(VicuTheme.dimensions.mediaFunctionsGridMinSize),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = VicuTheme.dimensions.screenGutter),
        horizontalArrangement = Arrangement.spacedBy(VicuTheme.dimensions.spacingUnit),
        verticalArrangement = Arrangement.spacedBy(VicuTheme.dimensions.spacingUnit),
    ) {
        items(functions, key = { it.labelRes }) { entry ->
            FunctionTile(
                iconRes = entry.iconRes,
                label = stringResource(entry.labelRes),
                onClick = entry.onClick,
            )
        }
    }
}

private class FunctionEntry(
    val iconRes: Int,
    val labelRes: Int,
    val onClick: () -> Unit,
)

/** 单个功能按钮：竖向 icon + 文字，卡片视觉直接落在页面上（不套外层容器）。 */
@Composable
private fun FunctionTile(
    iconRes: Int,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val styleState = remember { MutableStyleState(null) }
    val interactionSource = remember { MutableInteractionSource() }
    Column(
        modifier = modifier
            .aspectRatio(1f)
            .clip(VicuTheme.shapes.xl)
            .clickable(
                interactionSource = interactionSource,
                onClick = onClick,
            )
            .styleable(styleState, VicuTheme.styles.functionTile),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(
            VicuTheme.dimensions.spacingUnit,
            Alignment.CenterVertically,
        ),
    ) {
        Image(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier.size(VicuTheme.dimensions.iconLarge),
            colorFilter = ColorFilter.tint(VicuTheme.colors.onSurfaceVariant),
        )
        BasicText(
            text = label,
            style = VicuTheme.typography.bodySm.copy(color = VicuTheme.colors.onSurface),
        )
    }
}

@Composable
private fun ComingSoonContent(kind: MediaKind) {
    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = VicuTheme.dimensions.screenGutter),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(VicuTheme.dimensions.spacingUnit * 2),
        ) {
            Image(
                painter = painterResource(kind.iconRes),
                contentDescription = null,
                modifier = Modifier.size(VicuTheme.dimensions.iconLarge),
                colorFilter = ColorFilter.tint(VicuTheme.colors.onSurfaceVariant),
            )
            BasicText(
                stringResource(R.string.coming_soon),
                style = VicuTheme.typography.bodyLg.copy(color = VicuTheme.colors.onSurfaceVariant),
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun MediaFunctionsPageVideoPreview() {
    VicuTheme {
        MediaFunctionsPage(
            media = SelectedMedia(
                uri = "content://media/external/video/2",
                name = "abcdefghijk.mp4",
                kind = MediaKind.VIDEO,
            ),
            onExportAudio = {},
            onConvertVideo = {},
            onBack = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun MediaFunctionsPageComingSoonPreview() {
    VicuTheme {
        MediaFunctionsPage(
            media = SelectedMedia(
                uri = "content://media/external/images/1",
                name = "photo.jpg",
                kind = MediaKind.IMAGE,
            ),
            onExportAudio = {},
            onConvertVideo = {},
            onBack = {},
        )
    }
}
