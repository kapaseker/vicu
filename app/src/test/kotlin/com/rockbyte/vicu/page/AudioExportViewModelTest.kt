package com.rockbyte.vicu.page

import com.rockbyte.vicu.repo.AudioExportRepo
import com.rockbyte.vicu.repo.AudioExportRequest
import com.rockbyte.vicu.repo.AudioExportResult
import com.rockbyte.vicu.repo.AudioExportFormat
import com.rockbyte.vicu.repo.MediaKind
import com.rockbyte.vicu.repo.SelectedMedia
import org.junit.Assert.assertEquals
import org.junit.Test

class AudioExportViewModelTest {
    private val repo = object : AudioExportRepo {
        override suspend fun export(request: AudioExportRequest): AudioExportResult =
            error("Export is not used by binding tests")
    }

    @Test
    fun sameUriWithChangedIdentityRebindsSession() {
        val viewModel = AudioExportViewModel(repo)
        viewModel.bind(
            SelectedMedia("content://media/video/1", "old.mp4", MediaKind.VIDEO),
        )
        viewModel.selectFormat(AudioExportFormat.MP3)

        viewModel.bind(
            SelectedMedia("content://media/video/1", "renamed.mp4", MediaKind.VIDEO),
        )

        assertEquals("renamed.mp4", viewModel.uiState.value.videoName)
        assertEquals(
            AudioExportFormat.ORIGINAL,
            viewModel.uiState.value.format,
        )
        assertEquals(ExportPhase.Ready, viewModel.uiState.value.phase)
    }
}
