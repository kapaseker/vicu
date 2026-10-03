package com.rockbyte.vicu.repo

import org.junit.Assert.*
import org.junit.Test

class FFmpegAudioEncoderTest {
    @Test
    fun probeUsesKeyValueWriterForVersionIndependentParsing() {
        assertArrayEquals(
            arrayOf(
                "-v", "error", "-select_streams", "a:0",
                "-show_entries", "stream=codec_name,bit_rate,sample_fmt:format=duration,format_name:format_tags=major_brand",
                "-of", "default=nokey=0:noprint_wrappers=1", "input",
            ),
            probeAudioArguments("input"),
        )
    }

    @Test
    fun parsesCodecBitrateAndDuration() {
        // 真机 ffprobe 实际输出采样
        assertEquals(
            SourceAudioInfo("aac", 69, durationMs = 6000),
            parseAudioProbeOutput("codec_name=aac\nbit_rate=69447\nduration=6.000000"),
        )
    }

    @Test
    fun naBitrateYieldsNull() {
        assertEquals(
            SourceAudioInfo("mp3", null, durationMs = 6000),
            parseAudioProbeOutput("codec_name=mp3\nbit_rate=N/A\nduration=6.000000"),
        )
    }

    @Test
    fun parsesSampleFmtForWavBitDepth() {
        assertEquals(
            SourceAudioInfo("flac", 900, sampleFmt = "s32"),
            parseAudioProbeOutput("codec_name=flac\nbit_rate=900000\nsample_fmt=s32\nduration=N/A"),
        )
        assertNull(parseAudioProbeOutput("codec_name=flac\nsample_fmt=N/A")?.sampleFmt)
    }

    @Test
    fun missingOrInvalidDurationYieldsNull() {
        assertNull(parseAudioProbeOutput("codec_name=aac\nbit_rate=69447\nduration=N/A")?.durationMs)
        assertNull(parseAudioProbeOutput("codec_name=aac\nbit_rate=69447\nduration=0")?.durationMs)
        assertNull(parseAudioProbeOutput("codec_name=aac\nbit_rate=69447")?.durationMs)
    }

    @Test
    fun containerIsParsedAndNonFiniteDurationIsRejected() {
        val info = parseAudioProbeOutput("codec_name=opus\nformat_name=ogg\nduration=6.000")!!
        assertEquals("ogg", info.containerName)
        assertEquals(6000L, info.durationMs)
        assertEquals("M4A", parseAudioProbeOutput("codec_name=aac\nTAG:major_brand=M4A ")!!.containerBrand)
        assertNull(parseAudioProbeOutput("codec_name=aac\nduration=Infinity")?.durationMs)
    }

    @Test
    fun packetOffsetsAreParsedAndEmptyOrUnseekablePacketsAreRejected() {
        assertEquals(listOf(AudioPacketInfo(8288, 512), AudioPacketInfo(8800, 400)), parseAudioPackets(
            "[PACKET]\nsize=512\npos=8288\n[/PACKET]\n[PACKET]\npos=8800\nsize=400\n[/PACKET]"))
        for (output in listOf("", "[PACKET]\npos=N/A\nsize=10\n[/PACKET]", "[PACKET]\npos=1\nsize=0\n[/PACKET]")) {
            try { parseAudioPackets(output); fail("Expected packet rejection") } catch (_: IllegalStateException) { }
        }
    }

    @Test
    fun emptyOutputYieldsNull() {
        assertNull(parseAudioProbeOutput(""))
        assertNull(parseAudioProbeOutput("\n"))
        assertNull(parseAudioProbeOutput("codec_name=N/A\nbit_rate=N/A\nduration=N/A"))
    }
}
