package com.example.data

data class TokenMetrics(
    val modelName: String = "",
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

    val calculatedTotalTokens: Int
        get() = calculatedTotalInput + totalOutputTokens
}
