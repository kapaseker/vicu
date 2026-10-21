package com.rockbyte.vicu.ui.component

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.test.InstrumentationTestCase
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import com.rockbyte.vicu.MainActivity
import com.rockbyte.vicu.R
import com.rockbyte.vicu.ui.theme.VicuTheme

/** Exercises the VicuTextField clear icon through platform accessibility. */
@Suppress("DEPRECATION")
class VicuTextFieldTest : InstrumentationTestCase() {
    private var activity: Activity? = null

    override fun tearDown() {
        activity?.let { instrumentation.runOnMainSync { it.finish() } }
        super.tearDown()
    }

    fun testClearIconVisibilityAndAction() {
        val host = instrumentation.startActivitySync(Intent(instrumentation.targetContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) as MainActivity
        activity = host
        var last: String? = null
        val clearDesc = instrumentation.targetContext.getString(R.string.clear)
        fun show(value: String) {
            instrumentation.runOnMainSync {
                host.setContent { VicuTheme { VicuTextField(value = value, onValueChange = { last = it }) } }
            }
            instrumentation.waitForIdleSync()
            SystemClock.sleep(300) // Allow Compose accessibility nodes to reflect the replacement content.
        }
        show("")
        assertFalse(nodes().any { it.contentDescription?.toString() == clearDesc })
        show("sample.mp4")
        assertTrue(nodes().any { it.text?.toString() == "sample.mp4" })
        val clear = nodes().firstOrNull { it.contentDescription?.toString() == clearDesc }
        assertNotNull(clear)
        assertTrue(clear!!.performAction(AccessibilityNodeInfo.ACTION_CLICK))
        instrumentation.waitForIdleSync()
        SystemClock.sleep(300)
        assertEquals("", last)
        assertFalse(nodes().any { it.contentDescription?.toString() == clearDesc })
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
