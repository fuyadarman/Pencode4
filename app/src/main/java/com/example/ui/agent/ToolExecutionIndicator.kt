package com.example.ui.agent

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.AiActionLog
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Dedicated, highly polished Tool Call & Tool Execution Indicator.
 * Displays real-time and completed tool executions with:
 * - Tool Name badge & distinct icons
 * - Live animated execution spinner & glowing border
 * - Target resource / file path / bash command chip
 * - Execution duration indicator
 * - Expandable output, parameters, line range & code snippet inspection drawer
 */
@Composable
fun ToolExecutionIndicatorRow(
    log: AiActionLog,
    isGlobalThinking: Boolean,
    modifier: Modifier = Modifier
) {
    val classification = remember(log, isGlobalThinking) {
        ToolCallTypeClassifier.classify(log, isGlobalThinking)
    }

    var isExpanded by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()
    var isCopied by remember { mutableStateOf(false) }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    val borderColor = when {
        classification.isRunning -> classification.type.defaultColor.copy(alpha = pulseAlpha)
        classification.isFailed -> Color(0xFFEF4444).copy(alpha = 0.6f)
        else -> Color(0xFF262C3A)
    }

    val backgroundColor = when {
        classification.isRunning -> Color(0xFF101522)
        classification.isFailed -> Color(0xFF1E1317)
        else -> Color(0xFF0F131C)
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = backgroundColor,
        border = BorderStroke(1.dp, borderColor),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = !log.details.isNullOrBlank() || !log.lineRange.isNullOrBlank()) {
                isExpanded = !isExpanded
            }
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
            // Main Top Bar of the Tool Call Indicator
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left: Tool Icon + Tool Badge + Target Name
                Row(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    // Tool Icon in circular badge
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .background(
                                color = classification.type.defaultColor.copy(alpha = if (classification.isRunning) 0.25f else 0.15f),
                                shape = CircleShape
                            )
                            .border(
                                width = 1.dp,
                                color = classification.type.defaultColor.copy(alpha = if (classification.isRunning) 0.6f else 0.3f),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (classification.isRunning) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(13.dp),
                                strokeWidth = 1.8.dp,
                                color = classification.type.defaultColor
                            )
                        } else {
                            Icon(
                                imageVector = classification.type.icon,
                                contentDescription = classification.type.displayName,
                                tint = if (classification.isFailed) Color(0xFFF87171) else classification.type.defaultColor,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }

                    // Tool Name Tag (e.g. "read_file", "edit_file", "run_command")
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = classification.type.defaultColor.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, classification.type.defaultColor.copy(alpha = 0.25f))
                    ) {
                        Text(
                            text = classification.toolName,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = classification.type.defaultColor,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }

                    // Target Resource / File path / Command
                    Text(
                        text = classification.targetResource.ifBlank { log.title },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFFE2E8F0),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Right: Status Badge & Duration & Expand Arrow
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Line range pill if available
                    if (!log.lineRange.isNullOrBlank()) {
                        Surface(
                            shape = RoundedCornerShape(3.dp),
                            color = Color(0xFF1E293B),
                            border = BorderStroke(1.dp, Color(0xFF334155))
                        ) {
                            Text(
                                text = log.lineRange,
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                color = Color(0xFF94A3B8),
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }

                    // Duration chip
                    if (!classification.durationText.isNullOrBlank()) {
                        Text(
                            text = classification.durationText,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFF64748B)
                        )
                    }

                    // Status Indicator Pill
                    val (statusColor, statusBg) = when {
                        classification.isRunning -> Pair(classification.type.defaultColor, classification.type.defaultColor.copy(alpha = 0.15f))
                        classification.isFailed -> Pair(Color(0xFFEF4444), Color(0xFFEF4444).copy(alpha = 0.15f))
                        else -> Pair(Color(0xFF22C55E), Color(0xFF22C55E).copy(alpha = 0.15f))
                    }

                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = statusBg,
                        border = BorderStroke(1.dp, statusColor.copy(alpha = 0.3f))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp)
                        ) {
                            if (classification.isRunning) {
                                Box(
                                    modifier = Modifier
                                        .size(5.dp)
                                        .background(statusColor, CircleShape)
                                )
                            } else if (classification.isFailed) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Failed",
                                    tint = statusColor,
                                    modifier = Modifier.size(9.dp)
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Success",
                                    tint = statusColor,
                                    modifier = Modifier.size(9.dp)
                                )
                            }
                            Text(
                                text = classification.statusLabel,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = statusColor
                            )
                        }
                    }

                    // Expand / Collapse Chevron
                    if (!log.details.isNullOrBlank() || !log.lineRange.isNullOrBlank()) {
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = if (isExpanded) "Collapse" else "Expand",
                            tint = Color(0xFF64748B),
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }

            // Expandable Execution Payload & Output Viewer
            AnimatedVisibility(
                visible = isExpanded && (!log.details.isNullOrBlank() || !log.lineRange.isNullOrBlank()),
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                ) {
                    // Inner Payload Container
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFF070A0F),
                        border = BorderStroke(1.dp, Color(0xFF1E2433)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                            // Header bar with payload label & copy button
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DataObject,
                                        contentDescription = null,
                                        tint = Color(0xFF64748B),
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Text(
                                        text = "TOOL EXECUTION PAYLOAD / OUTPUT",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        color = Color(0xFF64748B)
                                    )
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(3.dp))
                                        .clickable {
                                            val textToCopy = "${log.lineRange?.let { "$it\n" } ?: ""}${log.details ?: ""}"
                                            clipboardManager.setText(AnnotatedString(textToCopy))
                                            isCopied = true
                                            coroutineScope.launch {
                                                delay(2000L)
                                                isCopied = false
                                            }
                                        }
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isCopied) Icons.Default.Check else Icons.Default.ContentCopy,
                                        contentDescription = "Copy output",
                                        tint = if (isCopied) Color(0xFF4ADE80) else Color(0xFF94A3B8),
                                        modifier = Modifier.size(11.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = if (isCopied) "Copied" else "Copy",
                                        fontSize = 9.sp,
                                        color = if (isCopied) Color(0xFF4ADE80) else Color(0xFF94A3B8)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            // Scrollable output content
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                            ) {
                                Text(
                                    text = log.details ?: "No output returned.",
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = Color(0xFF94A3B8),
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Live Banner displayed whenever the Agent is executing a tool in real-time.
 */
@Composable
fun LiveToolExecutionBanner(
    currentLog: AiActionLog,
    elapsedSeconds: Long,
    modifier: Modifier = Modifier
) {
    val classification = remember(currentLog) {
        ToolCallTypeClassifier.classify(currentLog, isGlobalThinking = true)
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFF0F172A),
        border = BorderStroke(1.dp, classification.type.defaultColor.copy(alpha = 0.5f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f, fill = false)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                    color = classification.type.defaultColor
                )
                Text(
                    text = "Executing ${classification.toolName}:",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = classification.type.defaultColor
                )
                Text(
                    text = classification.targetResource.ifBlank { currentLog.title },
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Text(
                text = "${elapsedSeconds}s",
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                color = Color(0xFF94A3B8)
            )
        }
    }
}
