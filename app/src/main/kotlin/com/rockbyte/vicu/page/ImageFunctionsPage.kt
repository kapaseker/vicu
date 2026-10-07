package com.rockbyte.vicu.page

import androidx.compose.runtime.Composable
import com.rockbyte.vicu.R
import com.rockbyte.vicu.repo.SelectedMedia
import com.rockbyte.vicu.ui.component.FunctionEntry
import com.rockbyte.vicu.ui.component.FunctionGrid
import com.rockbyte.vicu.ui.component.VicuScaffold

@Composable
fun ImageFunctionsPage(
    media: SelectedMedia,
    onCropImage: () -> Unit,
    onScaleImage: () -> Unit,
    onBack: () -> Unit,
) {
    VicuScaffold(title = media.name, onBack = onBack, marqueeTitle = true) {
        FunctionGrid(
            listOf(
                FunctionEntry(R.drawable.ic_crop, R.string.image_crop, onCropImage),
                FunctionEntry(R.drawable.ic_scale, R.string.image_scale, onScaleImage),
            ),
        )
    }
}
