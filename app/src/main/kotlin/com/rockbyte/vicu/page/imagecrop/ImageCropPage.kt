package com.rockbyte.vicu.page.imagecrop

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.rememberViewModelStoreOwner
import com.rockbyte.vicu.R
import com.rockbyte.vicu.repo.ImageCropError
import com.rockbyte.vicu.repo.SelectedMedia
import com.rockbyte.vicu.repo.imageCropRegion
import com.rockbyte.vicu.ui.component.CropMarquee
import com.rockbyte.vicu.ui.component.CropRectF
import com.rockbyte.vicu.ui.component.OutlineButton
import com.rockbyte.vicu.ui.component.PrimaryButton
import com.rockbyte.vicu.ui.component.ProgressButton
import com.rockbyte.vicu.ui.component.StatusRow
import com.rockbyte.vicu.ui.component.VicuScaffold
import com.rockbyte.vicu.ui.component.videoPreviewWidth
import com.rockbyte.vicu.ui.theme.VicuTheme
import org.koin.androidx.compose.koinViewModel

@Composable
fun ImageCropPage(media: SelectedMedia, onBack: () -> Unit, onGoHome: () -> Unit) {
    val owner = rememberViewModelStoreOwner()
    val activity = LocalActivity.current
    val viewModel = koinViewModel<ImageCropViewModel>(viewModelStoreOwner = owner)
    DisposableEffect(owner, activity) {
        onDispose {
            // Navigation marks the entry destroyed before disposal; clear on pop, retain on rotation.
            if (activity?.isChangingConfigurations != true) owner.viewModelStore.clear()
        }
    }
    LaunchedEffect(media) { viewModel.bind(media) }
    val state by viewModel.uiState.collectAsState()
    var cropRect by rememberSaveable(media.uri, stateSaver = cropRectSaver) {
        mutableStateOf(CropRectF(0f, 0f, 1f, 1f))
    }
    val saving = state.saveState == ImageCropSaveState.Saving
    val preview = (state.loadState as? ImageCropLoadState.Ready)?.preview
    BackHandler(enabled = saving) { }

    VicuScaffold(title = stringResource(R.string.image_crop), onBack = onBack, backEnabled = !saving) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val availableWidth = maxWidth - VicuTheme.dimensions.screenGutter * 2
            val availableHeight = maxHeight
            Column(
                modifier = Modifier.fillMaxSize().navigationBarsPadding()
                    .verticalScroll(rememberScrollState()).padding(VicuTheme.dimensions.screenGutter),
                verticalArrangement = Arrangement.spacedBy(VicuTheme.dimensions.spacingUnit * 2),
            ) {
                if (preview != null) {
                    val aspect = preview.info.width.toFloat() / preview.info.height
                    val image = remember(preview.bitmap) { preview.bitmap.asImageBitmap() }
                    val region = imageCropRegion(cropRect.left, cropRect.top, cropRect.right, cropRect.bottom,
                        preview.info.width, preview.info.height)
                    Box(Modifier.align(Alignment.CenterHorizontally)
                        .width(videoPreviewWidth(availableWidth, availableHeight, aspect)).aspectRatio(aspect)) {
                        Image(image, contentDescription = stringResource(R.string.image_crop_preview),
                            contentScale = ContentScale.FillBounds, modifier = Modifier.matchParentSize())
                        CropMarquee(
                            rect = cropRect,
                            enabled = !saving,
                            onRectChange = {
                                if (it != cropRect) {
                                    cropRect = it
                                    viewModel.selectionChanged()
                                }
                            },
                            modifier = Modifier.matchParentSize(),
                        )
                    }
                    BasicText(
                        text = stringResource(R.string.image_crop_dimensions, preview.info.width, preview.info.height,
                            region.width, region.height),
                        style = VicuTheme.typography.bodySm.copy(color = VicuTheme.colors.onSurfaceVariant),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(VicuTheme.dimensions.spacingUnit)) {
                    if (saving) {
                        ProgressButton(progress = null, label = stringResource(R.string.image_crop_saving),
                            modifier = Modifier.weight(1f))
                    } else {
                        PrimaryButton(
                            text = stringResource(when (state.saveState) {
                                ImageCropSaveState.Complete -> R.string.crop_success
                                is ImageCropSaveState.Failed -> R.string.crop_failed_retry
                                else -> R.string.confirm
                            }),
                            onClick = {
                                preview?.let {
                                    viewModel.save(imageCropRegion(cropRect.left, cropRect.top, cropRect.right, cropRect.bottom,
                                        it.info.width, it.info.height))
                                }
                            },
                            modifier = Modifier.weight(1f),
                            enabled = preview != null && state.saveState != ImageCropSaveState.Complete,
                        )
                    }
                    if (state.saveState == ImageCropSaveState.Complete) {
                        OutlineButton(stringResource(R.string.back_to_home), onGoHome, Modifier.weight(1f))
                    }
                }
                when (val load = state.loadState) {
                    ImageCropLoadState.Loading -> StatusRow(VicuTheme.colors.secondary,
                        stringResource(R.string.loading_image), pulsing = true)
                    is ImageCropLoadState.Failed -> ImageCropErrorRow(load.error)
                    is ImageCropLoadState.Ready -> Unit
                }
                when (val save = state.saveState) {
                    ImageCropSaveState.Saving -> StatusRow(VicuTheme.colors.secondary,
                        stringResource(R.string.image_crop_saving), pulsing = true)
                    ImageCropSaveState.Complete -> StatusRow(VicuTheme.colors.onSurfaceVariant,
                        stringResource(R.string.saved_to_pictures_folder))
                    is ImageCropSaveState.Failed -> ImageCropErrorRow(save.error)
                    ImageCropSaveState.Idle -> Unit
                }
            }
        }
    }
}

@Composable
private fun ImageCropErrorRow(error: ImageCropError) {
    StatusRow(dotColor = VicuTheme.colors.error, textColor = VicuTheme.colors.error,
        text = stringResource(when (error) {
            ImageCropError.LoadFailed -> R.string.image_load_failed_reselect
            ImageCropError.AnimatedUnsupported -> R.string.image_crop_error_animation
            ImageCropError.ImageTooLarge -> R.string.image_crop_error_large
            ImageCropError.CropFailed -> R.string.image_crop_error_crop
            ImageCropError.SaveFailed -> R.string.image_save_failed_retry
        }))
}

private val cropRectSaver = listSaver<CropRectF, Float>(
    save = { listOf(it.left, it.top, it.right, it.bottom) },
    restore = { CropRectF(it[0], it[1], it[2], it[3]) },
)
