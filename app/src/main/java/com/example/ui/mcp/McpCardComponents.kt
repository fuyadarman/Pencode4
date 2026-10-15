package com.example.ui.mcp

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.McpPlatformType
import com.example.data.McpServer
import com.example.ui.theme.AppTheme

@Composable
fun ProfessionalMcpServerCard(
    server: McpServer,
    isEnabledInWorkspace: Boolean,
    onToggleWorkspace: (Boolean) -> Unit,
    onConnectTest: () -> Unit,
    onOAuthConnect: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val platformType = McpPlatformType.fromString(server.platform)
    val platformBadgeColor = when (platformType) {
        McpPlatformType.SUPABASE -> Color(0xFF3ECF8E)
        McpPlatformType.CLOUDFLARE -> Color(0xFFF38020)
        McpPlatformType.VERCEL -> Color(0xFFE2E8F0)
        McpPlatformType.CUSTOM -> Color(0xFFA78BFA)
    }

    val isConnected = server.status.startsWith("Connected", ignoreCase = true)
    val isPending = server.status.startsWith("Connecting", ignoreCase = true) ||
            server.status.startsWith("Discovering", ignoreCase = true) ||
            server.status.startsWith("Awaiting", ignoreCase = true) ||
            server.status.startsWith("Authorizing", ignoreCase = true) ||
            server.status.startsWith("Exchanging", ignoreCase = true)

    val isError = server.status.contains("Error", ignoreCase = true) ||
            server.status.contains("failed", ignoreCase = true) ||
            server.status.contains("Timeout", ignoreCase = true) ||
            server.status.contains("No OAuth", ignoreCase = true)

    val statusDotColor = when {
        isConnected -> Color(0xFF10B981)
        isPending -> Color(0xFFF59E0B)
        isError -> Color(0xFFEF4444)
        else -> Color(0xFF64748B)
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(
                1.dp,
                if (isError) Color(0xFFEF4444).copy(alpha = 0.35f)
                else if (isConnected) Color(0xFF10B981).copy(alpha = 0.3f)
                else AppTheme.border,
                RoundedCornerShape(14.dp)
            ),
        colors = CardDefaults.cardColors(containerColor = AppTheme.bgCanvas),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // Top Row: Logo, Server Name, Platform Tag & Status Chip
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RealWebsiteMcpLogo(
                        platformType = platformType,
                        serverUrl = server.url,
                        size = 40.dp
                    )

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = server.name,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = AppTheme.textPrimary,
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                color = platformBadgeColor.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(4.dp),
                                border = BorderStroke(0.5.dp, platformBadgeColor.copy(alpha = 0.45f))
                            ) {
                                Text(
                                    text = platformType.displayName.uppercase(),
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = platformBadgeColor,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(2.dp))

                        Text(
                            text = server.url,
                            style = MaterialTheme.typography.bodySmall,
                            color = AppTheme.textSecondary,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Status Badge
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = statusDotColor.copy(alpha = 0.12f),
                    border = BorderStroke(0.8.dp, statusDotColor.copy(alpha = 0.35f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(statusDotColor)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = when {
                                isConnected && server.availableTools.isNotEmpty() -> "${server.availableTools.size} Tools"
                                isConnected -> "Active"
                                isPending -> "Connecting..."
                                isError -> "Error"
                                else -> "Ready"
                            },
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = statusDotColor
                        )
                    }
                }
            }

            // Error Banner (if error occurred) - Clean, Informative & Doesn't Hide Anything!
            if (isError) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF2A1215),
                    border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = "Error detail",
                            tint = Color(0xFFF87171),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = server.status,
                            fontSize = 11.5.sp,
                            color = Color(0xFFFECACA),
                            lineHeight = 15.sp,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Divider
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(AppTheme.border)
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Dedicated Full Action Toolbar - ALWAYS VISIBLE, NEVER HIDDEN
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Workspace Toggle (On/Off) Switch
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Switch(
                        checked = isEnabledInWorkspace,
                        onCheckedChange = onToggleWorkspace,
                        modifier = Modifier.height(28.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isEnabledInWorkspace) "Enabled" else "Disabled",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (isEnabledInWorkspace) Color(0xFF38BDF8) else AppTheme.textSecondary
                    )
                }

                // Action Buttons: Connect, Sync, Delete
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Connect / Re-auth Button
                    Button(
                        onClick = onOAuthConnect,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                        shape = RoundedCornerShape(7.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isConnected) Color(0xFF1E2436) else Color(0xFF6366F1),
                            contentColor = if (isConnected) Color(0xFFCBD5E1) else Color.White
                        ),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Icon(
                            imageVector = if (isConnected) Icons.Default.Refresh else Icons.Default.VpnKey,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isConnected) "Re-auth" else "Connect",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    // Sync Button
                    OutlinedButton(
                        onClick = onConnectTest,
                        contentPadding = PaddingValues(horizontal = 9.dp, vertical = 0.dp),
                        shape = RoundedCornerShape(7.dp),
                        border = BorderStroke(1.dp, Color(0xFF262E45)),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Sync,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = Color(0xFF94A3B8)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Sync",
                            fontSize = 11.sp,
                            color = Color(0xFFCBD5E1)
                        )
                    }

                    // Delete Button
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(7.dp))
                            .background(Color(0xFF2E1218))
                            .border(1.dp, Color(0xFFEF4444).copy(alpha = 0.3f), RoundedCornerShape(7.dp))
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Delete MCP Server",
                            tint = Color(0xFFF87171),
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }
        }
    }
}
