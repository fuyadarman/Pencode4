package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.McpPlatformType
import com.example.data.McpServer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun McpOAuthConnectDialog(
    server: McpServer,
    onStartOAuth: (clientId: String?) -> Unit,
    onConfirmConnect: (tokenOrApiKey: String) -> Unit,
    onDismiss: () -> Unit
) {
    val uriHandler = LocalUriHandler.current
    val platformType = McpPlatformType.fromString(server.platform)
    var authMode by remember { mutableStateOf(0) } // 0: OAuth 2.0 Flow, 1: Personal Access Token
    var tokenValue by remember { mutableStateOf(server.apiKey ?: "") }
    var oauthClientId by remember { 
        val envClientId = com.example.BuildConfig.SUPABASE_CLIENT_ID
        val defaultId = if (envClientId.isNotBlank() && envClientId != "null") envClientId else "0191848f-8044-4d51-b69a-296f32c4d900"
        mutableStateOf(if (platformType == com.example.data.McpPlatformType.SUPABASE) defaultId else "") 
    }
    var showToken by remember { mutableStateOf(false) }
    var isAuthorizing by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .clip(RoundedCornerShape(18.dp))
                .border(1.dp, Color(0xFF262C40), RoundedCornerShape(18.dp)),
            color = Color(0xFF0F1117),
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp)
            ) {
                // Header with Platform Logo
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        McpPlatformLogo(platformType = platformType, size = 40.dp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Connect ${server.name}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFF1F5F9),
                                    fontSize = 15.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = platformType.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF94A3B8),
                                fontSize = 11.sp
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Authentication Method Segmented Tabs
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF161A26),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF23293D))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(3.dp)
                    ) {
                        val isOAuth = authMode == 0
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { authMode = 0 },
                            shape = RoundedCornerShape(6.dp),
                            color = if (isOAuth) Color(0xFF262E45) else Color.Transparent
                        ) {
                            Text(
                                text = "OAuth 2.0 Browser",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (isOAuth) FontWeight.Bold else FontWeight.Medium,
                                color = if (isOAuth) Color(0xFFF1F5F9) else Color(0xFF94A3B8),
                                modifier = Modifier.padding(vertical = 7.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                fontSize = 12.sp
                            )
                        }

                        val isApiKey = authMode == 1
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { authMode = 1 },
                            shape = RoundedCornerShape(6.dp),
                            color = if (isApiKey) Color(0xFF262E45) else Color.Transparent
                        ) {
                            Text(
                                text = "API Key / Token",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (isApiKey) FontWeight.Bold else FontWeight.Medium,
                                color = if (isApiKey) Color(0xFFF1F5F9) else Color(0xFF94A3B8),
                                modifier = Modifier.padding(vertical = 7.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (authMode == 0) {
                    // OAuth 2.0 Connection Info
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF161B29)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Color(0xFF262E45), RoundedCornerShape(10.dp))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.VpnKey,
                                    contentDescription = null,
                                    tint = Color(0xFF818CF8),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Automated OAuth 2.0 PKCE",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFF1F5F9),
                                    fontSize = 12.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Authorizes Pencode to securely call ${platformType.displayName} APIs & resources on your behalf.",
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 11.sp,
                                color = Color(0xFF94A3B8)
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            OutlinedButton(
                                onClick = {
                                    try {
                                        uriHandler.openUri(platformType.buildAuthUrl(oauthClientId))
                                    } catch (e: Exception) {
                                        // Ignore
                                    }
                                },
                                shape = RoundedCornerShape(6.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2E3852)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(34.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                            ) {
                                Icon(imageVector = Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(13.dp), tint = Color(0xFF818CF8))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Open ${platformType.displayName} Auth URL directly in Browser", fontSize = 11.sp, color = Color(0xFFCBD5E1))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = oauthClientId,
                        onValueChange = { oauthClientId = it },
                        label = { Text("OAuth Client ID / App ID (Optional)", fontSize = 11.sp) },
                        placeholder = { 
                            Text(
                                if (platformType == com.example.data.McpPlatformType.SUPABASE) 
                                    "e.g. 123e4567-e89b-12d3-a456-426614174000 (UUID)"
                                else 
                                    "e.g. mcp_app_${platformType.name.lowercase()}",
                                fontSize = 11.sp,
                                color = Color(0xFF64748B)
                            ) 
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = tokenValue,
                        onValueChange = { input ->
                            var clean = input.trim()
                            if (clean.contains("code=")) {
                                try {
                                    val uri = android.net.Uri.parse(clean)
                                    val extractedCode = uri.getQueryParameter("code")
                                    if (!extractedCode.isNullOrBlank()) {
                                        clean = extractedCode
                                    }
                                } catch (e: Exception) {
                                    val codeIndex = clean.indexOf("code=")
                                    if (codeIndex != -1) {
                                        var extracted = clean.substring(codeIndex + 5)
                                        if (extracted.contains("&")) {
                                            extracted = extracted.substringBefore("&")
                                        }
                                        clean = extracted
                                    }
                                }
                            }
                            tokenValue = clean
                        },
                        label = { Text("Manual Callback URL / OAuth Code (Fallback)", fontSize = 11.sp) },
                        placeholder = { Text("Paste code or full redirect URL if needed", fontSize = 11.sp, color = Color(0xFF64748B)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showToken = !showToken }) {
                                Icon(
                                    imageVector = if (showToken) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = "Toggle token",
                                    tint = Color(0xFF94A3B8),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    )
                } else {
                    // Personal Access Token
                    OutlinedButton(
                        onClick = {
                            try {
                                uriHandler.openUri(platformType.defaultConsoleUrl)
                            } catch (e: Exception) {
                                // Ignore
                            }
                        },
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2E3852)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(36.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                    ) {
                        Icon(imageVector = Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color(0xFF818CF8))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Open ${platformType.displayName} Console to get API Key", fontSize = 11.sp, color = Color(0xFFCBD5E1))
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = tokenValue,
                        onValueChange = { tokenValue = it },
                        label = { Text("Personal Access Token / Secret Key", fontSize = 11.sp) },
                        placeholder = { Text("e.g. sb_secret_... or appwrite_key_...", fontSize = 11.sp, color = Color(0xFF64748B)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showToken = !showToken }) {
                                Icon(
                                    imageVector = if (showToken) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = "Toggle key",
                                    tint = Color(0xFF94A3B8),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Text("Cancel", color = Color(0xFF94A3B8), fontSize = 12.sp)
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = {
                            if (tokenValue.isNotBlank()) {
                                // If token/key is provided in either tab, connect directly!
                                onConfirmConnect(tokenValue.trim())
                            } else if (authMode == 0) {
                                isAuthorizing = true
                                onStartOAuth(oauthClientId.ifBlank { null })
                            } else {
                                onConfirmConnect(tokenValue.trim())
                            }
                        },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1), contentColor = Color.White),
                        modifier = Modifier.height(36.dp)
                    ) {
                        if (isAuthorizing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                        } else {
                            Icon(imageVector = Icons.Default.Link, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Text(
                            when {
                                tokenValue.isNotBlank() -> "Connect with Token"
                                authMode == 0 -> "Authorize via Browser"
                                else -> "Save & Connect"
                            },
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}
