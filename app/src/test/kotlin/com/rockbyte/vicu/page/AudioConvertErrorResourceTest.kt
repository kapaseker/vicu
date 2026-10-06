package com.rockbyte.vicu.page

import com.rockbyte.vicu.R
import com.rockbyte.vicu.repo.AudioConvertError
import org.junit.Assert.assertEquals
import org.junit.Test

class AudioConvertErrorResourceTest {

    @Test
    fun `each convert error maps to its UI string resource`() {
        assertEquals(R.string.audio_transcode_failed, AudioConvertError.TranscodeFailed.messageRes)
        assertEquals(R.string.cannot_create_conversion_file, AudioConvertError.OutputCreationFailed.messageRes)
        assertEquals(R.string.unknown_error, AudioConvertError.Unknown.messageRes)
    }

}
