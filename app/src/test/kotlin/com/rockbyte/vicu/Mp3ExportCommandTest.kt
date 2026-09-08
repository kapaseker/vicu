package com.rockbyte.vicu

import org.junit.Assert.assertArrayEquals
import org.junit.Test

class Mp3ExportCommandTest {

    @Test
    fun `extracts the first audio stream as a 192k mp3`() {
        assertArrayEquals(
            arrayOf(
                "-hide_banner",
                "-i",
                "ffkitsaf:input.mp4",
                "-map",
                "0:a:0",
                "-vn",
                "-c:a",
                "libmp3lame",
                "-b:a",
                "192k",
                "-f",
                "mp3",
                "ffkitsaf:output.mp3"
            ),
            Mp3ExportCommand.build("ffkitsaf:input.mp4", "ffkitsaf:output.mp3")
        )
    }
}
