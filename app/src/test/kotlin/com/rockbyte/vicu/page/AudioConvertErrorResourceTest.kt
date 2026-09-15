package com.rockbyte.vicu.page

import com.rockbyte.vicu.R
import com.rockbyte.vicu.repo.AudioConvertError
import org.junit.Assert.assertEquals
import org.junit.Test

class AudioConvertErrorResourceTest {

    @Test
    fun `each convert error maps to its UI string resource`() {
        assertEquals(R.string.audio_convert_error_transcode, AudioConvertError.TranscodeFailed.messageRes)
        assertEquals(R.string.audio_convert_error_output_creation, AudioConvertError.OutputCreationFailed.messageRes)
        assertEquals(R.string.audio_convert_error_unknown, AudioConvertError.Unknown.messageRes)
    }

}
