package com.omnisolve.overlay

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.webkit.CookieManager
import android.widget.Button
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.omnisolve.overlay.service.OverlayService

class MainActivity : AppCompatActivity() {

    companion object {
        const val PREF_KEY_DISPLAY_MODE = "PREF_DISPLAY_MODE"
        const val MODE_STEALTH = "STEALTH"
        const val MODE_WINDOW = "WINDOW"
    }

    private lateinit var tvGoogleStatus: TextView
    private lateinit var btnLoginGoogle: Button
    private lateinit var rgDisplayMode: RadioGroup
    private lateinit var rbStealthMode: RadioButton
    private lateinit var rbWindowMode: RadioButton
    private lateinit var tvOverlayStatus: TextView
    private lateinit var tvCaptureStatus: TextView
    private lateinit var btnToggleOverlay: Button

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

        tvGoogleStatus = findViewById(R.id.tv_google_status)
        btnLoginGoogle = findViewById(R.id.btn_login_google)
        rgDisplayMode = findViewById(R.id.rg_display_mode)
        rbStealthMode = findViewById(R.id.rb_stealth_mode)
        rbWindowMode = findViewById(R.id.rb_window_mode)
        tvOverlayStatus = findViewById(R.id.tv_overlay_status)
        tvCaptureStatus = findViewById(R.id.tv_capture_status)
        btnToggleOverlay = findViewById(R.id.btn_toggle_overlay)

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

        btnLoginGoogle.setOnClickListener {
            val intent = Intent(this, LoginActivity::class.java)
            loginLauncher.launch(intent)
        }

        findViewById<Button>(R.id.btn_star_github).setOnClickListener {
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
    }

    override fun onResume() {
        super.onResume()
        updatePermissionStatuses()
        updateGoogleLoginStatus()
    }

    private fun updateGoogleLoginStatus() {
        val cookie = CookieManager.getInstance().getCookie("https://gemini.google.com")
        val hasSession = !cookie.isNullOrBlank() && (
            cookie.contains("SID") || cookie.contains("HSID") ||
            cookie.contains("SSID") || cookie.contains("SAPISID") ||
            cookie.contains("ACCOUNT_CHOOSER")
        )

        if (hasSession) {
            tvGoogleStatus.text = "Logged In & Ready ✅ (Unlimited Web Gemini)"
            tvGoogleStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_light))
            btnLoginGoogle.text = "🔄 Manage / Re-Login Google Account"
        } else {
            tvGoogleStatus.text = "Not Logged In (Tap below to login once)"
            tvGoogleStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_orange_light))
            btnLoginGoogle.text = "🔑 Log into Google / Gemini"
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
        val displayMode = prefs.getString(PREF_KEY_DISPLAY_MODE, MODE_STEALTH) ?: MODE_STEALTH

        val serviceIntent = Intent(this, OverlayService::class.java).apply {
            putExtra(OverlayService.EXTRA_RESULT_CODE, resultCode)
            putExtra(OverlayService.EXTRA_RESULT_DATA, resultData)
            putExtra(OverlayService.EXTRA_DISPLAY_MODE, displayMode)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }

        isOverlayRunning = true
        btnToggleOverlay.text = "Stop Overlay Service"
        btnToggleOverlay.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
        Toast.makeText(this, "OmniSolve AI Overlay Active! Open any MCQ app.", Toast.LENGTH_LONG).show()
    }

    private fun stopOverlayForegroundService() {
        val serviceIntent = Intent(this, OverlayService::class.java)
        stopService(serviceIntent)
        isOverlayRunning = false
        btnToggleOverlay.text = "Start Real-Time Overlay Service"
        btnToggleOverlay.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_blue_dark))
        Toast.makeText(this, "Overlay Service Stopped", Toast.LENGTH_SHORT).show()
    }
}
