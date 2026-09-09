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

        // 2. Aggregate Tool Execution Summary Bar
        ToolExecutionSummaryBar(logs = displayLogs)

        // 3. Feed Items (Interleaved Thoughts & Dedicated Tool Execution Indicators)
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            displayLogs.forEach { log ->
                AgentFeedItemRow(log = log, isGlobalThinking = isThinking)
            }
        }
    }
}

@Composable
private fun AgentFeedItemRow(
    log: AiActionLog,
    isGlobalThinking: Boolean
) {
    val title = log.title
    val isThought = title.contains("thinking", ignoreCase = true) ||
            title.contains("formulating logic", ignoreCase = true) ||
            title.contains("Thought process", ignoreCase = true)

    if (isThought) {
        // Interleaved thought block rendered seamlessly with subtle step title
        val thoughtContent = log.details?.takeIf { it.isNotBlank() } ?: title
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                modifier = Modifier.padding(bottom = 2.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Psychology,
                    contentDescription = null,
                    tint = Color(0xFF58A6FF),
                    modifier = Modifier.size(13.dp)
                )
                Text(
                    text = log.title,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF8B949E)
                )
            }
            Text(
                text = thoughtContent,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                color = Color(0xFFC9D1D9),
                fontFamily = FontFamily.SansSerif
            )
        }
    } else {
        // Dedicated, high-contrast Tool Execution Indicator
        ToolExecutionIndicatorRow(
            log = log,
            isGlobalThinking = isGlobalThinking
        )
    }
}
