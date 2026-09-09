package com.example.ui.agent

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.AiActionLog

/**
 * Summary bar showing aggregate tool execution counts (Reads, Edits, Creates, Shell commands, etc.)
 */
@Composable
fun ToolExecutionSummaryBar(
    logs: List<AiActionLog>,
    modifier: Modifier = Modifier
) {
    val stats = remember(logs) {
        val toolLogs = logs.filter { log ->
            !log.title.contains("finished task execution", ignoreCase = true)
        }
        val classified = toolLogs.map { ToolCallTypeClassifier.classify(it, false) }
        val countsByType = classified.filter { !it.isThought }.groupBy { it.type }
        countsByType.mapValues { it.value.size }
    }

    if (stats.isEmpty()) return

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        stats.forEach { (type, count) ->
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = Color(0xFF131824),
                border = BorderStroke(1.dp, type.defaultColor.copy(alpha = 0.3f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = type.icon,
                        contentDescription = type.displayName,
                        tint = type.defaultColor,
                        modifier = Modifier.size(11.dp)
                    )
                    Text(
                        text = "${type.toolName}: $count",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Medium,
                        color = type.defaultColor
                    )
                }
            }
        }
    }
}
