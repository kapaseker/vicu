package com.rockbyte.vicu.repo

import android.net.Uri
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito.mock

class AudioWaveformRepositoryTest {
    @Test fun oppositePhaseChannelsKeepTheirPeakAndSilenceStaysSilent() = runBlocking {
        val samples = FloatArray(40)
        samples[0] = 0.8f; samples[1] = -0.8f
        val result = load(samples, channels = 2)
        assertArrayEquals(floatArrayOf(0.8f, 0f), result.peaks, 0.0001f)
        assertEquals(10.0, result.bucketDurationMs, 0.0001)
        assertEquals(20.0, result.durationMs, 0.0001)
    }

    @Test fun arbitraryChunkBoundariesPreserveSamplesAndPartialFinalBucket() = runBlocking {
        val samples = FloatArray(25) { if (it == 24) -0.95f else 0.2f }
        val result = load(samples, chunkSize = 7)
        assertArrayEquals(floatArrayOf(0.2f, 0.2f, 0.95f), result.peaks, 0.0001f)
        assertEquals(25.0, result.durationMs, 0.0001)
    }

    @Test fun invalidAmplitudesDoNotPoisonTheDisplayAndFullScaleIsFixed() = runBlocking {
        val result = load(floatArrayOf(Float.NaN, Float.POSITIVE_INFINITY, -2f, 0.25f))
        assertArrayEquals(floatArrayOf(1f), result.peaks, 0f)
        assertArrayEquals(floatArrayOf(0.25f), load(floatArrayOf(0.25f)).peaks, 0f)
    }

    @Test fun underestimatedDurationCompactsPeaksWithoutDroppingTheTail() = runBlocking {
        val store = object : AudioWaveformStore {
            override suspend fun decode(uri: Uri, onFormat: (AudioPcmFormat) -> Unit, onChunk: (ByteArray) -> Unit) {
                onFormat(AudioPcmFormat(1000, 1))
                val silence = pcm(FloatArray(10000))
                repeat(360) { onChunk(silence) }
                onChunk(pcm(floatArrayOf(0.9f)))
            }
        }
        val result = AudioWaveformRepository(store).load(mock(Uri::class.java), 10).getOrThrow()
        assertTrue(result.peaks.size <= 360000)
        assertEquals(20.0, result.bucketDurationMs, 0.0001)
        assertEquals(0.9f, result.peaks.last(), 0f)
        assertEquals(3600001.0, result.durationMs, 0.0001)
    }

    @Test fun truncatedPcmAndDecodeFailureReturnFailure() = runBlocking {
        val store = object : AudioWaveformStore {
            override suspend fun decode(uri: Uri, onFormat: (AudioPcmFormat) -> Unit, onChunk: (ByteArray) -> Unit) {
                onFormat(AudioPcmFormat(1000, 2)); onChunk(pcm(floatArrayOf(0.5f)))
            }
        }
        assertTrue(AudioWaveformRepository(store).load(mock(Uri::class.java), 10).isFailure)
    }

    @Test fun cancellationPropagatesAndRunsDecoderCleanup() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        var cleaned = false
        val store = object : AudioWaveformStore {
            override suspend fun decode(uri: Uri, onFormat: (AudioPcmFormat) -> Unit, onChunk: (ByteArray) -> Unit) {
                try { entered.complete(Unit); awaitCancellation() } finally { cleaned = true }
            }
        }
        val job = launch { AudioWaveformRepository(store).load(mock(Uri::class.java), 1000); fail("cancel must propagate") }
        entered.await(); job.cancelAndJoin()
        assertTrue(cleaned)
    }

    private suspend fun load(samples: FloatArray, channels: Int = 1, chunkSize: Int = 64): AudioWaveform {
        val bytes = pcm(samples)
        val store = object : AudioWaveformStore {
            override suspend fun decode(uri: Uri, onFormat: (AudioPcmFormat) -> Unit, onChunk: (ByteArray) -> Unit) {
                onFormat(AudioPcmFormat(1000, channels))
                for (start in bytes.indices step chunkSize) onChunk(bytes.copyOfRange(start, minOf(start + chunkSize, bytes.size)))
            }
        }
        return AudioWaveformRepository(store).load(mock(Uri::class.java), 100).getOrThrow()
    }

    private fun pcm(samples: FloatArray): ByteArray = ByteBuffer.allocate(samples.size * 4)
        .order(ByteOrder.LITTLE_ENDIAN).apply { samples.forEach { putFloat(it) } }.array()
}
