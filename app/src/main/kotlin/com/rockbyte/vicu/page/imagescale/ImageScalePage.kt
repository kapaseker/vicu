package com.rockbyte.vicu.page.imagescale

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.style.MutableStyleState
import androidx.compose.foundation.style.Style
import androidx.compose.foundation.style.styleable
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
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
import com.rockbyte.vicu.ui.component.VicuScaffold
import com.rockbyte.vicu.ui.component.scaleOutputSize
import com.rockbyte.vicu.ui.component.snapToUniform
import com.rockbyte.vicu.ui.component.videoPreviewWidth
import com.rockbyte.vicu.ui.theme.VicuTheme
import com.rockbyte.vicu.ui.theme.colors
import com.rockbyte.vicu.ui.theme.dimensions
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
                            contentScale = ContentScale.FillBounds,
                            modifier = Modifier.align(Alignment.Center)
                                .fillMaxWidth(scaleRect.right - scaleRect.left)
                                .fillMaxHeight(scaleRect.bottom - scaleRect.top))
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
                    BasicText(
                        text = stringResource(R.string.image_scale_dimensions, preview.info.width,
                            preview.info.height, output.width, output.height),
                        style = VicuTheme.typography.bodySm.copy(color = VicuTheme.colors.onSurfaceVariant),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    UniformToggleRow(uniform = uniform, enabled = !saving) {
                        // 非等比 → 等比：取较小占比收敛成等比框；等比 → 非等比：框不变。
                        if (!uniform) scaleRect = scaleRect.snapToUniform()
                        uniform = !uniform
                        viewModel.selectionChanged()
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
                        stringResource(R.string.loading_image), pulsing = true)
                    is ImageScaleLoadState.Failed -> ImageScaleErrorRow(load.error)
                    is ImageScaleLoadState.Ready -> Unit
                }
                when (val save = state.saveState) {
                    ImageScaleSaveState.Saving -> StatusRow(VicuTheme.colors.secondary,
                        stringResource(R.string.image_scale_saving), pulsing = true)
                    ImageScaleSaveState.Complete -> StatusRow(VicuTheme.colors.onSurfaceVariant,
                        stringResource(R.string.saved_to_pictures_folder))
                    is ImageScaleSaveState.Failed -> ImageScaleErrorRow(save.error)
                    ImageScaleSaveState.Idle -> Unit
                }
            }
        }
    }
}

/** 等比开关行：左侧开关滑块表达状态，右侧文字说明。整行可点。 */
@Composable
private fun UniformToggleRow(uniform: Boolean, enabled: Boolean, onToggle: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(VicuTheme.shapes.base)
            .toggleable(
                value = uniform,
                enabled = enabled,
                role = Role.Switch,
                interactionSource = interactionSource,
                onValueChange = { onToggle() },
            )
            // 竖向留白把 32dp 滑块行撑到 48dp 触控高度，同时扩到可点区内。
            .padding(vertical = VicuTheme.dimensions.spacingUnit)
            .alpha(if (enabled) VicuTheme.alpha.full else VicuTheme.alpha.disabled),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(VicuTheme.dimensions.spacingUnit),
    ) {
        VicuSwitch(checked = uniform)
        BasicText(
            text = stringResource(R.string.image_scale_uniform),
            style = VicuTheme.typography.bodyLg,
            modifier = Modifier.weight(1f),
        )
    }
}

/** 开关滑块：黑轨 + 白钮表示已开启，灰轨 + 白钮表示关闭，钮带位移动画。手绘以避开 Material3 依赖。 */
@Composable
private fun VicuSwitch(checked: Boolean) {
    val trackState = remember { MutableStyleState(null) }
    val thumbState = remember { MutableStyleState(null) }
    val dimensions = VicuTheme.dimensions
    val travel by animateDpAsState(
        targetValue = if (checked) {
            dimensions.switchTrackWidth - dimensions.switchThumbSize - dimensions.switchThumbInset * 2
        } else {
            0.dp
        },
        animationSpec = tween(VicuTheme.motion.switchAnimationDurationMillis),
        label = "switchThumb",
    )
    Box(
        modifier = Modifier
            .size(dimensions.switchTrackWidth, dimensions.switchTrackHeight)
            .clip(VicuTheme.shapes.full)
            .styleable(trackState, Style {
                background(if (checked) colors.primary else colors.surfaceContainerHighest)
            }),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .padding(start = dimensions.switchThumbInset)
                .offset(x = travel)
                .size(dimensions.switchThumbSize)
                .clip(VicuTheme.shapes.full)
                .styleable(thumbState, Style {
                    background(if (checked) colors.onPrimary else colors.surfaceContainerLowest)
                }),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun UniformToggleRowPreview() {
    VicuTheme {
        Column(
            modifier = Modifier.padding(VicuTheme.dimensions.screenGutter),
            verticalArrangement = Arrangement.spacedBy(VicuTheme.dimensions.spacingUnit * 2),
        ) {
            UniformToggleRow(uniform = true, enabled = true, onToggle = {})
            UniformToggleRow(uniform = false, enabled = true, onToggle = {})
            UniformToggleRow(uniform = true, enabled = false, onToggle = {})
        }
    }
}

@Composable
private fun ImageScaleErrorRow(error: ImageCropError) {
    StatusRow(dotColor = VicuTheme.colors.error, textColor = VicuTheme.colors.error,
        text = stringResource(when (error) {
            ImageCropError.LoadFailed -> R.string.image_load_failed_reselect
            ImageCropError.AnimatedUnsupported -> R.string.image_scale_error_animation
            ImageCropError.ImageTooLarge -> R.string.image_scale_error_large
            ImageCropError.CropFailed -> R.string.image_scale_error_scale
            ImageCropError.SaveFailed -> R.string.image_save_failed_retry
        }))
}

private val scaleRectSaver = listSaver<CropRectF, Float>(
    save = { listOf(it.left, it.top, it.right, it.bottom) },
    restore = { CropRectF(it[0], it[1], it[2], it[3]) },
)
