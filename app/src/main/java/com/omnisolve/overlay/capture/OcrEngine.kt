package com.omnisolve.overlay.capture

import android.graphics.Bitmap
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await

/**
 * On-device OCR using Google ML Kit.
 * Extracts all visible text from a screen bitmap — no network required.
 */
class OcrEngine {

    companion object {
        private const val TAG = "OcrEngine"
    }

    // ML Kit text recognizer — runs fully on-device, no API key needed
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    /**
     * Extract all text from the given bitmap using ML Kit OCR.
     * Returns the raw text string, or empty string on failure.
     * Must be called from a coroutine context.
     */
    suspend fun extractText(bitmap: Bitmap): String {
        return try {
            val inputImage = InputImage.fromBitmap(bitmap, 0)
            val result = recognizer.process(inputImage).await()
            val text = result.text.trim()
            Log.d(TAG, "OCR extracted ${text.length} chars: ${text.take(200)}")
            text
        } catch (e: Exception) {
            Log.e(TAG, "OCR failed", e)
            ""
        }
    }

    fun close() {
        try { recognizer.close() } catch (_: Exception) {}
    }
}
