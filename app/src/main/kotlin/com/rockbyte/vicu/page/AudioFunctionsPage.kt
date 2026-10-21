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
fun AudioFunctionsPage(
    media: SelectedMedia,
    onTrimAudio: () -> Unit,
    onConvertAudio: () -> Unit,
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
                add(FunctionEntry(R.drawable.ic_cut, R.string.audio_trim, onTrimAudio))
                add(FunctionEntry(R.drawable.ic_transfer, R.string.audio_convert, onConvertAudio))
            },
        )
    }
}
