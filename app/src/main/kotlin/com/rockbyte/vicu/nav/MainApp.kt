package com.rockbyte.vicu.nav

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.rockbyte.vicu.page.AudioExportPage
import com.rockbyte.vicu.page.HomePage
import com.rockbyte.vicu.page.MediaFunctionsPage
import com.rockbyte.vicu.page.VideoConvertPage
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
        },
    )
}
