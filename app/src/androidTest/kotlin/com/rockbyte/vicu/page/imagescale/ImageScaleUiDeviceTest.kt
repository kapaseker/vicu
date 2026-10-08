package com.rockbyte.vicu.page.imagescale

import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.MediaStore
import android.test.InstrumentationTestCase
import android.view.InputDevice
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.rockbyte.vicu.MainActivity
import com.rockbyte.vicu.R

/** Real gestures and rendered image bounds, using the platform test runner. */
@Suppress("DEPRECATION")
class ImageScaleUiDeviceTest : InstrumentationTestCase() {
    private var media: Uri? = null
    private var activity: Activity? = null

    override fun tearDown() {
        activity?.let { instrumentation.runOnMainSync { it.finish() } }
        media?.let { instrumentation.targetContext.contentResolver.delete(it, null, null) }
        super.tearDown()
    }

    fun testLandscapeCenteredResize() = verifyResize(800, 400)
    fun testPortraitCenteredResize() = verifyResize(400, 800)

    private fun verifyResize(width: Int, height: Int) {
        val context = instrumentation.targetContext
        val resolver = context.contentResolver
        val name = "scale-ui-${System.nanoTime()}.png"
        media = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/VicuScaleTests/")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        })!!
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        for (x in 0 until width) for (y in 0 until height) {
            bitmap.setPixel(x, y, if (x < width / 4) Color.RED else if (x >= width * 3 / 4) Color.BLUE else Color.GREEN)
        }
        resolver.openOutputStream(media!!)!!.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        resolver.update(media!!, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        activity = instrumentation.startActivitySync(Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        awaitCondition { nodes().any { it.contentDescription?.toString() == name } }
        tapNode(nodes().first { it.contentDescription?.toString() == name })
        awaitCondition { nodes().any { it.text?.toString() == context.getString(R.string.image_scale) } }
        tapNode(nodes().first { it.text?.toString() == context.getString(R.string.image_scale) })
        awaitCondition { hasDescription(R.string.scale_corner_bottom_right) }
        val original = bounds(R.string.image_scale_preview)
        val corners = listOf(R.string.scale_corner_bottom_right, R.string.scale_corner_top_left,
            R.string.scale_corner_top_right, R.string.scale_corner_bottom_left)
        corners.forEach { resource ->
            val before = bounds(R.string.image_scale_preview)
            val handle = bounds(resource)
            val dx = (original.exactCenterX() - handle.exactCenterX()) * 0.22f
            val dy = (original.exactCenterY() - handle.exactCenterY()) * 0.22f
            drag(handle.exactCenterX(), handle.exactCenterY(), handle.exactCenterX() + dx, handle.exactCenterY() + dy)
            awaitCondition { bounds(R.string.image_scale_preview).width() < before.width() }
            val scaled = bounds(R.string.image_scale_preview)
            assertCentered(original, scaled)
            assertEquals(width.toFloat() / height, scaled.width().toFloat() / scaled.height(), 0.03f)
            val screenshot = instrumentation.uiAutomation.takeScreenshot()!!
            try {
                assertEquals(Color.RED, screenshot.getPixel(scaled.left + scaled.width() / 10, scaled.centerY()))
                assertEquals(Color.BLUE, screenshot.getPixel(scaled.right - scaled.width() / 10, scaled.centerY()))
                assertTrue("old image remains outside scaled bounds",
                    screenshot.getPixel(original.left + 2, original.centerY()) != Color.RED)
            } finally { screenshot.recycle() }
        }
        tapNode(textNode(R.string.image_scale_uniform))
        awaitCondition { hasDescription(R.string.scale_edge_left) }
        val before = bounds(R.string.image_scale_preview)
        val left = bounds(R.string.scale_edge_left)
        drag(left.exactCenterX(), left.exactCenterY(), left.exactCenterX() + before.width() * 0.12f, left.exactCenterY())
        awaitCondition { bounds(R.string.image_scale_preview).width() < before.width() }
        val stretched = bounds(R.string.image_scale_preview)
        assertCentered(original, stretched)
        assertEquals(before.height(), stretched.height())
        tapNode(textNode(R.string.image_scale_uniform))
        awaitCondition { hasDescription(R.string.scale_corner_bottom_right) }
        val linked = bounds(R.string.image_scale_preview)
        assertCentered(original, linked)
        assertEquals(width.toFloat() / height, linked.width().toFloat() / linked.height(), 0.03f)
    }

    private fun assertCentered(original: Rect, scaled: Rect) {
        assertEquals(original.exactCenterX(), scaled.exactCenterX(), 1f)
        assertEquals(original.exactCenterY(), scaled.exactCenterY(), 1f)
    }
    private fun hasDescription(resource: Int) = nodes().any {
        it.contentDescription?.toString() == instrumentation.targetContext.getString(resource)
    }
    private fun descriptionNode(resource: Int) = nodes().first {
        it.contentDescription?.toString() == instrumentation.targetContext.getString(resource)
    }
    private fun textNode(resource: Int) = nodes().first {
        it.text?.toString() == instrumentation.targetContext.getString(resource)
    }
    private fun bounds(resource: Int) = Rect().also { descriptionNode(resource).getBoundsInScreen(it) }
    private fun tapNode(node: AccessibilityNodeInfo) {
        val rect = Rect().also { node.getBoundsInScreen(it) }
        tap(rect.exactCenterX(), rect.exactCenterY())
    }
    private fun awaitCondition(predicate: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 10000
        while (SystemClock.uptimeMillis() < deadline) {
            instrumentation.waitForIdleSync()
            if (predicate()) return
            SystemClock.sleep(50)
        }
        assertTrue("UI condition timed out", predicate())
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
}
