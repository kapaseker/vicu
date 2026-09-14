package com.rockbyte.vicu.page

import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rockbyte.vicu.repo.MediaItem
import com.rockbyte.vicu.repo.MediaRepo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class MediaLibraryUiState(
    val loading: Boolean = true,
    val items: List<MediaItem> = emptyList(),
    val hasAccess: Boolean? = null,
    val permissionsToRequest: List<String> = emptyList(),
)

/** 首页媒体库：通过 [MediaRepo] 聚合展示当前用户的图片/视频/音频。 */
class HomeViewModel(private val mediaRepo: MediaRepo) : ViewModel() {

    val uiState: StateFlow<MediaLibraryUiState>
        field = MutableStateFlow(MediaLibraryUiState())

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            uiState.value = uiState.value.copy(loading = true)
            val library = mediaRepo.loadLibrary()
            uiState.value = MediaLibraryUiState(
                loading = false,
                items = library.items,
                hasAccess = library.hasAccess,
                permissionsToRequest = library.permissionsToRequest,
            )
        }
    }

    suspend fun loadThumbnail(uri: Uri, width: Int, height: Int): Bitmap? =
        mediaRepo.loadThumbnail(uri, width, height)
}
