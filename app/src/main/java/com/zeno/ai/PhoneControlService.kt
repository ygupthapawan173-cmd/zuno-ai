package com.zeno.ai

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import org.json.JSONObject
import java.util.Locale

/**
 * Lets Zeno see the screen and tap / type / scroll / press back and home.
 * Only active after the user switches it on in Android Accessibility settings.
 */
class PhoneControlService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: PhoneControlService? = null

        /** Buttons Zeno will never press on its own. */
        private val BLOCKED = listOf(
            "pay now", "payment", "transfer", "send money", "buy now", "place order", "purchase",
            "uninstall", "factory reset", "erase all", "delete account", "confirm order", "proceed to pay",
        )
    }

    private val nodes = mutableMapOf<Int, AccessibilityNodeInfo>()

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    fun currentPackage(): String = rootInActiveWindow?.packageName?.toString() ?: "unknown"

    /** A numbered text description of what is on screen. */
    fun snapshot(): String {
        nodes.clear()
        val root = rootInActiveWindow ?: return "(screen not readable)"
        val sb = StringBuilder()
        var n = 0

        fun walk(node: AccessibilityNodeInfo) {
            if (n >= 90 || sb.length > 6000) return
            val text = node.text?.toString()?.take(60).orEmpty()
            val desc = node.contentDescription?.toString()?.take(60).orEmpty()
            val interesting = node.isClickable || node.isEditable || node.isScrollable ||
                text.isNotBlank() || desc.isNotBlank()
            if (node.isVisibleToUser && interesting) {
                n++
                nodes[n] = node
                val flags = mutableListOf<String>()
                if (node.isClickable) flags.add("click")
                if (node.isEditable) flags.add("edit")
                if (node.isScrollable) flags.add("scroll")
                if (node.isChecked) flags.add("checked")
                sb.append('[').append(n).append("] ")
                    .append(node.className?.toString()?.substringAfterLast('.') ?: "View").append(' ')
                if (text.isNotBlank()) sb.append('"').append(text).append("\" ")
                if (desc.isNotBlank()) sb.append("desc=\"").append(desc).append("\" ")
                if (flags.isNotEmpty()) sb.append('(').append(flags.joinToString(",")).append(')')
                sb.append('\n')
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { walk(it) }
            }
        }
        walk(root)
        return sb.toString().ifBlank { "(empty screen)" }
    }

    private fun blocked(node: AccessibilityNodeInfo): Boolean {
        val label = ((node.text?.toString() ?: "") + " " + (node.contentDescription?.toString() ?: ""))
            .lowercase(Locale.ROOT)
        return BLOCKED.any { label.contains(it) }
    }

    /** Runs one action from the agent and returns a short result for its history. */
    fun perform(a: JSONObject): String {
        return when (a.optString("action")) {
            "open_app" -> CommandHandler(this).openApp(a.optString("name").lowercase(Locale.ROOT))
            "click" -> {
                val node = nodes[a.optInt("id", -1)] ?: return "no element with that id"
                if (blocked(node)) return "BLOCKED for safety: ask the user to do this step themselves"
                var cur: AccessibilityNodeInfo? = node
                while (cur != null && !cur.isClickable) cur = cur.parent
                if (cur != null && cur.performAction(AccessibilityNodeInfo.ACTION_CLICK)) "clicked"
                else {
                    val r = Rect()
                    node.getBoundsInScreen(r)
                    tap(r.centerX().toFloat(), r.centerY().toFloat())
                }
            }
            "type" -> {
                val node = nodes[a.optInt("id", -1)] ?: return "no element with that id"
                val args = Bundle().apply {
                    putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, a.optString("text"))
                }
                node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
                if (node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) "typed" else "typing failed"
            }
            "enter" -> {
                val focus = rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
                if (focus != null && focus.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id)) "enter pressed"
                else "enter not possible, click the search/send button instead"
            }
            "scroll" -> {
                val target = nodes.values.firstOrNull { it.isScrollable } ?: return "nothing to scroll"
                val fwd = a.optString("direction", "down") != "up"
                val act = if (fwd) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
                if (target.performAction(act)) "scrolled" else "cannot scroll further"
            }
            "back" -> if (performGlobalAction(GLOBAL_ACTION_BACK)) "back" else "back failed"
            "home" -> if (performGlobalAction(GLOBAL_ACTION_HOME)) "home" else "home failed"
            "recents" -> if (performGlobalAction(GLOBAL_ACTION_RECENTS)) "recents" else "recents failed"
            "notifications" -> if (performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)) "notifications" else "failed"
            "wait" -> "waited"
            else -> "unknown action"
        }
    }

    private fun tap(x: Float, y: Float): String {
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 60))
            .build()
        return if (dispatchGesture(gesture, null, null)) "tapped" else "tap failed"
    }
}
