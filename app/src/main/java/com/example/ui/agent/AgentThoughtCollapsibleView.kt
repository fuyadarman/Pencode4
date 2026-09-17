package com.example.ui.agent

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.AiActionLog
import kotlinx.coroutines.isActive

/**
 * Collapsible UI component for AI Thoughts, Reasoning, and Planning.
 *
 * Implements the user requirement:
 * - Collapsed by default
 * - Header displaying Lightbulb icon and duration (e.g. "Thought for 2 seconds")
 * - Expand/collapse button matching Screenshot_20260916-083743_cropped.png
 * - Smoothly expands to reveal full thought / reasoning details
 */
@Composable
fun AgentThoughtCollapsibleView(
    log: AiActionLog,
    spec: ToolStyleSpec,
    isGlobalThinking: Boolean,
    modifier: Modifier = Modifier
) {
    // Collapsed by default as requested
    var isExpanded by remember { mutableStateOf(false) }

    val thoughtText = remember(log, spec) {
        val raw = log.details?.trim()?.takeIf { it.isNotBlank() }
            ?: spec.targetLabel.trim().takeIf { it.isNotBlank() }
            ?: log.title
        cleanThoughtJson(raw)
    }

    val isCurrentlyExecuting = spec.isExecuting && isGlobalThinking

    var liveElapsedSeconds by remember(log.id, isCurrentlyExecuting) {
        mutableStateOf(
            if (log.startTime > 0) maxOf(1L, (System.currentTimeMillis() - log.startTime) / 1000L) else 1L
        )
    }

    LaunchedEffect(isCurrentlyExecuting, log.id) {
        if (isCurrentlyExecuting) {
            while (isActive) {
                kotlinx.coroutines.delay(500)
                val start = if (log.startTime > 0) log.startTime else log.timestamp
                liveElapsedSeconds = maxOf(1L, (System.currentTimeMillis() - start) / 1000L)
            }
        }
    }

    // Calculate real completed seconds using AgentExecutionTimer
    val durationSeconds = remember(log.durationMillis, log.timestamp, log.startTime) {
        com.example.agent.AgentExecutionTimer.calculateDurationSeconds(
            durationMillis = log.durationMillis,
            startTime = log.startTime,
            endTime = log.timestamp
        )
    }

    val isReasoning = remember(log.title, spec.actionTitle, log.details) {
        log.title.contains("reasoning", ignoreCase = true) ||
        spec.actionTitle.contains("reasoning", ignoreCase = true) ||
        com.example.agent.AgentReasoningDetector.isReasoningText(log.details)
    }

    val headerLabel = com.example.agent.AgentExecutionTimer.formatThoughtHeader(
        isExecuting = isCurrentlyExecuting,
        durationSeconds = durationSeconds,
        liveElapsedSeconds = liveElapsedSeconds,
        isReasoning = isReasoning
    )

    val rotationAngle by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        label = "arrowRotation"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        // Collapsed Header Row matching Screenshot_20260916-133802 and Screenshot_20260916-083743
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    isExpanded = !isExpanded
                }
                .padding(vertical = 4.dp, horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left: Lightbulb Icon and "Thought for X seconds" text
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Lightbulb,
                    contentDescription = "Thinking Process",
                    tint = if (isCurrentlyExecuting) Color(0xFFE3B341) else Color(0xFF8B949E),
                    modifier = Modifier.size(16.dp)
                )

                Text(
                    text = headerLabel,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Normal,
                    color = Color(0xFF8B949E)
                )
            }

            // Right: Expand/Collapse Button matching Screenshot_20260916-083743_cropped.png
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF161B22))
                    .border(BorderStroke(1.dp, Color(0xFF30363D)), RoundedCornerShape(6.dp))
                    .clickable { isExpanded = !isExpanded },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = if (isExpanded) "Collapse thought" else "Expand thought",
                    tint = Color(0xFF8B949E),
                    modifier = Modifier
                        .size(16.dp)
                        .rotate(rotationAngle)
                )
            }
        }

        // Expanded Thought Details Container
        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFF0D1117),
                border = BorderStroke(1.dp, Color(0xFF21262D)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp)
                ) {
                    Text(
                        text = thoughtText,
                        fontSize = 12.5.sp,
                        lineHeight = 18.sp,
                        color = Color(0xFFC9D1D9),
                        fontFamily = FontFamily.SansSerif
                    )
                }
            }
        }
    }
}

/**
 * Strips raw JSON thought envelope if present, leaving purely clean readable text
 */
private fun cleanThoughtJson(raw: String): String {
    var text = raw.trim()
    if (text.startsWith("{\"thought\":") || text.startsWith("{\"thinking\":")) {
        val pattern = java.util.regex.Pattern.compile("\"(?:thought|thinking)\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"", java.util.regex.Pattern.DOTALL)
        val matcher = pattern.matcher(text)
        if (matcher.find()) {
            val extracted = matcher.group(1)
            if (!extracted.isNullOrBlank()) {
                text = extracted.replace("\\\"", "\"").replace("\\n", "\n").replace("\\t", "\t").replace("\\\\", "\\")
            }
        }
    }
    return text
}
