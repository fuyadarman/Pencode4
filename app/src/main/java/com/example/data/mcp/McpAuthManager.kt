package com.example.data.mcp

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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
        const val DEFAULT_CLIENT_ID = "pencode-mcp-client"
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

            // Google Search Console & Google Stitch OAuth discovery
            if (cleanUrl.contains("searchconsole") || cleanUrl.contains("webmasters") || cleanUrl.contains("stitch") || cleanUrl.contains("googleapis.com")) {
                val scopes = if (cleanUrl.contains("stitch")) {
                    listOf(
                        "https://www.googleapis.com/auth/cloud-platform",
                        "openid",
                        "email",
                        "profile"
                    )
                } else {
                    listOf(
                        GoogleSearchConsoleMcpService.SEARCH_CONSOLE_SCOPE,
                        "openid",
                        "email",
                        "profile"
                    )
                }
                return@withContext Result.success(
                    McpOAuthMetadata(
                        serverUrl = mcpServerUrl,
                        authorizationEndpoint = "https://accounts.google.com/o/oauth2/v2/auth",
                        tokenEndpoint = "https://oauth2.googleapis.com/token",
                        issuer = "https://accounts.google.com",
                        scopesSupported = scopes
                    )
                )
            }

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
     * 2. Dynamic Client Registration (DCR) if registration endpoint is available
     * 3. CIMD URL (if validated as publicly accessible HTTPS metadata document)
     * 4. Pre-registered fallback
     */
    suspend fun resolveClientId(metadata: McpOAuthMetadata, serverId: String, customClientId: String?): String {
        val cid = customClientId?.trim()
        if (!cid.isNullOrBlank()) return cid

        val existing = tokenStore.getTokens(serverId)?.clientId
        if (!existing.isNullOrBlank() && existing != DEFAULT_CLIENT_ID && existing != "cloudflare-mcp" && existing != "vercel-mcp-client") {
            return existing
        }

        val dcrEndpoint = metadata.registrationEndpoint
        if (!dcrEndpoint.isNullOrBlank()) {
            val dcrClientId = registerClientIfNeeded(metadata, serverId)
            if (dcrClientId != DEFAULT_CLIENT_ID && dcrClientId != "vercel-mcp-client") {
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

        // Google Search Console, Google Stitch & all Google Cloud OAuth
        if (metadata.authorizationEndpoint.contains("accounts.google.com") || 
            serverId.contains("google", ignoreCase = true) || 
            serverId.contains("searchconsole", ignoreCase = true) ||
            serverId.contains("stitch", ignoreCase = true) ||
            metadata.serverUrl.contains("stitch") ||
            metadata.serverUrl.contains("googleapis.com")) {
            val envGoogleCid = try { com.example.BuildConfig.GOOGLE_OAUTH_CLIENT_ID } catch (e: Throwable) { "" }
            return if (envGoogleCid.isNotBlank() && envGoogleCid != "null" && envGoogleCid.contains("apps.googleusercontent.com")) {
                envGoogleCid
            } else {
                "798989414934-nn16qvt7t909d7hvc73ccvr1u0t24rom.apps.googleusercontent.com"
            }
        }

        return DEFAULT_CLIENT_ID
    }

    private var loopbackJob: kotlinx.coroutines.Job? = null

    private fun startLoopbackServer(
        port: Int = 8080,
        onCallbackReceived: (Uri) -> Unit
    ) {
        loopbackJob?.cancel()
        loopbackJob = CoroutineScope(Dispatchers.IO).launch {
            var serverSocket: java.net.ServerSocket? = null
            try {
                serverSocket = java.net.ServerSocket(port)
                serverSocket.soTimeout = 120_000 // 2 minutes timeout
                val socket = serverSocket.accept()
                val reader = java.io.BufferedReader(java.io.InputStreamReader(socket.getInputStream()))
                val firstLine = reader.readLine() ?: ""
                val path = firstLine.split(" ").getOrNull(1) ?: "/callback"
                val uri = Uri.parse("http://localhost:$port$path")

                val responseBody = """
                    <!DOCTYPE html>
                    <html>
                    <head><title>Authorization Successful</title><meta name="viewport" content="width=device-width, initial-scale=1"></head>
                    <body style="font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; text-align:center; padding:50px 20px; background:#0B0F17; color:#F0F6FC;">
                        <div style="background:#161B22; border:1px solid #30363D; border-radius:16px; max-width:400px; margin:0 auto; padding:32px; box-shadow:0 8px 24px rgba(0,0,0,0.5);">
                            <div style="font-size:48px; margin-bottom:16px;">✅</div>
                            <h2 style="margin:0 0 8px 0; color:#58A6FF; font-size:22px;">MCP Authorization Successful</h2>
                            <p style="color:#8B949E; font-size:14px; margin:0 0 24px 0;">You can now close this tab and return to Pencode.</p>
                        </div>
                    </body>
                    </html>
                """.trimIndent()

                val writer = java.io.PrintWriter(socket.getOutputStream(), true)
                writer.print("HTTP/1.1 200 OK\r\n")
                writer.print("Content-Type: text/html; charset=UTF-8\r\n")
                writer.print("Content-Length: ${responseBody.toByteArray().size}\r\n")
                writer.print("Connection: close\r\n\r\n")
                writer.print(responseBody)
                writer.flush()
                socket.close()

                CoroutineScope(Dispatchers.Main).launch {
                    onCallbackReceived(uri)
                }
            } catch (e: Exception) {
                // Timeout or cancelled
            } finally {
                try { serverSocket?.close() } catch (ignored: Exception) {}
            }
        }
    }

    /**
     * Perform Dynamic Client Registration (DCR) if CIMD fails or is not supported
     */
    suspend fun registerClientIfNeeded(metadata: McpOAuthMetadata, serverId: String): String = withContext(Dispatchers.IO) {
        val existing = tokenStore.getTokens(serverId)
        if (!existing?.clientId.isNullOrBlank() &&
            existing!!.clientId != DEFAULT_CLIENT_ID &&
            existing.clientId != "cloudflare-mcp" &&
            existing.clientId != "vercel-mcp-client") {
            return@withContext existing.clientId!!
        }

        val regEndpoint = metadata.registrationEndpoint
        if (!regEndpoint.isNullOrBlank()) {
            val redirectCandidateSets = listOf(
                listOf(DEFAULT_REDIRECT_URI, CUSTOM_SCHEME_REDIRECT_URI),
                listOf("http://localhost:8080/callback", "http://127.0.0.1:8080/callback")
            )

            for (redirectList in redirectCandidateSets) {
                try {
                    val dcrBody = JSONObject().apply {
                        put("client_name", "Pencode AI Agent")
                        put("client_uri", "https://pencode.vercel.app")
                        val urisArray = org.json.JSONArray().apply {
                            redirectList.forEach { put(it) }
                        }
                        put("redirect_uris", urisArray)
                        val grantArray = org.json.JSONArray().apply {
                            put("authorization_code")
                            put("refresh_token")
                        }
                        put("grant_types", grantArray)
                        val respArray = org.json.JSONArray().apply {
                            put("code")
                        }
                        put("response_types", respArray)
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
                        val registeredRedirect = redirectList.first()
                        if (newClientId.isNotBlank()) {
                            val current = tokenStore.getTokens(serverId) ?: McpTokenData(serverId = serverId, accessToken = "")
                            tokenStore.saveTokens(
                                current.copy(
                                    clientId = newClientId,
                                    clientSecret = newClientSecret,
                                    redirectUri = registeredRedirect
                                )
                            )
                            return@withContext newClientId
                        }
                    }
                } catch (e: Exception) {
                    // Try next candidate set
                }
            }
        }

        DEFAULT_CLIENT_ID
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
        customRedirectUri: String? = null,
        scopes: List<String> = emptyList(),
        onLocalCallback: ((Uri) -> Unit)? = null
    ): Result<String> = withContext(Dispatchers.Main) {
        try {
            val verifier = generatePkceVerifier()
            val challenge = generatePkceChallenge(verifier)
            val state = "$serverId:${UUID.randomUUID()}"

            // Save PKCE verifier & state for token exchange
            val clientId = resolveClientId(metadata, serverId, customClientId)
            tokenStore.saveCodeVerifier(serverId, verifier, state)

            val currentTokens = tokenStore.getTokens(serverId) ?: McpTokenData(serverId = serverId, accessToken = "")
            val chosenRedirectUri = when {
                !customRedirectUri.isNullOrBlank() -> customRedirectUri
                !currentTokens.redirectUri.isNullOrBlank() -> currentTokens.redirectUri!!
                metadata.authorizationEndpoint.contains("vercel") || serverId.contains("vercel", ignoreCase = true) -> "http://localhost:8080/callback"
                else -> DEFAULT_REDIRECT_URI
            }

            tokenStore.saveTokens(
                currentTokens.copy(
                    clientId = clientId,
                    codeVerifier = verifier,
                    authState = state,
                    redirectUri = chosenRedirectUri
                )
            )

            // If using localhost callback, start loopback listener
            if (chosenRedirectUri.startsWith("http://localhost") || chosenRedirectUri.startsWith("http://127.0.0.1")) {
                startLoopbackServer(8080) { callbackUri ->
                    onLocalCallback?.invoke(callbackUri)
                }
            }

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
                .appendQueryParameter("redirect_uri", chosenRedirectUri)
                .appendQueryParameter("state", state)
                .appendQueryParameter("code_challenge", challenge)
                .appendQueryParameter("code_challenge_method", "S256")

            if (scopeString.isNotBlank()) {
                authUriBuilder.appendQueryParameter("scope", scopeString)
            }

            if (metadata.authorizationEndpoint.contains("accounts.google.com")) {
                authUriBuilder.appendQueryParameter("access_type", "offline")
                authUriBuilder.appendQueryParameter("prompt", "consent")
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

            val clientId = tokenData.clientId ?: DEFAULT_CLIENT_ID
            val redirectUri = tokenData.redirectUri
                ?: (if (metadata.tokenEndpoint.contains("vercel") || serverId.contains("vercel", ignoreCase = true)) "http://localhost:8080/callback" else DEFAULT_REDIRECT_URI)

            val formBuilder = FormBody.Builder()
                .add("grant_type", "authorization_code")
                .add("code", code)
                .add("redirect_uri", redirectUri)
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
