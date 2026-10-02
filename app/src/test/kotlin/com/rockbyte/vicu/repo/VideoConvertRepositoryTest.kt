package com.rockbyte.vicu.repo

import android.net.Uri
import com.rockbyte.vicu.player.PlayerEffect
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito.mock

class VideoConvertRepositoryTest {
    private class Fixture {
        val input: Uri = mock(Uri::class.java)
        val output: Uri = mock(Uri::class.java)
        val events = mutableListOf<String>()
        var source: SourceVideoInfo? = SourceVideoInfo(bitrateKbps = 4000)
        var timeReportsMs: List<Long> = emptyList()
        var probeError: Exception? = null
        var createError: Exception? = null
        var publishError: Exception? = null
        var deleteError: Exception? = null
        var executeError: Exception? = null
        var executeSuccess = true
        var duringExecute: () -> Unit = {}
        lateinit var arguments: List<String>
        val repo: VideoConvertRepo = VideoConvertRepository(
            object : VideoConverter {
                override fun probe(uri: Uri): SourceVideoInfo? {
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
            object : VideoOutputStore {
                override fun create(inputName: String, format: VideoConvertFormat): Uri {
                    events += "create"
                    assertEquals("video.mp4", inputName)
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
            format: VideoConvertFormat = VideoConvertFormat.MP4,
            quality: VideoConvertQuality = VideoConvertQuality.SUITABLE,
            videoFilter: String? = null,
            onProgress: (Float) -> Unit = {},
        ) = repo.convert(
            VideoConvertRequest(input, "video.mp4", format, quality, videoFilter),
            onProgress,
        )
    }

    @Test
    fun trimUsesOutputSeekAndSegmentDurationForProgress() = runBlocking {
        val f = Fixture()
        f.source = SourceVideoInfo(4000, 10000)
        f.timeReportsMs = listOf(1000, 2000, 4500)
        val progress = mutableListOf<Float>()
        val request = VideoConvertRequest(f.input, "video.mp4", VideoConvertFormat.MP4,
            VideoConvertQuality.SUITABLE, trim = PlayerEffect.Trim(3000, 7000))
        assertTrue(f.repo.convert(request, progress::add) is VideoConvertResult.Success)
        assertEquals(listOf(0.25f, 0.5f, 1f), progress)
        assertEquals(listOf("-ss", "3000ms", "-t", "4000ms"),
            f.arguments.drop(f.arguments.indexOf("-ss")).take(4))
        assertTrue(f.arguments.indexOf("-ss") > f.arguments.indexOf("-i"))
        assertEquals(listOf("probe", "create", "execute", "publish"), f.events)
    }

    @Test
    fun invalidTrimNeverCreatesOutput() = runBlocking {
        for (trim in listOf(PlayerEffect.Trim(-1, 500), PlayerEffect.Trim(500, 500),
            PlayerEffect.Trim(600, 500), PlayerEffect.Trim(0, 11000))) {
            val f = Fixture()
            f.source = SourceVideoInfo(4000, 10000)
            val request = VideoConvertRequest(f.input, "video.mp4", VideoConvertFormat.MP4,
                VideoConvertQuality.SUITABLE, trim = trim)
            assertTrue(f.repo.convert(request) is VideoConvertResult.Failure)
            assertFalse(f.events.contains("create"))
            assertFalse(f.events.contains("execute"))
        }
        val f = Fixture()
        f.source = null
        assertTrue(f.repo.convert(VideoConvertRequest(f.input, "video.mp4", VideoConvertFormat.MP4,
            VideoConvertQuality.SUITABLE, trim = PlayerEffect.Trim(0, 1000))) is VideoConvertResult.Failure)
        assertFalse(f.events.contains("create"))
    }

    @Test
    fun successfulConversionProbesOnceAndPublishesOutput() = runBlocking {
        val f = Fixture()
        assertEquals(VideoConvertResult.Success(f.output), f.convert())
        assertEquals(listOf("create", "probe", "execute", "publish"), f.events)
        assertEquals(listOf("-hide_banner", "-i", "input", "-map", "0:v:0", "-map", "0:a:0?",
            "-c:v", "libx264", "-preset", "veryfast", "-crf", "23",
            "-maxrate", "4000k", "-bufsize", "8000k",
            "-c:a", "aac", "-b:a", "128k", "-f", "mp4", "output"), f.arguments)
    }

    @Test
    fun suitableCapsAtSourceBitrateAcrossContainers() = runBlocking {
        for (format in VideoConvertFormat.entries) {
            val f = Fixture()
            assertTrue(f.convert(format) is VideoConvertResult.Success)
            val videoArgs = when (format) {
                VideoConvertFormat.MP4, VideoConvertFormat.MKV, VideoConvertFormat.MOV -> arrayOf(
                    "-c:v", "libx264", "-preset", "veryfast", "-crf", "23",
                    "-maxrate", "4000k", "-bufsize", "8000k")
                VideoConvertFormat.WEBM -> arrayOf(
                    "-c:v", "libvpx-vp9", "-b:v", "0", "-row-mt", "1", "-cpu-used", "4", "-crf", "23",
                    "-maxrate", "4000k", "-bufsize", "8000k")
                VideoConvertFormat.AVI -> arrayOf("-c:v", "mpeg4", "-b:v", "4000k")
            }
            val audioArgs = when (format) {
                VideoConvertFormat.WEBM -> arrayOf("-c:a", "libopus")
                VideoConvertFormat.AVI -> arrayOf("-c:a", "libmp3lame", "-b:a", "128k")
                else -> arrayOf("-c:a", "aac", "-b:a", "128k")
            }
            val container = when (format) {
                VideoConvertFormat.MKV -> "matroska"
                VideoConvertFormat.WEBM -> "webm"
                VideoConvertFormat.AVI -> "avi"
                else -> format.extension
            }
            assertEquals(listOf("-hide_banner", "-i", "input", "-map", "0:v:0", "-map", "0:a:0?",
                *videoArgs, *audioArgs, "-f", container, "output"), f.arguments)
        }
    }

    @Test
    fun videoFilterIsInsertedBeforeEncodingArguments() = runBlocking {
        val f = Fixture()
        assertTrue(f.convert(videoFilter = "crop=w=640:h=480:x=0:y=0") is VideoConvertResult.Success)
        assertEquals(listOf("-hide_banner", "-i", "input", "-map", "0:v:0", "-map", "0:a:0?",
            "-vf", "crop=w=640:h=480:x=0:y=0",
            "-c:v", "libx264", "-preset", "veryfast", "-crf", "23",
            "-maxrate", "4000k", "-bufsize", "8000k",
            "-c:a", "aac", "-b:a", "128k", "-f", "mp4", "output"), f.arguments)
    }

    @Test
    fun blankVideoFilterIsOmitted() = runBlocking {
        val f = Fixture()
        assertTrue(f.convert(videoFilter = "") is VideoConvertResult.Success)
        assertFalse(f.arguments.contains("-vf"))
    }

    @Test
    fun fixedQualityTiersIgnoreSourceBitrate() = runBlocking {
        for (format in VideoConvertFormat.entries) {
            for (quality in listOf(
                VideoConvertQuality.BEST_QUALITY, VideoConvertQuality.BALANCED, VideoConvertQuality.SMALLEST,
            )) {
                val f = Fixture()
                assertTrue(f.convert(format, quality) is VideoConvertResult.Success)
                val videoArgs = when (format) {
                    VideoConvertFormat.MP4, VideoConvertFormat.MKV, VideoConvertFormat.MOV -> arrayOf(
                        "-c:v", "libx264", "-preset", "veryfast", "-crf",
                        when (quality) {
                            VideoConvertQuality.BEST_QUALITY -> "16"
                            VideoConvertQuality.BALANCED -> "23"
                            else -> "34"
                        })
                    VideoConvertFormat.WEBM -> arrayOf(
                        "-c:v", "libvpx-vp9", "-b:v", "0", "-row-mt", "1", "-cpu-used", "4", "-crf",
                        when (quality) {
                            VideoConvertQuality.BEST_QUALITY -> "16"
                            VideoConvertQuality.BALANCED -> "23"
                            else -> "34"
                        })
                    VideoConvertFormat.AVI -> arrayOf(
                        "-c:v", "mpeg4", "-q:v",
                        when (quality) {
                            VideoConvertQuality.BEST_QUALITY -> "2"
                            VideoConvertQuality.BALANCED -> "4"
                            else -> "12"
                        })
                }
                val audioArgs = when (format) {
                    VideoConvertFormat.WEBM -> arrayOf("-c:a", "libopus")
                    VideoConvertFormat.AVI -> arrayOf("-c:a", "libmp3lame", "-b:a", "128k")
                    else -> arrayOf("-c:a", "aac", "-b:a", "128k")
                }
                val container = when (format) {
                    VideoConvertFormat.MKV -> "matroska"
                    VideoConvertFormat.WEBM -> "webm"
                    VideoConvertFormat.AVI -> "avi"
                    else -> format.extension
                }
                assertEquals(listOf("-hide_banner", "-i", "input", "-map", "0:v:0", "-map", "0:a:0?",
                    *videoArgs, *audioArgs, "-f", container, "output"), f.arguments)
                assertFalse(f.arguments.contains("-maxrate"))
            }
        }
    }

    @Test
    fun failedProbeFallsBackToPlainBalancedCrf() = runBlocking {
        val f = Fixture()
        f.probeError = IllegalStateException("probe failed")
        assertTrue(f.convert(VideoConvertFormat.MP4) is VideoConvertResult.Success)
        assertEquals(listOf("-hide_banner", "-i", "input", "-map", "0:v:0", "-map", "0:a:0?",
            "-c:v", "libx264", "-preset", "veryfast", "-crf", "23",
            "-c:a", "aac", "-b:a", "128k", "-f", "mp4", "output"), f.arguments)
    }

    @Test
    fun zeroBitrateSourceIsIgnoredForCap() = runBlocking {
        val f = Fixture()
        f.source = SourceVideoInfo(bitrateKbps = 0)
        assertTrue(f.convert() is VideoConvertResult.Success)
        assertFalse(f.arguments.contains("-maxrate"))
    }

    @Test
    fun progressIsDedupedAndClampedToUnitRange() = runBlocking {
        val f = Fixture()
        f.source = SourceVideoInfo(bitrateKbps = 4000, durationMs = 10_000)
        f.timeReportsMs = listOf(1_000, 1_004, 5_500, 11_000) // 1_004 与 1_000 同属 0.1% 档，应去重；11s 超出封顶为 1
        val progress = mutableListOf<Float>()
        assertTrue(f.convert(onProgress = progress::add) is VideoConvertResult.Success)
        assertEquals(listOf(0.1f, 0.55f, 1f), progress)
    }

    @Test
    fun unknownDurationReportsNoProgress() = runBlocking {
        val f = Fixture()
        f.source = SourceVideoInfo(bitrateKbps = 4000, durationMs = null)
        f.timeReportsMs = listOf(1_000, 5_000)
        val progress = mutableListOf<Float>()
        assertTrue(f.convert(onProgress = progress::add) is VideoConvertResult.Success)
        assertTrue(progress.isEmpty())
    }

    @Test
    fun creationFailureDoesNotExecuteOrDelete() = runBlocking {
        val f = Fixture()
        val error = IllegalStateException("create")
        f.createError = error
        val result = f.convert() as VideoConvertResult.Failure
        assertEquals(VideoConvertError.OutputCreationFailed, result.error)
        assertEquals(error.message, result.cause?.message)
        assertEquals(listOf("create"), f.events)
    }

    @Test
    fun failedEncodingDeletesWithoutPublishing() = runBlocking {
        val f = Fixture()
        f.executeSuccess = false
        val result = f.convert() as VideoConvertResult.Failure
        assertEquals(VideoConvertError.TranscodeFailed, result.error)
        assertEquals(listOf("create", "probe", "execute", "delete"), f.events)
    }

    @Test
    fun failedPublicationDeletesAndPreservesCause() = runBlocking {
        val f = Fixture()
        val error = IllegalStateException("publish")
        f.publishError = error
        val result = f.convert() as VideoConvertResult.Failure
        assertEquals(VideoConvertError.Unknown, result.error)
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
        val result = f.convert() as VideoConvertResult.Failure
        assertEquals(VideoConvertError.TranscodeFailed, result.error)
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
