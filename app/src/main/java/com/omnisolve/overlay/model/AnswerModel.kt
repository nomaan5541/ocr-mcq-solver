package com.omnisolve.overlay.model

import com.google.gson.annotations.SerializedName

/**
 * Minimal Data Model for Instant Option Letter Result (A, B, C, or D)
 */
data class AnswerModel(
    @SerializedName("correctChoice") val correctChoice: String = "?"
)
