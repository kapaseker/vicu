package com.rockbyte.vicu.page.imagescale

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
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.viewmodel.compose.rememberViewModelStoreOwner
import com.rockbyte.vicu.R
import com.rockbyte.vicu.repo.ImageCropError
import com.rockbyte.vicu.repo.SelectedMedia
import com.rockbyte.vicu.repo.imageCropRegion
import com.rockbyte.vicu.ui.component.CropRectF
import com.rockbyte.vicu.ui.component.OutlineButton
import com.rockbyte.vicu.ui.component.PrimaryButton
import com.rockbyte.vicu.ui.component.ProgressButton
import com.rockbyte.vicu.ui.component.ScaleMarquee
import com.rockbyte.vicu.ui.component.ScaleOutputSize
import com.rockbyte.vicu.ui.component.StatusRow
import com.rockbyte.vicu.ui.component.VicuButton
import com.rockbyte.vicu.ui.component.VicuScaffold
import com.rockbyte.vicu.ui.component.scaleOutputSize
import com.rockbyte.vicu.ui.component.snapToUniform
import com.rockbyte.vicu.ui.component.videoPreviewWidth
import com.rockbyte.vicu.ui.theme.VicuTheme
import org.koin.androidx.compose.koinViewModel

@Composable
fun ImageScalePage(media: SelectedMedia, onBack: () -> Unit, onGoHome: () -> Unit) {
    val owner = rememberViewModelStoreOwner()
    val activity = LocalActivity.current
    val viewModel = koinViewModel<ImageScaleViewModel>(viewModelStoreOwner = owner)
    DisposableEffect(owner, activity) {
        onDispose {
            // Navigation marks the entry destroyed before disposal; clear on pop, retain on rotation.
            if (activity?.isChangingConfigurations != true) owner.viewModelStore.clear()
        }
    }
    LaunchedEffect(media) { viewModel.bind(media) }
    val state by viewModel.uiState.collectAsState()
    var scaleRect by rememberSaveable(media.uri, stateSaver = scaleRectSaver) {
        mutableStateOf(CropRectF(0f, 0f, 1f, 1f))
    }
    var uniform by rememberSaveable(media.uri) { mutableStateOf(true) }
    val saving = state.saveState == ImageScaleSaveState.Saving
    val preview = (state.loadState as? ImageScaleLoadState.Ready)?.preview
    BackHandler(enabled = saving) { }

    VicuScaffold(title = stringResource(R.string.image_scale), onBack = onBack, backEnabled = !saving) {
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
                    val output = scaleOutputSize(scaleRect, preview.info.width, preview.info.height)
                    Box(Modifier.align(Alignment.CenterHorizontally)
                        .width(videoPreviewWidth(availableWidth, availableHeight, aspect)).aspectRatio(aspect)) {
                        Image(image, contentDescription = stringResource(R.string.image_scale_preview),
                            contentScale = ContentScale.FillBounds, modifier = Modifier.matchParentSize())
                        ScaleMarquee(
                            rect = scaleRect,
                            uniform = uniform,
                            enabled = !saving,
                            onRectChange = {
                                if (it != scaleRect) {
                                    scaleRect = it
                                    viewModel.selectionChanged()
                                }
                            },
                            modifier = Modifier.matchParentSize(),
                        )
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(VicuTheme.dimensions.spacingUnit),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        BasicText(
                            text = stringResource(R.string.image_scale_dimensions, preview.info.width,
                                preview.info.height, output.width, output.height),
                            style = VicuTheme.typography.bodySm.copy(color = VicuTheme.colors.onSurfaceVariant),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        UniformToggleButton(uniform = uniform, enabled = !saving) {
                            // 非等比 → 等比：取较小占比收敛成等比框；等比 → 非等比：框不变。
                            if (!uniform) scaleRect = scaleRect.snapToUniform()
                            uniform = !uniform
                            viewModel.selectionChanged()
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(VicuTheme.dimensions.spacingUnit)) {
                    if (saving) {
                        ProgressButton(progress = null, label = stringResource(R.string.image_scale_saving),
                            modifier = Modifier.weight(1f))
                    } else {
                        PrimaryButton(
                            text = stringResource(when (state.saveState) {
                                ImageScaleSaveState.Complete -> R.string.image_scale_success
                                is ImageScaleSaveState.Failed -> R.string.image_scale_failed_retry
                                else -> R.string.confirm
                            }),
                            onClick = {
                                preview?.let {
                                    val output: ScaleOutputSize =
                                        scaleOutputSize(scaleRect, it.info.width, it.info.height)
                                    viewModel.save(output.width, output.height)
                                }
                            },
                            modifier = Modifier.weight(1f),
                            enabled = preview != null && state.saveState != ImageScaleSaveState.Complete,
                        )
                    }
                    if (state.saveState == ImageScaleSaveState.Complete) {
                        OutlineButton(stringResource(R.string.back_to_home), onGoHome, Modifier.weight(1f))
                    }
                }
                when (val load = state.loadState) {
                    ImageScaleLoadState.Loading -> StatusRow(VicuTheme.colors.secondary,
                        stringResource(R.string.image_scale_loading), pulsing = true)
                    is ImageScaleLoadState.Failed -> ImageScaleErrorRow(load.error)
                    is ImageScaleLoadState.Ready -> Unit
                }
                when (val save = state.saveState) {
                    ImageScaleSaveState.Saving -> StatusRow(VicuTheme.colors.secondary,
                        stringResource(R.string.image_scale_saving), pulsing = true)
                    ImageScaleSaveState.Complete -> StatusRow(VicuTheme.colors.onSurfaceVariant,
                        stringResource(R.string.image_scale_complete))
                    is ImageScaleSaveState.Failed -> ImageScaleErrorRow(save.error)
                    ImageScaleSaveState.Idle -> Unit
                }
            }
        }
    }
}

/** 等比/非等比切换：等比显示 link（已联动），非等比显示 unlink（已断开）。 */
@Composable
private fun UniformToggleButton(uniform: Boolean, enabled: Boolean, onToggle: () -> Unit) {
    VicuButton(
        onClick = onToggle,
        enabled = enabled,
        style = VicuTheme.styles.outlineButton,
        rippleColor = VicuTheme.colors.onSurface,
    ) {
        Image(
            painter = painterResource(if (uniform) R.drawable.ic_link else R.drawable.ic_unlink),
            contentDescription = stringResource(
                if (uniform) R.string.image_scale_uniform_linked else R.string.image_scale_uniform_free,
            ),
            modifier = Modifier.size(VicuTheme.dimensions.iconMedium),
            colorFilter = ColorFilter.tint(VicuTheme.colors.onSurface),
        )
    }
}

@Composable
private fun ImageScaleErrorRow(error: ImageCropError) {
    StatusRow(dotColor = VicuTheme.colors.error, textColor = VicuTheme.colors.error,
        text = stringResource(when (error) {
            ImageCropError.LoadFailed -> R.string.image_crop_error_load
            ImageCropError.AnimatedUnsupported -> R.string.image_scale_error_animation
            ImageCropError.ImageTooLarge -> R.string.image_scale_error_large
            ImageCropError.CropFailed -> R.string.image_scale_error_scale
            ImageCropError.SaveFailed -> R.string.image_crop_error_save
        }))
}

private val scaleRectSaver = listSaver<CropRectF, Float>(
    save = { listOf(it.left, it.top, it.right, it.bottom) },
    restore = { CropRectF(it[0], it[1], it[2], it[3]) },
)
