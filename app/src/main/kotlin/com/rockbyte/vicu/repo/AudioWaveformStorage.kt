package com.rockbyte.vicu.repo

import android.content.Context
import android.net.Uri
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.FFmpegKitStreamOutput
import com.arthenica.ffmpegkit.FFprobeKit
import com.arthenica.ffmpegkit.ReturnCode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

internal class AudioWaveformStorage(context: Context) : AudioWaveformStore {
    private val appContext = context.applicationContext

    override suspend fun decode(uri: Uri, onFormat: (AudioPcmFormat) -> Unit, onChunk: (ByteArray) -> Unit) {
        withContext(Dispatchers.IO) {
            val probeDone = CompletableDeferred<AudioPcmFormat>()
            val probe = FFprobeKit.executeWithArgumentsAsync(arrayOf("-v", "error", "-select_streams", "a:0",
                "-show_entries", "stream=sample_rate,channels", "-of", "default=noprint_wrappers=1",
                FFmpegKitConfig.getSafParameterForRead(appContext, uri)), { session ->
                try {
                    check(ReturnCode.isSuccess(session.getReturnCode())) { "Waveform probe failed" }
                    val fields = session.getOutput().lineSequence().mapNotNull { line ->
                        val parts = line.split('=', limit = 2)
                        if (parts.size == 2) parts[0] to parts[1].trim() else null
                    }.toMap()
                    val format = AudioPcmFormat(fields.getValue("sample_rate").toInt(), fields.getValue("channels").toInt())
                    require(format.sampleRate > 0 && format.channels > 0)
                    probeDone.complete(format)
                } catch (error: Exception) { probeDone.completeExceptionally(error) }
            })
            val format = try { probeDone.await() } finally {
                if (!probeDone.isCompleted) FFmpegKit.cancel(probe.getSessionId())
            }
            currentCoroutineContext().ensureActive()
            onFormat(format)
            val stream = FFmpegKitStreamOutput.create("f32le", 256L * 1024)
            val completed = CompletableDeferred<Boolean>()
            var sessionId: Long? = null
            try {
                val session = FFmpegKit.executeWithArgumentsAsync(arrayOf("-v", "error", "-nostdin",
                    "-i", FFmpegKitConfig.getSafParameterForRead(appContext, uri), "-map", "0:a:0",
                    "-vn", "-sn", "-dn", "-c:a", "pcm_f32le", "-f", "f32le", stream.getUrl()),
                    { completed.complete(ReturnCode.isSuccess(it.getReturnCode())) })
                sessionId = session.getSessionId()
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val chunk = stream.read(64 * 1024, 100)
                    if (chunk == null) {
                        // A failed command may never open the output, so no EOF is produced.
                        if (completed.isCompleted) { check(completed.await()) { "Waveform decode failed" }; break }
                        continue
                    }
                    if (chunk.isEmpty()) break
                    onChunk(chunk)
                }
                check(completed.await()) { "Waveform decode failed" }
            } finally {
                // Close also wakes a native writer blocked by a full ring buffer.
                if (!completed.isCompleted) sessionId?.let(FFmpegKit::cancel)
                stream.close()
            }
        }
    }
}
