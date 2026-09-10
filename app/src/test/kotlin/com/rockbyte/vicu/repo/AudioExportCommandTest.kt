package com.rockbyte.vicu.repo

import org.junit.Assert.assertArrayEquals
import org.junit.Test

class AudioExportCommandTest {

    @Test
    fun `copies the first audio stream into an m4a container`() {
        assertArrayEquals(
            arrayOf(
                "-hide_banner",
                "-i",
                "ffkitsaf:input.mp4",
                "-map",
                "0:a:0",
                "-vn",
                "-c:a",
                "copy",
                "-f",
                "ipod",
                "ffkitsaf:output.m4a",
            ),
            AudioExportCommand.build(
                inputUrl = "ffkitsaf:input.mp4",
                outputUrl = "ffkitsaf:output.m4a",
                format = AudioExportFormat.ORIGINAL,
                target = EncodeTarget.Copy,
            )
        )
    }

    @Test
    fun `encodes mp3 with libmp3lame at the target bitrate`() {
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
                "ffkitsaf:output.mp3",
            ),
            AudioExportCommand.build(
                inputUrl = "ffkitsaf:input.mp4",
                outputUrl = "ffkitsaf:output.mp3",
                format = AudioExportFormat.MP3,
                target = EncodeTarget.Bitrate(192),
            )
        )
    }

    @Test
    fun `encodes aac at the target bitrate for m4a`() {
        assertArrayEquals(
            arrayOf(
                "-hide_banner",
                "-i",
                "ffkitsaf:input.mp4",
                "-map",
                "0:a:0",
                "-vn",
                "-c:a",
                "aac",
                "-b:a",
                "128k",
                "-f",
                "ipod",
                "ffkitsaf:output.m4a",
            ),
            AudioExportCommand.build(
                inputUrl = "ffkitsaf:input.mp4",
                outputUrl = "ffkitsaf:output.m4a",
                format = AudioExportFormat.M4A,
                target = EncodeTarget.Bitrate(128),
            )
        )
    }
}
