package com.example.data.mcp

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID
import java.util.concurrent.TimeUnit

data class McpOAuthMetadata(
    val serverUrl: String,
    val authorizationEndpoint: String,
    val tokenEndpoint: String,
    val issuer: String? = null,
    val registrationEndpoint: String? = null,
    val scopesSupported: List<String> = emptyList(),
    val responseTypesSupported: List<String> = listOf("code"),
    val codeChallengeMethodsSupported: List<String> = listOf("S256"),
    val clientMetadataUrl: String = "https://pencode.vercel.app/.well-known/mcp-client-metadata.json"
)

class McpAuthManager(
    private val context: Context,
    private val tokenStore: McpTokenStore
) {
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    companion object {
        const val DEFAULT_REDIRECT_URI = "https://pencode.vercel.app/oauth/callback"
        const val CUSTOM_SCHEME_REDIRECT_URI = "pencode://mcp/oauth/callback"
        const val DEFAULT_CIMD_CLIENT_ID = "https://pencode.vercel.app/.well-known/mcp-client-metadata.json"
    }

    /**
     * Validate whether CIMD metadata URL is publicly reachable & valid JSON
     */
    suspend fun validateCimdUrl(cimdUrl: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url(cimdUrl).get().build()
            val resp = httpClient.newCall(req).execute()
            if (resp.isSuccessful) {
                val body = resp.body?.string() ?: ""
                val json = JSONObject(body)
                return@withContext json.has("client_id") || json.has("client_name") || json.has("redirect_uris")
            }
        } catch (e: Exception) {
            // Unreachable or invalid
        }
        false
    }

    /**
     * 1. Discover OAuth Metadata from MCP server via Protected Resource & Auth Server Metadata
     */
    suspend fun discoverOAuthMetadata(mcpServerUrl: String): Result<McpOAuthMetadata> = withContext(Dispatchers.IO) {
        try {
            val cleanUrl = mcpServerUrl.trim().removeSuffix("/")
            val parsedUri = Uri.parse(cleanUrl)
            val baseUrl = "${parsedUri.scheme}://${parsedUri.host}${if (parsedUri.port > 0 && parsedUri.port != 80 && parsedUri.port != 443) ":${parsedUri.port}" else ""}"

            val discoveryCandidates = mutableListOf<String>()

            // A. Check Protected Resource Metadata / WWW-Authenticate
            try {
                val initReq = Request.Builder().url(cleanUrl).get().build()
                val initResp = httpClient.newCall(initReq).execute()
                val wwwAuth = initResp.header("WWW-Authenticate")
                if (!wwwAuth.isNullOrBlank()) {
                    // Extract authorization_uri or resource_metadata
                    val authUriMatch = Regex("authorization_uri=\"([^\"]+)\"").find(wwwAuth)
                    val metaMatch = Regex("resource_metadata=\"([^\"]+)\"").find(wwwAuth)
                    metaMatch?.groupValues?.get(1)?.let { discoveryCandidates.add(it) }
                    authUriMatch?.groupValues?.get(1)?.let { discoveryCandidates.add(it) }
                }
            } catch (ignored: Exception) {}

            discoveryCandidates.addAll(
                listOf(
                    "$cleanUrl/.well-known/oauth-protected-resource",
                    "$baseUrl/.well-known/oauth-protected-resource",
                    "$cleanUrl/.well-known/oauth-authorization-server",
                    "$baseUrl/.well-known/oauth-authorization-server",
                    "$cleanUrl/.well-known/openid-configuration",
                    "$baseUrl/.well-known/openid-configuration",
                    "$cleanUrl/.well-known/mcp"
                )
            )

            for (discUrl in discoveryCandidates.distinct()) {
                try {
                    val req = Request.Builder().url(discUrl).get().build()
                    val resp = httpClient.newCall(req).execute()
                    if (resp.isSuccessful) {
                        val body = resp.body?.string() ?: ""
                        val json = JSONObject(body)

                        // Check if protected resource points to authorization_servers
                        var authServerUrl = discUrl
                        if (json.has("authorization_servers")) {
                            val serversArr = json.optJSONArray("authorization_servers")
                            if (serversArr != null && serversArr.length() > 0) {
                                authServerUrl = serversArr.getString(0)
                                // Fetch auth server metadata if distinct
                                try {
                                    val authServerMetaReq = Request.Builder().url("$authServerUrl/.well-known/oauth-authorization-server").get().build()
                                    val authServerMetaResp = httpClient.newCall(authServerMetaReq).execute()
                                    if (authServerMetaResp.isSuccessful) {
                                        val metaJson = JSONObject(authServerMetaResp.body?.string() ?: "")
                                        val aEp = metaJson.optString("authorization_endpoint")
                                        val tEp = metaJson.optString("token_endpoint")
                                        if (aEp.isNotBlank() && tEp.isNotBlank()) {
                                            return@withContext Result.success(
                                                McpOAuthMetadata(
                                                    serverUrl = mcpServerUrl,
                                                    authorizationEndpoint = aEp,
                                                    tokenEndpoint = tEp,
                                                    issuer = metaJson.optString("issuer").takeIf { it.isNotBlank() },
                                                    registrationEndpoint = metaJson.optString("registration_endpoint").takeIf { it.isNotBlank() }
                                                )
                                            )
                                        }
                                    }
                                } catch (e: Exception) {}
                            }
                        }

                        val authEp = json.optString("authorization_endpoint")
                        val tokenEp = json.optString("token_endpoint")
                        val issuer = json.optString("issuer").takeIf { it.isNotBlank() }

                        if (authEp.isNotBlank() && tokenEp.isNotBlank()) {
                            val regEp = json.optString("registration_endpoint").takeIf { it.isNotBlank() }
                            val scopes = mutableListOf<String>()
                            json.optJSONArray("scopes_supported")?.let { arr ->
                                for (i in 0 until arr.length()) {
                                    scopes.add(arr.getString(i))
                                }
                            }
                            return@withContext Result.success(
                                McpOAuthMetadata(
                                    serverUrl = mcpServerUrl,
                                    authorizationEndpoint = authEp,
                                    tokenEndpoint = tokenEp,
                                    issuer = issuer,
                                    registrationEndpoint = regEp,
                                    scopesSupported = scopes
                                )
                            )
                        }
                    }
                } catch (ignored: Exception) {}
            }

            // OAuth discovery did not find valid OAuth endpoints
            Result.failure(Exception("OAuth metadata discovery failed. Server does not support standard OAuth 2.0."))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Resolve Client ID using order:
     * 1. User provided Client ID
     * 2. CIMD URL (if validated as publicly accessible HTTPS metadata document)
     * 3. Dynamic Client Registration (DCR)
     * 4. Pre-registered fallback
     */
    suspend fun resolveClientId(metadata: McpOAuthMetadata, serverId: String, customClientId: String?): String {
        val cid = customClientId?.trim()
        if (!cid.isNullOrBlank()) return cid

        val existing = tokenStore.getTokens(serverId)?.clientId
        if (!existing.isNullOrBlank()) return existing

        val dcrEndpoint = metadata.registrationEndpoint
        if (!dcrEndpoint.isNullOrBlank()) {
            val dcrClientId = registerClientIfNeeded(metadata, serverId)
            if (dcrClientId != DEFAULT_CIMD_CLIENT_ID) {
                return dcrClientId
            }
        }

        val cimdUrl = metadata.clientMetadataUrl
        if (validateCimdUrl(cimdUrl)) {
            return cimdUrl
        }

        // Supabase requires a UUID format for client_id
        if (metadata.authorizationEndpoint.contains("supabase") || serverId.contains("supabase", ignoreCase = true)) {
            val envClientId = com.example.BuildConfig.SUPABASE_CLIENT_ID
            return if (envClientId.isNotBlank() && envClientId != "null") envClientId else "0191848f-8044-4d51-b69a-296f32c4d900"
        }

        return DEFAULT_CIMD_CLIENT_ID
    }

    /**
     * Perform Dynamic Client Registration (DCR) if CIMD fails or is not supported
     */
    suspend fun registerClientIfNeeded(metadata: McpOAuthMetadata, serverId: String): String = withContext(Dispatchers.IO) {
        val existing = tokenStore.getTokens(serverId)
        if (!existing?.clientId.isNullOrBlank()) {
            return@withContext existing!!.clientId!!
        }

        val regEndpoint = metadata.registrationEndpoint
        if (!regEndpoint.isNullOrBlank()) {
            try {
                val dcrBody = JSONObject().apply {
                    put("client_name", "Pencode AI Agent")
                    put("client_uri", "https://pencode.vercel.app")
                    put("logo_uri", "https://pencode.vercel.app/logo.png")
                    put("redirect_uris", listOf(CUSTOM_SCHEME_REDIRECT_URI, DEFAULT_REDIRECT_URI))
                    put("grant_types", listOf("authorization_code", "refresh_token"))
                    put("response_types", listOf("code"))
                    put("token_endpoint_auth_method", "none")
                }

                val req = Request.Builder()
                    .url(regEndpoint)
                    .post(dcrBody.toString().toRequestBody("application/json".toMediaTypeOrNull()))
                    .build()

                val resp = httpClient.newCall(req).execute()
                if (resp.isSuccessful) {
                    val respJson = JSONObject(resp.body?.string() ?: "")
                    val newClientId = respJson.optString("client_id")
                    val newClientSecret = respJson.optString("client_secret").takeIf { it.isNotBlank() }
                    if (newClientId.isNotBlank()) {
                        val current = tokenStore.getTokens(serverId) ?: McpTokenData(serverId = serverId, accessToken = "")
                        tokenStore.saveTokens(current.copy(clientId = newClientId, clientSecret = newClientSecret))
                        return@withContext newClientId
                    }
                }
            } catch (e: Exception) {
                // Fallback
            }
        }

        DEFAULT_CIMD_CLIENT_ID
    }

    /**
     * PKCE Helper
     */
    fun generatePkceVerifier(): String {
        val random = SecureRandom()
        val bytes = ByteArray(32)
        random.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    fun generatePkceChallenge(verifier: String): String {
        val bytes = verifier.toByteArray(Charsets.US_ASCII)
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
    }

    /**
     * Build Authorization URL & Launch Custom Tab
     */
    suspend fun startAuthorizationFlow(
        activityContext: Context,
        serverId: String,
        metadata: McpOAuthMetadata,
        customClientId: String? = null,
        scopes: List<String> = emptyList()
    ): Result<String> = withContext(Dispatchers.Main) {
        try {
            val verifier = generatePkceVerifier()
            val challenge = generatePkceChallenge(verifier)
            val state = "$serverId:${UUID.randomUUID()}"

            // Save PKCE verifier & state for token exchange
            val clientId = resolveClientId(metadata, serverId, customClientId)
            tokenStore.saveCodeVerifier(serverId, verifier, state)

            val currentTokens = tokenStore.getTokens(serverId) ?: McpTokenData(serverId = serverId, accessToken = "")
            tokenStore.saveTokens(currentTokens.copy(clientId = clientId, codeVerifier = verifier, authState = state))

            val scopeString = if (scopes.isNotEmpty()) {
                scopes.joinToString(" ")
            } else if (metadata.scopesSupported.isNotEmpty()) {
                metadata.scopesSupported.joinToString(" ")
            } else {
                ""
            }

            val authUriBuilder = Uri.parse(metadata.authorizationEndpoint).buildUpon()
                .appendQueryParameter("response_type", "code")
                .appendQueryParameter("client_id", clientId)
                .appendQueryParameter("redirect_uri", DEFAULT_REDIRECT_URI)
                .appendQueryParameter("state", state)
                .appendQueryParameter("code_challenge", challenge)
                .appendQueryParameter("code_challenge_method", "S256")

            if (scopeString.isNotBlank()) {
                authUriBuilder.appendQueryParameter("scope", scopeString)
            }

            val authUri = authUriBuilder.build()

            val customTabsIntent = CustomTabsIntent.Builder()
                .setShowTitle(true)
                .build()

            customTabsIntent.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            customTabsIntent.launchUrl(activityContext, authUri)

            Result.success("Opened OAuth Authorization Browser: $authUri")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Exchange Authorization Code for Access/Refresh Tokens with Issuer & State Validation
     */
    suspend fun handleOAuthCallback(
        serverId: String,
        callbackUri: Uri,
        metadata: McpOAuthMetadata
    ): Result<McpTokenData> = withContext(Dispatchers.IO) {
        try {
            val code = callbackUri.getQueryParameter("code")
                ?: return@withContext Result.failure(Exception("Callback URL missing 'code' parameter."))

            val returnedState = callbackUri.getQueryParameter("state")
            val tokenData = tokenStore.getTokens(serverId)
                ?: return@withContext Result.failure(Exception("No pending OAuth state found for server."))

            if (!returnedState.isNullOrBlank() && !tokenData.authState.isNullOrBlank() && returnedState != tokenData.authState) {
                return@withContext Result.failure(Exception("OAuth state mismatch. Security verification failed."))
            }

            // Validate issuer parameter (RFC 9207)
            val callbackIssuer = callbackUri.getQueryParameter("iss")
            if (!callbackIssuer.isNullOrBlank() && !metadata.issuer.isNullOrBlank() && callbackIssuer != metadata.issuer) {
                return@withContext Result.failure(Exception("OAuth issuer mismatch! Expected '${metadata.issuer}', got '$callbackIssuer'"))
            }

            val verifier = tokenData.codeVerifier
                ?: return@withContext Result.failure(Exception("PKCE verifier missing."))

            val clientId = tokenData.clientId ?: DEFAULT_CIMD_CLIENT_ID

            val formBuilder = FormBody.Builder()
                .add("grant_type", "authorization_code")
                .add("code", code)
                .add("redirect_uri", DEFAULT_REDIRECT_URI)
                .add("code_verifier", verifier)
                .add("client_id", clientId)

            if (!tokenData.clientSecret.isNullOrBlank()) {
                formBuilder.add("client_secret", tokenData.clientSecret)
            } else if (metadata.tokenEndpoint.contains("supabase") || serverId.contains("supabase", ignoreCase = true)) {
                val envSecret = com.example.BuildConfig.SUPABASE_CLIENT_SECRET
                val secretToUse = if (envSecret.isNotBlank() && envSecret != "null") envSecret else "sba_f542cf0850dc25032d53447bbb5b6c8cde1ae950"
                formBuilder.add("client_secret", secretToUse)
            }

            val request = Request.Builder()
                .url(metadata.tokenEndpoint)
                .post(formBuilder.build())
                .addHeader("Accept", "application/json")
                .build()

            val response = httpClient.newCall(request).execute()
            val respBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Token exchange failed (${response.code}): $respBody"))
            }

            val json = JSONObject(respBody)
            val accessToken = json.optString("access_token")
            val refreshToken = json.optString("refresh_token").takeIf { it.isNotBlank() }
            val expiresIn = json.optLong("expires_in", 3600L)
            val tokenType = json.optString("token_type", "Bearer")
            val grantedScope = json.optString("scope", tokenData.scope)

            if (accessToken.isBlank()) {
                return@withContext Result.failure(Exception("No access_token returned in JSON response."))
            }

            val updatedTokens = McpTokenData(
                serverId = serverId,
                accessToken = accessToken,
                refreshToken = refreshToken ?: tokenData.refreshToken,
                tokenType = tokenType,
                expiresAtMillis = System.currentTimeMillis() + (expiresIn * 1000L),
                clientId = clientId,
                clientSecret = tokenData.clientSecret,
                scope = grantedScope
            )

            tokenStore.saveTokens(updatedTokens)
            Result.success(updatedTokens)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 6. Token Refresh
     */
    suspend fun refreshAccessToken(serverId: String, tokenEndpoint: String): Result<McpTokenData> = withContext(Dispatchers.IO) {
        try {
            val tokenData = tokenStore.getTokens(serverId)
                ?: return@withContext Result.failure(Exception("No tokens found for server $serverId."))

            val refresh = tokenData.refreshToken
                ?: return@withContext Result.failure(Exception("No refresh_token available for server $serverId."))

            val formBuilder = FormBody.Builder()
                .add("grant_type", "refresh_token")
                .add("refresh_token", refresh)

            if (!tokenData.clientId.isNullOrBlank()) {
                formBuilder.add("client_id", tokenData.clientId)
            }
            if (!tokenData.clientSecret.isNullOrBlank()) {
                formBuilder.add("client_secret", tokenData.clientSecret)
            }

            val request = Request.Builder()
                .url(tokenEndpoint)
                .post(formBuilder.build())
                .addHeader("Accept", "application/json")
                .build()

            val response = httpClient.newCall(request).execute()
            val respBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Token refresh failed (${response.code}): $respBody"))
            }

            val json = JSONObject(respBody)
            val newAccessToken = json.optString("access_token")
            val newRefreshToken = json.optString("refresh_token").takeIf { it.isNotBlank() } ?: refresh
            val expiresIn = json.optLong("expires_in", 3600L)

            val updated = tokenData.copy(
                accessToken = newAccessToken,
                refreshToken = newRefreshToken,
                expiresAtMillis = System.currentTimeMillis() + (expiresIn * 1000L)
            )

            tokenStore.saveTokens(updated)
            Result.success(updated)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
