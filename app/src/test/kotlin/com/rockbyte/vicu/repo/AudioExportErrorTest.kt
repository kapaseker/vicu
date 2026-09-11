package com.rockbyte.vicu.repo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AudioExportErrorTest {

    @Test
    fun `export exception carries structured error without user-facing message`() {
        val exception = AudioExportException(AudioExportError.TranscodeFailed)

        assertEquals(AudioExportError.TranscodeFailed, exception.error)
        assertNull(exception.message)
    }
}
