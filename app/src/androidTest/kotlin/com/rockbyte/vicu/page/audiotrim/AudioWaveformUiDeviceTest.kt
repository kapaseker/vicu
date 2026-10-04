package com.rockbyte.vicu.page.audiotrim

import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.MediaStore
import android.test.InstrumentationTestCase
import android.view.InputDevice
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import com.rockbyte.vicu.MainActivity
import com.rockbyte.vicu.R
import java.io.File

/** Real Compose gestures via platform input injection, with no UI-test dependency. */
@Suppress("DEPRECATION")
class AudioWaveformUiDeviceTest : InstrumentationTestCase() {
    private var media: Uri? = null
    private var activity: Activity? = null

    override fun tearDown() {
        activity?.let { instrumentation.runOnMainSync { it.finish() } }
        media?.let { instrumentation.targetContext.contentResolver.delete(it, null, null) }
        super.tearDown()
    }

    fun testPinchPanSelectionSeekAndReset() {
        openAudio(45.0, false)
        val wave = waveformBounds()
        val initial = windowDescription()
        pinch(wave)
        awaitCondition { windowDescription() != initial }
        val zoomed = windowDescription()
        drag(wave.centerX().toFloat(), wave.centerY().toFloat(), wave.centerX() - wave.width() / 6f, wave.centerY().toFloat())
        awaitCondition { windowDescription() != zoomed }
        assertTrue("background panning changed selection", texts().contains("0.000") && texts().contains("45.000"))
        tapText(R.string.waveform_show_all)
        awaitCondition { windowDescription() == initial }
        val inset = wave.width() * 0.03f
        drag(wave.left + inset, wave.centerY().toFloat(), wave.left + wave.width() * 0.25f, wave.centerY().toFloat())
        awaitCondition { texts().any { it.contains(" / ") && !it.startsWith("0.000") } }
        val beforeSeek = texts().first { it.contains(" / ") }
        tap(wave.centerX().toFloat(), wave.centerY().toFloat())
        awaitCondition { texts().any { it.contains(" / ") && it != beforeSeek } }
        tapText(R.string.player_play)
        awaitCondition { texts().contains(instrumentation.targetContext.getString(R.string.player_pause)) }
        tapText(R.string.player_pause)
        setEndpoint(R.string.trim_start, 20000f)
        setEndpoint(R.string.trim_end, 20001f)
        val handle = endpoint(R.string.trim_start)
        val handleBounds = Rect().also { handle.getBoundsInScreen(it) }
        drag(handleBounds.exactCenterX() - 1f, wave.exactCenterY(),
            handleBounds.exactCenterX() - wave.width() / 10f, wave.exactCenterY())
        awaitCondition { endpoint(R.string.trim_start).rangeInfo.current < 20000f }
        assertEquals(20001f, endpoint(R.string.trim_end).rangeInfo.current, 0.1f)
    }

    fun testShortSilenceSupportsPinchWithoutInvalidViewport() {
        openAudio(0.5, true)
        val initial = windowDescription()
        pinch(waveformBounds())
        instrumentation.waitForIdleSync()
        assertEquals(initial, windowDescription())
        assertTrue(texts().contains("0.500"))
    }

    private fun openAudio(duration: Double, silence: Boolean) {
        val context = instrumentation.targetContext
        val name = "waveform-ui-${System.nanoTime()}.wav"
        val file = File.createTempFile("waveform-ui-", ".wav", context.cacheDir)
        try {
            val input = if (silence) "anullsrc=r=8000:cl=stereo" else "aevalsrc=0.5*sin(2*PI*440*t)|-0.5*sin(2*PI*440*t):s=8000"
            val session = FFmpegKit.executeWithArguments(arrayOf("-y", "-v", "error", "-f", "lavfi", "-i", input,
                "-t", duration.toString(), "-c:a", "pcm_s16le", file.absolutePath))
            assertTrue(session.getOutput(), ReturnCode.isSuccess(session.getReturnCode()))
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, "audio/wav")
                put(MediaStore.MediaColumns.RELATIVE_PATH, "Music/VicuWaveformTests/")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            })!!
            media = uri
            resolver.openOutputStream(uri)!!.use { output -> file.inputStream().use { it.copyTo(output) } }
            resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        } finally { file.delete() }
        activity = instrumentation.startActivitySync(Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        awaitCondition { nodes().any { it.contentDescription?.toString() == name } }
        val item = nodes().first { it.contentDescription?.toString() == name }
        val bounds = Rect(); item.getBoundsInScreen(bounds)
        tap(bounds.exactCenterX(), bounds.exactCenterY())
        awaitCondition { texts().contains(context.getString(R.string.audio_trim)) }
        tapText(R.string.audio_trim)
        awaitCondition { nodes().any { it.contentDescription?.toString() == context.getString(R.string.waveform_description) } }
    }

    private fun nodes(): List<AccessibilityNodeInfo> {
        // Compose replaces the loading region with virtual nodes; request a fresh platform snapshot.
        if (Build.VERSION.SDK_INT >= 33) instrumentation.uiAutomation.clearCache()
        val result = mutableListOf<AccessibilityNodeInfo>()
        fun visit(node: AccessibilityNodeInfo) {
            result += node
            for (i in 0 until node.childCount) node.getChild(i)?.let(::visit)
        }
        instrumentation.uiAutomation.rootInActiveWindow?.let(::visit)
        return result
    }
    private fun texts(): List<String> = nodes().mapNotNull { it.text?.toString() }
    private fun endpoint(resource: Int): AccessibilityNodeInfo = nodes().first {
        it.contentDescription?.toString() == instrumentation.targetContext.getString(resource) && it.rangeInfo != null
    }
    private fun setEndpoint(resource: Int, value: Float) {
        assertTrue(endpoint(resource).performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.id,
            Bundle().apply { putFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE, value) }))
        awaitCondition { kotlin.math.abs(endpoint(resource).rangeInfo.current - value) < 0.1f }
    }
    private fun waveformNode(): AccessibilityNodeInfo = nodes().first {
        it.contentDescription?.toString() == instrumentation.targetContext.getString(R.string.waveform_description)
    }
    private fun waveformBounds(): Rect = Rect().also { waveformNode().getBoundsInScreen(it) }
    private fun windowDescription(): String {
        val bottom = waveformBounds().bottom
        val label = instrumentation.targetContext.getString(R.string.waveform_show_all)
        val current = nodes()
        val button = Rect().also { rect -> current.first { it.text?.toString() == label }.getBoundsInScreen(rect) }
        return current.filter { node ->
            val bounds = Rect().also { node.getBoundsInScreen(it) }
            node.text?.toString()?.matches(Regex("[0-9]+\\.[0-9]+")) == true &&
                bounds.top >= bottom && bounds.bottom <= button.top
        }.joinToString(" / ") { it.text.toString() }
    }
    private fun tapText(resource: Int) {
        val label = instrumentation.targetContext.getString(resource)
        val node = nodes().first { it.text?.toString() == label }
        val bounds = Rect(); node.getBoundsInScreen(bounds)
        tap(bounds.exactCenterX(), bounds.exactCenterY())
    }
    private fun awaitCondition(predicate: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 10000
        while (SystemClock.uptimeMillis() < deadline) {
            if (predicate()) return
            SystemClock.sleep(50)
        }
        assertTrue("UI condition timed out; texts=${texts()}", predicate())
    }

    private fun event(downTime: Long, action: Int, points: List<Pair<Float, Float>>) {
        val properties = Array(points.size) { i -> MotionEvent.PointerProperties().apply { id = i; toolType = MotionEvent.TOOL_TYPE_FINGER } }
        val coordinates = Array(points.size) { i -> MotionEvent.PointerCoords().apply {
            x = points[i].first; y = points[i].second; pressure = 1f; size = 1f
        } }
        val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, points.size, properties,
            coordinates, 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0)
        try { instrumentation.sendPointerSync(event) } finally { event.recycle() }
        SystemClock.sleep(20)
    }
    private fun tap(x: Float, y: Float) {
        val time = SystemClock.uptimeMillis()
        event(time, MotionEvent.ACTION_DOWN, listOf(x to y))
        event(time, MotionEvent.ACTION_UP, listOf(x to y))
    }
    private fun drag(x: Float, y: Float, toX: Float, toY: Float) {
        val time = SystemClock.uptimeMillis()
        event(time, MotionEvent.ACTION_DOWN, listOf(x to y))
        for (i in 1..20) event(time, MotionEvent.ACTION_MOVE, listOf((x + (toX - x) * i / 20) to (y + (toY - y) * i / 20)))
        event(time, MotionEvent.ACTION_UP, listOf(toX to toY))
    }
    private fun pinch(bounds: Rect) {
        val time = SystemClock.uptimeMillis()
        val center = bounds.exactCenterX()
        val y = bounds.exactCenterY()
        var left = center - bounds.width() * 0.1f
        var right = center + bounds.width() * 0.1f
        event(time, MotionEvent.ACTION_DOWN, listOf(left to y))
        event(time, MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), listOf(left to y, right to y))
        for (i in 1..20) {
            left = center - bounds.width() * (0.1f + i * 0.01f)
            right = center + bounds.width() * (0.1f + i * 0.01f)
            event(time, MotionEvent.ACTION_MOVE, listOf(left to y, right to y))
        }
        event(time, MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), listOf(left to y, right to y))
        event(time, MotionEvent.ACTION_UP, listOf(left to y))
    }
}
