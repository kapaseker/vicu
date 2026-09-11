package com.rockbyte.vicu.page

import com.rockbyte.vicu.R
import com.rockbyte.vicu.repo.AudioExportError
import com.rockbyte.vicu.repo.AudioExportException
import org.junit.Assert.assertEquals
import org.junit.Test

class AudioExportErrorResourceTest {

    @Test
    fun `each export error maps to its UI string resource`() {
        assertEquals(R.string.export_error_transcode, AudioExportError.TranscodeFailed.messageRes)
        assertEquals(R.string.export_error_output_creation, AudioExportError.OutputCreationFailed.messageRes)
        assertEquals(R.string.export_error_unknown, AudioExportError.Unknown.messageRes)
    }

    @Test
    fun `view model preserves repo error type and normalizes unexpected failures`() {
        assertEquals(
            AudioExportError.OutputCreationFailed,
            AudioExportException(AudioExportError.OutputCreationFailed).toAudioExportError(),
        )
        assertEquals(AudioExportError.Unknown, IllegalStateException().toAudioExportError())
    }
}
