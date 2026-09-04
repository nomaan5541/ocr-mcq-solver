package com.omnisolve.overlay.service

import android.annotation.SuppressLint
import android.app.*
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PixelFormat
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.*
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

        private const val GEMINI_WEB_URL = "https://gemini.google.com/"
        private const val CHROME_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"
    }

    private lateinit var windowManager: WindowManager

    // Bubble overlay (Small stealth HUD)
    private var bubbleView: View? = null
    private lateinit var bubbleParams: WindowManager.LayoutParams
    private var tvAnswerDisplay: TextView? = null
    private var ivScanIcon: ImageView? = null

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

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundSafely()

        displayMode = intent?.getStringExtra(EXTRA_DISPLAY_MODE)
            ?: getSharedPreferences("OmniSolvePrefs", Context.MODE_PRIVATE)
                .getString(MainActivity.PREF_KEY_DISPLAY_MODE, MainActivity.MODE_STEALTH)
                ?: MainActivity.MODE_STEALTH

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
        }, 500)

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
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

        bubbleView = LayoutInflater.from(this)
            .inflate(R.layout.layout_floating_bubble, null)

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
            gravity = Gravity.TOP or Gravity.START
            x = 24
            y = 350
        }

        val bubbleContainer = bubbleView!!.findViewById<View>(R.id.bubble_icon_container)
        val expandedToolbar = bubbleView!!.findViewById<View>(R.id.expanded_toolbar)
        val btnScan         = bubbleView!!.findViewById<View>(R.id.btn_scan)
        tvAnswerDisplay     = bubbleView!!.findViewById(R.id.tv_answer_display)
        ivScanIcon          = bubbleView!!.findViewById(R.id.iv_scan_icon)
        val btnToggleWindow = bubbleView!!.findViewById<View>(R.id.btn_toggle_window)
        val btnClose        = bubbleView!!.findViewById<View>(R.id.btn_close_toolbar)

        // Single tap: toggles expanded toolbar; Double tap: toggles 95% Ghost/Dead-Pixel Mode
        bubbleContainer.setOnTouchListener(createDragAndTapListener(
            bubbleParams,
            { bubbleView },
            onSingleTap = {
                if (expandedToolbar.visibility == View.VISIBLE) {
                    expandedToolbar.visibility = View.GONE
                } else {
                    expandedToolbar.visibility = View.VISIBLE
                }
                try { windowManager.updateViewLayout(bubbleView, bubbleParams) } catch (_: Exception) {}
            },
            onDoubleTap = {
                isGhostMode = !isGhostMode
                if (isGhostMode) {
                    bubbleView?.alpha = 0.05f // 95% transparent ghost mode
                    Toast.makeText(this@OverlayService, "🥷 Ghost Mode: 95% Invisible", Toast.LENGTH_SHORT).show()
                } else {
                    bubbleView?.alpha = 1.0f
                    Toast.makeText(this@OverlayService, "👁 Normal Visibility Restored", Toast.LENGTH_SHORT).show()
                }
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

        btnClose.setOnClickListener {
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

        // If in STEALTH mode, keep Gemini window hidden
        if (displayMode == MainActivity.MODE_WINDOW) {
            showGeminiWindow(true)
        } else {
            geminiWindowView!!.visibility = View.GONE
        }

        try {
            windowManager.addView(geminiWindowView, windowParams)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add gemini window view", e)
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

        // Bridge to receive answers back from Gemini Web DOM
        webView.addJavascriptInterface(object {
            @JavascriptInterface
            fun onAnswerResolved(answerLetter: String) {
                Log.d(TAG, "Bridge onAnswerResolved: $answerLetter")
                mainHandler.post {
                    setAnswerUI(answerLetter.trim().uppercase(), "#10B981")
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
        geminiWindowView?.visibility = if (show) View.VISIBLE else View.GONE
        setWindowFocusable(show)
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

    private fun triggerSolveFlow() {
        if (screenCaptureManager == null) {
            Toast.makeText(this, "Screen capture not ready. Grant permission in app.", Toast.LENGTH_SHORT).show()
            return
        }

        isScanning = true
        setAnswerUI("...", "#00F2FE", loading = true)
        val btnWindowScan = geminiWindowView?.findViewById<TextView>(R.id.btn_window_scan)
        btnWindowScan?.text = "⏳ Reading..."

        serviceScope.launch {
            try {
                // 1. Hide overlay for clean screenshot
                withContext(Dispatchers.Main) {
                    bubbleView?.visibility = View.INVISIBLE
                    if (isWindowVisible) geminiWindowView?.visibility = View.INVISIBLE
                }
                delay(300)

                // 2. Capture frame
                val bitmap: Bitmap? = withContext(Dispatchers.IO) {
                    screenCaptureManager?.captureCurrentFrame()
                }

                // 3. Restore visibility
                withContext(Dispatchers.Main) {
                    bubbleView?.visibility = View.VISIBLE
                    if (isWindowVisible) {
                        geminiWindowView?.visibility = View.VISIBLE
                        setWindowFocusable(true)
                        geminiWebView?.requestFocus()
                    }
                }

                if (bitmap == null) {
                    setAnswerUI("CAP", "#F59E0B")
                    btnWindowScan?.text = "⚡ Solve"
                    isScanning = false
                    return@launch
                }

                // 4. ML Kit OCR
                btnWindowScan?.text = "🧠 Extracting..."
                val extractedText = withContext(Dispatchers.Default) {
                    ocrEngine.extractText(bitmap)
                }
                bitmap.recycle()

                if (extractedText.isBlank() || extractedText.length < 5) {
                    setAnswerUI("TXT", "#F59E0B")
                    btnWindowScan?.text = "⚡ Solve"
                    isScanning = false
                    return@launch
                }

                // 5. Prompt for single-letter answer resolution
                val promptText = "CRITICAL INSTRUCTION: You are an expert MCQ Solver. Read the question and the choices below. Identify the single correct option letter (A, B, C, or D).\nSTRICT RULE: Reply ONLY with 'CORRECT_OPTION: X' where X is strictly one letter A, B, C, or D. Do not write any explanations, markdown or greetings.\n\n$extractedText"

                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("OmniSolve Question", promptText))

                // 6. Inject into Gemini Web and activate response watcher
                btnWindowScan?.text = "✨ Solving..."
                withContext(Dispatchers.Main) {
                    delay(150)
                    injectAndWatchGeminiWeb(promptText)
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

    private fun injectAndWatchGeminiWeb(text: String) {
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

                    // 1. Focus rich-textarea
                    let richTextarea = document.querySelector('rich-textarea');
                    if (richTextarea) simulateClick(richTextarea);

                    // 2. Find editable input element
                    let input = document.querySelector('rich-textarea div[contenteditable="true"]') ||
                                document.querySelector('rich-textarea p') ||
                                document.querySelector('div.ql-editor') ||
                                document.querySelector('div[contenteditable="true"]') || 
                                document.querySelector('textarea[aria-label*="Prompt"]') ||
                                document.querySelector('textarea') || 
                                document.querySelector('input[type="text"]');
                                
                    if (!input) return "INPUT_NOT_FOUND";

                    simulateClick(input);

                    // 3. Insert prompt
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

                    // 4. Click Send
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
                        } else if (attempts >= 15) {
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

                    // 5. Observer to extract strictly A / B / C / D from Gemini's response
                    function startResponseObserver() {
                        let pollCount = 0;
                        let pollInterval = setInterval(function() {
                            pollCount++;
                            let responseElements = document.querySelectorAll('.model-response-text, .markdown, message-content, [data-test-id="model-turn"]');
                            if (responseElements && responseElements.length > 0) {
                                let lastResp = responseElements[responseElements.length - 1];
                                let text = lastResp.textContent || "";
                                
                                // Priority 1: Strict CORRECT_OPTION format
                                let match = text.match(/CORRECT_OPTION:\s*\(?([A-D])\)?/i);
                                
                                // Priority 2: Standard MCQ answer formats
                                if (!match) match = text.match(/ANSWER:\s*\(?([A-D])\)?/i);
                                if (!match) match = text.match(/Option\s*\(?([A-D])\)?\s*(?:is\s*(?:the\s*)?correct|is\s*right)/i);
                                if (!match) match = text.match(/Correct\s*(?:Option|Answer)[:\s]*\(?([A-D])\)?/i);
                                if (!match) match = text.match(/\b([A-D])\s+is\s+(?:the\s+)?correct\s+(?:option|answer|choice)\b/i);
                                if (!match) match = text.match(/^\s*\(?([A-D])\)?(?:\.|\:|\s|$)/m);

                                if (match && match[1]) {
                                    let letter = match[1].toUpperCase();
                                    if (['A', 'B', 'C', 'D'].indexOf(letter) !== -1) {
                                        clearInterval(pollInterval);
                                        if (window.AndroidBridge) {
                                            window.AndroidBridge.onAnswerResolved(letter);
                                        }
                                        return;
                                    }
                                }
                            }

                            if (pollCount > 75) { // 15 seconds timeout
                                clearInterval(pollInterval);
                            }
                        }, 200);
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
            tvAnswerDisplay?.text = text
            tvAnswerDisplay?.setTextColor(Color.parseColor(colorHex))
            if (!loading) {
                ivScanIcon?.setImageResource(android.R.drawable.ic_media_play)
                isScanning = false
            }
        }
    }

    // ─── Touch Listeners ──────────────────────────────────────────────────────

    private fun createDragListener(
        params: WindowManager.LayoutParams,
        viewProvider: () -> View?
    ): View.OnTouchListener {
        return object : View.OnTouchListener {
            private var startX = 0; private var startY = 0
            private var rawX = 0f;  private var rawY = 0f

            override fun onTouch(v: View?, e: MotionEvent): Boolean {
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> {
                        startX = params.x; startY = params.y
                        rawX = e.rawX; rawY = e.rawY
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (e.rawX - rawX).toInt()
                        val dy = (e.rawY - rawY).toInt()
                        params.x = startX + dx
                        params.y = startY + dy
                        try {
                            viewProvider()?.let { windowManager.updateViewLayout(it, params) }
                        } catch (_: Exception) {}
                    }
                }
                return true
            }
        }
    }

    private var isGhostMode = false

    private fun createDragAndTapListener(
        params: WindowManager.LayoutParams,
        viewProvider: () -> View?,
        onSingleTap: () -> Unit,
        onDoubleTap: () -> Unit
    ): View.OnTouchListener {
        return object : View.OnTouchListener {
            private var startX = 0; private var startY = 0
            private var rawX = 0f;  private var rawY = 0f
            private var dragging = false
            private var t0 = 0L
            private var lastTapTime = 0L
            private val doubleTapTimeout = 300L
            private var pendingSingleTapRunnable: Runnable? = null

            override fun onTouch(v: View?, e: MotionEvent): Boolean {
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> {
                        startX = params.x; startY = params.y
                        rawX = e.rawX; rawY = e.rawY
                        dragging = false
                        t0 = System.currentTimeMillis()
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (e.rawX - rawX).toInt()
                        val dy = (e.rawY - rawY).toInt()
                        if (Math.abs(dx) > 10 || Math.abs(dy) > 10) dragging = true
                        params.x = startX + dx
                        params.y = startY + dy
                        try {
                            viewProvider()?.let { windowManager.updateViewLayout(it, params) }
                        } catch (_: Exception) {}
                    }
                    MotionEvent.ACTION_UP -> {
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
                return true
            }
        }
    }

    private fun dpToPx(dp: Int): Int =
        (dp * resources.displayMetrics.density).toInt()

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
