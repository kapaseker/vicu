package com.rockbyte.vicu.page

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.style.MutableStyleState
import androidx.compose.foundation.style.styleable
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.rockbyte.vicu.R
import com.rockbyte.vicu.nav.MediaFunctionsRoute
import com.rockbyte.vicu.repo.MediaKind
import com.rockbyte.vicu.ui.component.VicuButton
import com.rockbyte.vicu.ui.component.VicuScaffold
import com.rockbyte.vicu.ui.component.iconRes
import com.rockbyte.vicu.ui.theme.VicuSpacing
import com.rockbyte.vicu.ui.theme.VicuTheme

/**
 * 媒体功能列表页：所有多媒体点击后进入。
 * 视频提供「导出音频」入口；其余类型展示敬请期待占位。
 */
@Composable
fun MediaFunctionsPage(route: MediaFunctionsRoute, onExportAudio: () -> Unit) {
    VicuScaffold(title = route.name) {
        when (route.kind) {
            MediaKind.VIDEO -> VideoFunctionsContent(onExportAudio)
            MediaKind.IMAGE, MediaKind.AUDIO -> ComingSoonContent(route.kind)
        }
    }
}

@Composable
private fun VideoFunctionsContent(onExportAudio: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = VicuSpacing.gutter)
            .padding(vertical = VicuSpacing.gutter),
        verticalArrangement = Arrangement.spacedBy(VicuSpacing.unit * 2),
    ) {
        val cardState = remember { MutableStyleState(null) }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .styleable(cardState, VicuTheme.styles.card),
            verticalArrangement = Arrangement.spacedBy(VicuSpacing.unit * 2),
        ) {
            BasicText(
                stringResource(R.string.functions_description),
                style = VicuTheme.typography.bodyLg,
            )
            VicuButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = onExportAudio,
            ) {
                BasicText(stringResource(R.string.export_audio))
            }
        }
    }
}

@Composable
private fun ComingSoonContent(kind: MediaKind) {
    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = VicuSpacing.gutter),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(VicuSpacing.unit * 2),
        ) {
            Image(
                painter = painterResource(kind.iconRes),
                contentDescription = null,
                modifier = Modifier.size(32.dp),
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
            route = MediaFunctionsRoute(
                uri = "content://media/external/video/2",
                name = "clip.mp4",
                kind = MediaKind.VIDEO,
            ),
            onExportAudio = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun MediaFunctionsPageComingSoonPreview() {
    VicuTheme {
        MediaFunctionsPage(
            route = MediaFunctionsRoute(
                uri = "content://media/external/images/1",
                name = "photo.jpg",
                kind = MediaKind.IMAGE,
            ),
            onExportAudio = {},
        )
    }
}
