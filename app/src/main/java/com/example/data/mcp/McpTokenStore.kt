package com.example.data.mcp

import android.content.Context
import android.content.SharedPreferences
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

data class McpTokenData(
    val serverId: String,
    val accessToken: String,
    val refreshToken: String? = null,
    val tokenType: String = "Bearer",
    val expiresAtMillis: Long = 0L,
    val clientId: String? = null,
    val clientSecret: String? = null,
    val scope: String? = null,
    val codeVerifier: String? = null,
    val authState: String? = null,
    val redirectUri: String? = null
) {
    fun isExpired(): Boolean {
        if (expiresAtMillis <= 0L) return false
        return System.currentTimeMillis() >= (expiresAtMillis - 30_000L) // 30s buffer
    }
}

class McpTokenStore(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("mcp_token_store", Context.MODE_PRIVATE)
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val adapter = moshi.adapter(McpTokenData::class.java)

    fun saveTokens(tokenData: McpTokenData) {
        val json = adapter.toJson(tokenData)
        prefs.edit().putString("token_${tokenData.serverId}", json).apply()
    }

    fun getTokens(serverId: String): McpTokenData? {
        val json = prefs.getString("token_${serverId}", null) ?: return null
        return try {
            adapter.fromJson(json)
        } catch (e: Exception) {
            null
        }
    }

    fun saveCodeVerifier(serverId: String, verifier: String, state: String) {
        val current = getTokens(serverId) ?: McpTokenData(
            serverId = serverId,
            accessToken = ""
        )
        saveTokens(current.copy(codeVerifier = verifier, authState = state))
    }

    fun findServerIdByState(state: String): String? {
        if (state.isBlank()) return null
        val all = prefs.all
        for ((key, value) in all) {
            if (key.startsWith("token_") && value is String) {
                try {
                    val tokenData = adapter.fromJson(value)
                    if (tokenData?.authState == state || (tokenData?.authState != null && state.contains(tokenData.authState))) {
                        return tokenData.serverId
                    }
                } catch (_: Exception) {}
            }
        }
        return null
    }

    fun clearTokens(serverId: String) {
        prefs.edit().remove("token_${serverId}").apply()
    }

    fun clearAll() {
        prefs.edit().clear().apply()
    }
}
