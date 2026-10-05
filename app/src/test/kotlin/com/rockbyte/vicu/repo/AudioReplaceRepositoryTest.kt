package com.rockbyte.vicu.repo

import android.net.Uri
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock

class AudioReplaceRepositoryTest {
    private class Fixture {
        val videoInput: Uri = mock(Uri::class.java)
        val musicInput: Uri = mock(Uri::class.java)
        val output: Uri = mock(Uri::class.java)
        val events = mutableListOf<String>()
        var videoDurationMs: Long? = 10_000
        var musicDurationMs: Long? = 30_000
        var timeReportsMs: List<Long> = emptyList()
        var videoProbeError: Exception? = null
        var musicProbeError: Exception? = null
        var createError: Exception? = null
        var publishError: Exception? = null
        var deleteError: Exception? = null
        var executeError: Exception? = null
        var executeSuccess = true
        lateinit var arguments: List<String>
        val repo: AudioReplaceRepo = AudioReplaceRepository(
            object : VideoConverter {
                override fun probe(uri: Uri): SourceVideoInfo? {
                    assertSame(videoInput, uri)
                    events += "probeVideo"
                    videoProbeError?.let { throw it }
                    return SourceVideoInfo(bitrateKbps = 4000, durationMs = videoDurationMs)
                }
                override fun inputUrl(uri: Uri): String = "videoInput"
                override fun outputUrl(uri: Uri): String = "output"
                override fun execute(arguments: Array<String>, onTimeMs: (Long) -> Unit): Boolean {
                    events += "execute"
                    this@Fixture.arguments = arguments.toList()
                    timeReportsMs.forEach(onTimeMs)
                    executeError?.let { throw it }
                    return executeSuccess
                }
            },
            object : AudioEncoder {
                override fun probe(uri: Uri): SourceAudioInfo? {
                    assertSame(musicInput, uri)
                    events += "probeMusic"
                    musicProbeError?.let { throw it }
                    return SourceAudioInfo(codec = "mp3", bitrateKbps = 320, durationMs = musicDurationMs)
                }
                override fun inputUrl(uri: Uri): String = "musicInput"
                override fun outputUrl(uri: Uri): String = "output"
                override fun execute(arguments: Array<String>, onTimeMs: (Long) -> Unit): Boolean = true
            },
            object : VideoOutputStore {
                override fun create(inputName: String, format: VideoConvertFormat): Uri {
                    events += "create"
                    assertEquals("video.mp4", inputName)
                    assertEquals(VideoConvertFormat.MP4, format)
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
        suspend fun replace(
            mode: AudioReplaceMode = AudioReplaceMode.TRUNCATE,
            onProgress: (Float) -> Unit = {},
        ) = repo.replace(AudioReplaceRequest(videoInput, "video.mp4", musicInput, mode), onProgress)
    }

    @Test
    fun truncateCopiesVideoAndLimitsToVideoDuration() = runBlocking {
        val f = Fixture()
        assertTrue(f.replace(AudioReplaceMode.TRUNCATE) is AudioReplaceResult.Success)
        assertEquals(
            listOf(
                "-hide_banner", "-i", "videoInput", "-i", "musicInput",
                "-map", "0:v:0", "-map", "1:a:0",
                "-c:v", "copy",
                "-c:a", "aac", "-b:a", "128k",
                "-t", "10000ms",
                "-f", "mp4", "output",
            ),
            f.arguments,
        )
        assertEquals(listOf("probeVideo", "probeMusic", "create", "execute", "publish"), f.events)
    }

    @Test
    fun truncateWithUnknownVideoDurationFallsBackToShortest() = runBlocking {
        val f = Fixture()
        f.videoDurationMs = null
        assertTrue(f.replace(AudioReplaceMode.TRUNCATE) is AudioReplaceResult.Success)
        assertTrue(f.arguments.contains("-shortest"))
        assertFalse(f.arguments.contains("-t"))
    }

    @Test
    fun stretchAudioWithinSingleTempoRange() = runBlocking {
        val f = Fixture()
        f.videoDurationMs = 10_000
        f.musicDurationMs = 15_000
        assertTrue(f.replace(AudioReplaceMode.STRETCH_AUDIO) is AudioReplaceResult.Success)
        assertEquals(
            listOf(
                "-hide_banner", "-i", "videoInput", "-i", "musicInput",
                "-map", "0:v:0", "-map", "1:a:0",
                "-c:v", "copy",
                "-af", "atempo=1.5000",
                "-c:a", "aac", "-b:a", "128k",
                "-f", "mp4", "output",
            ),
            f.arguments,
        )
    }

    @Test
    fun stretchAudioChainsTempoBeyondSingleStageRange() = runBlocking {
        val f = Fixture()
        f.videoDurationMs = 10_000
        f.musicDurationMs = 40_000
        assertTrue(f.replace(AudioReplaceMode.STRETCH_AUDIO) is AudioReplaceResult.Success)
        assertEquals("atempo=2.0,atempo=2.0000", f.arguments[f.arguments.indexOf("-af") + 1])
        f.musicDurationMs = 4_000
        assertTrue(f.replace(AudioReplaceMode.STRETCH_AUDIO) is AudioReplaceResult.Success)
        assertEquals("atempo=0.5,atempo=0.8000", f.arguments[f.arguments.indexOf("-af") + 1])
    }

    @Test
    fun stretchVideoReencodesAndMatchesMusicDuration() = runBlocking {
        val f = Fixture()
        f.videoDurationMs = 10_000
        f.musicDurationMs = 25_000
        assertTrue(f.replace(AudioReplaceMode.STRETCH_VIDEO) is AudioReplaceResult.Success)
        assertEquals(
            listOf(
                "-hide_banner", "-i", "videoInput", "-i", "musicInput",
                "-map", "0:v:0", "-map", "1:a:0",
                "-c:v", "libx264", "-preset", "veryfast", "-crf", "23",
                "-vf", "setpts=PTS*2.5000",
                "-c:a", "aac", "-b:a", "128k",
                "-shortest",
                "-f", "mp4", "output",
            ),
            f.arguments,
        )
    }

    @Test
    fun stretchWithoutDurationsFailsBeforeCreatingOutput() = runBlocking {
        for (mode in listOf(AudioReplaceMode.STRETCH_AUDIO, AudioReplaceMode.STRETCH_VIDEO)) {
            for (missing in listOf(
                { f: Fixture -> f.videoDurationMs = null },
                { f: Fixture -> f.musicDurationMs = null },
            )) {
                val f = Fixture()
                missing(f)
                val result = f.replace(mode)
                assertTrue(result is AudioReplaceResult.Failure)
                assertEquals(AudioReplaceError.InvalidMedia, (result as AudioReplaceResult.Failure).error)
                assertFalse(f.events.contains("create"))
                assertFalse(f.events.contains("execute"))
            }
        }
    }

    @Test
    fun stretchVideoProgressUsesMusicDuration() = runBlocking {
        val f = Fixture()
        f.musicDurationMs = 30_000
        f.timeReportsMs = listOf(3_000, 3_004, 15_000, 31_000)
        val progress = mutableListOf<Float>()
        assertTrue(f.replace(AudioReplaceMode.STRETCH_VIDEO, progress::add) is AudioReplaceResult.Success)
        assertEquals(listOf(0.1f, 0.5f, 1f), progress)
    }

    @Test
    fun failedEncodingDeletesWithoutPublishing() = runBlocking {
        val f = Fixture()
        f.executeSuccess = false
        val result = f.replace() as AudioReplaceResult.Failure
        assertEquals(AudioReplaceError.TranscodeFailed, result.error)
        assertEquals(listOf("probeVideo", "probeMusic", "create", "execute", "delete"), f.events)
    }

    @Test
    fun creationFailureDoesNotExecuteOrDelete() = runBlocking {
        val f = Fixture()
        f.createError = IllegalStateException("create")
        val result = f.replace() as AudioReplaceResult.Failure
        assertEquals(AudioReplaceError.OutputCreationFailed, result.error)
        assertEquals(listOf("probeVideo", "probeMusic", "create"), f.events)
    }

    @Test
    fun atempoChainSplitsExtremeTempo() {
        assertEquals("atempo=1.3500", atempoFilterChain(1.35))
        assertEquals("atempo=2.0000", atempoFilterChain(2.0))
        assertEquals("atempo=0.5000", atempoFilterChain(0.5))
        assertEquals("atempo=2.0,atempo=2.0000", atempoFilterChain(4.0))
        assertEquals("atempo=0.5,atempo=0.8000", atempoFilterChain(0.4))
        assertEquals("atempo=2.0,atempo=1.7500", atempoFilterChain(3.5))
    }
}
