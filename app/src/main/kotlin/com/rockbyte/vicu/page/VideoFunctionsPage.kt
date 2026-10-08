package com.rockbyte.vicu.page

import androidx.compose.runtime.Composable
import com.rockbyte.vicu.R
import com.rockbyte.vicu.repo.SelectedMedia
import com.rockbyte.vicu.ui.component.FunctionEntry
import com.rockbyte.vicu.ui.component.FunctionGrid
import com.rockbyte.vicu.ui.component.VicuScaffold

@Composable
fun VideoFunctionsPage(
    media: SelectedMedia,
    onPlayVideo: () -> Unit,
    onCropVideo: () -> Unit,
    onScaleVideo: () -> Unit,
    onTrimVideo: () -> Unit,
    onExportAudio: () -> Unit,
    onConvertVideo: () -> Unit,
    onReplaceAudio: () -> Unit,
    onBack: () -> Unit,
) {
    VicuScaffold(title = media.name, onBack = onBack, marqueeTitle = true) {
        FunctionGrid(
            listOf(
                FunctionEntry(R.drawable.ic_play, R.string.play, onPlayVideo),
                FunctionEntry(R.drawable.ic_crop, R.string.crop, onCropVideo),
                FunctionEntry(R.drawable.ic_scale, R.string.video_scale, onScaleVideo),
                FunctionEntry(R.drawable.ic_cut, R.string.video_trim, onTrimVideo),
                FunctionEntry(R.drawable.ic_audio, R.string.export_audio, onExportAudio),
                FunctionEntry(R.drawable.ic_transfer, R.string.video_convert, onConvertVideo),
                FunctionEntry(R.drawable.ic_audio_tape, R.string.video_replace_audio, onReplaceAudio),
            ),
        )
    }
}
