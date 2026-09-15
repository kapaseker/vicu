package com.rockbyte.vicu.page

import com.rockbyte.vicu.repo.AudioConvertFormat
import com.rockbyte.vicu.repo.AudioConvertRepo
import com.rockbyte.vicu.repo.AudioConvertRequest
import com.rockbyte.vicu.repo.AudioConvertResult
import com.rockbyte.vicu.repo.MediaKind
import com.rockbyte.vicu.repo.SelectedMedia
import org.junit.Assert.assertEquals
import org.junit.Test

class AudioConvertViewModelTest {
    private val repo = object : AudioConvertRepo {
        override suspend fun convert(
            request: AudioConvertRequest,
            onProgress: (Float) -> Unit,
        ): AudioConvertResult = error("Conversion is not used by binding tests")
    }

    @Test
    fun sameUriWithChangedIdentityRebindsSession() {
        val viewModel = AudioConvertViewModel(repo)
        viewModel.bind(
            SelectedMedia("content://media/audio/1", "old.mp3", MediaKind.AUDIO),
        )
        viewModel.selectFormat(AudioConvertFormat.WAV)

        viewModel.bind(
            SelectedMedia("content://media/audio/1", "renamed.mp3", MediaKind.AUDIO),
        )

        assertEquals("renamed.mp3", viewModel.uiState.value.audioName)
        assertEquals(
            AudioConvertFormat.MP3,
            viewModel.uiState.value.format,
        )
        assertEquals(AudioConvertPhase.Ready, viewModel.uiState.value.phase)
    }
}
