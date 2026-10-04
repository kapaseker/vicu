package com.rockbyte.vicu.page.imagescale

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rockbyte.vicu.repo.ImageCropError
import com.rockbyte.vicu.repo.ImageCropException
import com.rockbyte.vicu.repo.ImageCropPreview
import com.rockbyte.vicu.repo.ImageScaleRepo
import com.rockbyte.vicu.repo.SelectedMedia
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface ImageScaleLoadState {
    data object Loading : ImageScaleLoadState
    data class Ready(val preview: ImageCropPreview) : ImageScaleLoadState
    data class Failed(val error: ImageCropError) : ImageScaleLoadState
}

sealed interface ImageScaleSaveState {
    data object Idle : ImageScaleSaveState
    data object Saving : ImageScaleSaveState
    data object Complete : ImageScaleSaveState
    data class Failed(val error: ImageCropError) : ImageScaleSaveState
}

data class ImageScaleUiState(
    val loadState: ImageScaleLoadState = ImageScaleLoadState.Loading,
    val saveState: ImageScaleSaveState = ImageScaleSaveState.Idle,
)

class ImageScaleViewModel(private val repo: ImageScaleRepo) : ViewModel() {
    val uiState: StateFlow<ImageScaleUiState>
        field = MutableStateFlow(ImageScaleUiState())

    private var media: SelectedMedia? = null
    private var loadJob: Job? = null
    private var saveJob: Job? = null

    fun bind(selected: SelectedMedia) {
        if (media == selected) return
        loadJob?.cancel()
        saveJob?.cancel()
        media = selected
        uiState.value = ImageScaleUiState()
        loadJob = viewModelScope.launch {
            val result = repo.load(Uri.parse(selected.uri))
            uiState.update { it.copy(loadState = result.fold(
                onSuccess = { preview -> ImageScaleLoadState.Ready(preview) },
                onFailure = { error -> ImageScaleLoadState.Failed(error.imageError(ImageCropError.LoadFailed)) },
            )) }
        }
    }

    fun save(width: Int, height: Int) {
        val selected = media ?: return
        val preview = (uiState.value.loadState as? ImageScaleLoadState.Ready)?.preview ?: return
        if (uiState.value.saveState == ImageScaleSaveState.Saving) return
        // Mark busy before dispatch, so two clicks in the same frame cannot start two saves.
        uiState.update { it.copy(saveState = ImageScaleSaveState.Saving) }
        saveJob = viewModelScope.launch {
            val result = repo.scale(Uri.parse(selected.uri), selected.name, width, height,
                preview.bitmap.allocationByteCount.toLong())
            uiState.update { it.copy(saveState = result.fold(
                onSuccess = { ImageScaleSaveState.Complete },
                onFailure = { error -> ImageScaleSaveState.Failed(error.imageError(ImageCropError.CropFailed)) },
            )) }
        }
    }

    fun selectionChanged() {
        uiState.update { if (it.saveState == ImageScaleSaveState.Saving) it else it.copy(saveState = ImageScaleSaveState.Idle) }
    }
}

private fun Throwable.imageError(fallback: ImageCropError): ImageCropError =
    (this as? ImageCropException)?.error ?: fallback
