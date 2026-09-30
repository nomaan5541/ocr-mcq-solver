package com.omnisolve.overlay.service

import android.annotation.SuppressLint
import android.app.*
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.media.projection.MediaProjectionManager
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.transition.AutoTransition
import android.transition.TransitionManager
import android.util.DisplayMetrics
import android.util.Log
import android.view.*
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.webkit.*
import android.widget.*
import androidx.core.app.NotificationCompat
import com.omnisolve.overlay.MainActivity
import com.omnisolve.overlay.R
import com.omnisolve.overlay.capture.OcrEngine
import com.omnisolve.overlay.capture.ScreenCaptureManager
import kotlinx.coroutines.*
import java.util.regex.Pattern

class OverlayService : Service() {

    companion object {
        private const val TAG = "OverlayService"
        const val CHANNEL_ID = "OmniSolveOverlayChannel"
        const val NOTIFICATION_ID = 1001
        const val EXTRA_RESULT_CODE = "EXTRA_RESULT_CODE"
        const val EXTRA_RESULT_DATA = "EXTRA_RESULT_DATA"
        const val EXTRA_DISPLAY_MODE = "EXTRA_DISPLAY_MODE"
        const val EXTRA_AUTO_CLICK = "EXTRA_AUTO_CLICK"
        const val EXTRA_AFK_MODE = "EXTRA_AFK_MODE"
        const val EXTRA_SCAN_INTERVAL_SEC = "EXTRA_SCAN_INTERVAL_SEC"
        const val EXTRA_NEXT_DELAY_MS = "EXTRA_NEXT_DELAY_MS"
        const val EXTRA_INSTANT_TRIGGER = "EXTRA_INSTANT_TRIGGER"
        const val EXTRA_EXTRACTION_ENGINE = "EXTRA_EXTRACTION_ENGINE"
        const val EXTRA_HUD_STYLE = "EXTRA_HUD_STYLE"
        const val EXTRA_CAMOUFLAGE = "EXTRA_CAMOUFLAGE"
        const val EXTRA_HAPTIC = "EXTRA_HAPTIC"
        const val EXTRA_ANTI_CHEAT = "EXTRA_ANTI_CHEAT"
        const val EXTRA_THEME_MODE = "EXTRA_THEME_MODE"
        const val EXTRA_ISLAND_OPACITY_INDEX = "EXTRA_ISLAND_OPACITY_INDEX"

        private const val GEMINI_WEB_URL = "https://gemini.google.com/"
        private const val CHROME_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"

        @Volatile
        private var instance: OverlayService? = null

        /**
         * Triggered from hardware volume buttons or external shortcuts.
         */
        fun triggerSolveFromHardware(): Boolean {
            val svc = instance ?: return false
            svc.mainHandler.post {
                if (!svc.isScanning) {
                    svc.triggerSolveFlow(autoClickMode = svc.isAutoClickEnabled)
                }
            }
            return true
        }
    }

    private lateinit var windowManager: WindowManager

    // HUD Configuration
    private var extractionEngine = MainActivity.ENGINE_NATIVE
    private var hudStyle = MainActivity.HUD_STYLE_ISLAND
    private var isCamouflageEnabled = false
    private var isHapticFeedbackEnabled = true
    private var isAntiCheatEnabled = true
    private var isAfkAutoAdvanceEnabled = false
    private var scanIntervalMs = 3000L
    private var nextDelayMs = 1500L
    private var isInstantTriggerEnabled = true
    private var themeMode = MainActivity.THEME_DARK

    // AEI Bot Question Signature Tracking (avoids duplicate solves on same question)
    @Volatile
    private var lastSolvedQuestionSignature: String = ""

    private fun computeQuestionSignature(text: String): String {
        return text.lowercase()
            .replace(Regex("[^a-z0-9]"), "")
            .take(70)
    }

    // Dynamic Island specific view references
    private var tvIslandTitle: TextView? = null
    private var ivIslandIcon: ImageView? = null
    private var btnIslandOpacity: TextView? = null

    // Dynamic Island Visibility / Opacity Presets: 100%, 90%, 50%, 25%
    private var currentIslandOpacityIndex = 0
    private val islandOpacityLevels = floatArrayOf(1.0f, 0.90f, 0.50f, 0.25f)
    private val islandOpacityLabels = arrayOf("👁 100%", "👁 90%", "👁 50%", "👁 25%")

    private fun applyIslandOpacity(index: Int, showToast: Boolean = true) {
        currentIslandOpacityIndex = (index + islandOpacityLevels.size) % islandOpacityLevels.size
        val targetAlpha = islandOpacityLevels[currentIslandOpacityIndex]
        val label = islandOpacityLabels[currentIslandOpacityIndex]

        bubbleView?.animate()?.cancel()
        bubbleView?.animate()
            ?.alpha(targetAlpha)
            ?.setDuration(240)
            ?.setInterpolator(DecelerateInterpolator())
            ?.start()

        btnIslandOpacity?.text = label

        try {
            getSharedPreferences("OmniSolvePrefs", Context.MODE_PRIVATE)
                .edit().putInt("PREF_ISLAND_OPACITY_INDEX", currentIslandOpacityIndex).apply()
        } catch (_: Exception) {}

        if (showToast) {
            val pct = (targetAlpha * 100).toInt()
            Toast.makeText(this, "👁 Dynamic Island Visibility: $pct%", Toast.LENGTH_SHORT).show()
        }
    }

    // Bubble overlay (Small stealth HUD)
    private var bubbleView: View? = null
    private lateinit var bubbleParams: WindowManager.LayoutParams
    private var tvAnswerDisplay: TextView? = null
    private var ivScanIcon: ImageView? = null

    private fun getDeviceBatteryPercentage(): Int {
        return try {
            val bm = getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            val pct = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 85
            if (pct in 1..100) pct else 85
        } catch (_: Exception) {
            85
        }
    }

    // Floating Gemini Window overlay
    private var geminiWindowView: View? = null
    private lateinit var windowParams: WindowManager.LayoutParams
    private var geminiWebView: WebView? = null
    private var progressBar: ProgressBar? = null
    private var isWindowVisible = false
    private var currentOpacityIndex = 0
    private val opacityLevels = floatArrayOf(1.0f, 0.75f, 0.45f)
    private val opacityLabels = arrayOf("👁 100%", "👁 75%", "👁 45%")

    // Sizing presets: 0 = S (Compact), 1 = M (Medium), 2 = L (Large)
    private var currentSizeIndex = 0
    private val sizeLabels = arrayOf("📐 S", "📐 M", "📐 L")

    private var displayMode = MainActivity.MODE_STEALTH

    private var screenCaptureManager: ScreenCaptureManager? = null
    private val ocrEngine = OcrEngine()

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val mainHandler = Handler(Looper.getMainLooper())

    private var isScanning = false
    private var isGeminiReady = false

    // Auto-click bot state
    private var isAutoClickEnabled = false
    private var autoClickJob: Job? = null
    private var btnBotToggle: ImageView? = null
    private val AUTO_CLICK_INTERVAL_MS = 5000L // Scan every 5 seconds

    // Pending auto-click data (set before JS injection, consumed by bridge callback)
    @Volatile
    private var pendingAutoClickBlocks: List<Pair<String, Rect>>? = null

    // Hardware volume button fallback receiver
    private var volumeReceiver: BroadcastReceiver? = null
    private var lastVolumeTriggerTime = 0L

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        registerVolumeButtonReceiver()
    }

    private fun registerVolumeButtonReceiver() {
        if (volumeReceiver != null) return
        volumeReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == "android.media.VOLUME_CHANGED_ACTION") {
                    val now = System.currentTimeMillis()
                    if (now - lastVolumeTriggerTime > 1500) { // 1.5s debounce
                        lastVolumeTriggerTime = now
                        Log.d(TAG, "Hardware volume change detected via broadcast receiver")
                        triggerSolveFromHardware()
                    }
                }
            }
        }
        try {
            val filter = IntentFilter("android.media.VOLUME_CHANGED_ACTION")
            registerReceiver(volumeReceiver, filter)
            Log.d(TAG, "Registered volume button fallback receiver")
        } catch (e: Exception) {
            Log.w(TAG, "Could not register volume receiver: ${e.message}")
        }
    }

    private fun unregisterVolumeButtonReceiver() {
        try {
            if (volumeReceiver != null) {
                unregisterReceiver(volumeReceiver)
                volumeReceiver = null
            }
        } catch (_: Exception) {}
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundSafely()

        displayMode = intent?.getStringExtra(EXTRA_DISPLAY_MODE)
            ?: getSharedPreferences("OmniSolvePrefs", Context.MODE_PRIVATE)
                .getString(MainActivity.PREF_KEY_DISPLAY_MODE, MainActivity.MODE_STEALTH)
                ?: MainActivity.MODE_STEALTH

        isAutoClickEnabled = intent?.getBooleanExtra(EXTRA_AUTO_CLICK, false)
            ?: getSharedPreferences("OmniSolvePrefs", Context.MODE_PRIVATE)
                .getBoolean(MainActivity.PREF_KEY_AUTO_CLICK, false)

        isAfkAutoAdvanceEnabled = intent?.getBooleanExtra(EXTRA_AFK_MODE, false)
            ?: getSharedPreferences("OmniSolvePrefs", Context.MODE_PRIVATE)
                .getBoolean(MainActivity.PREF_KEY_AFK_MODE, false)

        val scanSec = intent?.getIntExtra(EXTRA_SCAN_INTERVAL_SEC, -1) ?: -1
        val savedScanSec = if (scanSec > 0) scanSec else getSharedPreferences("OmniSolvePrefs", Context.MODE_PRIVATE)
            .getInt(MainActivity.PREF_KEY_SCAN_INTERVAL_SEC, 3)
        scanIntervalMs = (savedScanSec.coerceIn(1, 10) * 1000L)

        val nextDelay = intent?.getIntExtra(EXTRA_NEXT_DELAY_MS, -1) ?: -1
        nextDelayMs = if (nextDelay > 0) nextDelay.toLong() else getSharedPreferences("OmniSolvePrefs", Context.MODE_PRIVATE)
            .getInt(MainActivity.PREF_KEY_NEXT_DELAY_MS, 1500).toLong().coerceIn(500L, 4000L)

        isInstantTriggerEnabled = intent?.getBooleanExtra(EXTRA_INSTANT_TRIGGER, true)
            ?: getSharedPreferences("OmniSolvePrefs", Context.MODE_PRIVATE)
                .getBoolean(MainActivity.PREF_KEY_INSTANT_TRIGGER, true)

        extractionEngine = intent?.getStringExtra(EXTRA_EXTRACTION_ENGINE)
            ?: getSharedPreferences("OmniSolvePrefs", Context.MODE_PRIVATE)
                .getString(MainActivity.PREF_KEY_EXTRACTION_ENGINE, MainActivity.ENGINE_NATIVE)
                ?: MainActivity.ENGINE_NATIVE

        hudStyle = intent?.getStringExtra(EXTRA_HUD_STYLE)
            ?: getSharedPreferences("OmniSolvePrefs", Context.MODE_PRIVATE)
                .getString(MainActivity.PREF_KEY_HUD_STYLE, MainActivity.HUD_STYLE_ISLAND)
                ?: MainActivity.HUD_STYLE_ISLAND

        isCamouflageEnabled = intent?.getBooleanExtra(EXTRA_CAMOUFLAGE, false)
            ?: getSharedPreferences("OmniSolvePrefs", Context.MODE_PRIVATE)
                .getBoolean(MainActivity.PREF_KEY_CAMOUFLAGE, false)

        isHapticFeedbackEnabled = intent?.getBooleanExtra(EXTRA_HAPTIC, true)
            ?: getSharedPreferences("OmniSolvePrefs", Context.MODE_PRIVATE)
                .getBoolean(MainActivity.PREF_KEY_HAPTIC, true)

        isAntiCheatEnabled = intent?.getBooleanExtra(EXTRA_ANTI_CHEAT, true)
            ?: getSharedPreferences("OmniSolvePrefs", Context.MODE_PRIVATE)
                .getBoolean(MainActivity.PREF_KEY_ANTI_CHEAT, true)

        themeMode = intent?.getStringExtra(EXTRA_THEME_MODE)
            ?: getSharedPreferences("OmniSolvePrefs", Context.MODE_PRIVATE)
                .getString(MainActivity.PREF_KEY_THEME_MODE, MainActivity.THEME_DARK)
                ?: MainActivity.THEME_DARK

        val opacityIdx = intent?.getIntExtra(EXTRA_ISLAND_OPACITY_INDEX, -1) ?: -1
        if (opacityIdx in islandOpacityLevels.indices) {
            currentIslandOpacityIndex = opacityIdx
        }

        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
            ?: Activity.RESULT_CANCELED
        @Suppress("DEPRECATION")
        val resultData: Intent? = intent?.getParcelableExtra(EXTRA_RESULT_DATA)

        if (resultCode == Activity.RESULT_OK && resultData != null) {
            initScreenCapture(resultCode, resultData)
        } else {
            Log.w(TAG, "No MediaProjection extras — screen capture unavailable")
        }

        mainHandler.postDelayed({
            setupFloatingBubble()
            setupGeminiWindow()
            if (isAutoClickEnabled) startAutoClickBot()
        }, 500)

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAutoClickBot()
        serviceScope.cancel()
        unregisterVolumeButtonReceiver()
        instance = null
        try { screenCaptureManager?.stopCapture() } catch (_: Exception) {}
        try { ocrEngine.close() } catch (_: Exception) {}
        try { bubbleView?.let { windowManager.removeView(it) } } catch (_: Exception) {}
        try { geminiWebView?.destroy() } catch (_: Exception) {}
        try { geminiWindowView?.let { windowManager.removeView(it) } } catch (_: Exception) {}
        Log.d(TAG, "OverlayService destroyed")
    }

    // ─── Init Screen Capture & Foreground ─────────────────────────────────────

    private fun startForegroundSafely() {
        val notification = buildNotification()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            try { startForeground(NOTIFICATION_ID, notification) } catch (_: Exception) {}
        }
    }

    private fun initScreenCapture(resultCode: Int, resultData: Intent) {
        try {
            val mgr = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val projection = mgr.getMediaProjection(resultCode, resultData)
            if (projection == null) {
                Log.e(TAG, "getMediaProjection returned null")
                return
            }
            screenCaptureManager = ScreenCaptureManager(this)
            screenCaptureManager!!.setMediaProjection(projection)
            Log.d(TAG, "Screen capture ready")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to init screen capture", e)
            screenCaptureManager = null
        }
    }

    // ─── 1. Setup Floating Bubble (Stealth HUD) ───────────────────────────────

    private fun setupFloatingBubble() {
        if (bubbleView != null) return

        val isIsland = (hudStyle == MainActivity.HUD_STYLE_ISLAND)
        val layoutRes = if (isIsland) R.layout.layout_dynamic_island else R.layout.layout_floating_bubble

        bubbleView = LayoutInflater.from(this).inflate(layoutRes, null)

        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        bubbleParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            val statusBarH = getStatusBarHeight()
            if (isIsland) {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                x = 0
                y = statusBarH + dpToPx(8)
            } else {
                gravity = Gravity.TOP or Gravity.START
                x = 24
                y = statusBarH + dpToPx(80)
            }
        }

        val bubbleContainer: View
        val expandedToolbar: View
        val btnScan: View
        val btnToggleWindow: View?
        val btnClose: View

        if (isIsland) {
            bubbleContainer = bubbleView!!.findViewById(R.id.island_icon_container)
            expandedToolbar = bubbleView!!.findViewById(R.id.island_expanded_controls)
            btnScan         = bubbleView!!.findViewById(R.id.btn_island_scan)
            tvAnswerDisplay = bubbleView!!.findViewById(R.id.tv_island_answer)
            ivScanIcon      = bubbleView!!.findViewById(R.id.iv_island_scan_icon)
            btnToggleWindow = bubbleView!!.findViewById(R.id.btn_island_toggle_window)
            btnBotToggle    = bubbleView!!.findViewById(R.id.btn_island_bot_toggle)
            btnClose        = bubbleView!!.findViewById(R.id.btn_island_close)
            tvIslandTitle   = bubbleView!!.findViewById(R.id.tv_island_title)
            ivIslandIcon    = bubbleView!!.findViewById(R.id.iv_island_icon)

            if (isCamouflageEnabled) {
                ivIslandIcon?.setImageResource(R.drawable.ic_battery_camouflage)
                tvIslandTitle?.text = "🔋 Battery"
                tvAnswerDisplay?.text = "${getDeviceBatteryPercentage()}%"
                tvAnswerDisplay?.textSize = 13f
            } else {
                ivIslandIcon?.setImageResource(R.drawable.ic_virus_avatar)
                tvIslandTitle?.text = "OmniSolve"
                tvAnswerDisplay?.text = "?"
            }

            // Tapping island body also triggers solve
            bubbleView!!.findViewById<View>(R.id.ll_island_body)?.setOnClickListener {
                if (!isScanning) triggerSolveFlow()
            }
        } else {
            bubbleContainer = bubbleView!!.findViewById(R.id.bubble_icon_container)
            expandedToolbar = bubbleView!!.findViewById(R.id.expanded_toolbar)
            btnScan         = bubbleView!!.findViewById(R.id.btn_scan)
            tvAnswerDisplay = bubbleView!!.findViewById(R.id.tv_answer_display)
            ivScanIcon      = bubbleView!!.findViewById(R.id.iv_scan_icon)
            btnToggleWindow = bubbleView!!.findViewById(R.id.btn_toggle_window)
            btnBotToggle    = bubbleView!!.findViewById(R.id.btn_bot_toggle)
            btnClose        = bubbleView!!.findViewById(R.id.btn_close_toolbar)

            if (isCamouflageEnabled) {
                tvAnswerDisplay?.text = "${getDeviceBatteryPercentage()}%"
                tvAnswerDisplay?.textSize = 13f
            }
        }

        // Opacity / Visibility Toggle Button (100% -> 90% -> 50% -> 25%)
        val btnOpacityView: TextView? = if (isIsland) {
            bubbleView!!.findViewById(R.id.btn_island_opacity)
        } else {
            bubbleView!!.findViewById(R.id.btn_bubble_opacity)
        }
        btnIslandOpacity = btnOpacityView

        val savedOpacity = getSharedPreferences("OmniSolvePrefs", Context.MODE_PRIVATE)
            .getInt("PREF_ISLAND_OPACITY_INDEX", currentIslandOpacityIndex)
        applyIslandOpacity(savedOpacity, showToast = false)

        btnOpacityView?.setOnClickListener {
            applyIslandOpacity(currentIslandOpacityIndex + 1)
        }

        // Update bot toggle icon state
        updateBotToggleUI()

        // Single tap: toggles expanded toolbar with fluid morphing; Double tap: cycles Dynamic Island visibility (100% -> 90% -> 50% -> 25%)
        bubbleContainer.setOnTouchListener(createDragAndTapListener(
            bubbleParams,
            { bubbleView },
            onSingleTap = {
                val group = bubbleView as? ViewGroup
                if (group != null) {
                    val transition = AutoTransition().apply {
                        duration = 240
                        interpolator = OvershootInterpolator(1.2f)
                    }
                    TransitionManager.beginDelayedTransition(group, transition)
                }
                expandedToolbar.visibility = if (expandedToolbar.visibility == View.VISIBLE) View.GONE else View.VISIBLE
                try { windowManager.updateViewLayout(bubbleView, bubbleParams) } catch (_: Exception) {}
            },
            onDoubleTap = {
                applyIslandOpacity(currentIslandOpacityIndex + 1)
            }
        ))

        btnScan.setOnClickListener {
            if (!isScanning) triggerSolveFlow()
        }

        // Tapping the window toggle button or answer box opens/hides Gemini window
        btnToggleWindow?.setOnClickListener {
            toggleGeminiWindow()
        }

        tvAnswerDisplay?.setOnClickListener {
            toggleGeminiWindow()
        }

        // Bot toggle button
        btnBotToggle?.setOnClickListener {
            isAutoClickEnabled = !isAutoClickEnabled
            // Save preference
            getSharedPreferences("OmniSolvePrefs", Context.MODE_PRIVATE)
                .edit().putBoolean(MainActivity.PREF_KEY_AUTO_CLICK, isAutoClickEnabled).apply()
            updateBotToggleUI()
            if (isAutoClickEnabled) {
                if (!AutoClickAccessibilityService.isServiceRunning()) {
                    Toast.makeText(this@OverlayService, "⚠️ Enable Accessibility Service in Settings first!", Toast.LENGTH_LONG).show()
                    isAutoClickEnabled = false
                    updateBotToggleUI()
                } else {
                    startAutoClickBot()
                    val modeMsg = if (isAfkAutoAdvanceEnabled) "⚡ AEI Full AFK Bot ACTIVATED (Auto-Next ⏩)" else "🤖 Auto-Click Bot ACTIVATED"
                    Toast.makeText(this@OverlayService, modeMsg, Toast.LENGTH_SHORT).show()
                }
            } else {
                stopAutoClickBot()
                Toast.makeText(this@OverlayService, "🤖 Auto-Click Bot STOPPED", Toast.LENGTH_SHORT).show()
            }
        }

        // Long press bot button toggles AFK Auto-Advance mode on the fly
        btnBotToggle?.setOnLongClickListener {
            isAfkAutoAdvanceEnabled = !isAfkAutoAdvanceEnabled
            getSharedPreferences("OmniSolvePrefs", Context.MODE_PRIVATE)
                .edit().putBoolean(MainActivity.PREF_KEY_AFK_MODE, isAfkAutoAdvanceEnabled).apply()
            val status = if (isAfkAutoAdvanceEnabled) "⚡ AEI AFK Auto-Advance: ON ⏩" else "⏹️ AEI AFK Auto-Advance: OFF"
            Toast.makeText(this@OverlayService, status, Toast.LENGTH_SHORT).show()
            true
        }

        btnClose.setOnClickListener {
            val group = bubbleView as? ViewGroup
            if (group != null) {
                val transition = AutoTransition().apply {
                    duration = 200
                }
                TransitionManager.beginDelayedTransition(group, transition)
            }
            expandedToolbar.visibility = View.GONE
            try { windowManager.updateViewLayout(bubbleView, bubbleParams) } catch (_: Exception) {}
        }

        try {
            windowManager.addView(bubbleView, bubbleParams)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add floating bubble view", e)
        }
    }

    // ─── 2. Setup Floating Gemini Window / Headless Engine ────────────────────

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupGeminiWindow() {
        if (geminiWindowView != null) return

        geminiWindowView = LayoutInflater.from(this)
            .inflate(R.layout.layout_floating_gemini_window, null)

        val initialWidth = dpToPx(270)
        val initialHeight = dpToPx(310)

        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        windowParams = WindowManager.LayoutParams(
            initialWidth,
            initialHeight,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 24
            y = 120
        }

        // Header controls
        val headerDragArea = geminiWindowView!!.findViewById<View>(R.id.header_drag_area)
        val btnWindowScan = geminiWindowView!!.findViewById<TextView>(R.id.btn_window_scan)
        val btnSize = geminiWindowView!!.findViewById<TextView>(R.id.btn_window_size)
        val btnOpacity = geminiWindowView!!.findViewById<TextView>(R.id.btn_window_opacity)
        val btnMinimize = geminiWindowView!!.findViewById<View>(R.id.btn_window_minimize)
        val btnClose = geminiWindowView!!.findViewById<View>(R.id.btn_window_close)
        val btnResizeGrip = geminiWindowView!!.findViewById<View>(R.id.btn_resize_grip)
        progressBar = geminiWindowView!!.findViewById(R.id.web_progress_bar)
        geminiWebView = geminiWindowView!!.findViewById(R.id.gemini_webview)

        headerDragArea.setOnTouchListener(createDragListener(windowParams, { geminiWindowView }))

        btnWindowScan.setOnClickListener {
            if (!isScanning) triggerSolveFlow()
        }

        val btnNewChat = geminiWindowView!!.findViewById<View?>(R.id.btn_window_new_chat)
        btnNewChat?.setOnClickListener {
            geminiWebView?.loadUrl("https://gemini.google.com/app")
            Toast.makeText(this, "🧹 Started fresh Gemini session", Toast.LENGTH_SHORT).show()
        }

        btnSize.setOnClickListener {
            currentSizeIndex = (currentSizeIndex + 1) % sizeLabels.size
            applySizePreset(currentSizeIndex)
            btnSize.text = sizeLabels[currentSizeIndex]
        }

        btnResizeGrip.setOnTouchListener(createResizeListener())

        btnOpacity.setOnClickListener {
            currentOpacityIndex = (currentOpacityIndex + 1) % opacityLevels.size
            geminiWindowView?.alpha = opacityLevels[currentOpacityIndex]
            btnOpacity.text = opacityLabels[currentOpacityIndex]
        }

        // Minimize / Close window back to bubble (does NOT kill the background service)
        btnMinimize.setOnClickListener {
            showGeminiWindow(false)
        }

        btnClose.setOnClickListener {
            showGeminiWindow(false)
        }

        configureWebView(geminiWebView!!)
        geminiWebView!!.loadUrl(GEMINI_WEB_URL)

        try {
            windowManager.addView(geminiWindowView, windowParams)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add gemini window view", e)
        }

        // If in STEALTH mode, keep Gemini window off-screen but alive
        // (View.GONE makes WebView 0×0 which prevents JS execution)
        if (displayMode == MainActivity.MODE_WINDOW) {
            showGeminiWindow(true)
        } else {
            // Off-screen: WebView still loads and runs JS
            windowParams.width = 1
            windowParams.height = 1
            windowParams.x = -5000
            windowParams.y = -5000
            geminiWindowView!!.alpha = 0f
            isWindowVisible = false
            setWindowFocusable(false)
            try { windowManager.updateViewLayout(geminiWindowView, windowParams) } catch (_: Exception) {}
        }
    }

    private fun applySizePreset(index: Int) {
        val metrics = resources.displayMetrics
        when (index) {
            0 -> {
                windowParams.width = dpToPx(270)
                windowParams.height = dpToPx(310)
            }
            1 -> {
                windowParams.width = dpToPx(340).coerceAtMost((metrics.widthPixels * 0.85).toInt())
                windowParams.height = dpToPx(440).coerceAtMost((metrics.heightPixels * 0.55).toInt())
            }
            2 -> {
                windowParams.width = (metrics.widthPixels * 0.92).toInt().coerceAtMost(dpToPx(420))
                windowParams.height = (metrics.heightPixels * 0.65).toInt().coerceAtMost(dpToPx(580))
            }
        }
        try { windowManager.updateViewLayout(geminiWindowView, windowParams) } catch (_: Exception) {}
    }

    private fun createResizeListener(): View.OnTouchListener {
        return object : View.OnTouchListener {
            private var startW = 0
            private var startH = 0
            private var rawStartX = 0f
            private var rawStartY = 0f

            override fun onTouch(v: View?, e: MotionEvent): Boolean {
                val metrics = resources.displayMetrics
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> {
                        startW = windowParams.width
                        startH = windowParams.height
                        rawStartX = e.rawX
                        rawStartY = e.rawY
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (e.rawX - rawStartX).toInt()
                        val dy = (e.rawY - rawStartY).toInt()
                        val newW = (startW + dx).coerceIn(dpToPx(180), metrics.widthPixels)
                        val newH = (startH + dy).coerceIn(dpToPx(160), metrics.heightPixels)
                        windowParams.width = newW
                        windowParams.height = newH
                        try { windowManager.updateViewLayout(geminiWindowView, windowParams) } catch (_: Exception) {}
                    }
                }
                return true
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView(webView: WebView) {
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            cookieManager.setAcceptThirdPartyCookies(webView, true)
        }

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            userAgentString = CHROME_USER_AGENT
            cacheMode = WebSettings.LOAD_DEFAULT
        }

        // Unified bridge to receive answers from Gemini Web DOM
        // Supports both manual mode (onAnswerResolved) and auto-click mode (onAutoClickAnswer)
        webView.addJavascriptInterface(object {
            @JavascriptInterface
            fun onAnswerResolved(answerLetter: String) {
                val letter = answerLetter.trim().uppercase()
                Log.d(TAG, "Bridge onAnswerResolved: $letter")
                mainHandler.post {
                    setAnswerUI(letter, "#10B981")
                }
            }

            @JavascriptInterface
            fun onAutoClickAnswer(answerLetter: String) {
                val letter = answerLetter.trim().uppercase()
                Log.d(TAG, "AutoClick Bridge received: $letter")
                val blocks = pendingAutoClickBlocks
                pendingAutoClickBlocks = null
                mainHandler.post {
                    setAnswerUI(letter, "#10B981")
                    if (blocks != null) {
                        performAutoClick(letter, blocks)
                    }
                }
            }
        }, "AndroidBridge")

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                if (newProgress < 100) {
                    progressBar?.visibility = View.VISIBLE
                    progressBar?.progress = newProgress
                } else {
                    progressBar?.visibility = View.GONE
                }
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean = false

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                progressBar?.visibility = View.GONE
                isGeminiReady = true
                Log.d(TAG, "Gemini page fully loaded, ready for injection")
            }
        }

        webView.setOnTouchListener { _, _ ->
            setWindowFocusable(true)
            false
        }
    }

    private fun toggleGeminiWindow() {
        if (isWindowVisible) {
            showGeminiWindow(false)
        } else {
            showGeminiWindow(true)
        }
    }

    private fun showGeminiWindow(show: Boolean) {
        isWindowVisible = show
        val winView = geminiWindowView ?: return
        if (show) {
            winView.visibility = View.VISIBLE
            applySizePreset(currentSizeIndex)
            windowParams.x = 24
            windowParams.y = 120
            setWindowFocusable(true)
            try { windowManager.updateViewLayout(winView, windowParams) } catch (_: Exception) {}

            // Resume webview execution when window becomes visible
            geminiWebView?.onResume()

            // Spring Pop In Animation
            winView.scaleX = 0.85f
            winView.scaleY = 0.85f
            winView.alpha = 0f
            winView.animate()
                .scaleX(1.0f)
                .scaleY(1.0f)
                .alpha(opacityLevels[currentOpacityIndex])
                .setDuration(320)
                .setInterpolator(OvershootInterpolator(1.4f))
                .start()
        } else {
            // Spring Collapse Out before moving off-screen
            winView.animate()
                .scaleX(0.85f)
                .scaleY(0.85f)
                .alpha(0f)
                .setDuration(200)
                .setInterpolator(AccelerateInterpolator())
                .withEndAction {
                    windowParams.width = 1
                    windowParams.height = 1
                    windowParams.x = -5000
                    windowParams.y = -5000
                    setWindowFocusable(false)
                    try { windowManager.updateViewLayout(winView, windowParams) } catch (_: Exception) {}
                    // DO NOT call onPause() - keeping WebView active preserves persistent Gemini web session!
                }
                .start()
        }
    }

    private fun setWindowFocusable(focusable: Boolean) {
        if (geminiWindowView == null) return
        val currentFlags = windowParams.flags
        val newFlags = if (focusable) {
            (currentFlags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()) or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        } else {
            currentFlags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        }
        if (currentFlags != newFlags) {
            windowParams.flags = newFlags
            try { windowManager.updateViewLayout(geminiWindowView, windowParams) } catch (_: Exception) {}
        }
    }

    // ─── 3. Solving Flow: Screen OCR → Background Gemini → Single Letter HUD ─

    /**
     * Core solve flow: Extracts text (Native A11y tree or Screen Capture OCR) → Gemini → Answer letter.
     * @param autoClickMode If true, also performs auto-click on the detected answer option.
     */
    private fun triggerSolveFlow(autoClickMode: Boolean = false) {
        if (!isGeminiReady) {
            if (!autoClickMode) {
                Toast.makeText(this, "⏳ Gemini is still loading, please wait...", Toast.LENGTH_SHORT).show()
            }
            return
        }

        val isNativeMode = (extractionEngine == MainActivity.ENGINE_NATIVE)
        val a11yActive = AutoClickAccessibilityService.isServiceRunning()

        if (!isNativeMode && screenCaptureManager == null) {
            if (!autoClickMode) {
                Toast.makeText(this, "Screen capture not ready. Grant permission in app.", Toast.LENGTH_SHORT).show()
            }
            return
        }
        if (isNativeMode && !a11yActive && screenCaptureManager == null) {
            if (!autoClickMode) {
                Toast.makeText(this, "Please enable Accessibility Service or grant Screen Capture permission.", Toast.LENGTH_SHORT).show()
            }
            return
        }

        isScanning = true
        setAnswerUI("...", "#00F2FE", loading = true)
        val btnWindowScan = geminiWindowView?.findViewById<TextView>(R.id.btn_window_scan)
        btnWindowScan?.text = "⏳ Reading..."

        serviceScope.launch {
            try {
                var extractedText = ""
                var textBlocks = emptyList<Pair<String, Rect>>()
                var bitmapToRecycle: Bitmap? = null

                if (isNativeMode && a11yActive) {
                    btnWindowScan?.text = "⚡ Scraping UI..."
                    val scraped = withContext(Dispatchers.Default) {
                        AutoClickAccessibilityService.extractScreenHierarchy()
                    }
                    if (scraped != null && scraped.first.length >= 5) {
                        extractedText = scraped.first
                        textBlocks = scraped.second
                        Log.d(TAG, "Native accessibility scraping succeeded: ${textBlocks.size} blocks, ${extractedText.length} chars")
                    } else if (screenCaptureManager != null) {
                        Log.w(TAG, "Native scraping returned empty, falling back to Screen Capture OCR...")
                        val ocrData = captureAndOcr()
                        if (ocrData == null) {
                            setAnswerUI("CAP", "#F59E0B")
                            btnWindowScan?.text = "⚡ Solve"
                            isScanning = false
                            return@launch
                        }
                        extractedText = ocrData.first
                        textBlocks = ocrData.second
                        bitmapToRecycle = ocrData.third
                    } else {
                        setAnswerUI("TXT", "#F59E0B")
                        btnWindowScan?.text = "⚡ Solve"
                        isScanning = false
                        return@launch
                    }
                } else {
                    val ocrData = captureAndOcr()
                    if (ocrData == null) {
                        setAnswerUI("CAP", "#F59E0B")
                        btnWindowScan?.text = "⚡ Solve"
                        isScanning = false
                        return@launch
                    }
                    extractedText = ocrData.first
                    textBlocks = ocrData.second
                    bitmapToRecycle = ocrData.third
                }

                if (extractedText.isBlank() || extractedText.length < 5) {
                    setAnswerUI("TXT", "#F59E0B")
                    btnWindowScan?.text = "⚡ Solve"
                    isScanning = false
                    bitmapToRecycle?.recycle()
                    return@launch
                }

                // Check if this looks like an MCQ (has option labels)
                val isMcq = Regex("""\b[A-Da-d][.)\s]""").containsMatchIn(extractedText) ||
                            Regex("""\b(option|choice)\s*[A-Da-d]""", RegexOption.IGNORE_CASE).containsMatchIn(extractedText) ||
                            Regex("""\b[1-4][.)\s]""").containsMatchIn(extractedText)

                if (autoClickMode && !isMcq) {
                    // Not an MCQ screen — skip in auto-click mode
                    setAnswerUI("?", "#64748B")
                    btnWindowScan?.text = "⚡ Solve"
                    isScanning = false
                    bitmapToRecycle?.recycle()
                    return@launch
                }

                // AEI AFK Bot: Check if question is identical to already solved question
                val questionSignature = computeQuestionSignature(extractedText)
                if (autoClickMode && questionSignature == lastSolvedQuestionSignature && lastSolvedQuestionSignature.isNotEmpty()) {
                    Log.d(TAG, "AEI AFK Bot: Screen still shows already answered question ($questionSignature). Waiting for new question...")
                    btnWindowScan?.text = "⚡ Solve"
                    isScanning = false
                    bitmapToRecycle?.recycle()
                    return@launch
                }

                if (autoClickMode) {
                    lastSolvedQuestionSignature = questionSignature
                }

                bitmapToRecycle?.recycle()

                // 5. Strict single-letter answer prompt formulation
                val promptText = "CRITICAL INSTRUCTION: You are an expert MCQ Solver. Read the question and the choices below. Identify the single correct option letter (A, B, C, or D).\nSTRICT RULE: Reply ONLY with 'CORRECT_OPTION: X' where X is strictly one letter A, B, C, or D. Do not write any explanations, markdown or greetings.\n\n$extractedText"

                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("OmniSolve Question", promptText))

                // 6. Inject into Gemini Web and activate response watcher
                btnWindowScan?.text = "✨ Solving..."
                withContext(Dispatchers.Main) {
                    if (autoClickMode) {
                        pendingAutoClickBlocks = textBlocks
                    } else {
                        pendingAutoClickBlocks = null
                    }
                    injectAndWatchGeminiWeb(promptText, isAutoClick = autoClickMode)
                }

                delay(2500)
                btnWindowScan?.text = "⚡ Solve"
                isScanning = false

            } catch (e: Exception) {
                Log.e(TAG, "SolveFlow error: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    setAnswerUI("ERR", "#F43F5E")
                    btnWindowScan?.text = "⚡ Solve"
                    isScanning = false
                }
            }
        }
    }

    /**
     * Fallback helper: captures screen via MediaProjection, hides overlays briefly, and extracts text via ML Kit OCR.
     */
    private suspend fun captureAndOcr(): Triple<String, List<Pair<String, Rect>>, Bitmap?>? {
        val scm = screenCaptureManager ?: return null
        withContext(Dispatchers.Main) {
            bubbleView?.visibility = View.INVISIBLE
            if (isWindowVisible) geminiWindowView?.visibility = View.INVISIBLE
        }
        delay(300)

        val bitmap: Bitmap? = withContext(Dispatchers.IO) {
            scm.captureCurrentFrame()
        }

        withContext(Dispatchers.Main) {
            bubbleView?.visibility = View.VISIBLE
            if (isWindowVisible) {
                geminiWindowView?.visibility = View.VISIBLE
                setWindowFocusable(true)
                geminiWebView?.requestFocus()
            }
        }

        if (bitmap == null) return null

        val ocrResult = withContext(Dispatchers.Default) {
            ocrEngine.extractTextWithBounds(bitmap)
        }
        return Triple(ocrResult.first, ocrResult.second, bitmap)
    }

    private fun injectAndWatchGeminiWeb(text: String, isAutoClick: Boolean) {
        val sanitized = text
            .replace("\\", "\\\\")
            .replace("`", "\\`")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "")

        val jsScript = """
            (function() {
                try {
                    let prompt = "$sanitized";
                    
                    function simulateClick(el) {
                        if (!el) return;
                        ['pointerdown', 'mousedown', 'pointerup', 'mouseup', 'click'].forEach(type => {
                            let evt = new MouseEvent(type, { bubbles: true, cancelable: true, view: window });
                            el.dispatchEvent(evt);
                        });
                        try { el.focus(); } catch(_) {}
                    }

                    // 1. Tag all PREVIOUS response turns as old so they can NEVER be read for the current question
                    let oldElements = document.querySelectorAll('.model-response-text, .markdown, message-content, [data-test-id="model-turn"], model-response');
                    for (let i = 0; i < oldElements.length; i++) {
                        oldElements[i].setAttribute('data-omni-old', 'true');
                    }

                    // 2. Focus rich-textarea
                    let richTextarea = document.querySelector('rich-textarea');
                    if (richTextarea) simulateClick(richTextarea);

                    // 3. Find editable input element
                    let input = document.querySelector('rich-textarea div[contenteditable="true"]') ||
                                document.querySelector('rich-textarea p') ||
                                document.querySelector('div.ql-editor') ||
                                document.querySelector('div[contenteditable="true"]') || 
                                document.querySelector('textarea[aria-label*="Prompt"]') ||
                                document.querySelector('textarea') || 
                                document.querySelector('input[type="text"]');
                                
                    if (!input) return "INPUT_NOT_FOUND";

                    simulateClick(input);

                    // 4. Insert prompt
                    if (input.tagName && (input.tagName.toLowerCase() === 'textarea' || input.tagName.toLowerCase() === 'input')) {
                        input.value = prompt;
                        input.dispatchEvent(new Event('input', { bubbles: true }));
                        input.dispatchEvent(new Event('change', { bubbles: true }));
                    } else {
                        try {
                            let selection = window.getSelection();
                            let range = document.createRange();
                            range.selectNodeContents(input);
                            selection.removeAllRanges();
                            selection.addRange(range);
                        } catch(_) {}

                        let execSuccess = false;
                        try {
                            execSuccess = document.execCommand('insertText', false, prompt);
                        } catch(_) {}

                        if (!execSuccess || !input.textContent || input.textContent.trim().length === 0) {
                            input.innerHTML = '<p>' + prompt.replace(/\n/g, '<br>') + '</p>';
                            try {
                                input.dispatchEvent(new InputEvent('beforeinput', { bubbles: true, inputType: 'insertText', data: prompt }));
                                input.dispatchEvent(new InputEvent('input', { bubbles: true, inputType: 'insertText', data: prompt }));
                            } catch(_) {}
                        }

                        input.dispatchEvent(new Event('input', { bubbles: true }));
                        input.dispatchEvent(new Event('change', { bubbles: true }));
                    }

                    // 5. Click Send
                    let attempts = 0;
                    let sendTimer = setInterval(function() {
                        attempts++;
                        let sendBtn = document.querySelector('button[aria-label*="Send"]') || 
                                      document.querySelector('button[aria-label*="send"]') ||
                                      document.querySelector('button[aria-label*="Submit"]') || 
                                      document.querySelector('.send-button') ||
                                      document.querySelector('button.send-button-container') ||
                                      document.querySelector('mat-icon[fonticon="send"]')?.closest('button') ||
                                      document.querySelector('mat-icon[data-mat-icon-name="send"]')?.closest('button') ||
                                      document.querySelector('button[data-test-id="send-button"]');
                                      
                        if (sendBtn && !sendBtn.disabled && sendBtn.getAttribute('aria-disabled') !== 'true') {
                            clearInterval(sendTimer);
                            simulateClick(sendBtn);
                            startResponseObserver();
                        } else if (attempts >= 12) {
                            clearInterval(sendTimer);
                            try {
                                let enterEvt = new KeyboardEvent('keydown', {
                                    key: 'Enter', code: 'Enter', keyCode: 13, which: 13, bubbles: true, cancelable: true
                                });
                                input.dispatchEvent(enterEvt);
                            } catch(_) {}
                            if (sendBtn) simulateClick(sendBtn);
                            startResponseObserver();
                        }
                    }, 100);

                    // 6. Response Observer: inspects ONLY newly created turns (ignoring old ones)
                    function startResponseObserver() {
                        if (window._omniPollInterval) {
                            clearInterval(window._omniPollInterval);
                            window._omniPollInterval = null;
                        }
                        window._omniAnswerDelivered = false;

                        let pollCount = 0;
                        let pollInterval = setInterval(function() {
                            pollCount++;
                            let allResponses = document.querySelectorAll('.model-response-text, .markdown, message-content, [data-test-id="model-turn"]');
                            let newResponses = [];
                            for (let i = 0; i < allResponses.length; i++) {
                                let el = allResponses[i];
                                if (el.getAttribute('data-omni-old') !== 'true' && !el.closest('[data-omni-old="true"]')) {
                                    newResponses.push(el);
                                }
                            }

                            // If Gemini hasn't mounted the new response element yet, keep waiting!
                            if (newResponses.length === 0) {
                                if (pollCount > 90) {
                                    clearInterval(pollInterval);
                                    window._omniPollInterval = null;
                                }
                                return;
                            }

                            let targetElement = newResponses[newResponses.length - 1];
                            let text = (targetElement.textContent || "").trim();
                            if (!text || text.length === 0) return;

                            // Check if model is still actively generating/streaming
                            let isStreaming = !!document.querySelector('button[aria-label*="Stop"], button[aria-label*="stop"], [data-test-id="stop-button"], mat-icon[fonticon="stop"]');

                            // Strict matching first
                            let match = text.match(/CORRECT_OPTION:\s*\(?([A-D])\)?/i);
                            if (!match) match = text.match(/ANSWER:\s*\(?([A-D])\)?/i);
                            if (!match) match = text.match(/Option\s*\(?([A-D])\)?\s*(?:is\s*(?:the\s*)?correct|is\s*right)/i);
                            if (!match) match = text.match(/Correct\s*(?:Option|Answer)[:\s]*\(?([A-D])\)?/i);
                            if (!match) match = text.match(/\b([A-D])\s+is\s+(?:the\s+)?correct\s+(?:option|answer|choice)\b/i);

                            // While streaming, wait until strict pattern is formed
                            if (!match && isStreaming) {
                                return;
                            }

                            // Fallback after generation is completely finished (Stop button gone)
                            if (!match && !isStreaming && pollCount > 6) {
                                match = text.match(/(?:therefore|hence|so|answer is|option)\s*\(?([A-D])\)?/i) ||
                                        text.match(/\b([A-D])\b(?=[^A-D]*$)/) ||
                                        text.match(/^\s*\(?([A-D])\)?(?:\.|\:|\s|$)/m);
                            }

                            if (match && match[1]) {
                                let letter = match[1].toUpperCase();
                                if (['A', 'B', 'C', 'D'].indexOf(letter) !== -1) {
                                    clearInterval(pollInterval);
                                    window._omniPollInterval = null;
                                    if (!window._omniAnswerDelivered && window.AndroidBridge) {
                                        window._omniAnswerDelivered = true;
                                        if ($isAutoClick) {
                                            window.AndroidBridge.onAutoClickAnswer(letter);
                                        } else {
                                            window.AndroidBridge.onAnswerResolved(letter);
                                        }
                                    }
                                    return;
                                }
                            }

                            if (pollCount > 90) { // ~13s timeout
                                clearInterval(pollInterval);
                                window._omniPollInterval = null;
                            }
                        }, 150);

                        window._omniPollInterval = pollInterval;
                    }

                    return "SUCCESS";
                } catch(err) {
                    return "ERROR: " + err.message;
                }
            })();
        """.trimIndent()

        mainHandler.post {
            geminiWebView?.evaluateJavascript(jsScript) { result ->
                Log.d(TAG, "JS Injection result: $result")
                if (result == null || result.contains("INPUT_NOT_FOUND")) {
                    Toast.makeText(this, "Question copied to clipboard!", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun setAnswerUI(text: String, colorHex: String, loading: Boolean = false) {
        mainHandler.post {
            if (isCamouflageEnabled) {
                if (loading) {
                    tvIslandTitle?.text = "🔋 Syncing..."
                    tvAnswerDisplay?.text = "..."
                    tvAnswerDisplay?.setTextColor(Color.parseColor("#38BDF8"))
                } else {
                    val code = when (text.uppercase()) {
                        "A" -> 91
                        "B" -> 92
                        "C" -> 93
                        "D" -> 94
                        else -> null
                    }
                    if (code != null) {
                        tvIslandTitle?.text = "🔋 Battery"
                        tvAnswerDisplay?.text = "${code}%"
                        tvAnswerDisplay?.setTextColor(Color.parseColor("#10B981"))
                    } else {
                        tvIslandTitle?.text = "🔋 Battery"
                        tvAnswerDisplay?.text = if (text.length <= 4) text else "${getDeviceBatteryPercentage()}%"
                        tvAnswerDisplay?.setTextColor(Color.parseColor(colorHex))
                    }
                }
            } else {
                if (loading) {
                    tvIslandTitle?.text = "⚡ Solving..."
                    tvAnswerDisplay?.text = text
                    tvAnswerDisplay?.setTextColor(Color.parseColor(colorHex))
                } else {
                    if (text in listOf("A", "B", "C", "D")) {
                        tvIslandTitle?.text = "⚡ Answer"
                    } else {
                        tvIslandTitle?.text = "OmniSolve"
                    }
                    tvAnswerDisplay?.text = text
                    tvAnswerDisplay?.setTextColor(Color.parseColor(colorHex))
                }
            }

            if (loading) {
                startScanBreathingAnim()
            } else {
                stopScanBreathingAnim()
                ivScanIcon?.setImageResource(android.R.drawable.ic_media_play)
                isScanning = false
                if (text.uppercase() in listOf("A", "B", "C", "D")) {
                    animateAnswerPop(tvAnswerDisplay)
                    triggerHapticFeedback(text)
                }
            }

            try { bubbleView?.let { windowManager.updateViewLayout(it, bubbleParams) } } catch (_: Exception) {}
        }
    }

    /**
     * Silent Haptic Vibration Feedback:
     * 1 short pulse = A
     * 2 short pulses = B
     * 3 short pulses = C
     * 4 short pulses = D
     */
    private fun triggerHapticFeedback(answerLetter: String) {
        if (!isHapticFeedbackEnabled) return
        val pulseCount = when (answerLetter.trim().uppercase()) {
            "A" -> 1
            "B" -> 2
            "C" -> 3
            "D" -> 4
            else -> 0
        }
        if (pulseCount == 0) return

        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            } ?: return

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // Crisp modern haptic composition
                val composition = VibrationEffect.startComposition()
                for (i in 0 until pulseCount) {
                    composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.9f, 0)
                    if (i < pulseCount - 1) {
                        composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.4f, 110)
                    }
                }
                vibrator.vibrate(composition.compose())
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val timings = mutableListOf<Long>(0)
                val pulseMs = 80L
                val pauseMs = 120L
                for (i in 0 until pulseCount) {
                    timings.add(pulseMs)
                    if (i < pulseCount - 1) timings.add(pauseMs)
                }
                val effect = VibrationEffect.createWaveform(timings.toLongArray(), -1)
                vibrator.vibrate(effect)
            } else {
                val timings = mutableListOf<Long>(0)
                for (i in 0 until pulseCount) {
                    timings.add(80L)
                    if (i < pulseCount - 1) timings.add(120L)
                }
                @Suppress("DEPRECATION")
                vibrator.vibrate(timings.toLongArray(), -1)
            }
            Log.d(TAG, "Haptic feedback delivered for option $answerLetter ($pulseCount pulses)")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to deliver haptic feedback: ${e.message}")
        }
    }

    // ─── Auto-Click Bot Logic ─────────────────────────────────────────────────

    private fun updateBotToggleUI() {
        mainHandler.post {
            if (isAutoClickEnabled) {
                btnBotToggle?.setColorFilter(Color.parseColor("#10B981")) // Green = active
            } else {
                btnBotToggle?.setColorFilter(Color.parseColor("#64748B")) // Gray = inactive
            }
        }
    }

    private fun startAutoClickBot() {
        if (autoClickJob?.isActive == true) return
        Log.d(TAG, "Auto-click bot STARTED (interval=${scanIntervalMs}ms, nextDelay=${nextDelayMs}ms, instantTrigger=$isInstantTriggerEnabled)")
        autoClickJob = serviceScope.launch {
            while (isActive && isAutoClickEnabled) {
                if (!isScanning && isGeminiReady) {
                    triggerSolveFlow(autoClickMode = true)
                }
                // When instant trigger is active and waiting for new question, check rapidly (800ms)
                val checkInterval = if (isInstantTriggerEnabled && lastSolvedQuestionSignature.isNotEmpty()) {
                    800L
                } else {
                    scanIntervalMs
                }
                delay(checkInterval)
            }
        }
    }

    private fun stopAutoClickBot() {
        autoClickJob?.cancel()
        autoClickJob = null
        Log.d(TAG, "Auto-click bot STOPPED")
    }

    /**
     * Intelligently locates the option on screen corresponding to the answer letter (A, B, C, D).
     * Handles:
     * - Standard letters: "(A)", "A.", "A)", "[A]", "Option A"
     * - Numeric options: "(1)", "1.", "1)", "[1]", "Option 1" (where A=1, B=2, C=3, D=4)
     * - Cluster analysis: Identifies the group of 4 options vertically aligned, avoiding false matches in browser chrome.
     */
    private fun findOptionTargetRect(targetLetter: String, textBlocks: List<Pair<String, Rect>>): Rect? {
        val letter = targetLetter.uppercase().trim()
        val numStr = when (letter) { "A" -> "1"; "B" -> "2"; "C" -> "3"; "D" -> "4"; else -> "" }

        val allLetters = listOf("A", "B", "C", "D")
        val allNumbers = listOf("1", "2", "3", "4")

        // Screen geometry cutoffs to avoid top browser bar and bottom system bar
        val displayMetrics = resources.displayMetrics
        val screenHeight = displayMetrics.heightPixels
        val topCutoff = (screenHeight * 0.08).toInt()

        // Filter out blocks in top 8% of screen (browser URL bar area)
        val validBlocks = textBlocks.filter { it.second.top >= topCutoff }

        fun getCandidates(keys: List<String>): Map<String, List<Rect>> {
            val map = mutableMapOf<String, MutableList<Rect>>()
            for (k in keys) map[k] = mutableListOf()

            for (block in validBlocks) {
                val t = block.first.trim()
                for (k in keys) {
                    val isMatch = t.matches(Regex("""^\s*\(?${k}\s*[.)\]:-].*""", RegexOption.IGNORE_CASE)) ||
                                  t.matches(Regex("""^\s*Option\s*${k}\b.*""", RegexOption.IGNORE_CASE)) ||
                                  t.matches(Regex("""^\s*\(?${k}\)?\s*$""", RegexOption.IGNORE_CASE))
                    if (isMatch) {
                        map[k]?.add(block.second)
                    }
                }
            }
            return map
        }

        val letterMap = getCandidates(allLetters)
        val numberMap = if (numStr.isNotEmpty()) getCandidates(allNumbers) else emptyMap()

        val letterCount = allLetters.count { (letterMap[it]?.size ?: 0) > 0 }
        val numberCount = allNumbers.count { (numberMap[it]?.size ?: 0) > 0 }

        val useNumbers = (numberCount > letterCount && numberCount >= 2)
        val activeMap = if (useNumbers) numberMap else letterMap
        val activeKey = if (useNumbers) numStr else letter

        val candidates = activeMap[activeKey] ?: emptyList()
        if (candidates.isNotEmpty()) {
            if (candidates.size == 1) return candidates.first()

            // When multiple candidates exist, pick the one vertically aligned with the option cluster
            val otherCandidates = activeMap.filter { it.key != activeKey }.values.flatten()
            if (otherCandidates.isNotEmpty()) {
                val avgX = otherCandidates.map { it.left }.average()
                return candidates.minByOrNull { Math.abs(it.left - avgX) } ?: candidates.first()
            }
            return candidates.first()
        }

        // Secondary fallback: "Option X" anywhere in valid text
        for (block in validBlocks) {
            val t = block.first.trim()
            if (t.contains(Regex("""\bOption\s*${letter}\b""", RegexOption.IGNORE_CASE))) {
                return block.second
            }
        }

        // Tertiary fallback: Starts with letter on its own
        for (block in validBlocks) {
            val t = block.first.trim()
            if (t.matches(Regex("""^${letter}\b.*""", RegexOption.IGNORE_CASE))) {
                return block.second
            }
        }

        return null
    }

    /**
     * Find the bounding box for the correct answer option and tap it via AccessibilityService.
     */
    private fun performAutoClick(answerLetter: String, textBlocks: List<Pair<String, Rect>>) {
        val targetRect = findOptionTargetRect(answerLetter, textBlocks)

        if (targetRect == null) {
            Log.w(TAG, "Could not locate option $answerLetter on screen — text blocks: ${textBlocks.size}")
            val msg = if (isCamouflageEnabled) "🔋 Battery: ${when(answerLetter){"A"->91;"B"->92;"C"->93;"D"->94;else->90}}%" else "🤖 Answer: $answerLetter (could not locate to click)"
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
            return
        }

        serviceScope.launch {
            // Natural human delay: 350ms - 650ms if anti-cheat, 50ms otherwise
            val readingDelay = if (isAntiCheatEnabled) {
                350L + kotlin.random.Random.nextLong(300L)
            } else {
                50L
            }
            delay(readingDelay)

            // Click coordinate calculation:
            // For wide blocks (full question option row), the radio button/checkbox circle is on the left side (~24dp in).
            // For narrow badges (a circular button or letter badge), use horizontal center.
            val clickX = if (targetRect.width() > dpToPx(70)) {
                (targetRect.left + dpToPx(24).toFloat()).coerceIn(targetRect.left + 4f, targetRect.right - 4f)
            } else {
                targetRect.centerX().toFloat()
            }
            val clickY = targetRect.centerY().toFloat()

            Log.d(TAG, "Auto-clicking option $answerLetter at ($clickX, $clickY) [rect=$targetRect]")
            val clicked = AutoClickAccessibilityService.performClick(clickX, clickY, antiCheat = isAntiCheatEnabled)
            withContext(Dispatchers.Main) {
                if (clicked) {
                    val successMsg = if (isCamouflageEnabled) "🔋 Option Synced" else "🤖 Selected: $answerLetter ✅"
                    Toast.makeText(this@OverlayService, successMsg, Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@OverlayService, "⚠️ Answer: $answerLetter — Enable Accessibility Service to auto-click", Toast.LENGTH_LONG).show()
                }
            }

            // AEI AFK Hands-Free Auto-Advance ("Save & Next" / "Submit" / "Next")
            if (clicked && isAfkAutoAdvanceEnabled) {
                val delayTime = if (isAntiCheatEnabled) {
                    (nextDelayMs + kotlin.random.Random.nextLong(300L) - 150L).coerceAtLeast(300L)
                } else {
                    nextDelayMs
                }
                delay(delayTime)

                val nextRect = findNextButtonRect(textBlocks)
                if (nextRect != null) {
                    val nextClickX = nextRect.centerX().toFloat()
                    val nextClickY = nextRect.centerY().toFloat()
                    Log.d(TAG, "AEI AFK Bot: Auto-advancing via button at ($nextClickX, $nextClickY)")
                    val nextClicked = AutoClickAccessibilityService.performClick(nextClickX, nextClickY, antiCheat = isAntiCheatEnabled)
                    withContext(Dispatchers.Main) {
                        if (nextClicked) {
                            Toast.makeText(this@OverlayService, "🤖 Auto-Advanced ⏩", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    Log.d(TAG, "AEI AFK Bot: Next/Submit button not found on screen.")
                }
            }
        }
    }

    /**
     * Find "Save & Next", "Next Question", "Submit", "Continue" button on screen.
     * Excludes final destructive actions like "Finish Exam", "Submit Test".
     */
    private fun findNextButtonRect(textBlocks: List<Pair<String, Rect>>): Rect? {
        val excludePattern = Regex("""\b(Finish\s*Test|Finish\s*Exam|End\s*Test|End\s*Exam|Exit|Quit|Cancel|Close|Previous|Prev|Back)\b""", RegexOption.IGNORE_CASE)

        val p1 = Regex("""\bSave\s*(?:&|and)\s*(?:Next|Continue)\b""", RegexOption.IGNORE_CASE)
        val p2 = Regex("""\b(?:Next\s*Question|Submit\s*(?:&|and)\s*Next|Submit\s*Answer)\b""", RegexOption.IGNORE_CASE)
        val p3 = Regex("""^\s*(?:Next|Submit|Continue|Proceed|Confirm)\s*[$>→»]?\s*$""", RegexOption.IGNORE_CASE)
        val p4 = Regex("""\b(?:Next|Submit|Continue|Proceed)\b""", RegexOption.IGNORE_CASE)
        val p5 = Regex("""^[>→»]$""")

        val screenHeight = resources.displayMetrics.heightPixels

        // Prioritize bottom-half of screen where navigation/submit buttons reside
        val candidateBlocks = textBlocks.filter { !excludePattern.containsMatchIn(it.first) }
            .sortedByDescending { it.second.top }

        // 1. High-priority "Save & Next" / "Save & Continue"
        for (block in candidateBlocks) {
            if (p1.containsMatchIn(block.first)) return block.second
        }
        // 2. "Next Question" / "Submit & Next" / "Submit Answer"
        for (block in candidateBlocks) {
            if (p2.containsMatchIn(block.first)) return block.second
        }
        // 3. Exact "Next" / "Submit" / "Continue"
        for (block in candidateBlocks) {
            if (p3.containsMatchIn(block.first)) return block.second
        }
        // 4. Any words containing Next / Submit / Continue
        for (block in candidateBlocks) {
            if (p4.containsMatchIn(block.first)) return block.second
        }
        // 5. Arrow icon symbols (>, →, ») near bottom
        for (block in candidateBlocks) {
            if (p5.matches(block.first.trim()) && block.second.top > screenHeight * 0.45) return block.second
        }
        return null
    }

    // ─── Touch Listeners with Fluid Jelly Physics ──────────────────────────────

    private fun createDragListener(
        params: WindowManager.LayoutParams,
        viewProvider: () -> View?
    ): View.OnTouchListener {
        return object : View.OnTouchListener {
            private var startX = 0; private var startY = 0
            private var rawX = 0f;  private var rawY = 0f
            private var lastRawX = 0f; private var lastRawY = 0f

            override fun onTouch(v: View?, e: MotionEvent): Boolean {
                val targetView = viewProvider()
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> {
                        startX = params.x; startY = params.y
                        rawX = e.rawX; rawY = e.rawY
                        lastRawX = e.rawX; lastRawY = e.rawY
                        targetView?.animate()?.cancel()
                        targetView?.animate()
                            ?.scaleX(0.98f)
                            ?.scaleY(0.98f)
                            ?.setDuration(120)
                            ?.setInterpolator(DecelerateInterpolator())
                            ?.start()
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val moveDx = e.rawX - lastRawX
                        val moveDy = e.rawY - lastRawY
                        lastRawX = e.rawX; lastRawY = e.rawY

                        val dx = (e.rawX - rawX).toInt()
                        val dy = (e.rawY - rawY).toInt()
                        params.x = startX + dx
                        params.y = startY + dy
                        try {
                            targetView?.let { windowManager.updateViewLayout(it, params) }
                        } catch (_: Exception) {}

                        // Subtle velocity tilt and dynamic stretching on dragging window
                        if (targetView != null) {
                            val tilt = (moveDx * 0.08f).coerceIn(-6f, 6f)
                            targetView.rotation = tilt
                            targetView.scaleX = (0.98f + Math.abs(moveDx) * 0.002f).coerceIn(0.96f, 1.02f)
                            targetView.scaleY = (0.98f + Math.abs(moveDy) * 0.002f).coerceIn(0.96f, 1.02f)
                        }
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        targetView?.animate()
                            ?.scaleX(1.0f)
                            ?.scaleY(1.0f)
                            ?.rotation(0f)
                            ?.setDuration(350)
                            ?.setInterpolator(OvershootInterpolator(2.2f))
                            ?.start()
                    }
                }
                return true
            }
        }
    }

    private fun createDragAndTapListener(
        params: WindowManager.LayoutParams,
        viewProvider: () -> View?,
        onSingleTap: () -> Unit,
        onDoubleTap: () -> Unit
    ): View.OnTouchListener {
        return object : View.OnTouchListener {
            private var startX = 0; private var startY = 0
            private var rawX = 0f;  private var rawY = 0f
            private var lastRawX = 0f; private var lastRawY = 0f
            private var dragging = false
            private var t0 = 0L
            private var lastTapTime = 0L
            private val doubleTapTimeout = 300L
            private var pendingSingleTapRunnable: Runnable? = null

            override fun onTouch(v: View?, e: MotionEvent): Boolean {
                val targetView = v ?: viewProvider()
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> {
                        startX = params.x; startY = params.y
                        rawX = e.rawX; rawY = e.rawY
                        lastRawX = e.rawX; lastRawY = e.rawY
                        dragging = false
                        t0 = System.currentTimeMillis()

                        // Tactile Jelly Squish on Touch Down
                        targetView?.animate()?.cancel()
                        targetView?.animate()
                            ?.scaleX(0.90f)
                            ?.scaleY(0.90f)
                            ?.setDuration(120)
                            ?.setInterpolator(DecelerateInterpolator())
                            ?.start()
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val moveDx = e.rawX - lastRawX
                        val moveDy = e.rawY - lastRawY
                        lastRawX = e.rawX; lastRawY = e.rawY

                        val totalDx = (e.rawX - rawX).toInt()
                        val totalDy = (e.rawY - rawY).toInt()
                        if (Math.abs(totalDx) > 8 || Math.abs(totalDy) > 8) dragging = true

                        params.x = startX + totalDx
                        val safeMinY = getStatusBarHeight()
                        params.y = (startY + totalDy).coerceAtLeast(safeMinY)
                        try {
                            viewProvider()?.let { windowManager.updateViewLayout(it, params) }
                        } catch (_: Exception) {}

                        if (dragging && targetView != null) {
                            // Dynamic jelly tilt and directional velocity stretching
                            val tilt = (moveDx * 0.25f).coerceIn(-14f, 14f)
                            targetView.rotation = tilt

                            val stretchX = (1.0f + Math.abs(moveDx) * 0.004f).coerceIn(0.88f, 1.15f)
                            val stretchY = (1.0f + Math.abs(moveDy) * 0.004f).coerceIn(0.88f, 1.15f)
                            targetView.scaleX = stretchX
                            targetView.scaleY = stretchY
                        }
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        // Drag-to-dismiss check: if dragged to the bottom 10% of screen, minimize overlay
                        val screenH = resources.displayMetrics.heightPixels
                        if (dragging && params.y > (screenH * 0.88)) {
                            targetView?.animate()
                                ?.scaleX(0f)
                                ?.scaleY(0f)
                                ?.alpha(0f)
                                ?.setDuration(240)
                                ?.withEndAction {
                                    stopSelf()
                                }
                                ?.start()
                            Toast.makeText(this@OverlayService, "👋 OmniSolve Closed", Toast.LENGTH_SHORT).show()
                            return true
                        }

                        // Decaying Elastic Wobble Spring Rebound upon finger release
                        targetView?.animate()
                            ?.scaleX(1.0f)
                            ?.scaleY(1.0f)
                            ?.rotation(0f)
                            ?.setDuration(450)
                            ?.setInterpolator(OvershootInterpolator(3.5f))
                            ?.start()

                        if (e.action == MotionEvent.ACTION_UP) {
                            val pressDuration = System.currentTimeMillis() - t0
                            if (!dragging && pressDuration < 350) {
                                val now = System.currentTimeMillis()
                                if (now - lastTapTime < doubleTapTimeout) {
                                    // Double tap detected
                                    pendingSingleTapRunnable?.let { mainHandler.removeCallbacks(it) }
                                    pendingSingleTapRunnable = null
                                    lastTapTime = 0L
                                    onDoubleTap()
                                } else {
                                    lastTapTime = now
                                    pendingSingleTapRunnable?.let { mainHandler.removeCallbacks(it) }
                                    val runnable = Runnable {
                                        onSingleTap()
                                        pendingSingleTapRunnable = null
                                    }
                                    pendingSingleTapRunnable = runnable
                                    mainHandler.postDelayed(runnable, doubleTapTimeout)
                                }
                            }
                        }
                    }
                }
                return true
            }
        }
    }

    // ─── Animation Helpers: Scanning Breath & Answer Spring Pop ───────────────

    private var scanBreathingAnimator: ObjectAnimator? = null

    private fun startScanBreathingAnim() {
        val target = ivScanIcon ?: return
        if (scanBreathingAnimator?.isRunning == true) return

        val scaleX = PropertyValuesHolder.ofFloat(View.SCALE_X, 1.0f, 1.25f, 1.0f)
        val scaleY = PropertyValuesHolder.ofFloat(View.SCALE_Y, 1.0f, 1.25f, 1.0f)
        val alpha = PropertyValuesHolder.ofFloat(View.ALPHA, 1.0f, 0.6f, 1.0f)

        scanBreathingAnimator = ObjectAnimator.ofPropertyValuesHolder(target, scaleX, scaleY, alpha).apply {
            duration = 750
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }

    private fun stopScanBreathingAnim() {
        scanBreathingAnimator?.cancel()
        scanBreathingAnimator = null
        ivScanIcon?.let {
            it.scaleX = 1.0f
            it.scaleY = 1.0f
            it.alpha = 1.0f
        }
    }

    private fun animateAnswerPop(view: View?) {
        if (view == null) return
        view.scaleX = 0.35f
        view.scaleY = 0.35f
        view.animate()
            .scaleX(1.0f)
            .scaleY(1.0f)
            .setDuration(450)
            .setInterpolator(OvershootInterpolator(2.8f))
            .start()
    }

    private fun dpToPx(dp: Int): Int =
        (dp * resources.displayMetrics.density).toInt()

    private fun getStatusBarHeight(): Int {
        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resourceId > 0) resources.getDimensionPixelSize(resourceId) else dpToPx(36)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                CHANNEL_ID, "OmniSolve Overlay", NotificationManager.IMPORTANCE_LOW
            ).apply { description = "MCQ AI solver overlay" }
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("OCR MCQ Solver Active")
            .setContentText("Headless Gemini solver active. Tap bubble to solve.")
            .setSmallIcon(R.drawable.ic_virus_avatar)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
}
