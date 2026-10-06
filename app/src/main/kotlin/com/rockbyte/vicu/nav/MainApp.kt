package com.rockbyte.vicu.nav

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.rockbyte.vicu.page.AudioConvertPage
import com.rockbyte.vicu.page.AudioExportPage
import com.rockbyte.vicu.page.AudioReplacePage
import com.rockbyte.vicu.page.MediaPickerPage
import com.rockbyte.vicu.page.HomePage
import com.rockbyte.vicu.page.MediaFunctionsPage
import com.rockbyte.vicu.page.VideoConvertPage
import com.rockbyte.vicu.page.imagecrop.ImageCropPage
import com.rockbyte.vicu.page.imagescale.ImageScalePage
import com.rockbyte.vicu.page.crop.CropPage
import com.rockbyte.vicu.page.trim.TrimPage
import com.rockbyte.vicu.page.audiotrim.AudioTrimPage
import com.rockbyte.vicu.page.player.PlayerPage
import com.rockbyte.vicu.repo.MediaKind
import com.rockbyte.vicu.repo.SelectedMedia
import kotlinx.serialization.Serializable

@Serializable
data object HomeRoute : NavKey

/** 功能列表目的地；kind 决定可用功能集。 */
@Serializable
data class MediaFunctionsRoute(
    val media: SelectedMedia,
) : NavKey

/** 导出音频目的地；携带所选视频。 */
@Serializable
data class AudioExportRoute(
    val media: SelectedMedia,
) : NavKey

/** 视频转换目的地；携带所选视频。 */
@Serializable
data class VideoConvertRoute(
    val media: SelectedMedia,
) : NavKey

/** 音频转换目的地；携带所选音频。 */
@Serializable
data class AudioConvertRoute(
    val media: SelectedMedia,
) : NavKey

/** 视频播放目的地；携带所选视频。 */
@Serializable
data class PlayerRoute(
    val media: SelectedMedia,
) : NavKey

/** 视频裁剪目的地；携带所选视频。 */
@Serializable
data class CropRoute(
    val media: SelectedMedia,
) : NavKey

/** 图片裁剪目的地。 */
@Serializable
data class ImageCropRoute(val media: SelectedMedia) : NavKey

/** 图片缩放目的地。 */
@Serializable
data class ImageScaleRoute(val media: SelectedMedia) : NavKey

/** 视频段落截取目的地。 */
@Serializable
data class TrimRoute(val media: SelectedMedia) : NavKey

/** 替换音轨目的地；携带所选视频。 */
@Serializable
data class AudioReplaceRoute(val media: SelectedMedia) : NavKey

/** 应用内媒体选择目的地；kinds 决定可选的媒体类型（如仅音乐）。 */
@Serializable
data class MediaPickerRoute(val kinds: Set<MediaKind>) : NavKey

/** 音频截取目的地。 */
@Serializable
data class AudioTrimRoute(val media: SelectedMedia) : NavKey

/** Navigation 3 路由与目的地集中注册（AGENTS.md）。 */
@Composable
fun MainApp() {
    val backStack = rememberNavBackStack(HomeRoute)
    NavDisplay(
        backStack = backStack,
        entryProvider = entryProvider {
            entry<HomeRoute> {
                HomePage(
                    onMediaClick = { item ->
                        backStack.add(
                            MediaFunctionsRoute(
                                SelectedMedia(item.uri.toString(), item.name, item.kind),
                            )
                        )
                    }
                )
            }
            entry<MediaFunctionsRoute> { route ->
                MediaFunctionsPage(
                    media = route.media,
                    onExportAudio = { backStack.add(AudioExportRoute(route.media)) },
                    onConvertVideo = { backStack.add(VideoConvertRoute(route.media)) },
                    onConvertAudio = { backStack.add(AudioConvertRoute(route.media)) },
                    onPlayVideo = { backStack.add(PlayerRoute(route.media)) },
                    onCropVideo = { backStack.add(CropRoute(route.media)) },
                    onTrimVideo = { backStack.add(TrimRoute(route.media)) },
                    onTrimAudio = { backStack.add(AudioTrimRoute(route.media)) },
                    onCropImage = { backStack.add(ImageCropRoute(route.media)) },
                    onScaleImage = { backStack.add(ImageScaleRoute(route.media)) },
                    onReplaceAudio = { backStack.add(AudioReplaceRoute(route.media)) },
                    onBack = { backStack.removeLastOrNull() },
                )
            }
            entry<AudioExportRoute> { route ->
                AudioExportPage(
                    media = route.media,
                    onBack = { backStack.removeLastOrNull() },
                    onGoHome = {
                        backStack.clear()
                        backStack.add(HomeRoute)
                    },
                )
            }
            entry<VideoConvertRoute> { route ->
                VideoConvertPage(
                    media = route.media,
                    onBack = { backStack.removeLastOrNull() },
                    onGoHome = {
                        backStack.clear()
                        backStack.add(HomeRoute)
                    },
                )
            }
            entry<AudioConvertRoute> { route ->
                AudioConvertPage(
                    media = route.media,
                    onBack = { backStack.removeLastOrNull() },
                    onGoHome = {
                        backStack.clear()
                        backStack.add(HomeRoute)
                    },
                )
            }
            entry<PlayerRoute> { route ->
                PlayerPage(
                    media = route.media,
                    onBack = { backStack.removeLastOrNull() },
                )
            }
            entry<TrimRoute> { route ->
                TrimPage(
                    media = route.media,
                    onBack = { backStack.removeLastOrNull() },
                    onGoHome = {
                        backStack.clear()
                        backStack.add(HomeRoute)
                    },
                )
            }
            entry<AudioReplaceRoute> { route ->
                AudioReplacePage(
                    media = route.media,
                    onPickMusic = { backStack.add(MediaPickerRoute(setOf(MediaKind.AUDIO))) },
                    onBack = { backStack.removeLastOrNull() },
                    onGoHome = {
                        backStack.clear()
                        backStack.add(HomeRoute)
                    },
                )
            }
            entry<MediaPickerRoute> { route ->
                MediaPickerPage(
                    kinds = route.kinds,
                    onDone = { backStack.removeLastOrNull() },
                    onBack = { backStack.removeLastOrNull() },
                )
            }
            entry<AudioTrimRoute> { route ->
                AudioTrimPage(route.media,
                    onBack = { backStack.removeLastOrNull() },
                    onGoHome = { backStack.clear(); backStack.add(HomeRoute) },
                )
            }
            entry<ImageCropRoute> { route ->
                ImageCropPage(
                    media = route.media,
                    onBack = { backStack.removeLastOrNull() },
                    onGoHome = {
                        backStack.clear()
                        backStack.add(HomeRoute)
                    },
                )
            }
            entry<ImageScaleRoute> { route ->
                ImageScalePage(
                    media = route.media,
                    onBack = { backStack.removeLastOrNull() },
                    onGoHome = {
                        backStack.clear()
                        backStack.add(HomeRoute)
                    },
                )
            }
            entry<CropRoute> { route ->
                CropPage(
                    media = route.media,
                    onBack = { backStack.removeLastOrNull() },
                    onGoHome = {
                        backStack.clear()
                        backStack.add(HomeRoute)
                    },
                )
            }
        },
    )
}
