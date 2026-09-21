package com.omnisolve.overlay

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.transition.AutoTransition
import android.transition.TransitionManager
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.webkit.CookieManager
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.omnisolve.overlay.service.AutoClickAccessibilityService
import com.omnisolve.overlay.service.OverlayService

class MainActivity : AppCompatActivity() {

    companion object {
        const val PREF_KEY_DISPLAY_MODE = "PREF_DISPLAY_MODE"
        const val PREF_KEY_AUTO_CLICK = "PREF_AUTO_CLICK"
        const val PREF_KEY_AFK_MODE = "PREF_AFK_MODE"
        const val PREF_KEY_SCAN_INTERVAL_SEC = "PREF_SCAN_INTERVAL_SEC"
        const val PREF_KEY_NEXT_DELAY_MS = "PREF_NEXT_DELAY_MS"
        const val PREF_KEY_INSTANT_TRIGGER = "PREF_INSTANT_TRIGGER"
        const val PREF_KEY_HUD_STYLE = "PREF_HUD_STYLE"
        const val PREF_KEY_CAMOUFLAGE = "PREF_CAMOUFLAGE"
        const val PREF_KEY_HAPTIC = "PREF_HAPTIC"
        const val PREF_KEY_ANTI_CHEAT = "PREF_ANTI_CHEAT"
        const val PREF_KEY_EXTRACTION_ENGINE = "PREF_EXTRACTION_ENGINE"
        const val ENGINE_NATIVE = "NATIVE"
        const val ENGINE_OCR = "OCR"
        const val MODE_STEALTH = "STEALTH"
        const val MODE_WINDOW = "WINDOW"
        const val HUD_STYLE_ISLAND = "DYNAMIC_ISLAND"
        const val HUD_STYLE_BUBBLE = "BUBBLE"
        const val PREF_KEY_THEME_MODE = "PREF_THEME_MODE"
        const val THEME_DARK = "DARK"
        const val THEME_LIGHT = "LIGHT"
    }

    private lateinit var mainRoot: RelativeLayout
    private lateinit var headerBar: LinearLayout
    private lateinit var bottomDock: FrameLayout
    private lateinit var btnThemeToggle: FrameLayout
    private lateinit var ivThemeIcon: ImageView
    private lateinit var tvAppTitle: TextView
    private lateinit var tvAppSubtitle: TextView
    private lateinit var tvThemeTag: TextView
    private var currentThemeMode = THEME_DARK

    private lateinit var btnProfileAccount: View
    private lateinit var ivProfileStatusDot: View
    private lateinit var tvGoogleStatus: TextView
    private lateinit var btnLoginGoogle: Button
    private lateinit var rgExtractionEngine: RadioGroup
    private lateinit var rbEngineNative: RadioButton
    private lateinit var rbEngineOcr: RadioButton
    private lateinit var rgHudStyle: RadioGroup
    private lateinit var rbHudIsland: RadioButton
    private lateinit var rbHudBubble: RadioButton
    private lateinit var swBatteryCamouflage: Switch
    private lateinit var swHapticFeedback: Switch
    private lateinit var rgDisplayMode: RadioGroup
    private lateinit var rbStealthMode: RadioButton
    private lateinit var rbWindowMode: RadioButton
    private lateinit var tvOverlayStatus: TextView
    private lateinit var tvCaptureStatus: TextView
    private lateinit var btnToggleOverlay: Button
    private lateinit var swAutoClickBot: Switch
    private lateinit var swAfkMode: Switch
    private lateinit var swAntiCheat: Switch
    private lateinit var swInstantTrigger: Switch
    private lateinit var tvScanIntervalLabel: TextView
    private lateinit var tvSpeedPresetTag: TextView
    private lateinit var sbScanInterval: SeekBar
    private lateinit var tvNextDelayLabel: TextView
    private lateinit var sbNextDelay: SeekBar
    private lateinit var tvAccessibilityStatus: TextView
    private lateinit var btnOpenAccessibility: Button

    private var isOverlayRunning = false
    private val prefs by lazy { getSharedPreferences("OmniSolvePrefs", Context.MODE_PRIVATE) }

    private val loginLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        updateGoogleLoginStatus()
    }

    private val mediaProjectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            tvCaptureStatus.text = "Active & Authorized"
            tvCaptureStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_light))
            startOverlayForegroundService(result.resultCode, result.data!!)
        } else {
            Toast.makeText(this, "Screen capture permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // ─── 1. All View Bindings (Must initialize before any theme or listeners) ──
        mainRoot = findViewById(R.id.main_root)
        headerBar = findViewById(R.id.header_bar)
        bottomDock = findViewById(R.id.bottom_dock)
        btnThemeToggle = findViewById(R.id.btn_theme_toggle)
        ivThemeIcon = findViewById(R.id.iv_theme_icon)
        tvAppTitle = findViewById(R.id.tv_app_title)
        tvAppSubtitle = findViewById(R.id.tv_app_subtitle)
        tvThemeTag = findViewById(R.id.tv_theme_tag)

        btnProfileAccount = findViewById<View>(R.id.btn_profile_account)
        ivProfileStatusDot = findViewById<View>(R.id.iv_profile_status_dot)
        tvGoogleStatus = findViewById(R.id.tv_google_status)
        btnLoginGoogle = findViewById(R.id.btn_login_google)
        rgExtractionEngine = findViewById(R.id.rg_extraction_engine)
        rbEngineNative = findViewById(R.id.rb_engine_native)
        rbEngineOcr = findViewById(R.id.rb_engine_ocr)
        rgHudStyle = findViewById(R.id.rg_hud_style)
        rbHudIsland = findViewById(R.id.rb_hud_island)
        rbHudBubble = findViewById(R.id.rb_hud_bubble)
        swBatteryCamouflage = findViewById(R.id.sw_battery_camouflage)
        swHapticFeedback = findViewById(R.id.sw_haptic_feedback)
        rgDisplayMode = findViewById(R.id.rg_display_mode)
        rbStealthMode = findViewById(R.id.rb_stealth_mode)
        rbWindowMode = findViewById(R.id.rb_window_mode)
        tvOverlayStatus = findViewById(R.id.tv_overlay_status)
        tvCaptureStatus = findViewById(R.id.tv_capture_status)
        btnToggleOverlay = findViewById(R.id.btn_toggle_overlay)
        swAutoClickBot = findViewById(R.id.sw_auto_click_bot)
        swAfkMode = findViewById(R.id.sw_afk_mode)
        swAntiCheat = findViewById(R.id.sw_anti_cheat)
        swInstantTrigger = findViewById(R.id.sw_instant_trigger)
        tvScanIntervalLabel = findViewById(R.id.tv_scan_interval_label)
        tvSpeedPresetTag = findViewById(R.id.tv_speed_preset_tag)
        sbScanInterval = findViewById(R.id.sb_scan_interval)
        tvNextDelayLabel = findViewById(R.id.tv_next_delay_label)
        sbNextDelay = findViewById(R.id.sb_next_delay)
        tvAccessibilityStatus = findViewById(R.id.tv_accessibility_status)
        btnOpenAccessibility = findViewById(R.id.btn_open_accessibility)

        // ─── 2. Setup Click Listeners ─────────────────────────────────────────
        btnThemeToggle.setOnClickListener {
            currentThemeMode = if (currentThemeMode == THEME_DARK) THEME_LIGHT else THEME_DARK
            prefs.edit().putString(PREF_KEY_THEME_MODE, currentThemeMode).apply()
            applyThemeMode(currentThemeMode, animate = true)
            val label = if (currentThemeMode == THEME_LIGHT) "☀️ Crystal Glass (White) Mode" else "🌙 Liquid Glass (Dark) Mode"
            Toast.makeText(this, label, Toast.LENGTH_SHORT).show()
        }

        btnProfileAccount.setOnClickListener {
            loginLauncher.launch(Intent(this, LoginActivity::class.java))
        }

        // Instant Trigger on Screen Change
        swInstantTrigger.isChecked = prefs.getBoolean(PREF_KEY_INSTANT_TRIGGER, true)
        swInstantTrigger.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(PREF_KEY_INSTANT_TRIGGER, isChecked).apply()
        }

        // Scan Interval (1s - 10s)
        val savedInterval = prefs.getInt(PREF_KEY_SCAN_INTERVAL_SEC, 3)
        sbScanInterval.progress = (savedInterval - 1).coerceIn(0, 9)
        updateScanIntervalUI(savedInterval)

        sbScanInterval.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val sec = progress + 1
                updateScanIntervalUI(sec)
                if (fromUser) {
                    prefs.edit().putInt(PREF_KEY_SCAN_INTERVAL_SEC, sec).apply()
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Next Button Delay (500ms - 3500ms)
        val savedNextDelay = prefs.getInt(PREF_KEY_NEXT_DELAY_MS, 1500)
        sbNextDelay.progress = ((savedNextDelay - 500) / 500).coerceIn(0, 6)
        updateNextDelayUI(savedNextDelay)

        sbNextDelay.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val ms = 500 + progress * 500
                updateNextDelayUI(ms)
                if (fromUser) {
                    prefs.edit().putInt(PREF_KEY_NEXT_DELAY_MS, ms).apply()
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Load saved extraction engine (Native vs OCR)
        val savedEngine = prefs.getString(PREF_KEY_EXTRACTION_ENGINE, ENGINE_NATIVE)
        if (savedEngine == ENGINE_OCR) {
            rbEngineOcr.isChecked = true
        } else {
            rbEngineNative.isChecked = true
        }
        rgExtractionEngine.setOnCheckedChangeListener { _, checkedId ->
            val engine = if (checkedId == R.id.rb_engine_ocr) ENGINE_OCR else ENGINE_NATIVE
            prefs.edit().putString(PREF_KEY_EXTRACTION_ENGINE, engine).apply()
            if (engine == ENGINE_NATIVE) {
                Toast.makeText(this, "⚡ Native Mode: 0ms scraping & zero screenshots", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "📷 OCR Mode: Screen capture & ML Kit", Toast.LENGTH_SHORT).show()
            }
        }

        // Load saved HUD style (Dynamic Island vs Bubble)
        val savedHud = prefs.getString(PREF_KEY_HUD_STYLE, HUD_STYLE_ISLAND)
        if (savedHud == HUD_STYLE_BUBBLE) {
            rbHudBubble.isChecked = true
        } else {
            rbHudIsland.isChecked = true
        }
        rgHudStyle.setOnCheckedChangeListener { _, checkedId ->
            val hud = if (checkedId == R.id.rb_hud_bubble) HUD_STYLE_BUBBLE else HUD_STYLE_ISLAND
            prefs.edit().putString(PREF_KEY_HUD_STYLE, hud).apply()
        }

        // Load saved Battery Camouflage preference
        swBatteryCamouflage.isChecked = prefs.getBoolean(PREF_KEY_CAMOUFLAGE, false)
        swBatteryCamouflage.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(PREF_KEY_CAMOUFLAGE, isChecked).apply()
            if (isChecked) {
                Toast.makeText(this, "🔋 Camouflage Active: 91%=A, 92%=B, 93%=C, 94%=D", Toast.LENGTH_SHORT).show()
            }
        }

        // Load saved Silent Haptic Vibration Feedback preference
        swHapticFeedback.isChecked = prefs.getBoolean(PREF_KEY_HAPTIC, true)
        swHapticFeedback.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(PREF_KEY_HAPTIC, isChecked).apply()
            if (isChecked) {
                Toast.makeText(this, "📳 Haptic Enabled: 1 pulse=A, 2=B, 3=C, 4=D", Toast.LENGTH_SHORT).show()
            }
        }

        // Load saved display mode preference
        val savedMode = prefs.getString(PREF_KEY_DISPLAY_MODE, MODE_STEALTH)
        if (savedMode == MODE_WINDOW) {
            rbWindowMode.isChecked = true
        } else {
            rbStealthMode.isChecked = true
        }

        rgDisplayMode.setOnCheckedChangeListener { _, checkedId ->
            val mode = if (checkedId == R.id.rb_window_mode) MODE_WINDOW else MODE_STEALTH
            prefs.edit().putString(PREF_KEY_DISPLAY_MODE, mode).apply()
        }

        // Load saved auto-click preference
        swAutoClickBot.isChecked = prefs.getBoolean(PREF_KEY_AUTO_CLICK, false)
        swAutoClickBot.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked && !AutoClickAccessibilityService.isServiceRunning()) {
                Toast.makeText(this, "⚠️ Enable Accessibility Service first!", Toast.LENGTH_LONG).show()
                swAutoClickBot.isChecked = false
                return@setOnCheckedChangeListener
            }
            prefs.edit().putBoolean(PREF_KEY_AUTO_CLICK, isChecked).apply()
        }

        // Load saved AEI AFK Hands-Free preference
        swAfkMode.isChecked = prefs.getBoolean(PREF_KEY_AFK_MODE, false)
        swAfkMode.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked && !AutoClickAccessibilityService.isServiceRunning()) {
                Toast.makeText(this, "⚠️ Enable Accessibility Service first!", Toast.LENGTH_LONG).show()
                swAfkMode.isChecked = false
                return@setOnCheckedChangeListener
            }
            prefs.edit().putBoolean(PREF_KEY_AFK_MODE, isChecked).apply()
            if (isChecked) {
                Toast.makeText(this, "⚡ AEI AFK Bot: Auto-advances to Next question!", Toast.LENGTH_SHORT).show()
            }
        }

        // Load saved Anti-Cheat preference
        swAntiCheat.isChecked = prefs.getBoolean(PREF_KEY_ANTI_CHEAT, true)
        swAntiCheat.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(PREF_KEY_ANTI_CHEAT, isChecked).apply()
        }

        btnOpenAccessibility.setOnClickListener {
            try {
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                startActivity(intent)
                Toast.makeText(this, "Find 'OCR MCQ Solver' and enable it", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(this, "Could not open accessibility settings", Toast.LENGTH_SHORT).show()
            }
        }

        btnLoginGoogle.setOnClickListener {
            val intent = Intent(this, LoginActivity::class.java)
            loginLauncher.launch(intent)
        }

        findViewById<View>(R.id.btn_star_github).setOnClickListener {
            try {
                val browserIntent = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://github.com/nomaan5541/ocr-mcq-solver")
                )
                startActivity(browserIntent)
            } catch (e: Exception) {
                Toast.makeText(this, "Opening GitHub...", Toast.LENGTH_SHORT).show()
            }
        }

        btnToggleOverlay.setOnClickListener {
            if (!isOverlayRunning) {
                checkOverlayPermissionAndStart()
            } else {
                stopOverlayForegroundService()
            }
        }

        val btnGithub = findViewById<View>(R.id.btn_star_github)
        applyJellyTouch(btnToggleOverlay, btnThemeToggle, btnProfileAccount, btnLoginGoogle, btnOpenAccessibility, btnGithub)

        // Apply saved theme safely now that all views and components are initialized
        currentThemeMode = prefs.getString(PREF_KEY_THEME_MODE, THEME_DARK) ?: THEME_DARK
        applyThemeMode(currentThemeMode, animate = false)
    }

    override fun onResume() {
        super.onResume()
        updatePermissionStatuses()
        updateGoogleLoginStatus()
        updateAccessibilityStatus()
    }

    private fun updateGoogleLoginStatus() {
        val cookie = CookieManager.getInstance().getCookie("https://gemini.google.com")
        val hasSession = !cookie.isNullOrBlank() && (
            cookie.contains("SID") || cookie.contains("HSID") ||
            cookie.contains("SSID") || cookie.contains("SAPISID") ||
            cookie.contains("ACCOUNT_CHOOSER")
        )

        if (hasSession) {
            tvGoogleStatus.text = "Gemini Ready ✅ (Unlimited Web)"
            tvGoogleStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_light))
            btnLoginGoogle.text = "🔄 Manage"
            val dotDrawable = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(Color.parseColor("#10B981"))
                setStroke(3, Color.parseColor("#FFFFFF"))
            }
            ivProfileStatusDot.setBackground(dotDrawable)
        } else {
            tvGoogleStatus.text = "Not Logged In (Tap to login)"
            tvGoogleStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_orange_light))
            btnLoginGoogle.text = "🔑 Login"
            val dotDrawable = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(Color.parseColor("#F59E0B"))
                setStroke(3, Color.parseColor("#FFFFFF"))
            }
            ivProfileStatusDot.setBackground(dotDrawable)
        }
    }

    private fun updatePermissionStatuses() {
        if (Settings.canDrawOverlays(this)) {
            tvOverlayStatus.text = "Granted (Can Overlap Apps)"
            tvOverlayStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_light))
        } else {
            tvOverlayStatus.text = "Not Granted (Tap Start to Enable)"
            tvOverlayStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_light))
        }
    }

    private fun updateAccessibilityStatus() {
        if (AutoClickAccessibilityService.isServiceRunning()) {
            tvAccessibilityStatus.text = "✅ Accessibility Service: Enabled & Running"
            tvAccessibilityStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_light))
        } else {
            tvAccessibilityStatus.text = "⚠️ Accessibility Service: Not Enabled (Required for Auto-Click)"
            tvAccessibilityStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_orange_light))
        }
    }

    private fun checkOverlayPermissionAndStart() {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
            Toast.makeText(this, "Please enable 'Draw over other apps' for OmniSolve, then tap Start again", Toast.LENGTH_LONG).show()
            return
        }

        // Request Screen Recording / MediaProjection permission
        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjectionLauncher.launch(projectionManager.createScreenCaptureIntent())
    }

    private fun startOverlayForegroundService(resultCode: Int, resultData: Intent) {
        val extractionEngine = prefs.getString(PREF_KEY_EXTRACTION_ENGINE, ENGINE_NATIVE) ?: ENGINE_NATIVE
        val displayMode = prefs.getString(PREF_KEY_DISPLAY_MODE, MODE_STEALTH) ?: MODE_STEALTH
        val autoClick = prefs.getBoolean(PREF_KEY_AUTO_CLICK, false)
        val hudStyle = prefs.getString(PREF_KEY_HUD_STYLE, HUD_STYLE_ISLAND) ?: HUD_STYLE_ISLAND
        val camouflage = prefs.getBoolean(PREF_KEY_CAMOUFLAGE, false)
        val haptic = prefs.getBoolean(PREF_KEY_HAPTIC, true)
        val antiCheat = prefs.getBoolean(PREF_KEY_ANTI_CHEAT, true)
        val afkMode = prefs.getBoolean(PREF_KEY_AFK_MODE, false)
        val scanIntervalSec = prefs.getInt(PREF_KEY_SCAN_INTERVAL_SEC, 3)
        val nextDelayMs = prefs.getInt(PREF_KEY_NEXT_DELAY_MS, 1500)
        val instantTrigger = prefs.getBoolean(PREF_KEY_INSTANT_TRIGGER, true)

        val serviceIntent = Intent(this, OverlayService::class.java).apply {
            putExtra(OverlayService.EXTRA_RESULT_CODE, resultCode)
            putExtra(OverlayService.EXTRA_RESULT_DATA, resultData)
            putExtra(OverlayService.EXTRA_DISPLAY_MODE, displayMode)
            putExtra(OverlayService.EXTRA_AUTO_CLICK, autoClick)
            putExtra(OverlayService.EXTRA_AFK_MODE, afkMode)
            putExtra(OverlayService.EXTRA_SCAN_INTERVAL_SEC, scanIntervalSec)
            putExtra(OverlayService.EXTRA_NEXT_DELAY_MS, nextDelayMs)
            putExtra(OverlayService.EXTRA_INSTANT_TRIGGER, instantTrigger)
            putExtra(OverlayService.EXTRA_EXTRACTION_ENGINE, extractionEngine)
            putExtra(OverlayService.EXTRA_HUD_STYLE, hudStyle)
            putExtra(OverlayService.EXTRA_CAMOUFLAGE, camouflage)
            putExtra(OverlayService.EXTRA_HAPTIC, haptic)
            putExtra(OverlayService.EXTRA_ANTI_CHEAT, antiCheat)
            putExtra(OverlayService.EXTRA_THEME_MODE, currentThemeMode)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }

        isOverlayRunning = true
        btnToggleOverlay.text = "⏹️ Stop Overlay Service"
        btnToggleOverlay.setBackgroundResource(R.drawable.bg_glass_card)
        Toast.makeText(this, "OmniSolve AI Overlay Active! Open any MCQ app.", Toast.LENGTH_LONG).show()
    }

    private fun stopOverlayForegroundService() {
        val serviceIntent = Intent(this, OverlayService::class.java)
        stopService(serviceIntent)
        isOverlayRunning = false
        btnToggleOverlay.text = "🚀 Launch OmniSolve Overlay"
        btnToggleOverlay.setBackgroundResource(R.drawable.bg_glass_cta)
        Toast.makeText(this, "Overlay Service Stopped", Toast.LENGTH_SHORT).show()
    }

    private fun updateScanIntervalUI(sec: Int) {
        tvScanIntervalLabel.text = "Scan Speed Interval: ${sec}s"
        when {
            sec <= 1 -> {
                tvSpeedPresetTag.text = "🚀 Turbo"
                tvSpeedPresetTag.setTextColor(Color.parseColor("#F43F5E"))
            }
            sec in 2..3 -> {
                tvSpeedPresetTag.text = "⚡ Fast"
                tvSpeedPresetTag.setTextColor(Color.parseColor("#38BDF8"))
            }
            sec in 4..5 -> {
                tvSpeedPresetTag.text = "🏃 Balanced"
                tvSpeedPresetTag.setTextColor(Color.parseColor("#10B981"))
            }
            else -> {
                tvSpeedPresetTag.text = "🛡️ Stealth"
                tvSpeedPresetTag.setTextColor(Color.parseColor("#A78BFA"))
            }
        }
    }

    private fun updateNextDelayUI(ms: Int) {
        val secStr = String.format("%.1f", ms / 1000f)
        tvNextDelayLabel.text = "Auto-Advance 'Next' Delay: ${secStr}s"
    }

    // ─── Theme Switching (Dark Liquid Glass vs White Crystal Glass) ───────────

    private fun applyThemeMode(mode: String, animate: Boolean = true) {
        try {
            val isDark = (mode == THEME_DARK)

            if (animate) {
                val transition = AutoTransition().apply {
                    duration = 280
                    interpolator = AccelerateDecelerateInterpolator()
                }
                TransitionManager.beginDelayedTransition(mainRoot, transition)
            }

            if (isDark) {
                mainRoot.setBackgroundColor(Color.parseColor("#070B14"))
                headerBar.setBackgroundResource(R.drawable.bg_glass_header)
                bottomDock.setBackgroundResource(R.drawable.bg_glass_header)
                btnThemeToggle.setBackgroundResource(R.drawable.bg_glass_profile)
                if (::btnProfileAccount.isInitialized) {
                    btnProfileAccount.setBackgroundResource(R.drawable.bg_glass_profile)
                }
                ivThemeIcon.setImageResource(R.drawable.ic_theme_sun)
                tvAppTitle.setTextColor(Color.parseColor("#F8FAFC"))
                tvAppSubtitle.setTextColor(Color.parseColor("#94A3B8"))
                tvThemeTag.text = "LIQUID GLASS"
                tvThemeTag.setTextColor(Color.parseColor("#00F2FE"))
            } else {
                mainRoot.setBackgroundColor(Color.parseColor("#F1F5F9"))
                headerBar.setBackgroundResource(R.drawable.bg_glass_header_light)
                bottomDock.setBackgroundResource(R.drawable.bg_glass_header_light)
                btnThemeToggle.setBackgroundResource(R.drawable.bg_glass_profile_light)
                if (::btnProfileAccount.isInitialized) {
                    btnProfileAccount.setBackgroundResource(R.drawable.bg_glass_profile_light)
                }
                ivThemeIcon.setImageResource(R.drawable.ic_theme_moon)
                tvAppTitle.setTextColor(Color.parseColor("#0F172A"))
                tvAppSubtitle.setTextColor(Color.parseColor("#475569"))
                tvThemeTag.text = "CRYSTAL GLASS"
                tvThemeTag.setTextColor(Color.parseColor("#0284C7"))
            }

            applyThemeRecursive(mainRoot, isDark)
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error applying theme", e)
        }
    }

    private fun applyThemeRecursive(view: View, isDark: Boolean) {
        when (view.id) {
            R.id.card_google_account, R.id.card_engine, R.id.card_hud_style,
            R.id.card_camo, R.id.card_haptics, R.id.card_display_mode,
            R.id.card_permissions -> {
                view.setBackgroundResource(if (isDark) R.drawable.bg_glass_card else R.drawable.bg_glass_card_light)
            }
            R.id.card_aei_bot -> {
                view.setBackgroundResource(if (isDark) R.drawable.bg_glass_card_emerald else R.drawable.bg_glass_card_emerald_light)
            }
        }

        if (view is TextView && view !is CompoundButton && view !is Button) {
            val id = view.id
            if (id != R.id.tv_theme_tag &&
                id != R.id.tv_speed_preset_tag && id != R.id.tv_google_status &&
                id != R.id.tv_accessibility_status && id != R.id.tv_capture_status &&
                id != R.id.tv_overlay_status && id != R.id.tv_camouflage_guide) {

                val cur = view.currentTextColor
                if (isDark) {
                    if (cur == Color.parseColor("#0F172A") || cur == Color.parseColor("#1E293B")) {
                        view.setTextColor(Color.parseColor("#F0F4FD"))
                    } else if (cur == Color.parseColor("#475569") || cur == Color.parseColor("#64748B")) {
                        view.setTextColor(Color.parseColor("#94A3B8"))
                    }
                } else {
                    if (cur == Color.parseColor("#F0F4FD") || cur == Color.parseColor("#F8FAFC") || cur == Color.parseColor("#E2E8F0")) {
                        view.setTextColor(Color.parseColor("#0F172A"))
                    } else if (cur == Color.parseColor("#94A3B8")) {
                        view.setTextColor(Color.parseColor("#475569"))
                    }
                }
            }
        }

        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                applyThemeRecursive(view.getChildAt(i), isDark)
            }
        }
    }

    // ─── Interactive Jelly Touch Rebound Animation ────────────────────────────

    private fun applyJellyTouch(vararg views: View?) {
        for (v in views) {
            v?.setOnTouchListener { target, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        target.animate()?.cancel()
                        target.animate()
                            ?.scaleX(0.92f)
                            ?.scaleY(0.92f)
                            ?.setDuration(110)
                            ?.setInterpolator(DecelerateInterpolator())
                            ?.start()
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        target.animate()
                            ?.scaleX(1.0f)
                            ?.scaleY(1.0f)
                            ?.setDuration(400)
                            ?.setInterpolator(OvershootInterpolator(2.8f))
                            ?.start()
                    }
                }
                false // Allow standard onClickListener to still trigger!
            }
        }
    }
}

