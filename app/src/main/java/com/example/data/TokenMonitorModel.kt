package com.example.data

data class TokenMetrics(
    val modelName: String = "gemini-3.1-flash-lite",
    val executionTimeSeconds: Long = 0,
    val isLive: Boolean = false,
    val systemTokens: Int = 2440,
    val userTokens: Int = 2,
    val historyTokens: Int = 0,
    val skillTokens: Int = 0,
    val totalInputTokens: Int = 0,
    val totalOutputTokens: Int = 0
) {
    val calculatedTotalInput: Int
        get() = if (totalInputTokens > 0) totalInputTokens else (systemTokens + userTokens + historyTokens + skillTokens)
}
