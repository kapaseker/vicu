package com.rockbyte.vicu.page

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rockbyte.vicu.repo.MediaItem
import com.rockbyte.vicu.repo.MediaKind
import com.rockbyte.vicu.repo.MediaRepo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

data class MediaLibraryUiState(
    val loading: Boolean = true,
    val items: List<MediaItem> = emptyList(),
    val hasAccess: Boolean? = null,
    val permissionsToRequest: List<String> = emptyList(),
)

/** 首页媒体库：通过 [MediaRepo] 聚合展示当前用户的图片/视频/音频，权限状态由本层判断。 */
class HomeViewModel(
    private val appContext: Context,
    private val mediaRepo: MediaRepo,
) : ViewModel() {

    private val requiredMediaPermissions = mediaPermissionsForSdk(Build.VERSION.SDK_INT)

    val uiState: StateFlow<MediaLibraryUiState>
        field = MutableStateFlow(MediaLibraryUiState())

    init {
        viewModelScope.launch {
            // repo 每次重查（含授权后 refresh）都会发射，这里统一刷新列表并重算权限状态
            mediaRepo.library.collectLatest { items ->
                uiState.value = MediaLibraryUiState(
                    loading = false,
                    items = items,
                    hasAccess = hasMediaAccess(),
                    permissionsToRequest = requiredMediaPermissions,
                )
            }
        }
    }

    /** 权限授权回调后触发 repo 重查。 */
    fun refresh() = mediaRepo.refresh()

    /** 任一媒体权限已授权即视为可访问（部分授权可用）。 */
    private fun hasMediaAccess(): Boolean =
        requiredMediaPermissions.any {
            appContext.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
        }
}

internal fun mediaPermissionsForSdk(sdkInt: Int): List<String> =
    if (sdkInt >= Build.VERSION_CODES.TIRAMISU) {
        MediaKind.entries.map { mediaPermissionFor(it, sdkInt) }
    } else {
        listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

internal fun mediaPermissionFor(kind: MediaKind, sdkInt: Int): String =
    if (sdkInt < Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_EXTERNAL_STORAGE
    } else {
        when (kind) {
            MediaKind.IMAGE -> Manifest.permission.READ_MEDIA_IMAGES
            MediaKind.VIDEO -> Manifest.permission.READ_MEDIA_VIDEO
            MediaKind.AUDIO -> Manifest.permission.READ_MEDIA_AUDIO
        }
    }
