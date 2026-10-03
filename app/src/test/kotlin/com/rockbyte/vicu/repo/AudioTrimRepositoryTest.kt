package com.rockbyte.vicu.repo

import android.net.Uri
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito.mock

class AudioTrimRepositoryTest {
    private class Fixture {
        val input: Uri = mock(Uri::class.java)
        val output: Uri = mock(Uri::class.java)
        var source: SourceAudioInfo? = SourceAudioInfo("mp3", 320, 10000, containerName = "mp3")
        val events = mutableListOf<String>()
        var reports = listOf(500L, 504L, 2500L, 6000L)
        var createError: Exception? = null
        var probeError: Exception? = null
        var publishError: Exception? = null
        var executeError: Exception? = null
        var duringExecute: () -> Unit = {}
        var deleteError: Exception? = null
        var successful = true
        var flacFinalized = false
        var encodedPackets = listOf(AudioPacketInfo(42, 100))
        var format: AudioTrimFormat? = null
        var arguments = emptyList<String>()
        val repo = AudioTrimRepository(object : AudioEncoder {
            override fun probe(uri: Uri): SourceAudioInfo? {
                events += "probe"
                probeError?.let { throw it }
                return source
            }
            override fun packets(uri: Uri, firstOnly: Boolean) = encodedPackets
            override fun inputUrl(uri: Uri) = "input"
            override fun outputUrl(uri: Uri) = "output"
            override fun execute(arguments: Array<String>, onTimeMs: (Long) -> Unit): Boolean {
                events += "execute"
                this@Fixture.arguments = arguments.toList()
                reports.forEach(onTimeMs)
                duringExecute()
                executeError?.let { throw it }
                return successful
            }
        }, object : AudioTrimStore {
            override fun create(inputName: String, format: AudioTrimFormat): Uri {
                events += "create"
                this@Fixture.format = format
                createError?.let { throw it }
                return output
            }
            override fun finalizeFlac(uri: Uri, packets: List<AudioPacketInfo>) { flacFinalized = true }
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
        })
        suspend fun trim(start: Long = 1500, end: Long = 6500, progress: (Float) -> Unit = {}) =
            repo.trim(AudioTrimRequest(input, "misleading.wav", start, end), progress)
    }

    @Test fun copiesSourceCodecAndUsesSegmentDurationForProgress() = runBlocking {
        val f = Fixture()
        val progress = mutableListOf<Float>()
        assertEquals(AudioTrimResult.Success(f.output), f.trim(progress = progress::add))
        assertEquals(AudioTrimFormat.MP3, f.format)
        assertEquals(listOf("probe", "create", "execute", "publish"), f.events)
        assertEquals(listOf("-hide_banner", "-i", "input", "-ss", "1.500", "-t", "5.000",
            "-map", "0:a:0", "-vn", "-c:a", "copy", "-map_chapters", "-1", "-f", "mp3", "output"), f.arguments)
        assertEquals(listOf(0.1f, 0.5f, 1f), progress)
    }

    @Test fun fiveContainersAreDetectedWithoutFilenameGuessing() = runBlocking {
        for ((container, codec, format) in listOf(
            Triple("mp3", "mp3", AudioTrimFormat.MP3),
            Triple("mov,mp4,m4a,3gp,3g2,mj2", "alac", AudioTrimFormat.M4A),
            Triple("wav", "pcm_s24le", AudioTrimFormat.WAV),
            Triple("flac", "flac", AudioTrimFormat.FLAC),
            Triple("ogg", "vorbis", AudioTrimFormat.OGG),
            Triple("ogg", "opus", AudioTrimFormat.OGG),
        )) {
            val f = Fixture()
            f.source = SourceAudioInfo(codec, null, 10000, containerName = container)
            assertTrue(f.trim() is AudioTrimResult.Success)
            assertEquals(format, f.format)
            assertEquals(format == AudioTrimFormat.FLAC, f.flacFinalized)
            assertEquals("copy", f.arguments[f.arguments.indexOf("-c:a") + 1])
            assertEquals(format.muxer, f.arguments[f.arguments.indexOf("-f") + 1])
        }
    }

    @Test fun rejectsMissingAudioInvalidDurationAndUnsupportedContainerBeforeCreatingOutput() = runBlocking {
        for ((source, error) in listOf(
            null to AudioTrimError.ProbeFailed,
            SourceAudioInfo(null, null, 10000, containerName = "mp3") to AudioTrimError.ProbeFailed,
            SourceAudioInfo("mp3", null, 0, containerName = "mp3") to AudioTrimError.ProbeFailed,
            SourceAudioInfo("aac", null, 10000, containerName = "aac") to AudioTrimError.UnsupportedFormat,
            SourceAudioInfo("aac", null, 10000, containerName = "mov,mp4,m4a,3gp,3g2,mj2", containerBrand = "3gp5") to AudioTrimError.UnsupportedFormat,
            SourceAudioInfo("aac", null, 10000, containerName = "mov,mp4,m4a,3gp,3g2,mj2", containerBrand = "qt  ") to AudioTrimError.UnsupportedFormat,
        )) {
            val f = Fixture(); f.source = source
            assertEquals(error, (f.trim() as AudioTrimResult.Failure).error)
            assertEquals(listOf("probe"), f.events)
        }
    }

    @Test fun rejectsInvalidRangesWithoutCreatingOutput() = runBlocking {
        for ((start, end) in listOf(-1L to 5000L, 5000L to 5000L, 6000L to 5000L, 0L to 10001L)) {
            val f = Fixture()
            assertEquals(AudioTrimError.InvalidRange, (f.trim(start, end) as AudioTrimResult.Failure).error)
            assertEquals(listOf("probe"), f.events)
        }
    }

    @Test fun emptyEncodedSegmentIsDeletedInsteadOfPublished() = runBlocking {
        val f = Fixture(); f.encodedPackets = emptyList()
        assertEquals(AudioTrimError.TrimFailed, (f.trim() as AudioTrimResult.Failure).error)
        assertEquals(listOf("probe", "create", "execute", "delete"), f.events)
    }

    @Test fun creationFailureDoesNotDeleteOrExecute() = runBlocking {
        val f = Fixture(); f.createError = IllegalStateException("create")
        assertEquals(AudioTrimError.OutputCreationFailed, (f.trim() as AudioTrimResult.Failure).error)
        assertEquals(listOf("probe", "create"), f.events)
    }

    @Test fun executionAndPublicationFailuresRollback() = runBlocking {
        val f = Fixture(); f.successful = false
        assertEquals(AudioTrimError.TrimFailed, (f.trim() as AudioTrimResult.Failure).error)
        assertEquals(listOf("probe", "create", "execute", "delete"), f.events)
        val p = Fixture(); p.publishError = IllegalStateException("publish")
        assertEquals(AudioTrimError.OutputPublicationFailed, (p.trim() as AudioTrimResult.Failure).error)
        assertEquals(listOf("probe", "create", "execute", "publish", "delete"), p.events)
    }

    @Test fun cancellationWaitsForTheWriterThenDeletesPendingOutput() = runBlocking {
        val f = Fixture()
        val started = CompletableDeferred<Unit>()
        val release = java.util.concurrent.CountDownLatch(1)
        f.duringExecute = {
            started.complete(Unit)
            check(release.await(5, java.util.concurrent.TimeUnit.SECONDS))
        }
        val task = async { f.trim() }
        started.await(); task.cancel(); release.countDown()
        try { task.await(); fail("Expected cancellation") } catch (_: CancellationException) {
            task.join()
            assertEquals(listOf("probe", "create", "execute", "delete"), f.events)
        }
    }

    @Test fun cleanupDoesNotReplaceOriginalFailureAndCancellationPropagates() = runBlocking {
        val f = Fixture()
        f.executeError = IllegalStateException("execute"); f.deleteError = IllegalStateException("delete")
        val failure = f.trim() as AudioTrimResult.Failure
        assertEquals(f.executeError!!.message, failure.cause!!.message)
        assertSame(f.deleteError, failure.cause.suppressed.single())
        val c = Fixture(); c.executeError = CancellationException("cancel")
        try { c.trim(); fail("Expected cancellation") } catch (_: CancellationException) {
            assertEquals(listOf("probe", "create", "execute", "delete"), c.events)
        }
    }
}
