package com.rockbyte.vicu.repo

import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
import java.nio.file.StandardOpenOption.*

class FlacStreamCopyTest {
    private fun packet(number: Long, variable: Boolean = false): ByteArray {
        val header = byteArrayOf(0xff.toByte(), (if (variable) 0xf9 else 0xf8).toByte(), 0x79, 0x08) +
            flacNumber(number) + byteArrayOf(0x11, 0xff.toByte()) // 4608 samples
        val withHeaderCrc = header + flacCrc8(header).toByte()
        val body = withHeaderCrc + byteArrayOf(0x10, 0x20, 0x30, 0x40)
        val crc = flacCrc16(body)
        return body + byteArrayOf((crc shr 8).toByte(), crc.toByte())
    }
    @Test fun rebasesFixedAndVariableFrameNumbersWithoutChangingCompressedPayload() {
        for (variable in listOf(false, true)) {
            val original = packet(if (variable) 100000 else 128, variable)
            val normalized = normalizeFlacFrame(original, 0, 0)
            assertEquals(4608, normalized.samples)
            assertArrayEquals(packet(0, variable), normalized.bytes)
            assertEquals(0, flacCrc16(normalized.bytes))
            val second = normalizeFlacFrame(original, 1, 4608)
            assertArrayEquals(packet(if (variable) 4608 else 1, variable), second.bytes)
        }
    }
    @Test fun invalidPacketChecksumsAreRejected() {
        val corrupt = packet(5); corrupt[corrupt.lastIndex] = 0
        try { normalizeFlacFrame(corrupt, 0, 0); fail("Expected corrupt packet rejection") }
        catch (_: IllegalArgumentException) { }
    }
    @Test fun streamInfoGetsActualSampleCountAndClearsObsoleteChecksum() {
        val input = Files.createTempFile("flac-input", ".flac")
        val output = Files.createTempFile("flac-output", ".flac")
        try {
            val info = ByteArray(34) { 0x11 }
            // 48kHz, mono, 16-bit, originally 6 seconds.
            val packed = (48000L shl 44) or (15L shl 36) or 288000L
            for (i in 0..7) info[10 + i] = (packed shr ((7-i)*8)).toByte()
            val metadata = "fLaC".toByteArray(Charsets.US_ASCII) + byteArrayOf(0x80.toByte(), 0, 0, 34) + info
            val first = packet(16); val second = packet(17)
            Files.write(input, metadata + first + second)
            Files.newByteChannel(input, READ).use { source ->
                Files.newByteChannel(output, WRITE, TRUNCATE_EXISTING).use { target ->
                    normalizeFlacStream(source, listOf(AudioPacketInfo(42, first.size),
                        AudioPacketInfo(42L + first.size, second.size)), target)
                }
            }
            val result = Files.readAllBytes(output)
            var samples = result[21].toLong() and 15
            for (i in 22..25) samples = (samples shl 8) or (result[i].toLong() and 255)
            assertEquals(9216L, samples)
            assertTrue(result.sliceArray(26..41).all { it == 0.toByte() })
            assertArrayEquals(packet(0) + packet(1), result.copyOfRange(42, result.size))
        } finally { Files.deleteIfExists(input); Files.deleteIfExists(output) }
    }
}
