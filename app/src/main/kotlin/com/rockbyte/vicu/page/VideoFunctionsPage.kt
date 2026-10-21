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
    val viewModel = koinViewModel<FunctionsViewModel>()
    val title = rememberMediaTitle(media, viewModel)
    VicuScaffold(title = title.text, onBack = onBack, marqueeTitle = true) {
        FunctionGrid(
            buildList {
                add(FunctionEntry(R.drawable.ic_share, R.string.share) { viewModel.share(media) })
                add(FunctionEntry(R.drawable.ic_edit, R.string.rename, title.onClick))
                if (media.source == MediaSource.WORKS) {
                    add(FunctionEntry(R.drawable.ic_album, R.string.save_to_album) { viewModel.saveToAlbum(media) })
                }
                add(FunctionEntry(R.drawable.ic_play, R.string.play, onPlayVideo))
                add(FunctionEntry(R.drawable.ic_crop, R.string.crop, onCropVideo))
                add(FunctionEntry(R.drawable.ic_scale, R.string.video_scale, onScaleVideo))
                add(FunctionEntry(R.drawable.ic_cut, R.string.video_trim, onTrimVideo))
                add(FunctionEntry(R.drawable.ic_audio, R.string.export_audio, onExportAudio))
                add(FunctionEntry(R.drawable.ic_transfer, R.string.video_convert, onConvertVideo))
                add(FunctionEntry(R.drawable.ic_audio_tape, R.string.video_replace_audio, onReplaceAudio))
            },
        )
    }
}
