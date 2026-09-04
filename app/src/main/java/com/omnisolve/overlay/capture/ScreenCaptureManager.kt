package com.omnisolve.overlay.capture

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager

class ScreenCaptureManager(private val context: Context) {

    companion object {
        private const val TAG = "ScreenCaptureManager"
    }

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var screenWidth = 1080
    private var screenHeight = 2400
    private var screenDensity = 320

    @Volatile
    private var isReady = false

    init {
        detectScreenDimensions()
    }

    private fun detectScreenDimensions() {
        try {
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val bounds = wm.currentWindowMetrics.bounds
                screenWidth = bounds.width()
                screenHeight = bounds.height()
                screenDensity = context.resources.configuration.densityDpi
            } else {
                val metrics = DisplayMetrics()
                @Suppress("DEPRECATION")
                wm.defaultDisplay.getRealMetrics(metrics)
                screenWidth = metrics.widthPixels
                screenHeight = metrics.heightPixels
                screenDensity = metrics.densityDpi
            }
        } catch (e: Exception) {
            Log.e(TAG, "Could not detect screen dimensions, using 1080x2400", e)
        }

        // Clamp to safe values
        if (screenWidth  <= 0) screenWidth  = 1080
        if (screenHeight <= 0) screenHeight = 2400
        if (screenDensity <= 0) screenDensity = 420

        Log.d(TAG, "Screen: ${screenWidth}x${screenHeight} @${screenDensity}dpi")
    }

    fun setMediaProjection(projection: MediaProjection) {
        mediaProjection = projection
        initVirtualDisplay()
    }

    @SuppressLint("WrongConstant")
    private fun initVirtualDisplay() {
        val proj = mediaProjection ?: run {
            Log.e(TAG, "initVirtualDisplay: mediaProjection is null")
            return
        }

        try {
            // REQUIRED on Android 14 (API 34): register callback before createVirtualDisplay
            proj.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    Log.d(TAG, "MediaProjection stopped externally")
                    isReady = false
                }
            }, Handler(Looper.getMainLooper()))

            // maxImages=3: enough buffer without hogging memory
            imageReader = ImageReader.newInstance(
                screenWidth, screenHeight, PixelFormat.RGBA_8888, 3
            )

            virtualDisplay = proj.createVirtualDisplay(
                "OmniSolveCapture",
                screenWidth,
                screenHeight,
                screenDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader!!.surface,
                null,
                Handler(Looper.getMainLooper())
            )

            isReady = true
            Log.d(TAG, "VirtualDisplay created: ${screenWidth}x${screenHeight}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create VirtualDisplay", e)
            isReady = false
        }
    }

    /**
     * Capture the current screen frame.
     * Retries up to ~1.5 seconds waiting for a frame to be rendered.
     * Must be called from a background thread (uses Thread.sleep).
     */
    fun captureCurrentFrame(): Bitmap? {
        if (!isReady) {
            Log.e(TAG, "Not ready (isReady=false)")
            return null
        }
        val reader = imageReader ?: run {
            Log.e(TAG, "ImageReader is null")
            return null
        }

        // Step 1: Drain any stale frames sitting in the buffer
        drainImageReader(reader)

        // Step 2: Wait for VirtualDisplay to push a fresh frame
        var image: Image? = null
        val maxAttempts = 30          // 30 × 50ms = 1.5s max wait
        for (attempt in 1..maxAttempts) {
            image = safeAcquireLatest(reader)
            if (image != null) {
                Log.d(TAG, "Frame acquired on attempt $attempt")
                break
            }
            Thread.sleep(50)
        }

        if (image == null) {
            Log.e(TAG, "No frame available after $maxAttempts attempts")
            return null
        }

        return try {
            imageToBitmap(image)
        } finally {
            safeClose(image)
        }
    }

    /** Acquire latest image safely — returns null instead of throwing */
    private fun safeAcquireLatest(reader: ImageReader): Image? {
        return try {
            reader.acquireLatestImage()
        } catch (e: Exception) {
            Log.w(TAG, "acquireLatestImage failed: ${e.message}")
            null
        }
    }

    /** Close all pending images in the ImageReader buffer to avoid buffer overflow */
    private fun drainImageReader(reader: ImageReader) {
        repeat(3) {
            try {
                val img = reader.acquireLatestImage()
                img?.close()
            } catch (_: Exception) {}
        }
    }

    private fun imageToBitmap(image: Image): Bitmap? {
        val planes = image.planes
        if (planes.isEmpty()) {
            Log.e(TAG, "No planes in image")
            return null
        }

        val buffer = planes[0].buffer
        val pixelStride = planes[0].pixelStride
        val rowStride = planes[0].rowStride

        if (pixelStride <= 0 || rowStride <= 0) {
            Log.e(TAG, "Invalid strides: pixel=$pixelStride row=$rowStride")
            return null
        }

        val rowPadding = rowStride - pixelStride * screenWidth
        val bitmapWidth = screenWidth + if (pixelStride > 0) rowPadding / pixelStride else 0

        if (bitmapWidth <= 0 || screenHeight <= 0) {
            Log.e(TAG, "Invalid bitmap dimensions: ${bitmapWidth}x${screenHeight}")
            return null
        }

        return try {
            val rawBitmap = Bitmap.createBitmap(bitmapWidth, screenHeight, Bitmap.Config.ARGB_8888)
            rawBitmap.copyPixelsFromBuffer(buffer)

            // Crop padding if present
            if (bitmapWidth != screenWidth) {
                val cropped = Bitmap.createBitmap(rawBitmap, 0, 0, screenWidth, screenHeight)
                rawBitmap.recycle()
                Log.d(TAG, "Bitmap cropped from ${bitmapWidth}x$screenHeight → ${screenWidth}x$screenHeight")
                cropped
            } else {
                Log.d(TAG, "Bitmap captured: ${rawBitmap.width}x${rawBitmap.height}")
                rawBitmap
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create Bitmap from image buffer", e)
            null
        }
    }

    private fun safeClose(image: Image?) {
        try { image?.close() } catch (_: Exception) {}
    }

    fun stopCapture() {
        isReady = false
        try { virtualDisplay?.release() } catch (_: Exception) {}
        try { imageReader?.close() } catch (_: Exception) {}
        try { mediaProjection?.stop() } catch (_: Exception) {}
        virtualDisplay = null
        imageReader = null
        mediaProjection = null
        Log.d(TAG, "Capture stopped & resources released")
    }
}
