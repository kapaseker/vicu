package com.rockbyte.vicu.page

import android.app.Activity
import android.content.Intent
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.test.InstrumentationTestCase
import android.view.InputDevice
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.runtime.key
import com.rockbyte.vicu.MainActivity
import com.rockbyte.vicu.R
import com.rockbyte.vicu.repo.MediaItem
import com.rockbyte.vicu.repo.MediaKind
import com.rockbyte.vicu.repo.MediaSource
import com.rockbyte.vicu.repo.WorksLibraryState
import com.rockbyte.vicu.ui.theme.VicuTheme

/** Exercises both actual page contents, including pointer gestures and accessibility tabs. */
@Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
class MediaSourceUiDeviceTest : InstrumentationTestCase() {
    private var activity: MainActivity? = null
    private var generation = 0
    private var selected: MediaItem? = null

    override fun tearDown() {
        activity?.let { instrumentation.runOnMainSync { it.finish() } }
        super.tearDown()
    }
    private fun host(): MainActivity {
        return activity ?: (instrumentation.startActivitySync(Intent(instrumentation.targetContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) as MainActivity).also { activity = it }
    }
    private fun show(picker: Boolean, denied: Boolean = false, empty: Boolean = false,
                     initialSource: MediaSource = MediaSource.SYSTEM) {
        val system = if (empty) emptyList() else items("system")
        val works = WorksLibraryState(if (empty) emptyList() else items("work"), loading = false)
        val host = host()
        val version = ++generation
        instrumentation.runOnMainSync {
            host.setContent { VicuTheme { key(version) {
                if (picker) MediaPickerContent(
                    MediaPickerUiState(loading = false, items = system, hasAccess = !denied), works,
                    onGrant = {}, onPick = { selected = it }, onBack = {})
                else HomePageContent(
                    MediaLibraryUiState(loading = false, items = system, hasAccess = !denied), works,
                    initialSource, onRefresh = {}, onMediaClick = { selected = it })
            } } }
        }
        await { tab("系统") != null && tab("作品库") != null }
    }
    private fun items(prefix: String) = (0..99).map {
        MediaItem(Uri.parse("content://fixture/$prefix/$it"), "$prefix-$it.mp3", MediaKind.AUDIO, 1_800_000_000, 60_000)
    }

    fun testBothPagesClickSwipeSelectionAndScrollPositions() {
        for (picker in listOf(false, true)) {
            show(picker)
            await { tab("系统")?.isSelected == true && visibleNames("system").isNotEmpty() }
            val bounds = screen()
            drag(bounds.width() * .5f, bounds.height() * .8f, bounds.width() * .5f, bounds.height() * .35f)
            val systemPosition = visibleNames("system")
            assertFalse(systemPosition.isEmpty())
            assertFalse(systemPosition.contains("system-0.mp3"))
            tap(tab("作品库")!!)
            await { tab("作品库")?.isSelected == true && visibleNames("work").contains("work-0.mp3") }
            drag(bounds.width() * .5f, bounds.height() * .8f, bounds.width() * .5f, bounds.height() * .35f)
            val worksPosition = visibleNames("work")
            assertFalse(worksPosition.isEmpty())
            swipe(right = true)
            await { tab("系统")?.isSelected == true && visibleNames("system") == systemPosition }
            swipe(right = false)
            await { tab("作品库")?.isSelected == true && visibleNames("work") == worksPosition }
            selected = null
            val name = worksPosition.first()
            tap(nodes().first { it.text?.toString() == name })
            await { selected?.name == name }
        }
    }

    fun testBothPagesWorksEmptyIsAvailableWithoutSystemPermission() {
        for (picker in listOf(false, true)) {
            show(picker, denied = true, empty = true)
            await { nodes().any { it.text?.toString() == instrumentation.targetContext.getString(R.string.grant_media_permission) } }
            tap(tab("作品库")!!)
            await { nodes().any { it.isVisibleToUser && it.text?.toString() == "你还未创建作品" } }
            swipe(right = true)
            await { tab("系统")?.isSelected == true }
        }
    }

    fun testHomeCanInitiallyOpenWorksAfterSave() {
        show(picker = false, initialSource = MediaSource.WORKS)
        await { tab("作品库")?.isSelected == true && visibleNames("work").contains("work-0.mp3") }
    }

    fun testRealSaveReturnsToWorksTwiceAndLoadsThumbnail() {
        host()
        val context = instrumentation.targetContext
        val works = org.koin.core.context.GlobalContext.get().get<com.rockbyte.vicu.repo.WorksStore>()
        works.query()
        val stem = "works-flow-${System.nanoTime()}"
        val source = context.contentResolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            android.content.ContentValues().apply {
                put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, "$stem.png")
                put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "image/png")
                put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/VicuTests/")
                put(android.provider.MediaStore.MediaColumns.IS_PENDING, 1)
            })!!
        val bitmap = android.graphics.Bitmap.createBitmap(320, 240, android.graphics.Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.RED)
        context.contentResolver.openOutputStream(source)!!.use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 95, it) }
        bitmap.recycle()
        context.contentResolver.update(source, android.content.ContentValues().apply {
            put(android.provider.MediaStore.MediaColumns.IS_PENDING, 0)
        }, null, null)
        try {
            host()
            for (iteration in 1..2) {
                await { tab("系统") != null }
                if (!tab("系统")!!.isSelected) tap(tab("系统")!!)
                await { nodes().any { it.contentDescription?.toString() == "$stem.png" } }
                tap(nodes().first { it.contentDescription?.toString() == "$stem.png" })
                await { nodes().any { it.text?.toString() == context.getString(R.string.image_crop) } }
                tap(nodes().first { it.text?.toString() == context.getString(R.string.image_crop) })
                await { nodes().any { it.text?.toString() == context.getString(R.string.confirm) } }
                tap(nodes().first { it.text?.toString() == context.getString(R.string.confirm) })
                await { nodes().any { it.text?.toString() == context.getString(R.string.saved_to_pictures_folder) } }
                tap(nodes().first { it.text?.toString() == context.getString(R.string.back_to_home) })
                await { tab("作品库")?.isSelected == true }
                assertEquals(iteration, works.query().count { it.name.startsWith(stem) })
                val work = works.query().first { it.name.startsWith(stem) }
                await { nodes().any { it.contentDescription?.toString() == work.name } }
                await {
                    val node = nodes().firstOrNull { it.contentDescription?.toString() == work.name } ?: return@await false
                    val rect = Rect().also { node.getBoundsInScreen(it) }
                    val screenshot = instrumentation.uiAutomation.takeScreenshot()!!
                    try { screenshot.getPixel(rect.centerX(), rect.centerY()) == android.graphics.Color.RED }
                    finally { screenshot.recycle() }
                }
            }
            val screenshot = instrumentation.uiAutomation.takeScreenshot()!!
            try { java.io.File(context.cacheDir, "works-home.png").outputStream().use {
                screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            } } finally { screenshot.recycle() }
        } finally {
            works.query().filter { it.name.startsWith(stem) }.forEach { works.delete(it.uri) }
            context.contentResolver.delete(source, null, null)
        }
    }

    private fun tab(text: String): AccessibilityNodeInfo? {
        var node = nodes().firstOrNull { it.text?.toString() == text }
        while (node != null) {
            if (node.isClickable || node.isSelected) return node
            node = node.parent
        }
        return null
    }
    private fun visibleNames(prefix: String) = nodes().filter { it.isVisibleToUser &&
        it.text?.toString()?.startsWith("$prefix-") == true }.map { it.text.toString() }.toSet()
    private fun screen(): Rect = Rect().also { instrumentation.uiAutomation.rootInActiveWindow!!.getBoundsInScreen(it) }
    private fun tap(node: AccessibilityNodeInfo) {
        val rect = Rect().also { node.getBoundsInScreen(it) }
        drag(rect.exactCenterX(), rect.exactCenterY(), rect.exactCenterX(), rect.exactCenterY())
    }
    private fun swipe(right: Boolean) {
        val bounds = screen()
        val from = bounds.width() * if (right) .2f else .8f
        val to = bounds.width() * if (right) .8f else .2f
        drag(from, bounds.height() * .45f, to, bounds.height() * .45f)
    }
    private fun drag(x: Float, y: Float, toX: Float, toY: Float) {
        val down = SystemClock.uptimeMillis()
        fun event(action: Int, px: Float, py: Float) {
            val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, px, py, 0)
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            try { instrumentation.sendPointerSync(event) } finally { event.recycle() }
            SystemClock.sleep(20)
        }
        event(MotionEvent.ACTION_DOWN, x, y)
        for (i in 1..20) event(MotionEvent.ACTION_MOVE, x + (toX - x) * i / 20, y + (toY - y) * i / 20)
        event(MotionEvent.ACTION_UP, toX, toY)
        instrumentation.waitForIdleSync()
        SystemClock.sleep(400)
    }
    private fun await(predicate: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 10000
        while (SystemClock.uptimeMillis() < deadline) {
            instrumentation.waitForIdleSync()
            if (predicate()) return
            SystemClock.sleep(50)
        }
        assertTrue("UI condition timed out: " + nodes().map { "${it.text}/${it.isSelected}/${it.isClickable}" }, predicate())
    }
    private fun nodes(): List<AccessibilityNodeInfo> {
        if (Build.VERSION.SDK_INT >= 33) instrumentation.uiAutomation.clearCache()
        val result = mutableListOf<AccessibilityNodeInfo>()
        fun visit(node: AccessibilityNodeInfo) {
            result += node
            for (i in 0 until node.childCount) node.getChild(i)?.let(::visit)
        }
        instrumentation.uiAutomation.rootInActiveWindow?.let(::visit)
        return result
    }
}
