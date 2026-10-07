package com.rockbyte.vicu.page

import android.content.Intent
import android.graphics.Rect
import android.os.Build
import android.os.SystemClock
import android.test.InstrumentationTestCase
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.runtime.key
import com.rockbyte.vicu.MainActivity
import com.rockbyte.vicu.R
import com.rockbyte.vicu.repo.MediaKind
import com.rockbyte.vicu.repo.SelectedMedia
import com.rockbyte.vicu.ui.theme.VicuTheme

@Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
class MediaFunctionsUiDeviceTest : InstrumentationTestCase() {
    private var activity: MainActivity? = null
    private var generation = 0
    private var clicked = ""

    override fun tearDown() {
        activity?.let { instrumentation.runOnMainSync { it.finish() } }
        super.tearDown()
    }

    private fun show(kind: MediaKind, name: String) {
        val host = activity ?: (instrumentation.startActivitySync(
            Intent(instrumentation.targetContext, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        ) as MainActivity).also { activity = it }
        val version = ++generation
        val media = SelectedMedia("content://fixture/1", name, kind)
        instrumentation.runOnMainSync {
            host.setContent { VicuTheme { key(version) {
                when (kind) {
                    MediaKind.VIDEO -> VideoFunctionsPage(media,
                        onPlayVideo = { clicked = "play" },
                        onCropVideo = { clicked = "crop" },
                        onTrimVideo = { clicked = "trimVideo" },
                        onExportAudio = { clicked = "export" },
                        onConvertVideo = { clicked = "convertVideo" },
                        onReplaceAudio = { clicked = "replace" },
                        onBack = { clicked = "back" })
                    MediaKind.AUDIO -> AudioFunctionsPage(media,
                        onTrimAudio = { clicked = "trimAudio" },
                        onConvertAudio = { clicked = "convertAudio" },
                        onBack = { clicked = "back" })
                    MediaKind.IMAGE -> ImageFunctionsPage(media,
                        onCropImage = { clicked = "cropImage" },
                        onScaleImage = { clicked = "scaleImage" },
                        onBack = { clicked = "back" })
                }
            } } }
        }
        await { nodes().any { it.text?.toString() == name } }
    }

    fun testEachPageShowsOnlyItsFunctionsAndDispatchesClicks() {
        val groups = listOf(
            MediaKind.VIDEO to listOf(R.string.play to "play", R.string.crop to "crop",
                R.string.video_trim to "trimVideo", R.string.export_audio to "export",
                R.string.video_convert to "convertVideo", R.string.video_replace_audio to "replace"),
            MediaKind.AUDIO to listOf(R.string.audio_trim to "trimAudio", R.string.audio_convert to "convertAudio"),
            MediaKind.IMAGE to listOf(R.string.image_crop to "cropImage", R.string.image_scale to "scaleImage"),
        )
        val allLabels = groups.flatMap { it.second }.map { instrumentation.targetContext.getString(it.first) }
        for ((kind, entries) in groups) {
            val name = when (kind) {
                MediaKind.VIDEO -> "短视频.mp4"
                MediaKind.AUDIO -> "完整的中文音乐文件名称".repeat(8) + ".mp3"
                MediaKind.IMAGE -> "very-long-image-file-name-".repeat(8) + ".png"
            }
            show(kind, name)
            val expected = entries.map { instrumentation.targetContext.getString(it.first) }
            await { nodes().mapNotNull { it.text?.toString() }.containsAll(expected) }
            assertEquals(expected.toSet(), nodes().mapNotNull { it.text?.toString() }.filter { it in allLabels }.toSet())
            assertFalse(nodes().any { it.text?.toString()?.contains("选择要对") == true })
            for ((label, action) in entries) {
                clicked = ""
                var node = nodes().first { it.text?.toString() == instrumentation.targetContext.getString(label) }
                while (!node.isClickable) node = node.parent ?: error("No clickable function")
                assertTrue(node.performAction(AccessibilityNodeInfo.ACTION_CLICK))
                await { clicked == action }
            }
            clicked = ""
            val back = nodes().first { it.contentDescription?.toString() == instrumentation.targetContext.getString(R.string.back) }
            click(back)
            await { clicked == "back" }
        }
    }

    fun testShortTitleIsStillAndLongTitlesMoveWithinHeader() {
        for (name in listOf("a.png", "完整的中文文件名称".repeat(10) + ".png", "long-file-name-".repeat(15) + ".png")) {
            show(MediaKind.IMAGE, name)
            SystemClock.sleep(400)
            val title = nodes().first { it.text?.toString() == name }
            val rect = Rect().also { title.getBoundsInScreen(it) }
            val before = instrumentation.uiAutomation.takeScreenshot()!!
            SystemClock.sleep(2000)
            val after = instrumentation.uiAutomation.takeScreenshot()!!
            try {
                var changed = false
                for (y in rect.top until rect.bottom) for (x in rect.left until rect.right) {
                    if (before.getPixel(x, y) != after.getPixel(x, y)) changed = true
                }
                assertEquals("Title movement: $name", name != "a.png", changed)
                java.io.File(instrumentation.targetContext.cacheDir, "functions-title-${generation}.png")
                    .outputStream().use { after.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            } finally { before.recycle(); after.recycle() }
            assertTrue(nodes().any { it.text?.toString() == name })
        }
    }

    fun testHomeOpensEachKindAndBackKeepsWorksSelected() {
        val host = instrumentation.startActivitySync(
            Intent(instrumentation.targetContext, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        ) as MainActivity
        activity = host
        val store = org.koin.core.context.GlobalContext.get().get<com.rockbyte.vicu.repo.WorksStore>()
        val repo = org.koin.core.context.GlobalContext.get().get<com.rockbyte.vicu.repo.WorksRepo>()
        val fixtures = mutableListOf<android.net.Uri>()
        val entries = listOf(MediaKind.VIDEO to R.string.video_trim,
            MediaKind.AUDIO to R.string.audio_trim, MediaKind.IMAGE to R.string.image_scale)
        try {
            for ((kind, _) in entries) {
                val extension = when (kind) {
                    MediaKind.VIDEO -> "mp4"
                    MediaKind.AUDIO -> "mp3"
                    MediaKind.IMAGE -> "png"
                }
                val uri = store.create("functions-${System.nanoTime()}", "", extension, kind)
                fixtures += uri
                // Only library metadata and navigation are exercised; no media decoder is invoked by the test.
                instrumentation.targetContext.contentResolver.openOutputStream(uri)!!.use { it.write(1) }
                store.publish(uri)
            }
            repo.refresh()
            await { nodes().any { it.text?.toString() == "作品库" } }
            click(nodes().first { it.text?.toString() == "作品库" })
            for ((index, entry) in entries.withIndex()) {
                val name = store.query().first { it.uri == fixtures[index] }.name
                await { nodes().any { it.contentDescription?.toString() == name } }
                click(nodes().first { it.contentDescription?.toString() == name })
                await { nodes().any { it.text?.toString() == name } }
                assertTrue(nodes().any { it.text?.toString() == instrumentation.targetContext.getString(entry.second) })
                click(nodes().first { it.contentDescription?.toString() == instrumentation.targetContext.getString(R.string.back) })
                await { nodes().any { it.contentDescription?.toString() == name } }
                var tab = nodes().first { it.text?.toString() == "作品库" }
                while (!tab.isSelected && tab.parent != null) tab = tab.parent
                assertTrue("Back must preserve the selected library", tab.isSelected)
            }
        } finally {
            fixtures.forEach { store.delete(it) }
            repo.refresh()
        }
    }

    private fun click(start: AccessibilityNodeInfo) {
        var node = start
        while (!node.isClickable) node = node.parent ?: error("No clickable ancestor")
        assertTrue(node.performAction(AccessibilityNodeInfo.ACTION_CLICK))
        instrumentation.waitForIdleSync()
    }

    private fun await(predicate: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 10000
        while (SystemClock.uptimeMillis() < deadline) {
            instrumentation.waitForIdleSync()
            if (predicate()) return
            SystemClock.sleep(50)
        }
        assertTrue("UI condition timed out", predicate())
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
