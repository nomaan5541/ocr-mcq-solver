package com.omnisolve.overlay.api

import android.util.Log
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.omnisolve.overlay.model.AnswerModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Sends extracted MCQ text to Gemini and returns the correct answer letter.
 * NO image/bitmap involved — text-only requests are tiny, fast, and reliable.
 */
class GeminiVisionClient {

    companion object {
        private const val TAG = "GeminiClient"
        private const val MODEL = "gemini-2.0-flash"
        private const val API_BASE = "https://generativelanguage.googleapis.com/v1beta/models"
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    /**
     * Given the OCR-extracted text from the screen, ask Gemini which option is correct.
     * Returns AnswerModel with the correct choice letter (A/B/C/D).
     * Throws a descriptive exception on error.
     */
    suspend fun analyzeMCQText(extractedText: String, apiKey: String?): AnswerModel =
        withContext(Dispatchers.IO) {
            if (apiKey.isNullOrBlank()) {
                throw IllegalStateException("NO_API_KEY")
            }
            if (extractedText.isBlank()) {
                throw IllegalStateException("EMPTY_TEXT")
            }

            callGeminiTextApi(extractedText.trim(), apiKey)
        }

    private fun callGeminiTextApi(questionText: String, apiKey: String): AnswerModel {
        val url = "$API_BASE/$MODEL:generateContent?key=$apiKey"

        // Build the prompt — pure text, clear instructions, no image involved
        val prompt = buildPrompt(questionText)

        // Build JSON body with Gson objects (no string interpolation — safe)
        val textPart = JsonObject().apply {
            addProperty("text", prompt)
        }
        val partsArray = JsonArray().apply { add(textPart) }
        val contentObj = JsonObject().apply { add("parts", partsArray) }
        val contentsArray = JsonArray().apply { add(contentObj) }

        val genConfig = JsonObject().apply {
            addProperty("temperature", 0.0)
            addProperty("maxOutputTokens", 50)   // Answer is tiny — no need for more
        }

        val requestBody = JsonObject().apply {
            add("contents", contentsArray)
            add("generationConfig", genConfig)
        }

        val bodyJson = requestBody.toString()
        Log.d(TAG, "Sending to Gemini — prompt length: ${bodyJson.length} chars")
        Log.d(TAG, "Question text:\n${questionText.take(300)}")

        val httpRequest = Request.Builder()
            .url(url)
            .post(bodyJson.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        return httpClient.newCall(httpRequest).execute().use { response ->
            val responseBody = response.body?.string() ?: ""
            Log.d(TAG, "Gemini HTTP ${response.code}: $responseBody")

            if (!response.isSuccessful) {
                val errDetail = parseApiError(responseBody, response.code)
                throw Exception(errDetail)
            }

            parseGeminiResponse(responseBody)
        }
    }

    private fun buildPrompt(questionText: String): String = """
You are an expert MCQ (multiple choice question) solver.

Below is the exact text extracted from a phone screen showing a multiple choice question. Read it carefully and determine the CORRECT answer.

--- SCREEN TEXT START ---
$questionText
--- SCREEN TEXT END ---

Instructions:
- Identify the question and all answer options (labeled A, B, C, D or 1, 2, 3, 4).
- Choose the ONE correct answer.
- Reply with ONLY this JSON, nothing else:
{"correctChoice": "A"}

Replace "A" with the actual correct option letter. Do not include any explanation.
    """.trimIndent()

    private fun parseGeminiResponse(responseBody: String): AnswerModel {
        val root = try {
            JsonParser.parseString(responseBody).asJsonObject
        } catch (_: Exception) {
            throw Exception("JSON_PARSE_ERROR")
        }

        // Check for safety block
        val candidates = root.getAsJsonArray("candidates")
        if (candidates == null || candidates.size() == 0) {
            val blockReason = root.getAsJsonObject("promptFeedback")
                ?.get("blockReason")?.asString
            throw Exception(if (blockReason != null) "BLOCKED:$blockReason" else "NO_CANDIDATES")
        }

        val candidate = candidates[0].asJsonObject
        val finishReason = candidate.get("finishReason")?.asString
        if (finishReason == "SAFETY") throw Exception("BLOCKED:SAFETY")

        val rawText = candidate
            .getAsJsonObject("content")
            ?.getAsJsonArray("parts")
            ?.get(0)?.asJsonObject
            ?.get("text")?.asString
            ?: throw Exception("NO_TEXT_IN_RESPONSE")

        Log.d(TAG, "Gemini raw response: $rawText")
        return extractAnswerLetter(rawText)
    }

    private fun extractAnswerLetter(raw: String): AnswerModel {
        val text = raw.trim()
            .replace(Regex("```json\\s*", RegexOption.IGNORE_CASE), "")
            .replace(Regex("```\\s*"), "")
            .trim()

        // Strategy 1: Parse JSON {"correctChoice": "X"}
        val jsonStart = text.indexOf('{')
        val jsonEnd   = text.lastIndexOf('}')
        if (jsonStart != -1 && jsonEnd > jsonStart) {
            try {
                val obj = JsonParser.parseString(text.substring(jsonStart, jsonEnd + 1)).asJsonObject
                val choice = obj.get("correctChoice")?.asString?.uppercase()?.trim()
                if (choice != null && choice.length == 1 && choice[0] in 'A'..'D') {
                    Log.d(TAG, "Answer (JSON): $choice")
                    return AnswerModel(correctChoice = choice)
                }
            } catch (_: Exception) {}
        }

        // Strategy 2: Regex for correctChoice value
        val patterns = listOf(
            Regex("""correctChoice["'\s]*:["'\s]*([A-Da-d])"""),
            Regex("""answer["'\s]*:["'\s]*([A-Da-d])""", RegexOption.IGNORE_CASE),
            Regex("""correct.*?([A-Da-d])\b""", RegexOption.IGNORE_CASE),
            Regex("""option\s*([A-Da-d])\b""", RegexOption.IGNORE_CASE),
            Regex("""\b([A-Da-d])\s*is\s*correct""", RegexOption.IGNORE_CASE),
        )
        for (p in patterns) {
            val m = p.find(text)
            if (m != null) {
                val letter = m.groupValues[1].uppercase()
                Log.d(TAG, "Answer (regex): $letter")
                return AnswerModel(correctChoice = letter)
            }
        }

        // Strategy 3: Only A–D letters in the whole response
        val letters = text.replace(Regex("[^A-Da-d]"), "")
        if (letters.length == 1) {
            val letter = letters.uppercase()
            Log.d(TAG, "Answer (single char): $letter")
            return AnswerModel(correctChoice = letter)
        }

        // Strategy 4: First standalone A/B/C/D
        val wb = Regex("""\b([A-Da-d])\b""").find(text)
        if (wb != null) {
            val letter = wb.groupValues[1].uppercase()
            Log.d(TAG, "Answer (word boundary): $letter")
            return AnswerModel(correctChoice = letter)
        }

        Log.e(TAG, "Could not extract letter from: $text")
        throw Exception("PARSE_ERROR")
    }

    private fun parseApiError(body: String, code: Int): String {
        val msg = try {
            JsonParser.parseString(body).asJsonObject
                .getAsJsonObject("error")?.get("message")?.asString ?: ""
        } catch (_: Exception) { "" }

        return when (code) {
            400  -> "BAD_REQUEST"
            401, 403 -> "INVALID_KEY"
            429  -> "RATE_LIMIT"
            500, 503 -> "SERVER_ERROR"
            else -> "HTTP_$code"
        }
    }
}
