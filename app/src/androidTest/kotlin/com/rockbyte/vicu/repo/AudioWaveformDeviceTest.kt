package com.rockbyte.vicu.repo

import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.test.AndroidTestCase
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

/** Exercises the bundled native stream protocol rather than a JVM substitute. */
@Suppress("DEPRECATION")
class AudioWaveformDeviceTest : AndroidTestCase() {
    private val files = mutableListOf<File>()

    override fun tearDown() { files.forEach { it.delete() }; super.tearDown() }

    private fun file(): File = File.createTempFile("waveform-test-", ".fixture", context.cacheDir).also { files += it }

    private fun source(format: AudioTrimFormat, codec: String, silence: Boolean = false): Uri {
        val target = file()
        val input = if (silence) "anullsrc=r=48000:cl=stereo" else
            "aevalsrc=0.5*sin(2*PI*440*t)|-0.5*sin(2*PI*440*t):s=48000:d=2"
        val session = FFmpegKit.executeWithArguments(arrayOf("-y", "-v", "error", "-f", "lavfi", "-i", input,
            "-t", "2", "-c:a", codec, "-f", format.muxer, target.absolutePath))
        assertTrue(session.getOutput(), ReturnCode.isSuccess(session.getReturnCode()))
        return Uri.fromFile(target)
    }

    fun testFiveFormatsKeepOppositePhaseStereoPeaks() = runBlocking {
        val repo = AudioWaveformRepository(AudioWaveformStorage(context))
        for ((format, codec) in listOf(AudioTrimFormat.MP3 to "libmp3lame", AudioTrimFormat.M4A to "aac",
            AudioTrimFormat.WAV to "pcm_s24le", AudioTrimFormat.FLAC to "flac", AudioTrimFormat.OGG to "libvorbis",
            AudioTrimFormat.OGG to "libopus")) {
            val waveform = withTimeout(15000) { repo.load(source(format, codec), 2000).getOrThrow() }
            assertTrue("$format peak lost", waveform.peaks.max() > 0.3f)
            assertTrue("$format duration=${waveform.durationMs}", waveform.durationMs in 1900.0..2200.0)
            assertTrue(waveform.peaks.size <= 360000)
        }
    }

    fun testSilenceIsFlat() = runBlocking {
        val waveform = withTimeout(15000) { AudioWaveformRepository(AudioWaveformStorage(context))
            .load(source(AudioTrimFormat.WAV, "pcm_s16le", true), 2000).getOrThrow() }
        assertTrue(waveform.peaks.all { it == 0f })
    }

    fun testCancellationLeavesOtherFfmpegSessionsAndPlatformPlaybackUsable() = runBlocking {
        val input = source(AudioTrimFormat.WAV, "pcm_s16le")
        val prepared = CountDownLatch(1)
        val main = Handler(Looper.getMainLooper())
        lateinit var preview: AudioPreviewStorage
        main.post { preview = AudioPreviewStorage(context)
            preview.open(input, { prepared.countDown() }, {}, { prepared.countDown() }) }
        assertTrue(prepared.await(5, TimeUnit.SECONDS))
        val output = file()
        val completed = CountDownLatch(1)
        val other = FFmpegKit.executeWithArgumentsAsync(arrayOf("-y", "-v", "error", "-re", "-f", "lavfi",
            "-i", "sine=duration=3", "-c:a", "pcm_s16le", "-f", "wav", output.absolutePath), { completed.countDown() })
        try {
            var gotPcm = false
            val decode = launch {
                AudioWaveformStorage(context).decode(input, {}, {
                    gotPcm = true
                    cancel() // Cancel after the output is open, including a possibly blocked native writer.
                })
            }
            withTimeout(5000) { decode.join() }
            assertTrue(gotPcm); assertTrue(decode.isCancelled)
            assertTrue(completed.await(8, TimeUnit.SECONDS))
            assertTrue("waveform cancellation affected another session", ReturnCode.isSuccess(other.getReturnCode()))
            val played = CountDownLatch(1)
            var started = false
            main.post { started = preview.play(); played.countDown() }
            assertTrue(played.await(5, TimeUnit.SECONDS)); assertTrue(started)
            assertTrue(AudioWaveformRepository(AudioWaveformStorage(context)).load(input, 2000).isSuccess)
        } finally {
            FFmpegKit.cancel(other.getSessionId())
            val released = CountDownLatch(1)
            main.post { preview.release(); released.countDown() }
            assertTrue(released.await(5, TimeUnit.SECONDS))
        }
    }
}
