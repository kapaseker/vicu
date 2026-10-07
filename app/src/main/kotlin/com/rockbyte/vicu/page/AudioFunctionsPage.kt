package com.rockbyte.vicu.page

import androidx.compose.runtime.Composable
import com.rockbyte.vicu.R
import com.rockbyte.vicu.repo.SelectedMedia
import com.rockbyte.vicu.ui.component.FunctionEntry
import com.rockbyte.vicu.ui.component.FunctionGrid
import com.rockbyte.vicu.ui.component.VicuScaffold

@Composable
fun AudioFunctionsPage(
    media: SelectedMedia,
    onTrimAudio: () -> Unit,
    onConvertAudio: () -> Unit,
    onBack: () -> Unit,
) {
    VicuScaffold(title = media.name, onBack = onBack, marqueeTitle = true) {
        FunctionGrid(
            listOf(
                FunctionEntry(R.drawable.ic_cut, R.string.audio_trim, onTrimAudio),
                FunctionEntry(R.drawable.ic_transfer, R.string.audio_convert, onConvertAudio),
            ),
        )
    }
}
