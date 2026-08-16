package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.McpPlatformType
import com.example.data.McpServer
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun McpManagementDialog(
    workspaceId: String,
    workspaceName: String,
    servers: List<McpServer>,
    onAddServer: (name: String, url: String, platform: String, apiKey: String?) -> Unit,
    onToggleWorkspace: (serverId: String, enabled: Boolean) -> Unit,
    onTestConnect: suspend (serverId: String) -> Unit,
    onStartOAuthFlow: (android.content.Context, String, String?) -> Unit = { _, _, _ -> },
    onDeleteServer: (serverId: String) -> Unit,
    onDismiss: () -> Unit
) {
    var showAddForm by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Workspace MCPs, 1: All MCP Servers
    var selectedOAuthServer by remember { mutableStateOf<McpServer?>(null) }
    val scope = rememberCoroutineScope()

    val context = androidx.compose.ui.platform.LocalContext.current

    if (selectedOAuthServer != null) {
        val target = selectedOAuthServer!!
        McpOAuthConnectDialog(
            server = target,
            onStartOAuth = { clientId ->
                selectedOAuthServer = null
                onAddServer(target.name, target.url, target.platform, null)
                val activeServerId = servers.find { it.platform == target.platform || it.id == target.id }?.id ?: target.id
                val activity = context as? android.app.Activity ?: (context as? android.content.ContextWrapper)?.baseContext as? android.app.Activity ?: context
                onStartOAuthFlow(activity, activeServerId, clientId)
            },
            onConfirmConnect = { token ->
                selectedOAuthServer = null
                onAddServer(target.name, target.url, target.platform, token)
                val activeServerId = servers.find { it.platform == target.platform || it.id == target.id }?.id ?: target.id
                scope.launch { onTestConnect(activeServerId) }
            },
            onDismiss = { selectedOAuthServer = null }
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.90f)
                .clip(RoundedCornerShape(18.dp))
                .border(1.dp, Color(0xFF262C40), RoundedCornerShape(18.dp)),
            color = Color(0xFF0F1117),
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF6366F1).copy(alpha = 0.16f))
                                .border(1.dp, Color(0xFF6366F1).copy(alpha = 0.35f), RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Extension,
                                contentDescription = "MCP Integration",
                                tint = Color(0xFF818CF8),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Model Context Protocol (MCP)",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFF1F5F9),
                                    fontSize = 15.sp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    color = Color(0xFF6366F1).copy(alpha = 0.18f),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = "PRO",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF818CF8),
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Integrate remote database, auth & serverless tools into AI workflows",
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

                // Custom Segmented Pill Tab Row
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFF161A26),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF23293D))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(3.dp)
                    ) {
                        val tab0Active = selectedTab == 0
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { selectedTab = 0 },
                            shape = RoundedCornerShape(8.dp),
                            color = if (tab0Active) Color(0xFF262E45) else Color.Transparent
                        ) {
                            Text(
                                text = "Active in $workspaceName",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (tab0Active) FontWeight.Bold else FontWeight.Medium,
                                color = if (tab0Active) Color(0xFFF1F5F9) else Color(0xFF94A3B8),
                                modifier = Modifier.padding(vertical = 8.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                fontSize = 12.sp
                            )
                        }

                        val tab1Active = selectedTab == 1
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { selectedTab = 1 },
                            shape = RoundedCornerShape(8.dp),
                            color = if (tab1Active) Color(0xFF262E45) else Color.Transparent
                        ) {
                            Text(
                                text = "All MCP Servers (${servers.size})",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (tab1Active) FontWeight.Bold else FontWeight.Medium,
                                color = if (tab1Active) Color(0xFFF1F5F9) else Color(0xFF94A3B8),
                                modifier = Modifier.padding(vertical = 8.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Action Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (selectedTab == 0) "Workspace active integrations:" else "Registered MCP endpoints:",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFF94A3B8),
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.sp
                    )

                    Button(
                        onClick = { showAddForm = !showAddForm },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (showAddForm) Color(0xFF262E45) else Color(0xFF6366F1),
                            contentColor = Color.White
                        ),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Icon(
                            imageVector = if (showAddForm) Icons.Default.ExpandLess else Icons.Default.Add,
                            contentDescription = "Add MCP",
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = if (showAddForm) "Close" else "Add Remote MCP", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Add MCP Server Form
                AnimatedVisibility(visible = showAddForm) {
                    AddMcpServerCard(
                        onSave = { name, url, platform, apiKey ->
                            onAddServer(name, url, platform, apiKey)
                            showAddForm = false
                        }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // List of Servers
                val displayServers = if (selectedTab == 0) {
                    servers.filter { it.enabledWorkspaces.contains(workspaceId) }
                } else {
                    servers
                }

                if (displayServers.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF1E2333)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Dns,
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp),
                                    tint = Color(0xFF64748B)
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = if (selectedTab == 0) "No MCP servers enabled for $workspaceName" else "No MCP servers added yet",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFFCBD5E1),
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (selectedTab == 0) "Switch to 'All MCP Servers' to toggle or click 'Add Remote MCP'" else "Click 'Add Remote MCP' above to register your first endpoint",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF64748B),
                                fontSize = 11.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(displayServers, key = { it.id }) { server ->
                            McpServerItemCard(
                                server = server,
                                isEnabledInWorkspace = server.enabledWorkspaces.contains(workspaceId),
                                onToggleWorkspace = { enabled -> onToggleWorkspace(server.id, enabled) },
                                onConnectTest = {
                                    scope.launch { onTestConnect(server.id) }
                                },
                                onOAuthConnect = {
                                    selectedOAuthServer = server
                                },
                                onDelete = { onDeleteServer(server.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddMcpServerCard(
    onSave: (name: String, url: String, platform: String, apiKey: String?) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var selectedPlatform by remember { mutableStateOf(McpPlatformType.CUSTOM) }
    var apiKey by remember { mutableStateOf("") }
    var showApiKey by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFF262E45), RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF131724))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.AddCircleOutline,
                    contentDescription = null,
                    tint = Color(0xFF818CF8),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Register Remote MCP Server",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFF1F5F9),
                    fontSize = 13.sp
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Platform Preset selector
            Text("Select Platform Preset:", style = MaterialTheme.typography.labelSmall, fontSize = 11.sp, color = Color(0xFF94A3B8))
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                McpPlatformType.entries.take(3).forEach { plat ->
                    val isSelected = selectedPlatform == plat
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                selectedPlatform = plat
                                if (name.isEmpty() || McpPlatformType.entries.any { it.displayName == name || name.startsWith(it.displayName) }) {
                                    name = "${plat.displayName} MCP"
                                }
                                if (url.isEmpty() || McpPlatformType.entries.any { it.defaultUrlPlaceholder == url }) {
                                    url = plat.defaultUrlPlaceholder
                                }
                            },
                        shape = RoundedCornerShape(6.dp),
                        color = if (isSelected) Color(0xFF6366F1).copy(alpha = 0.25f) else Color(0xFF1A2030),
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (isSelected) Color(0xFF6366F1) else Color(0xFF262C40))
                    ) {
                        Text(
                            text = plat.displayName,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) Color(0xFF818CF8) else Color(0xFF94A3B8),
                            modifier = Modifier.padding(vertical = 6.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                McpPlatformType.entries.drop(3).forEach { plat ->
                    val isSelected = selectedPlatform == plat
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                selectedPlatform = plat
                                if (name.isEmpty() || McpPlatformType.entries.any { it.displayName == name || name.startsWith(it.displayName) }) {
                                    name = "${plat.displayName} MCP"
                                }
                                if (url.isEmpty() || McpPlatformType.entries.any { it.defaultUrlPlaceholder == url }) {
                                    url = plat.defaultUrlPlaceholder
                                }
                            },
                        shape = RoundedCornerShape(6.dp),
                        color = if (isSelected) Color(0xFF6366F1).copy(alpha = 0.25f) else Color(0xFF1A2030),
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (isSelected) Color(0xFF6366F1) else Color(0xFF262C40))
                    ) {
                        Text(
                            text = plat.displayName,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) Color(0xFF818CF8) else Color(0xFF94A3B8),
                            modifier = Modifier.padding(vertical = 6.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Server Name", fontSize = 11.sp) },
                placeholder = { Text("e.g. Supabase Production MCP", fontSize = 12.sp, color = Color(0xFF64748B)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("Remote MCP Server URL", fontSize = 11.sp) },
                placeholder = { Text(selectedPlatform.defaultUrlPlaceholder, fontSize = 12.sp, color = Color(0xFF64748B)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                shape = RoundedCornerShape(8.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = { Text("API Key / Bearer Token (Optional)", fontSize = 11.sp) },
                placeholder = { Text("Leave blank if using OAuth browser flow", fontSize = 12.sp, color = Color(0xFF64748B)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showApiKey = !showApiKey }) {
                        Icon(
                            imageVector = if (showApiKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = "Toggle API Key",
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Button(
                    onClick = {
                        if (name.isNotBlank() && url.isNotBlank()) {
                            onSave(name.trim(), url.trim(), selectedPlatform.name, apiKey.trim().ifEmpty { null })
                        }
                    },
                    enabled = name.isNotBlank() && url.isNotBlank(),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1), contentColor = Color.White),
                    modifier = Modifier.height(36.dp)
                ) {
                    Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Save MCP Server", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
fun McpServerItemCard(
    server: McpServer,
    isEnabledInWorkspace: Boolean,
    onToggleWorkspace: (Boolean) -> Unit,
    onConnectTest: () -> Unit,
    onOAuthConnect: () -> Unit,
    onDelete: () -> Unit
) {
    val platformType = McpPlatformType.fromString(server.platform)
    val platformBadgeColor = when (platformType) {
        McpPlatformType.SUPABASE -> Color(0xFF3ECF8E)
        McpPlatformType.CLOUDFLARE -> Color(0xFFF38020)
        McpPlatformType.VERCEL -> Color(0xFFE2E8F0)
        McpPlatformType.GOOGLE_STITCH -> Color(0xFF60A5FA)
        McpPlatformType.CUSTOM -> Color(0xFFA78BFA)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFF22283A), RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF131722))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    McpPlatformLogo(platformType = platformType, size = 36.dp)

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = server.name,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFF1F5F9),
                                fontSize = 13.sp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                color = platformBadgeColor.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(4.dp),
                                border = androidx.compose.foundation.BorderStroke(0.5.dp, platformBadgeColor.copy(alpha = 0.4f))
                            ) {
                                Text(
                                    text = platformType.displayName.uppercase(),
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = platformBadgeColor,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                )
                            }
                        }

                        Text(
                            text = server.url,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF64748B),
                            fontSize = 11.sp,
                            maxLines = 1
                        )
                    }
                }

                Switch(
                    checked = isEnabledInWorkspace,
                    onCheckedChange = onToggleWorkspace
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Divider line
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color(0xFF1E2333))
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Status indicator
                val isConnected = server.status.startsWith("Connected")
                val isPending = server.status.startsWith("Connecting") || server.status.startsWith("Discovering") || server.status.startsWith("Awaiting") || server.status.startsWith("Authorizing")
                val statusColor = when {
                    isConnected -> Color(0xFF10B981)
                    isPending -> Color(0xFFF59E0B)
                    else -> Color(0xFFEF4444)
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(statusColor)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (server.availableTools.isNotEmpty()) "${server.status} (${server.availableTools.size} tools)" else server.status,
                        fontSize = 11.sp,
                        color = statusColor,
                        fontWeight = FontWeight.Medium
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Button(
                        onClick = onOAuthConnect,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isConnected) Color(0xFF1E2436) else Color(0xFF6366F1),
                            contentColor = if (isConnected) Color(0xFFCBD5E1) else Color.White
                        ),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Icon(imageVector = Icons.Default.VpnKey, contentDescription = null, modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (isConnected) "Re-auth" else "Connect", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }

                    OutlinedButton(
                        onClick = onConnectTest,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        shape = RoundedCornerShape(6.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF262E45)),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(12.dp), tint = Color(0xFF94A3B8))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Sync", fontSize = 11.sp, color = Color(0xFFCBD5E1))
                    }

                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Delete MCP",
                            tint = Color(0xFFF43F5E),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}
