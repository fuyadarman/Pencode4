package com.example.auth.github

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GitHubOAuthDialog(
    isOpen: Boolean,
    onDismiss: () -> Unit,
    onAuthSuccess: (token: String, username: String) -> Unit
) {
    if (!isOpen) return

    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val coroutineScope = rememberCoroutineScope()
    val authManager = remember { GitHubAuthManager.getInstance(context) }
    val authState by authManager.authState.collectAsState()

    var showInAppWebView by remember { mutableStateOf(false) }
    var manualTokenInput by remember { mutableStateOf("") }
    var showManualTokenOption by remember { mutableStateOf(false) }
    var showWorkerSettings by remember { mutableStateOf(false) }
    var workerUrlInput by remember { mutableStateOf(authState.workerUrl) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var isProcessing by remember { mutableStateOf(false) }

    val oauthAuthorizeUrl = "https://github.com/login/oauth/authorize?client_id=${GitHubAuthManager.CLIENT_ID}&scope=repo,workflow,read:user"

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(if (showInAppWebView) 0.90f else 0.82f),
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF13151F),
            border = BorderStroke(1.dp, Color(0xFF2E3248))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            modifier = Modifier.size(36.dp),
                            shape = CircleShape,
                            color = Color(0xFF24292E)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.CloudSync,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                text = "GitHub Account Authorization",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Pencode App (ID: ${GitHubAuthManager.APP_ID})",
                                color = Color(0xFF94A3B8),
                                fontSize = 11.sp
                            )
                        }
                    }

                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF94A3B8), modifier = Modifier.size(20.dp))
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (showInAppWebView) {
                    // In-App WebView for OAuth Authorization
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        AndroidView(
                            factory = { ctx ->
                                WebView(ctx).apply {
                                    settings.javaScriptEnabled = true
                                    settings.domStorageEnabled = true
                                    settings.userAgentString = "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                                    webViewClient = object : WebViewClient() {
                                        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                            val url = request?.url?.toString() ?: return false
                                            if (url.contains("code=") || url.contains("pencode://") || url.contains("/oauth/callback")) {
                                                val uri = android.net.Uri.parse(url)
                                                val code = uri.getQueryParameter("code")
                                                if (!code.isNullOrBlank()) {
                                                    isProcessing = true
                                                    statusMessage = "Exchanging GitHub code for access token..."
                                                    showInAppWebView = false
                                                    coroutineScope.launch {
                                                        val res = authManager.exchangeOAuthCode(code)
                                                        isProcessing = false
                                                        res.onSuccess { token ->
                                                            onAuthSuccess(token, authManager.authState.value.username)
                                                            onDismiss()
                                                        }.onFailure { err ->
                                                            statusMessage = "Error: ${err.message}"
                                                        }
                                                    }
                                                    return true
                                                }
                                            }
                                            return false
                                        }

                                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                            if (url?.contains("code=") == true) {
                                                val uri = android.net.Uri.parse(url)
                                                val code = uri.getQueryParameter("code")
                                                if (!code.isNullOrBlank()) {
                                                    isProcessing = true
                                                    statusMessage = "Exchanging code..."
                                                    showInAppWebView = false
                                                    coroutineScope.launch {
                                                        val res = authManager.exchangeOAuthCode(code)
                                                        isProcessing = false
                                                        res.onSuccess { token ->
                                                            onAuthSuccess(token, authManager.authState.value.username)
                                                            onDismiss()
                                                        }.onFailure { err ->
                                                            statusMessage = "Error: ${err.message}"
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    loadUrl(oauthAuthorizeUrl)
                                }
                            },
                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp))
                        )
                    }
                } else {
                    // Normal OAuth Card Flow
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        if (authState.isConnected) {
                            // Already connected card
                            Card(
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E2235)),
                                border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.5f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    if (authState.avatarUrl.isNotBlank()) {
                                        AsyncImage(
                                            model = authState.avatarUrl,
                                            contentDescription = "Avatar",
                                            modifier = Modifier.size(44.dp).clip(CircleShape)
                                        )
                                    } else {
                                        Surface(
                                            modifier = Modifier.size(44.dp),
                                            shape = CircleShape,
                                            color = Color(0xFF3B82F6)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(
                                                    text = authState.username.take(2).uppercase(),
                                                    color = Color.White,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Text(
                                                text = authState.displayName.ifBlank { authState.username },
                                                color = Color.White,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = Color(0xFF10B981).copy(alpha = 0.2f)
                                            ) {
                                                Text(
                                                    text = "Connected",
                                                    color = Color(0xFF10B981),
                                                    fontSize = 10.sp,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                        Text(
                                            text = "@${authState.username}",
                                            color = Color(0xFF94A3B8),
                                            fontSize = 12.sp
                                        )
                                        Text(
                                            text = "${authState.userRepos.size} repositories available",
                                            color = Color(0xFF64748B),
                                            fontSize = 11.sp
                                        )
                                    }

                                    IconButton(
                                        onClick = { authManager.disconnect() },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.Logout, contentDescription = "Disconnect", tint = Color(0xFFEF4444), modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        } else {
                            // Primary Connect Button with GitHub App OAuth
                            Button(
                                onClick = {
                                    showInAppWebView = true
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF24292E)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Default.LockOpen, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                    Text(
                                        text = "Authorize via GitHub App",
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            // Browser redirect button
                            OutlinedButton(
                                onClick = {
                                    try {
                                        uriHandler.openUri(oauthAuthorizeUrl)
                                    } catch (_: Exception) {}
                                },
                                modifier = Modifier.fillMaxWidth().height(42.dp),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, Color(0xFF38405B))
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Default.OpenInBrowser, contentDescription = null, tint = Color(0xFF60A5FA), modifier = Modifier.size(16.dp))
                                    Text("Open in External Browser", color = Color(0xFF93C5FD), fontSize = 12.sp)
                                }
                            }

                            // GitHub App Public Page Link
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = "Install Pencode GitHub App",
                                    color = Color(0xFF818CF8),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.clickable {
                                        try {
                                            uriHandler.openUri(GitHubAuthManager.APP_PUBLIC_URL)
                                        } catch (_: Exception) {}
                                    }
                                )
                            }
                        }

                        // Status or Error message
                        if (!statusMessage.isNullOrBlank()) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (statusMessage?.startsWith("Error") == true) Color(0xFF7F1D1D) else Color(0xFF1E3A8A),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = statusMessage.orEmpty(),
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        }

                        // Cloudflare Worker backend config
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showWorkerSettings = !showWorkerSettings }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Default.Dns, contentDescription = null, tint = Color(0xFFF97316), modifier = Modifier.size(16.dp))
                                Text("Cloudflare Worker Backend", color = Color(0xFFE2E8F0), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                            Icon(
                                imageVector = if (showWorkerSettings) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                tint = Color(0xFF94A3B8),
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        AnimatedVisibility(visible = showWorkerSettings) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = "Endpoint for exchanging OAuth code with client secret securely:",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 10.sp
                                )
                                OutlinedTextField(
                                    value = workerUrlInput,
                                    onValueChange = { workerUrlInput = it },
                                    label = { Text("Worker Base URL", fontSize = 11.sp) },
                                    placeholder = { Text("https://pencode-auth.<subdomain>.workers.dev", fontSize = 11.sp) },
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White,
                                        focusedBorderColor = Color(0xFFF97316),
                                        unfocusedBorderColor = Color(0xFF38405B),
                                        focusedContainerColor = Color(0xFF1A1D2D),
                                        unfocusedContainerColor = Color(0xFF1A1D2D)
                                    ),
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                Button(
                                    onClick = {
                                        authManager.updateWorkerUrl(workerUrlInput)
                                        statusMessage = "Worker URL updated successfully!"
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF97316)),
                                    modifier = Modifier.fillMaxWidth().height(36.dp),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Save Worker URL", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        // Manual Token option
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showManualTokenOption = !showManualTokenOption }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Direct Access Token / Manual Entry", color = Color(0xFF64748B), fontSize = 11.sp)
                            Icon(
                                imageVector = if (showManualTokenOption) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                tint = Color(0xFF64748B),
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        AnimatedVisibility(visible = showManualTokenOption) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = manualTokenInput,
                                    onValueChange = { manualTokenInput = it },
                                    label = { Text("GitHub Token / OAuth Token", fontSize = 11.sp) },
                                    placeholder = { Text("gho_... or ghp_...", fontSize = 11.sp) },
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White,
                                        focusedBorderColor = Color(0xFF6366F1),
                                        unfocusedBorderColor = Color(0xFF38405B),
                                        focusedContainerColor = Color(0xFF1A1D2D),
                                        unfocusedContainerColor = Color(0xFF1A1D2D)
                                    ),
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                Button(
                                    onClick = {
                                        if (manualTokenInput.isNotBlank()) {
                                            isProcessing = true
                                            coroutineScope.launch {
                                                val res = authManager.connectWithToken(manualTokenInput)
                                                isProcessing = false
                                                res.onSuccess { token ->
                                                    onAuthSuccess(token, authManager.authState.value.username)
                                                    onDismiss()
                                                }.onFailure { err ->
                                                    statusMessage = "Error: ${err.message}"
                                                }
                                            }
                                        }
                                    },
                                    enabled = manualTokenInput.isNotBlank() && !isProcessing,
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1)),
                                    modifier = Modifier.fillMaxWidth().height(36.dp),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Connect Token", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
