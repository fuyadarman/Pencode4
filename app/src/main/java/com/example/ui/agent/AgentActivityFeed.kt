package com.example.ui.agent

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.AiActionLog
import kotlinx.coroutines.delay

/**
 * Modern minimalist Agent Activity Feed replacing the old operations timeline.
 * Inspired by OpenAI Canvas / Devin agent feed design:
 * - Real-time "Working for Xm Ys" elapsed timer
 * - Interleaved thought paragraphs
 * - Clean, compact action rows (Skills, Sub-agents, Tools, Optimizations)
 */
@Composable
fun AgentActivityFeed(
    displayLogs: List<AiActionLog>,
    isThinking: Boolean,
    modifier: Modifier = Modifier,
    initialElapsedSeconds: Long = 0L
) {
    var elapsedSeconds by remember { mutableStateOf(initialElapsedSeconds) }
    val startTime = remember(isThinking) {
        if (displayLogs.isNotEmpty()) displayLogs.first().startTime else System.currentTimeMillis()
    }

    LaunchedEffect(isThinking, startTime) {
        if (isThinking) {
            while (true) {
                val now = System.currentTimeMillis()
                elapsedSeconds = maxOf(0L, (now - startTime) / 1000L)
                delay(1000L)
            }
        } else if (displayLogs.isNotEmpty()) {
            val last = displayLogs.last().timestamp
            val first = displayLogs.first().startTime
            elapsedSeconds = maxOf(0L, (last - first) / 1000L)
        }
    }

    val formattedDuration = remember(elapsedSeconds) {
        val hours = elapsedSeconds / 3600
        val minutes = (elapsedSeconds % 3600) / 60
        val seconds = elapsedSeconds % 60
        buildString {
            if (hours > 0) append("${hours}h ")
            if (minutes > 0 || hours > 0) append("${minutes}m ")
            append("${seconds}s")
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        // 1. Elapsed Runtime Header ("Working for 1m 24s")
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(bottom = 12.dp, top = 4.dp)
        ) {
            if (isThinking) {
                CircularProgressIndicator(
                    modifier = Modifier.size(12.dp),
                    strokeWidth = 1.5.dp,
                    color = Color(0xFF7D8590)
                )
            }
            Text(
                text = if (isThinking) "Working for $formattedDuration" else "Completed in $formattedDuration",
                fontSize = 13.sp,
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Normal,
                color = Color(0xFF8B949E)
            )
        }

        // 2. Feed Items: Tool Execution Indicators matching screenshot design
        ToolExecutionListView(
            logs = displayLogs,
            isThinking = isThinking
        )
    }
}

@Composable
private fun AgentFeedItemRow(
    log: AiActionLog,
    isGlobalThinking: Boolean
) {
    var isExpanded by remember { mutableStateOf(false) }

    val title = log.title
    val isThought = title.contains("thinking", ignoreCase = true) ||
            title.contains("formulating logic", ignoreCase = true) ||
            title.contains("Thought process", ignoreCase = true)
    val isSkill = title.contains("skill", ignoreCase = true)
    val isSubAgent = title.contains("agent", ignoreCase = true) ||
            title.contains("security boundary", ignoreCase = true) ||
            title.contains("specialist", ignoreCase = true) ||
            title.contains("teammate", ignoreCase = true)
    val isOptimization = title.contains("optimized", ignoreCase = true) ||
            title.contains("prun", ignoreCase = true) ||
            title.contains("compressed", ignoreCase = true) ||
            title.contains("cache", ignoreCase = true)

    if (isThought) {
        val spec = remember(log, isGlobalThinking) {
            ToolExecutionItemMapper.mapLog(log, isGlobalThinking)
        }
        AgentThoughtCollapsibleView(
            log = log,
            spec = spec,
            isGlobalThinking = isGlobalThinking
        )
    } else {
        // Compact action row with modern icon & clean typography
        val (icon, iconTint, displayText, statusLabel) = when {
            isSkill -> {
                Quad(Icons.Default.AutoAwesome, Color(0xFF8B949E), title, null)
            }
            isSubAgent -> {
                val status = when (log.status) {
                    "thinking" -> "started working"
                    "success" -> "finished"
                    else -> "updated"
                }
                Quad(Icons.Default.Bolt, Color(0xFFE3B341), title, status)
            }
            isOptimization -> {
                Quad(Icons.Default.Sync, Color(0xFF8B949E), "Optimized the conversation", null)
            }
            title.startsWith("Edit:") || title.startsWith("Patch:") || title.contains("Modified file") -> {
                Quad(Icons.Default.Edit, Color(0xFF7EE787), title, if (log.status == "thinking") "editing" else null)
            }
            title.contains("search", ignoreCase = true) -> {
                Quad(Icons.Default.Search, Color(0xFF8B949E), title, null)
            }
            else -> {
                val cleanTitle = if (title.contains("Read files") || title.contains("Read:")) {
                    "Read files, ran commands"
                } else {
                    title
                }
                Quad(Icons.Default.Description, Color(0xFF8B949E), cleanTitle, null)
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .clickable(enabled = !log.details.isNullOrBlank()) {
                    isExpanded = !isExpanded
                }
                .padding(vertical = 3.dp, horizontal = 2.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = displayText,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.SansSerif,
                        color = Color(0xFFE6EDF3),
                        maxLines = 1
                    )
                }

                if (statusLabel != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = statusLabel,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.SansSerif,
                        color = Color(0xFF8B949E)
                    )
                } else if (!log.details.isNullOrBlank()) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = "Details",
                        tint = Color(0xFF484F58),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            // Expanded details view for file diffs or command logs
            AnimatedVisibility(visible = isExpanded && !log.details.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFF161B22),
                    border = BorderStroke(1.dp, Color(0xFF30363D)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp, start = 22.dp)
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        if (!log.lineRange.isNullOrBlank()) {
                            Text(
                                text = log.lineRange,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF58A6FF)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                        Text(
                            text = log.details ?: "",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFF8B949E)
                        )
                    }
                }
            }
        }
    }
}

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
