package com.omnisolve.overlay.capture

import android.graphics.Bitmap
import android.graphics.Rect
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

    /**
     * Extract text AND bounding box coordinates for each text block.
     * Returns a Pair of:
     *   - Full extracted text (same as extractText)
     *   - List of (blockText, boundingRect) for each recognized text block
     *
     * The bounding rects are in the bitmap's coordinate space (= screen pixels).
     * This is used by the auto-click bot to locate MCQ options on screen.
     */
    suspend fun extractTextWithBounds(bitmap: Bitmap): Pair<String, List<Pair<String, Rect>>> {
        return try {
            val inputImage = InputImage.fromBitmap(bitmap, 0)
            val result = recognizer.process(inputImage).await()
            val fullText = result.text.trim()
            Log.d(TAG, "OCR extracted ${fullText.length} chars with bounds")

            val blocks = mutableListOf<Pair<String, Rect>>()
            for (block in result.textBlocks) {
                // First extract each line so individual options (A, B, C, D) have exact bounds
                for (line in block.lines) {
                    val lineText = line.text.trim()
                    val lineBounds = line.boundingBox
                    if (lineBounds != null && lineText.isNotBlank()) {
                        blocks.add(Pair(lineText, lineBounds))
                    }
                }
                // Also keep the block-level entry
                val blockText = block.text.trim()
                val bounds = block.boundingBox
                if (bounds != null && blockText.isNotBlank()) {
                    blocks.add(Pair(blockText, bounds))
                }
            }

            Pair(fullText, blocks)
        } catch (e: Exception) {
            Log.e(TAG, "OCR with bounds failed", e)
            Pair("", emptyList())
        }
    }

    fun close() {
        try { recognizer.close() } catch (_: Exception) {}
    }
}

