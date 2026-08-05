package com.homeremote

import android.accessibilityservice.AccessibilityService
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityEvent

class RemoteAccessibilityService : AccessibilityService() {

    private lateinit var adbInjector: AdbKeyInjector
    private val commandListener: (Command) -> Unit = { handleCommand(it) }

    override fun onServiceConnected() {
        adbInjector = AdbKeyInjector(this)
        CommandBus.subscribe(commandListener)
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        CommandBus.unsubscribe(commandListener)
        adbInjector.close()
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {}
    override fun onInterrupt() {}

    private fun handleCommand(cmd: Command) {
        when (cmd.action) {
            "key" -> handleKey(cmd.value)
            "power" -> performGlobalAction(GLOBAL_ACTION_POWER_DIALOG)
            "text" -> pasteText(cmd.value)
        }
    }

    private fun handleKey(key: String) {
        when (key) {
            "BACK" -> performGlobalAction(GLOBAL_ACTION_BACK)
            "HOME" -> performGlobalAction(GLOBAL_ACTION_HOME)
            "MENU" -> performGlobalAction(GLOBAL_ACTION_RECENTS)
            "DPAD_UP", "DPAD_DOWN", "DPAD_LEFT", "DPAD_RIGHT", "DPAD_CENTER" -> navigateDpad(key)
            "DEL" -> deleteChar()
            "ENTER" -> clickFocused()
        }
    }

    private fun navigateDpad(key: String) {
        if (Build.VERSION.SDK_INT >= 33) {
            val action = when (key) {
                "DPAD_UP" -> 16
                "DPAD_DOWN" -> 17
                "DPAD_LEFT" -> 18
                "DPAD_RIGHT" -> 19
                "DPAD_CENTER" -> 20
                else -> return
            }
            performGlobalAction(action)
        } else {
            val keyCode = when (key) {
                "DPAD_UP" -> 19
                "DPAD_DOWN" -> 20
                "DPAD_LEFT" -> 21
                "DPAD_RIGHT" -> 22
                "DPAD_CENTER" -> 23
                else -> return
            }
            if (adbInjector.injectKeyEvent(keyCode)) return

            // Fallback: accessibility focus navigation
            if (key == "DPAD_CENTER") { clickFocused(); return }
            val root = rootInActiveWindow ?: return
            val direction = when (key) {
                "DPAD_UP" -> View.FOCUS_UP
                "DPAD_DOWN" -> View.FOCUS_DOWN
                "DPAD_LEFT" -> View.FOCUS_LEFT
                "DPAD_RIGHT" -> View.FOCUS_RIGHT
                else -> return
            }
            val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)
                ?: root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            val next = focused?.focusSearch(direction)
            next?.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS)
        }
    }

    private fun clickFocused() {
        val root = rootInActiveWindow ?: return
        val node = root.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)
            ?: root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        node?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    private fun deleteChar() {
        val root = rootInActiveWindow ?: return
        val node = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return
        val text = node.text?.toString() ?: return
        if (text.isNotEmpty()) {
            val args = Bundle()
            args.putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                text.dropLast(1)
            )
            node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        }
    }

    private fun pasteText(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("homeremote", text))
        val root = rootInActiveWindow ?: return
        val node = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return
        node.performAction(AccessibilityNodeInfo.ACTION_PASTE)
    }
}
