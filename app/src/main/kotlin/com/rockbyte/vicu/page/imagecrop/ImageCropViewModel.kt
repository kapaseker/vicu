package com.rockbyte.vicu.page.imagecrop

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rockbyte.vicu.repo.ImageCropError
import com.rockbyte.vicu.repo.ImageCropException
import com.rockbyte.vicu.repo.ImageCropPreview
import com.rockbyte.vicu.repo.ImageCropRegion
import com.rockbyte.vicu.repo.ImageCropRepo
import com.rockbyte.vicu.repo.SelectedMedia
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface ImageCropLoadState {
    data object Loading : ImageCropLoadState
    data class Ready(val preview: ImageCropPreview) : ImageCropLoadState
    data class Failed(val error: ImageCropError) : ImageCropLoadState
}

sealed interface ImageCropSaveState {
    data object Idle : ImageCropSaveState
    data object Saving : ImageCropSaveState
    data object Complete : ImageCropSaveState
    data class Failed(val error: ImageCropError) : ImageCropSaveState
}

data class ImageCropUiState(
    val loadState: ImageCropLoadState = ImageCropLoadState.Loading,
    val saveState: ImageCropSaveState = ImageCropSaveState.Idle,
)

class ImageCropViewModel(private val repo: ImageCropRepo) : ViewModel() {
    val uiState: StateFlow<ImageCropUiState>
        field = MutableStateFlow(ImageCropUiState())

    private var media: SelectedMedia? = null
    private var loadJob: Job? = null
    private var saveJob: Job? = null

    fun bind(selected: SelectedMedia) {
        if (media == selected) return
        loadJob?.cancel()
        saveJob?.cancel()
        media = selected
        uiState.value = ImageCropUiState()
        loadJob = viewModelScope.launch {
            val result = repo.load(Uri.parse(selected.uri))
            uiState.update { it.copy(loadState = result.fold(
                onSuccess = { preview -> ImageCropLoadState.Ready(preview) },
                onFailure = { error -> ImageCropLoadState.Failed(error.imageError(ImageCropError.LoadFailed)) },
            )) }
        }
    }

    fun save(region: ImageCropRegion) {
        val selected = media ?: return
        val preview = (uiState.value.loadState as? ImageCropLoadState.Ready)?.preview ?: return
        if (uiState.value.saveState == ImageCropSaveState.Saving) return
        // Mark busy before dispatch, so two clicks in the same frame cannot start two saves.
        uiState.update { it.copy(saveState = ImageCropSaveState.Saving) }
        saveJob = viewModelScope.launch {
            val result = repo.crop(Uri.parse(selected.uri), selected.name, region, preview.bitmap.allocationByteCount.toLong())
            uiState.update { it.copy(saveState = result.fold(
                onSuccess = { ImageCropSaveState.Complete },
                onFailure = { error -> ImageCropSaveState.Failed(error.imageError(ImageCropError.CropFailed)) },
            )) }
        }
    }

    fun selectionChanged() {
        uiState.update { if (it.saveState == ImageCropSaveState.Saving) it else it.copy(saveState = ImageCropSaveState.Idle) }
    }
}

private fun Throwable.imageError(fallback: ImageCropError): ImageCropError =
    (this as? ImageCropException)?.error ?: fallback
