package com.rockbyte.vicu.repo

import android.net.Uri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito.mock

class AudioConvertRepositoryTest {
    private class Fixture {
        val input: Uri = mock(Uri::class.java)
        val output: Uri = mock(Uri::class.java)
        val events = mutableListOf<String>()
        var source: SourceAudioInfo? = SourceAudioInfo(codec = "mp3", bitrateKbps = 320)
        var timeReportsMs: List<Long> = emptyList()
        var probeError: Exception? = null
        var createError: Exception? = null
        var publishError: Exception? = null
        var deleteError: Exception? = null
        var executeError: Exception? = null
        var executeSuccess = true
        var duringExecute: () -> Unit = {}
        lateinit var arguments: List<String>
        val repo: AudioConvertRepo = AudioConvertRepository(
            object : AudioEncoder {
                override fun probe(uri: Uri): SourceAudioInfo? {
                    assertSame(input, uri)
                    events += "probe"
                    probeError?.let { throw it }
                    return source
                }
                override fun inputUrl(uri: Uri): String = "input"
                override fun outputUrl(uri: Uri): String = "output"
                override fun execute(arguments: Array<String>, onTimeMs: (Long) -> Unit): Boolean {
                    events += "execute"
                    this@Fixture.arguments = arguments.toList()
                    timeReportsMs.forEach(onTimeMs)
                    duringExecute()
                    executeError?.let { throw it }
                    return executeSuccess
                }
            },
            object : AudioConvertStore {
                override fun create(inputName: String, format: AudioConvertFormat): Uri {
                    events += "create"
                    assertEquals("audio.mp3", inputName)
                    createError?.let { throw it }
                    return output
                }
                override fun publish(uri: Uri) {
                    assertSame(output, uri)
                    events += "publish"
                    publishError?.let { throw it }
                }
                override fun delete(uri: Uri) {
                    assertSame(output, uri)
                    events += "delete"
                    deleteError?.let { throw it }
                }
            },
        )
        suspend fun convert(
            format: AudioConvertFormat = AudioConvertFormat.MP3,
            quality: AudioConvertQuality = AudioConvertQuality.SUITABLE,
            onProgress: (Float) -> Unit = {},
        ) = repo.convert(AudioConvertRequest(input, "audio.mp3", format, quality), onProgress)
    }

    @Test
    fun successfulConversionProbesOnceAndPublishesOutput() = runBlocking {
        val f = Fixture()
        assertEquals(AudioConvertResult.Success(f.output), f.convert())
        assertEquals(listOf("create", "probe", "execute", "publish"), f.events)
        assertEquals(
            listOf("-hide_banner", "-i", "input", "-map", "0:a:0", "-vn",
                "-c:a", "libmp3lame", "-b:a", "192k", "-f", "mp3", "output"),
            f.arguments,
        )
    }

    @Test
    fun suitableCapsAtSourceBitrateForLossyFormats() = runBlocking {
        for (format in listOf(AudioConvertFormat.MP3, AudioConvertFormat.M4A, AudioConvertFormat.OGG)) {
            val f = Fixture()
            assertTrue(f.convert(format) is AudioConvertResult.Success)
            val codecArgs = when (format) {
                AudioConvertFormat.MP3 -> arrayOf("-c:a", "libmp3lame", "-b:a", "192k")
                AudioConvertFormat.M4A -> arrayOf("-c:a", "aac", "-b:a", "192k")
                else -> arrayOf("-c:a", "libopus", "-b:a", "192k")
            }
            assertEquals(
                listOf("-hide_banner", "-i", "input", "-map", "0:a:0", "-vn",
                    *codecArgs, "-f", format.extension, "output"),
                f.arguments,
            )
        }
    }

    @Test
    fun suitableFallsBackTo192kWhenProbeFailsOrBitrateIsUnknown() = runBlocking {
        for (source in listOf(null, SourceAudioInfo(codec = "mp3", bitrateKbps = null))) {
            val f = Fixture()
            f.source = source
            assertTrue(f.convert() is AudioConvertResult.Success)
            assertTrue(f.arguments.contains("192k"))
        }
    }

    @Test
    fun fixedQualityTiersIgnoreSourceBitrate() = runBlocking {
        for (quality in listOf(
            AudioConvertQuality.BEST_QUALITY, AudioConvertQuality.BALANCED, AudioConvertQuality.SMALLEST,
        )) {
            val f = Fixture()
            assertTrue(f.convert(AudioConvertFormat.MP3, quality) is AudioConvertResult.Success)
            assertEquals(
                listOf("-hide_banner", "-i", "input", "-map", "0:a:0", "-vn",
                    "-c:a", "libmp3lame", "-b:a",
                    when (quality) {
                        AudioConvertQuality.BEST_QUALITY -> "320k"
                        AudioConvertQuality.BALANCED -> "192k"
                        else -> "128k"
                    },
                    "-f", "mp3", "output"),
                f.arguments,
            )
        }
    }

    @Test
    fun losslessFormatsOmitBitrateArguments() = runBlocking {
        for (format in listOf(AudioConvertFormat.WAV, AudioConvertFormat.FLAC)) {
            val f = Fixture()
            assertTrue(f.convert(format) is AudioConvertResult.Success)
            val codecArgs = when (format) {
                AudioConvertFormat.WAV -> arrayOf("-c:a", "pcm_s16le")
                else -> arrayOf("-c:a", "flac")
            }
            assertEquals(
                listOf("-hide_banner", "-i", "input", "-map", "0:a:0", "-vn",
                    *codecArgs, "-f", format.extension, "output"),
                f.arguments,
            )
            assertFalse(f.arguments.contains("-b:a"))
        }
    }

    @Test
    fun wavBitDepthFollowsSourceSampleFmt() = runBlocking {
        for (sampleFmt in listOf("s32", "s32p", "flt", "fltp", "dbl", "dblp", null)) {
            val f = Fixture()
            f.source = SourceAudioInfo(codec = "flac", bitrateKbps = 900, sampleFmt = sampleFmt)
            assertTrue(f.convert(AudioConvertFormat.WAV) is AudioConvertResult.Success)
            val expected = when (sampleFmt) {
                "s32", "s32p" -> "pcm_s32le"
                "flt", "fltp" -> "pcm_f32le"
                "dbl", "dblp" -> "pcm_f64le"
                else -> "pcm_s16le" // 探测失败回退 16 位
            }
            assertEquals(expected, f.arguments[f.arguments.indexOf("-c:a") + 1])
        }
    }

    @Test
    fun progressIsDedupedAndClampedToUnitRange() = runBlocking {
        val f = Fixture()
        f.source = SourceAudioInfo(codec = "mp3", bitrateKbps = 320, durationMs = 10_000)
        f.timeReportsMs = listOf(1_000, 1_004, 5_500, 11_000) // 1_004 与 1_000 同属 0.1% 档，应去重；11s 超出封顶为 1
        val progress = mutableListOf<Float>()
        assertTrue(f.convert(onProgress = progress::add) is AudioConvertResult.Success)
        assertEquals(listOf(0.1f, 0.55f, 1f), progress)
    }

    @Test
    fun unknownDurationReportsNoProgress() = runBlocking {
        val f = Fixture()
        f.source = SourceAudioInfo(codec = "mp3", bitrateKbps = 320, durationMs = null)
        f.timeReportsMs = listOf(1_000, 5_000)
        val progress = mutableListOf<Float>()
        assertTrue(f.convert(onProgress = progress::add) is AudioConvertResult.Success)
        assertTrue(progress.isEmpty())
    }

    @Test
    fun creationFailureDoesNotExecuteOrDelete() = runBlocking {
        val f = Fixture()
        val error = IllegalStateException("create")
        f.createError = error
        val result = f.convert() as AudioConvertResult.Failure
        assertEquals(AudioConvertError.OutputCreationFailed, result.error)
        assertEquals(error.message, result.cause?.message)
        assertEquals(listOf("create"), f.events)
    }

    @Test
    fun failedEncodingDeletesWithoutPublishing() = runBlocking {
        val f = Fixture()
        f.executeSuccess = false
        val result = f.convert() as AudioConvertResult.Failure
        assertEquals(AudioConvertError.TranscodeFailed, result.error)
        assertEquals(listOf("create", "probe", "execute", "delete"), f.events)
    }

    @Test
    fun failedPublicationDeletesAndPreservesCause() = runBlocking {
        val f = Fixture()
        val error = IllegalStateException("publish")
        f.publishError = error
        val result = f.convert() as AudioConvertResult.Failure
        assertEquals(AudioConvertError.Unknown, result.error)
        assertEquals(error.message, result.cause?.message)
        assertEquals(listOf("create", "probe", "execute", "publish", "delete"), f.events)
    }

    @Test
    fun cleanupFailureDoesNotReplaceEncodingFailure() = runBlocking {
        val f = Fixture()
        val error = IllegalStateException("execute")
        val cleanup = IllegalStateException("delete")
        f.executeError = error
        f.deleteError = cleanup
        val result = f.convert() as AudioConvertResult.Failure
        assertEquals(AudioConvertError.TranscodeFailed, result.error)
        assertEquals(error.message, result.cause?.message)
        assertSame(cleanup, result.cause?.suppressed?.single())
    }

    @Test
    fun probeCancellationRollsBackAndPropagates() = runBlocking {
        val f = Fixture()
        f.probeError = CancellationException("cancel")
        try {
            f.convert()
            fail("Expected cancellation")
        } catch (_: CancellationException) {
            assertEquals(listOf("create", "probe", "delete"), f.events)
        }
    }

    @Test
    fun cancellationWhileConvertingWaitsForWriterThenRollsBack() = runBlocking {
        val f = Fixture()
        val started = CompletableDeferred<Unit>()
        val release = java.util.concurrent.CountDownLatch(1)
        f.duringExecute = {
            started.complete(Unit)
            check(release.await(5, java.util.concurrent.TimeUnit.SECONDS))
        }
        val convert = async { f.convert() }
        started.await()
        convert.cancel()
        release.countDown()
        try {
            convert.await()
            fail("Expected cancellation")
        } catch (_: CancellationException) {
            convert.join()
            assertEquals(listOf("create", "probe", "execute", "delete"), f.events)
        }
    }
}
