package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
                .fillMaxWidth(0.95f)
                .clip(RoundedCornerShape(20.dp)),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Header with Platform Logo
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        McpPlatformLogo(platformType = platformType, size = 42.dp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Connect to ${server.name}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = platformType.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp
                            )
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Authentication Method Tabs
                TabRow(selectedTabIndex = authMode, modifier = Modifier.clip(RoundedCornerShape(10.dp))) {
                    Tab(
                        selected = authMode == 0,
                        onClick = { authMode = 0 },
                        text = { Text("OAuth 2.0 Auth", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
                    )
                    Tab(
                        selected = authMode == 1,
                        onClick = { authMode = 1 },
                        text = { Text("API Token / Key", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (authMode == 0) {
                    // OAuth 2.0 Connection Info
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.VpnKey,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "OAuth 2.0 Authorization Endpoint",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Authorizes AI Studio to invoke tools & API endpoints on ${platformType.displayName} on your behalf.",
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            OutlinedButton(
                                onClick = {
                                    try {
                                        uriHandler.openUri(platformType.buildAuthUrl(oauthClientId))
                                    } catch (e: Exception) {
                                        // Ignore
                                    }
                                },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(imageVector = Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Open ${platformType.displayName} Portal / Auth Link", fontSize = 11.sp)
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
                                    "e.g. mcp_app_${platformType.name.lowercase()}"
                            ) 
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = tokenValue,
                        onValueChange = { tokenValue = it },
                        label = { Text("OAuth Access Token / Bearer Code", fontSize = 11.sp) },
                        placeholder = { Text("Enter token or authorization code") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showToken = !showToken }) {
                                Icon(
                                    imageVector = if (showToken) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = "Toggle token"
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
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(imageVector = Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Get ${platformType.displayName} API Token / Key", fontSize = 11.sp)
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = tokenValue,
                        onValueChange = { tokenValue = it },
                        label = { Text("Personal Access Token / Secret Key", fontSize = 11.sp) },
                        placeholder = { Text("e.g. sb_secret_... or appwrite_key_...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showToken = !showToken }) {
                                Icon(
                                    imageVector = if (showToken) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = "Toggle key"
                                )
                            }
                        }
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
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
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (isAuthorizing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                        } else {
                            Icon(imageVector = Icons.Default.Link, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Text(
                            when {
                                tokenValue.isNotBlank() -> "Connect with Token"
                                authMode == 0 -> "Authorize via Browser"
                                else -> "Save & Connect"
                            }
                        )
                    }
                }
            }
        }
    }
}
