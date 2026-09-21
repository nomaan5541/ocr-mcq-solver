package com.omnisolve.overlay.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Minimal AccessibilityService that enables programmatic screen tapping and native UI inspection.
 * - Extracts native text and view coordinates without screenshots (0ms latency, zero FLAG_SECURE detection).
 * - Uses dispatchGesture() (API 24+) or native ACTION_CLICK to interact with any app.
 *
 * The user MUST manually enable this service in:
 *   Settings → Accessibility → OmniSolve Auto-Click
 */
class AutoClickAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "AutoClickA11y"

        @Volatile
        private var instance: AutoClickAccessibilityService? = null

        /**
         * Check if the accessibility service is currently running.
         */
        fun isServiceRunning(): Boolean = instance != null

        /**
         * Extract native text and bounding boxes directly from the OS Accessibility Node tree.
         * Returns (fullExtractedText, list of (textBlock, boundingRect)) or null if unavailable.
         */
        fun extractScreenHierarchy(): Pair<String, List<Pair<String, Rect>>>? {
            val svc = instance
            if (svc == null) {
                Log.w(TAG, "AccessibilityService not running — cannot scrape node hierarchy")
                return null
            }
            return svc.scrapeCurrentWindow()
        }

        /**
         * Perform a tap at the given screen coordinates.
         * Tries native ACTION_CLICK on the node first, then falls back to gesture tap.
         */
        fun performClick(x: Float, y: Float, antiCheat: Boolean = true): Boolean {
            val svc = instance
            if (svc == null) {
                Log.w(TAG, "AccessibilityService not running — cannot click at ($x, $y)")
                return false
            }
            // Try native action click first if antiCheat doesn't explicitly mandate jitter gesture
            if (!antiCheat && svc.tryNativeClick(x, y)) {
                return true
            }
            return svc.dispatchTap(x, y, antiCheat)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d(TAG, "AutoClickAccessibilityService CONNECTED")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Not used — we only need the service for dispatchGesture
    }

    override fun onKeyEvent(event: KeyEvent?): Boolean {
        if (event == null) return false
        if (event.action == KeyEvent.ACTION_DOWN) {
            val keyCode = event.keyCode
            if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                Log.d(TAG, "Hardware volume key pressed: $keyCode")
                val handled = OverlayService.triggerSolveFromHardware()
                if (handled) {
                    return true // Consume event so volume slider is suppressed
                }
            }
        }
        return super.onKeyEvent(event)
    }

    override fun onInterrupt() {
        Log.d(TAG, "AutoClickAccessibilityService INTERRUPTED")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        Log.d(TAG, "AutoClickAccessibilityService DESTROYED")
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    /**
     * Dispatch a humanized tap gesture at the given screen coordinates.
     * Uses GestureDescription API (requires API 24+, our minSdk is 26).
     *
     * Anti-Cheat Avoidance:
     * - Randomized touch hold duration (55ms - 105ms) simulating human finger contact.
     * - Micro-displacement trajectory (< 1.5px) simulating real human capacitive flesh compression.
     */
    private fun dispatchTap(x: Float, y: Float, antiCheat: Boolean = true): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            Log.e(TAG, "dispatchGesture requires API 24+")
            return false
        }

        try {
            val duration = if (antiCheat) {
                55L + kotlin.random.Random.nextLong(50L) // 55ms to 105ms
            } else {
                50L
            }

            val path = Path().apply {
                moveTo(x, y)
                if (antiCheat) {
                    // Micro-movement on touch release simulating finger roll / contact variation
                    val microX = x + (kotlin.random.Random.nextFloat() * 1.5f - 0.75f)
                    val microY = y + (kotlin.random.Random.nextFloat() * 1.5f - 0.75f)
                    lineTo(microX, microY)
                }
            }

            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, duration))
                .build()

            val dispatched = dispatchGesture(gesture, object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    Log.d(TAG, "Anti-Cheat Tap completed at ($x, $y) with duration=${duration}ms")
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    Log.w(TAG, "Tap cancelled at ($x, $y)")
                }
            }, null)

            Log.d(TAG, "dispatchGesture at ($x, $y): dispatched=$dispatched, duration=${duration}ms")
            return dispatched
        } catch (e: Exception) {
            Log.e(TAG, "Failed to dispatch tap at ($x, $y)", e)
            return false
        }
    }

    /**
     * Inspect active window node hierarchy directly without taking screenshots.
     * Extracts all visible text from native Android widgets (TextView, RadioButton, Button, etc.)
     * and maps them to their exact screen bounding rectangles.
     */
    private fun scrapeCurrentWindow(): Pair<String, List<Pair<String, Rect>>>? {
        val root = try {
            rootInActiveWindow
        } catch (e: Exception) {
            Log.e(TAG, "Failed to access rootInActiveWindow: ${e.message}")
            null
        } ?: run {
            Log.w(TAG, "rootInActiveWindow is null — window content not accessible")
            return null
        }

        val rawBlocks = mutableListOf<Pair<String, Rect>>()

        fun traverse(node: AccessibilityNodeInfo?) {
            if (node == null) return

            // Skip OmniSolve's own overlay windows/views
            if (node.packageName != null && node.packageName.toString() == packageName) {
                return
            }

            if (node.isVisibleToUser) {
                val text = node.text?.toString()?.trim()
                val desc = node.contentDescription?.toString()?.trim()
                val effectiveText = when {
                    !text.isNullOrBlank() -> text
                    !desc.isNullOrBlank() -> desc
                    else -> null
                }

                if (!effectiveText.isNullOrBlank()) {
                    val bounds = Rect()
                    node.getBoundsInScreen(bounds)
                    if (bounds.width() > 0 && bounds.height() > 0) {
                        rawBlocks.add(Pair(effectiveText, bounds))
                    }
                }
            }

            val count = node.childCount
            for (i in 0 until count) {
                try {
                    val child = node.getChild(i)
                    traverse(child)
                } catch (_: Exception) {}
            }
        }

        try {
            traverse(root)
        } catch (e: Exception) {
            Log.e(TAG, "Error traversing accessibility tree: ${e.message}", e)
        }

        if (rawBlocks.isEmpty()) {
            Log.w(TAG, "No text blocks found in native accessibility hierarchy")
            return null
        }

        // Sort blocks in natural reading order: Top-to-bottom, Left-to-right
        val sortedBlocks = rawBlocks.sortedWith(Comparator { a, b ->
            val yDiff = a.second.top - b.second.top
            if (Math.abs(yDiff) > 16) {
                yDiff
            } else {
                a.second.left - b.second.left
            }
        })

        // Deduplicate identical text at the exact same screen location
        val uniqueBlocks = mutableListOf<Pair<String, Rect>>()
        val seenSignatures = HashSet<String>()
        for (block in sortedBlocks) {
            val sig = "${block.first}_${block.second.left}_${block.second.top}"
            if (seenSignatures.add(sig)) {
                uniqueBlocks.add(block)
            }
        }

        val fullText = buildString {
            for (block in uniqueBlocks) {
                append(block.first).append("\n")
            }
        }.trim()

        Log.d(TAG, "Native scraping complete: ${uniqueBlocks.size} blocks, ${fullText.length} chars")
        return Pair(fullText, uniqueBlocks)
    }

    /**
     * Attempts native ACTION_CLICK on the deepest clickable accessibility node at the given screen point.
     * Returns true if a native click was successfully handled.
     */
    private fun tryNativeClick(x: Float, y: Float): Boolean {
        val root = try { rootInActiveWindow } catch (_: Exception) { null } ?: return false
        val px = x.toInt()
        val py = y.toInt()
        val tempRect = Rect()
        var bestNode: AccessibilityNodeInfo? = null

        fun findClickableNode(node: AccessibilityNodeInfo?) {
            if (node == null) return
            if (node.packageName != null && node.packageName.toString() == packageName) return

            node.getBoundsInScreen(tempRect)
            if (tempRect.contains(px, py)) {
                if (node.isClickable) {
                    bestNode = node
                }
                for (i in 0 until node.childCount) {
                    try {
                        findClickableNode(node.getChild(i))
                    } catch (_: Exception) {}
                }
            }
        }

        try {
            findClickableNode(root)
            bestNode?.let {
                val success = it.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                if (success) {
                    Log.d(TAG, "Native ACTION_CLICK dispatched successfully to ${it.className}")
                    return true
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Native ACTION_CLICK error: ${e.message}")
        }
        return false
    }
}
