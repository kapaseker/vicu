package com.rockbyte.vicu.page

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rockbyte.vicu.repo.MediaItem
import com.rockbyte.vicu.repo.MediaRepo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class MediaLibraryUiState(
    val loading: Boolean = false,
    val items: List<MediaItem> = emptyList(),
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
            uiState.value = MediaLibraryUiState(loading = true)
            val items = mediaRepo.queryAllMedia()
            uiState.value = MediaLibraryUiState(items = items)
        }
    }
}
