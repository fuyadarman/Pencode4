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
                scope.launch {
                    val activity = context as? android.app.Activity ?: (context as? android.content.ContextWrapper)?.baseContext as? android.app.Activity ?: context
                    val mcpMgr = com.example.data.McpManager(context)
                    mcpMgr.startOAuthFlow(activity, activeServerId, clientId)
                }
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
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.88f)
                .clip(RoundedCornerShape(20.dp)),
            color = MaterialTheme.colorScheme.surface,
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Extension,
                                contentDescription = "MCP Integration",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Remote MCP Servers",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Manage Appwrite, Supabase, Cloudflare, Vercel & Custom MCPs for $workspaceName",
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

                Spacer(modifier = Modifier.height(12.dp))

                // Tabs
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Active in $workspaceName", fontSize = 13.sp, fontWeight = FontWeight.Medium) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("All MCP Servers (${servers.size})", fontSize = 13.sp, fontWeight = FontWeight.Medium) }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Action Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (selectedTab == 0) "Toggle MCPs on/off for AI in this workspace:" else "Remote MCP Server Connections:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )

                    Button(
                        onClick = { showAddForm = !showAddForm },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = if (showAddForm) Icons.Default.ExpandLess else Icons.Default.Add,
                            contentDescription = "Add MCP",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = if (showAddForm) "Close Form" else "Add Remote MCP", fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

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
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Dns,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.outline
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = if (selectedTab == 0) "No MCP servers enabled for this workspace.\nSwitch tab or enable servers below." else "No MCP servers added yet.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
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
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "Add Remote MCP Server URL",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Platform Preset selector
            Text("Platform Preset:", style = MaterialTheme.typography.labelSmall, fontSize = 11.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                McpPlatformType.entries.take(4).forEach { plat ->
                    FilterChip(
                        selected = selectedPlatform == plat,
                        onClick = {
                            selectedPlatform = plat
                            if (name.isEmpty() || McpPlatformType.entries.any { it.displayName == name }) {
                                name = "${plat.displayName} MCP"
                            }
                            if (url.isEmpty() || McpPlatformType.entries.any { it.defaultUrlPlaceholder == url }) {
                                url = plat.defaultUrlPlaceholder
                            }
                        },
                        label = { Text(plat.displayName, fontSize = 10.sp) },
                        modifier = Modifier.height(28.dp)
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                McpPlatformType.entries.drop(4).forEach { plat ->
                    FilterChip(
                        selected = selectedPlatform == plat,
                        onClick = {
                            selectedPlatform = plat
                            if (name.isEmpty() || McpPlatformType.entries.any { it.displayName == name }) {
                                name = "${plat.displayName} MCP"
                            }
                            if (url.isEmpty() || McpPlatformType.entries.any { it.defaultUrlPlaceholder == url }) {
                                url = plat.defaultUrlPlaceholder
                            }
                        },
                        label = { Text(plat.displayName, fontSize = 10.sp) },
                        modifier = Modifier.height(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Server Name", fontSize = 11.sp) },
                placeholder = { Text("e.g. Appwrite Cloud MCP") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(6.dp))

            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("Remote MCP Server URL", fontSize = 11.sp) },
                placeholder = { Text(selectedPlatform.defaultUrlPlaceholder) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
            )

            Spacer(modifier = Modifier.height(6.dp))

            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = { Text("API Key / Bearer Token (Optional)", fontSize = 11.sp) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showApiKey = !showApiKey }) {
                        Icon(
                            imageVector = if (showApiKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = "Toggle API Key"
                        )
                    }
                }
            )

            Spacer(modifier = Modifier.height(10.dp))

            Button(
                onClick = {
                    if (name.isNotBlank() && url.isNotBlank()) {
                        onSave(name.trim(), url.trim(), selectedPlatform.name, apiKey.trim().ifEmpty { null })
                    }
                },
                enabled = name.isNotBlank() && url.isNotBlank(),
                modifier = Modifier.align(Alignment.End)
            ) {
                Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Save & Connect")
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
        McpPlatformType.VERCEL -> Color(0xFF000000)
        McpPlatformType.GOOGLE_STITCH -> Color(0xFF4285F4)
        McpPlatformType.CUSTOM -> Color(0xFF7C4DFF)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
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
                    McpPlatformLogo(platformType = platformType, size = 38.dp)

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = server.name,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                color = platformBadgeColor.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = platformType.displayName,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = platformBadgeColor,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Text(
                            text = platformType.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium,
                            fontSize = 11.sp
                        )

                        Text(
                            text = server.url,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 10.sp
                        )
                    }
                }

                Switch(
                    checked = isEnabledInWorkspace,
                    onCheckedChange = onToggleWorkspace
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Status indicator
                val statusColor = when {
                    server.status.startsWith("Connected") -> Color(0xFF4CAF50)
                    server.status.startsWith("Connecting") -> Color(0xFFFF9800)
                    else -> MaterialTheme.colorScheme.error
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
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

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = onOAuthConnect,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Icon(imageVector = Icons.Default.VpnKey, contentDescription = null, modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (server.status.startsWith("Connected")) "Reconnect" else "Connect (OAuth)", fontSize = 10.sp)
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    OutlinedButton(
                        onClick = onConnectTest,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Text("Sync", fontSize = 10.sp)
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete MCP",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}
