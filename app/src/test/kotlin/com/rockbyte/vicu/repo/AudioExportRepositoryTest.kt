package com.rockbyte.vicu.repo

import android.net.Uri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito.mock

class AudioExportRepositoryTest {
    private class Fixture {
        val input: Uri = mock(Uri::class.java)
        val output: Uri = mock(Uri::class.java)
        val events = mutableListOf<String>()
        var source: SourceAudioInfo? = SourceAudioInfo("aac", 256)
        var probeError: Exception? = null
        var createError: Exception? = null
        var publishError: Exception? = null
        var deleteError: Exception? = null
        var executeError: Exception? = null
        var executeSuccess = true
        var duringExecute: () -> Unit = {}
        lateinit var arguments: List<String>
        val repo: AudioExportRepo = AudioExportRepository(
            object : AudioEncoder {
                override fun probe(uri: Uri): SourceAudioInfo? {
                    assertSame(input, uri)
                    events += "probe"
                    probeError?.let { throw it }
                    return source
                }
                override fun inputUrl(uri: Uri): String = "input"
                override fun outputUrl(uri: Uri): String = "output"
                override fun execute(arguments: Array<String>): Boolean {
                    events += "execute"
                    this@Fixture.arguments = arguments.toList()
                    duringExecute()
                    executeError?.let { throw it }
                    return executeSuccess
                }
            },
            object : AudioOutputStore {
                override fun create(inputName: String, format: AudioExportFormat): Uri {
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
        suspend fun export(
            format: AudioExportFormat = AudioExportFormat.ORIGINAL,
            quality: AudioExportQuality = AudioExportQuality.BEST,
        ) = repo.export(AudioExportRequest(input, "video.mp4", format, quality))
    }

    @Test fun successfulExportProbesOnceAndPublishesOutput() = runBlocking {
        val f = Fixture()
        assertEquals(AudioExportResult.Success(f.output), f.export())
        assertEquals(listOf("create", "probe", "execute", "publish"), f.events)
        assertEquals(listOf("-hide_banner", "-i", "input", "-map", "0:a:0", "-vn",
            "-c:a", "copy", "-f", "ipod", "output"), f.arguments)
    }

    @Test fun allFormatsAndQualityLevelsRespectSourceBitrateAndContainer() = runBlocking {
        for (format in AudioExportFormat.entries) {
            for (quality in AudioExportQuality.entries) {
                for (sourceBitrate in listOf<Int?>(96, 256, 4608, null)) {
                    val f = Fixture()
                    f.source = SourceAudioInfo("ac3", sourceBitrate)
                    assertTrue(f.export(format, quality) is AudioExportResult.Success)
                    val requested = when (quality) {
                        AudioExportQuality.BEST, AudioExportQuality.HIGH -> 320
                        AudioExportQuality.MEDIUM -> 192
                        AudioExportQuality.LOW -> 128
                    }
                    assertEquals(listOf("-hide_banner", "-i", "input", "-map", "0:a:0", "-vn",
                        "-c:a", if (format == AudioExportFormat.MP3) "libmp3lame" else "aac",
                        "-b:a", "${minOf(requested, sourceBitrate ?: requested)}k",
                        "-f", if (format == AudioExportFormat.MP3) "mp3" else "ipod", "output"),
                        f.arguments)
                }
            }
        }
    }

    @Test fun failedProbePreservesOriginalBestCopyFallback() = runBlocking {
        val f = Fixture()
        f.probeError = IllegalStateException("probe failed")
        assertTrue(f.export() is AudioExportResult.Success)
        assertTrue(f.arguments.contains("copy"))
    }

    @Test fun missingSourceTranscodesOtherFormatsAtDefaultBitrate() = runBlocking {
        val f = Fixture()
        f.source = null
        assertTrue(f.export(AudioExportFormat.MP3) is AudioExportResult.Success)
        assertTrue(f.arguments.contains("320k"))
    }

    @Test fun creationFailureDoesNotExecuteOrDelete() = runBlocking {
        val f = Fixture()
        val error = IllegalStateException("create")
        f.createError = error
        val result = f.export() as AudioExportResult.Failure
        assertEquals(AudioExportError.OutputCreationFailed, result.error)
        assertEquals(error.message, result.cause?.message)
        assertEquals(listOf("create"), f.events)
    }

    @Test fun failedEncodingDeletesWithoutPublishing() = runBlocking {
        val f = Fixture()
        f.executeSuccess = false
        val result = f.export() as AudioExportResult.Failure
        assertEquals(AudioExportError.TranscodeFailed, result.error)
        assertEquals(listOf("create", "probe", "execute", "delete"), f.events)
    }

    @Test fun failedPublicationDeletesAndPreservesCause() = runBlocking {
        val f = Fixture()
        val error = IllegalStateException("publish")
        f.publishError = error
        val result = f.export() as AudioExportResult.Failure
        assertEquals(AudioExportError.Unknown, result.error)
        assertEquals(error.message, result.cause?.message)
        assertEquals(listOf("create", "probe", "execute", "publish", "delete"), f.events)
    }

    @Test fun cleanupFailureDoesNotReplaceEncodingFailure() = runBlocking {
        val f = Fixture()
        val error = IllegalStateException("execute")
        val cleanup = IllegalStateException("delete")
        f.executeError = error
        f.deleteError = cleanup
        val result = f.export() as AudioExportResult.Failure
        assertEquals(AudioExportError.TranscodeFailed, result.error)
        assertEquals(error.message, result.cause?.message)
        assertSame(cleanup, result.cause?.suppressed?.single())
    }

    @Test fun probeCancellationRollsBackAndPropagates() = runBlocking {
        val f = Fixture()
        f.probeError = CancellationException("cancel")
        try {
            f.export()
            fail("Expected cancellation")
        } catch (_: CancellationException) {
            assertEquals(listOf("create", "probe", "delete"), f.events)
        }
    }

    @Test fun cancellationWhileEncodingWaitsForWriterThenRollsBack() = runBlocking {
        val f = Fixture()
        val started = CompletableDeferred<Unit>()
        val release = java.util.concurrent.CountDownLatch(1)
        f.duringExecute = {
            started.complete(Unit)
            check(release.await(5, java.util.concurrent.TimeUnit.SECONDS))
        }
        val export = async { f.export() }
        started.await()
        export.cancel()
        release.countDown()
        try {
            export.await()
            fail("Expected cancellation")
        } catch (_: CancellationException) {
            export.join()
            assertEquals(listOf("create", "probe", "execute", "delete"), f.events)
        }
    }
}
