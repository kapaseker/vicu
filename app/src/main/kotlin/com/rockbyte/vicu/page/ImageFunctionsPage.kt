package com.rockbyte.vicu.page

import androidx.compose.runtime.Composable
import com.rockbyte.vicu.R
import com.rockbyte.vicu.repo.MediaSource
import com.rockbyte.vicu.repo.SelectedMedia
import com.rockbyte.vicu.ui.component.FunctionEntry
import com.rockbyte.vicu.ui.component.FunctionGrid
import com.rockbyte.vicu.ui.component.VicuScaffold
import org.koin.androidx.compose.koinViewModel

@Composable
fun ImageFunctionsPage(
    media: SelectedMedia,
    onCropImage: () -> Unit,
    onScaleImage: () -> Unit,
    onBack: () -> Unit,
) {
    val viewModel = koinViewModel<FunctionsViewModel>()
    VicuScaffold(title = media.name, onBack = onBack, marqueeTitle = true) {
        FunctionGrid(
            buildList {
                add(FunctionEntry(R.drawable.ic_share, R.string.share) { viewModel.share(media) })
                if (media.source == MediaSource.WORKS) {
                    add(FunctionEntry(R.drawable.ic_album, R.string.save_to_album) { viewModel.saveToAlbum(media) })
                }
                add(FunctionEntry(R.drawable.ic_crop, R.string.image_crop, onCropImage))
                add(FunctionEntry(R.drawable.ic_scale, R.string.image_scale, onScaleImage))
            },
        )
    }
}
