package com.rockbyte.vicu.repo

import org.junit.Assert.assertEquals
import org.junit.Test

class AudioExportTargetTest {

    @Test
    fun `original best copies aac source`() {
        assertEquals(
            EncodeTarget.Copy,
            resolveTarget(AudioExportFormat.ORIGINAL, AudioExportQuality.BEST, SourceAudioInfo("aac", 128)),
        )
    }

    @Test
    fun `original best still copies when probe failed`() {
        assertEquals(
            EncodeTarget.Copy,
            resolveTarget(AudioExportFormat.ORIGINAL, AudioExportQuality.BEST, null),
        )
    }

    @Test
    fun `original best re-encodes non aac source at its bitrate`() {
        assertEquals(
            EncodeTarget.Bitrate(160),
            resolveTarget(AudioExportFormat.ORIGINAL, AudioExportQuality.BEST, SourceAudioInfo("ac3", 160)),
        )
    }

    @Test
    fun `best falls back to 320k when source bitrate unknown`() {
        assertEquals(
            EncodeTarget.Bitrate(320),
            resolveTarget(AudioExportFormat.MP3, AudioExportQuality.BEST, SourceAudioInfo("aac", null)),
        )
    }

    @Test
    fun `best caps at 320k for high bitrate source`() {
        assertEquals(
            EncodeTarget.Bitrate(320),
            resolveTarget(AudioExportFormat.M4A, AudioExportQuality.BEST, SourceAudioInfo("pcm_s16le", 4608)),
        )
    }

    @Test
    fun `best matches source bitrate when lower than 320`() {
        assertEquals(
            EncodeTarget.Bitrate(96),
            resolveTarget(AudioExportFormat.MP3, AudioExportQuality.BEST, SourceAudioInfo("aac", 96)),
        )
    }

    @Test
    fun `fixed tiers clamp to source bitrate`() {
        assertEquals(
            EncodeTarget.Bitrate(96),
            resolveTarget(AudioExportFormat.MP3, AudioExportQuality.HIGH, SourceAudioInfo("aac", 96)),
        )
        assertEquals(
            EncodeTarget.Bitrate(192),
            resolveTarget(AudioExportFormat.M4A, AudioExportQuality.MEDIUM, SourceAudioInfo("aac", 256)),
        )
        assertEquals(
            EncodeTarget.Bitrate(320),
            resolveTarget(AudioExportFormat.M4A, AudioExportQuality.HIGH, null),
        )
        assertEquals(
            EncodeTarget.Bitrate(128),
            resolveTarget(AudioExportFormat.MP3, AudioExportQuality.LOW, null),
        )
    }
}
