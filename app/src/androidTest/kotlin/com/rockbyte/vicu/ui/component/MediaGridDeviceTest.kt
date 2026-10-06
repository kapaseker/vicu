package com.rockbyte.vicu.ui.component

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.test.InstrumentationTestCase
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import com.rockbyte.vicu.MainActivity
import com.rockbyte.vicu.repo.MediaItem
import com.rockbyte.vicu.repo.MediaKind
import com.rockbyte.vicu.ui.theme.VicuTheme

/** Exercises the actual shared Compose cards through platform accessibility. */
@Suppress("DEPRECATION")
class MediaGridDeviceTest : InstrumentationTestCase() {
    private var activity: Activity? = null

    override fun tearDown() {
        activity?.let { instrumentation.runOnMainSync { it.finish() } }
        super.tearDown()
    }

    fun testMetadataAndClick() {
        val host = instrumentation.startActivitySync(Intent(instrumentation.targetContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) as MainActivity
        activity = host
        var selected: MediaItem? = null
        val music = MediaItem(Uri.parse("content://test/audio/1"), "完整的很长音乐文件名称-background-music.mp3", MediaKind.AUDIO, 1, 3_665_000)
        fun show(item: MediaItem) {
            instrumentation.runOnMainSync {
                host.setContent { VicuTheme { MediaGrid(listOf(item), false, { selected = it }) } }
            }
            instrumentation.waitForIdleSync()
            SystemClock.sleep(300) // Allow Compose accessibility nodes to reflect the replacement content.
        }
        show(music)
        awaitText(abbreviateMediaFileName(music.name))
        awaitText("1:01:05")
        val card = nodes().first { it.isClickable }
        assertTrue(card.performAction(AccessibilityNodeInfo.ACTION_CLICK))
        instrumentation.waitForIdleSync()
        assertEquals(music, selected)
        show(music.copy(kind = MediaKind.VIDEO, durationMs = 65_000))
        awaitText("1:05")
        show(music.copy(durationMs = 0))
        awaitText(abbreviateMediaFileName(music.name))
        assertFalse(nodes().any { it.text?.toString()?.contains("1:01:05") == true })
        show(music.copy(kind = MediaKind.IMAGE))
        assertFalse(nodes().any { it.text?.toString()?.contains("1:01:05") == true })
    }

    private fun awaitText(text: String) {
        val deadline = SystemClock.uptimeMillis() + 10000
        while (SystemClock.uptimeMillis() < deadline) {
            if (nodes().any { it.text?.toString()?.contains(text) == true }) return
            SystemClock.sleep(50)
        }
        fail("Missing text: $text")
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
