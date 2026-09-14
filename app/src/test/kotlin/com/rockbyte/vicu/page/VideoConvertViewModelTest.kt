package com.rockbyte.vicu.page

import com.rockbyte.vicu.repo.MediaKind
import com.rockbyte.vicu.repo.SelectedMedia
import com.rockbyte.vicu.repo.VideoConvertFormat
import com.rockbyte.vicu.repo.VideoConvertRepo
import com.rockbyte.vicu.repo.VideoConvertRequest
import com.rockbyte.vicu.repo.VideoConvertResult
import org.junit.Assert.assertEquals
import org.junit.Test

class VideoConvertViewModelTest {
    private val repo = object : VideoConvertRepo {
        override suspend fun convert(
            request: VideoConvertRequest,
            onProgress: (Float) -> Unit,
        ): VideoConvertResult = error("Conversion is not used by binding tests")
    }

    @Test
    fun sameUriWithChangedIdentityRebindsSession() {
        val viewModel = VideoConvertViewModel(repo)
        viewModel.bind(
            SelectedMedia("content://media/video/1", "old.mp4", MediaKind.VIDEO),
        )
        viewModel.selectFormat(VideoConvertFormat.WEBM)

        viewModel.bind(
            SelectedMedia("content://media/video/1", "renamed.mp4", MediaKind.VIDEO),
        )

        assertEquals("renamed.mp4", viewModel.uiState.value.videoName)
        assertEquals(
            VideoConvertFormat.MP4,
            viewModel.uiState.value.format,
        )
        assertEquals(ConvertPhase.Ready, viewModel.uiState.value.phase)
    }
}
