package com.rockbyte.vicu.repo

import org.junit.Assert.*
import org.junit.Test

class FFmpegVideoConverterTest {
    @Test
    fun probeUsesKeyValueWriterForVersionIndependentParsing() {
        assertArrayEquals(
            arrayOf(
                "-v", "error", "-select_streams", "v:0",
                "-show_entries", "stream=bit_rate:format=bit_rate,duration",
                "-of", "default=nokey=0:noprint_wrappers=1", "input",
            ),
            probeArguments("input"),
        )
    }

    @Test
    fun parsesStreamBitrateAndContainerDuration() {
        // 真机 ffprobe 实际输出采样：format 行 duration 在 bit_rate 之前
        assertEquals(
            SourceVideoInfo(bitrateKbps = 30, durationMs = 6000),
            parseProbeOutput("bit_rate=30696\nduration=6.000000\nbit_rate=105878"),
        )
    }

    @Test
    fun streamNaFallsBackToContainerBitrate() {
        // 回归：视频流 N/A（如 MKV）时须回退容器总码率，而非音频码率（select_streams 已排除音频行）
        assertEquals(
            SourceVideoInfo(bitrateKbps = 863, durationMs = 6000),
            parseProbeOutput("bit_rate=N/A\nduration=6.000000\nbit_rate=863042"),
        )
    }

    @Test
    fun containerDurationInvalidOrMissingYieldsNull() {
        assertNull(parseProbeOutput("bit_rate=30696\nduration=N/A\nbit_rate=105878").durationMs)
        assertNull(parseProbeOutput("bit_rate=30696\nduration=0\nbit_rate=105878").durationMs)
        assertNull(parseProbeOutput("bit_rate=30696\nduration=abc\nbit_rate=105878").durationMs)
        assertNull(parseProbeOutput("bit_rate=30696\nbit_rate=105878").durationMs)
    }

    @Test
    fun allInvalidYieldsNull() {
        assertNull(parseProbeOutput("bit_rate=N/A\nduration=N/A\nbit_rate=N/A").bitrateKbps)
        assertNull(parseProbeOutput("").bitrateKbps)
        assertNull(parseProbeOutput("bit_rate=0\nduration=0\nbit_rate=0").bitrateKbps)
    }
}
