package com.rockbyte.vicu.repo

import android.media.MediaExtractor
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.test.AndroidTestCase
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Real local-AAR probing/copying, publication, and platform seek/replay coverage. */
@Suppress("DEPRECATION")
class AudioTrimDeviceTest : AndroidTestCase() {
    private val files = mutableListOf<File>()
    private val outputs = mutableListOf<Uri>()
    private val main = Handler(Looper.getMainLooper())
    private lateinit var encoder: FFmpegAudioEncoder
    private lateinit var works: WorksStorage
    private lateinit var outputStore: AudioTrimStorage

    override fun setUp() {
        super.setUp()
        encoder = FFmpegAudioEncoder(context)
        works = WorksStorage(context, System::currentTimeMillis)
        outputStore = AudioTrimStorage(context, works)
    }
    override fun tearDown() {
        outputs.forEach(outputStore::delete)
        files.forEach { it.delete() }
        super.tearDown()
    }

    private fun source(format: AudioTrimFormat, codec: String): Uri {
        val file = File.createTempFile("audio-trim-test-", ".fixture", context.cacheDir)
        files += file
        val session = FFmpegKit.executeWithArguments(arrayOf("-y", "-hide_banner",
            "-f", "lavfi", "-i", "sine=frequency=880:sample_rate=48000:duration=6",
            "-c:a", codec, "-f", format.muxer, file.absolutePath))
        assertTrue("fixture codec=$codec: ${session.getOutput()}", ReturnCode.isSuccess(session.getReturnCode()))
        return Uri.fromFile(file)
    }

    fun testFiveFormatsPreserveCodecAndPublishValidSegments() = runBlocking {
        for ((format, codec) in listOf(
            AudioTrimFormat.MP3 to "libmp3lame", AudioTrimFormat.M4A to "aac",
            AudioTrimFormat.WAV to "pcm_s24le", AudioTrimFormat.FLAC to "flac",
            AudioTrimFormat.OGG to "libvorbis", AudioTrimFormat.OGG to "libopus",
        )) {
            val input = source(format, codec)
            val sourceInfo = encoder.probe(input)!!
            assertNotNull("must probe the audio stream", sourceInfo.codec)
            assertNotNull(sourceInfo.containerName)
            val repo = AudioTrimRepository(encoder, outputStore)
            val inspected = repo.inspect(input) as AudioTrimProbeResult.Success
            assertEquals(format, inspected.info.format)
            val result = repo.trim(AudioTrimRequest(input, "device-test.fixture", 1500, 4500))
            assertTrue("$format: $result", result is AudioTrimResult.Success)
            val output = (result as AudioTrimResult.Success).outputUri
            outputs += output
            val outputInfo = encoder.probe(output)!!
            assertEquals(sourceInfo.codec, outputInfo.codec)
            val tolerance = if (format == AudioTrimFormat.FLAC) 150L else 100L
            assertTrue("$format duration=${outputInfo.durationMs}", kotlin.math.abs(outputInfo.durationMs!! - 3000) <= tolerance)
            val work = works.query().single { it.uri == output }
            assertEquals(MediaKind.AUDIO, work.kind)
            assertEquals("${context.packageName}.works", output.authority)
            assertTrue(work.name.endsWith(".${format.extension}"))
            context.contentResolver.query(output, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)!!.use {
                assertTrue(it.moveToFirst())
                assertEquals(work.name, it.getString(0))
            }
            val extractor = MediaExtractor()
            try {
                extractor.setDataSource(context, output, null)
                assertEquals(1, extractor.trackCount)
                extractor.selectTrack(0)
                assertTrue("first sample=${extractor.sampleTime}", extractor.sampleTime in 0..100000)
            } finally { extractor.release() }
            assertTrue(File(input.path!!).exists())
        }
    }

    fun testVeryShortSelectionNeverPublishesAnEmptyAudioFile() = runBlocking {
        for ((format, codec) in listOf(
            AudioTrimFormat.MP3 to "libmp3lame", AudioTrimFormat.M4A to "aac",
            AudioTrimFormat.WAV to "pcm_s16le", AudioTrimFormat.FLAC to "flac", AudioTrimFormat.OGG to "libvorbis",
        )) {
            var destination: Uri? = null
            val recordingStore = object : AudioTrimStore by outputStore {
                override fun create(inputName: String, format: AudioTrimFormat): Uri =
                    outputStore.create(inputName, format).also { destination = it; outputs += it }
            }
            val result = AudioTrimRepository(encoder, recordingStore).trim(
                AudioTrimRequest(source(format, codec), "short-test.fixture", 1501, 1502))
            when (result) {
                is AudioTrimResult.Success -> assertTrue(encoder.packets(result.outputUri, true).isNotEmpty())
                is AudioTrimResult.Failure -> {
                    assertEquals(AudioTrimError.TrimFailed, result.error)
                    destination?.let { uri ->
                        outputs.remove(uri) // The repository already deleted this failed pending output.
                        assertFalse(works.query().any { it.uri == uri })
                        try {
                            context.contentResolver.openInputStream(uri)?.close()
                            fail("Deleted output must not be readable")
                        } catch (_: java.io.FileNotFoundException) { }
                    }
                }
            }
        }
    }

    fun testSharedMp4DemuxerDoesNotMisclassifyMovOr3gpAsM4a() = runBlocking {
        for (muxer in listOf("mov", "3gp")) {
            val file = File.createTempFile("audio-trim-unsupported-", ".fixture", context.cacheDir)
            files += file
            val session = FFmpegKit.executeWithArguments(arrayOf("-y", "-v", "error", "-f", "lavfi",
                "-i", "sine=duration=1", "-c:a", "aac", "-f", muxer, file.absolutePath))
            assertTrue(ReturnCode.isSuccess(session.getReturnCode()))
            assertEquals(AudioTrimProbeResult.Failure(AudioTrimError.UnsupportedFormat),
                AudioTrimRepository(encoder, outputStore).inspect(Uri.fromFile(file)))
        }
    }

    private fun onMain(action: () -> Unit) {
        val done = CountDownLatch(1)
        var failure: Throwable? = null
        main.post { try { action() } catch (error: Throwable) { failure = error } finally { done.countDown() } }
        assertTrue(done.await(5, TimeUnit.SECONDS))
        failure?.let { throw it }
    }
    private fun awaitState(repo: AudioPreviewRepo, predicate: (AudioPreviewState) -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8)
        while (!predicate(repo.state.value) && System.nanoTime() < deadline) Thread.sleep(20)
        assertTrue("last preview state=${repo.state.value}", predicate(repo.state.value))
    }

    fun testPlatformPreviewSeeksStopsAtEndpointReplaysAndReleases() {
        for ((format, codec) in listOf(
            AudioTrimFormat.MP3 to "libmp3lame", AudioTrimFormat.M4A to "aac",
            AudioTrimFormat.WAV to "pcm_s16le", AudioTrimFormat.FLAC to "flac",
            AudioTrimFormat.OGG to "libvorbis",
        )) {
            val sourceUri = source(format, codec)
            val result = runBlocking { AudioTrimRepository(encoder, outputStore)
                .trim(AudioTrimRequest(sourceUri, "preview-test.fixture", 1500, 4500)) }
            assertTrue(result is AudioTrimResult.Success)
            val input = (result as AudioTrimResult.Success).outputUri
            outputs += input
            lateinit var repo: AudioPreviewRepo
            onMain { repo = AudioPreviewRepository(AudioPreviewStorage(context)); repo.open(input, 1000, 1800) }
            try {
                awaitState(repo) { it.phase == AudioPreviewPhase.Ready }
                onMain { repo.seekTo(100); repo.togglePlayPause() }
                awaitState(repo) { it.playing }
                awaitState(repo) { !it.playing && it.positionMs == 1800L }
                onMain { repo.togglePlayPause() }
                awaitState(repo) { it.playing && it.positionMs in 1000..1500 }
                onMain { repo.pause() }
                assertFalse(repo.state.value.playing)
                onMain { repo.setRange(2000, 3000); repo.seekTo(9000) }
                awaitState(repo) { it.positionMs == 3000L }
            } finally { onMain { repo.release() } }
            assertEquals(AudioPreviewPhase.Idle, repo.state.value.phase)
        }
    }
}
