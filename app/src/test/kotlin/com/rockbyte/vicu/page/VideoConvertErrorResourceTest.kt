package com.rockbyte.vicu.page

import com.rockbyte.vicu.R
import com.rockbyte.vicu.repo.VideoConvertError
import org.junit.Assert.assertEquals
import org.junit.Test

class VideoConvertErrorResourceTest {

    @Test
    fun `each convert error maps to its UI string resource`() {
        assertEquals(R.string.convert_error_transcode, VideoConvertError.TranscodeFailed.messageRes)
        assertEquals(R.string.cannot_create_conversion_file, VideoConvertError.OutputCreationFailed.messageRes)
        assertEquals(R.string.unknown_error, VideoConvertError.Unknown.messageRes)
    }

}
