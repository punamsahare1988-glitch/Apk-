package com.example.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Path
import android.os.Build
import android.os.Bundle
import android.util.DisplayMetrics
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.model.RemoteInputEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.lang.ref.WeakReference

class ScreenHostAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = WeakReference(this)
        _isServiceActive.value = true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // No-op, passive listener
    }

    override fun onInterrupt() {
        _isServiceActive.value = false
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        _isServiceActive.value = false
    }

    companion object {
        private var instance: WeakReference<ScreenHostAccessibilityService>? = null

        private val _isServiceActive = MutableStateFlow(false)
        val isServiceActive: StateFlow<Boolean> = _isServiceActive.asStateFlow()

        fun isConnected(): Boolean {
            return instance?.get() != null && _isServiceActive.value
        }

        /**
         * Dispatches a tap/click at relative screen coordinates (0.0 - 1.0)
         */
        fun performTap(relativeX: Float, relativeY: Float) {
            val service = instance?.get() ?: return
            val metrics = service.resources.displayMetrics
            val realX = relativeX * metrics.widthPixels
            val realY = relativeY * metrics.heightPixels

            val path = Path().apply {
                moveTo(realX, realY)
            }
            val stroke = GestureDescription.StrokeDescription(path, 0, 50)
            val gesture = GestureDescription.Builder().addStroke(stroke).build()
            service.dispatchGesture(gesture, null, null)
        }

        /**
         * Dispatches a swipe or drag gesture
         */
        fun performSwipe(startX: Float, startY: Float, endX: Float, endY: Float, durationMs: Long = 250L) {
            val service = instance?.get() ?: return
            val metrics = service.resources.displayMetrics
            val x1 = startX * metrics.widthPixels
            val y1 = startY * metrics.heightPixels
            val x2 = endX * metrics.widthPixels
            val y2 = endY * metrics.heightPixels

            val path = Path().apply {
                moveTo(x1, y1)
                lineTo(x2, y2)
            }
            val stroke = GestureDescription.StrokeDescription(path, 0, durationMs.coerceIn(50L, 1000L))
            val gesture = GestureDescription.Builder().addStroke(stroke).build()
            service.dispatchGesture(gesture, null, null)
        }

        /**
         * Dispatches global system navigation buttons (Back, Home, Recents, etc.)
         */
        fun performSystemAction(actionName: String) {
            val service = instance?.get() ?: return
            when (actionName.uppercase()) {
                "BACK" -> service.performGlobalAction(GLOBAL_ACTION_BACK)
                "HOME" -> service.performGlobalAction(GLOBAL_ACTION_HOME)
                "RECENTS" -> service.performGlobalAction(GLOBAL_ACTION_RECENTS)
                "NOTIFICATIONS" -> service.performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
                "QUICK_SETTINGS" -> service.performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)
                "POWER" -> service.performGlobalAction(GLOBAL_ACTION_POWER_DIALOG)
                "SCREENSHOT" -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        service.performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)
                    }
                }
                "LOCK_SCREEN" -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        service.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
                    }
                }
            }
        }

        /**
         * Injects text into currently focused text box or system clipboard
         */
        fun injectText(context: Context, text: String) {
            val service = instance?.get()
            var injected = false

            if (service != null) {
                try {
                    val rootNode = service.rootInActiveWindow
                    val focusedNode = rootNode?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
                    if (focusedNode != null) {
                        val args = Bundle().apply {
                            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
                        }
                        injected = focusedNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
                    }
                } catch (e: Exception) {
                    // Fallback to clipboard
                }
            }

            if (!injected) {
                try {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    val clip = ClipData.newPlainText("Remote Text", text)
                    clipboard?.setPrimaryClip(clip)
                } catch (e: Exception) {
                    // Ignore
                }
            }
        }

        /**
         * Dispatches generic remote input event from WebSocket
         */
        fun handleRemoteInput(context: Context, event: RemoteInputEvent) {
            when (event.type) {
                "click", "tap", "down" -> performTap(event.x, event.y)
                "swipe" -> performSwipe(event.startX, event.startY, event.endX, event.endY, event.durationMs)
                "action" -> performSystemAction(event.action)
                "text" -> injectText(context, event.text)
            }
        }
    }
}
