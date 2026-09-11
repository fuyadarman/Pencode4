package com.example.ui.agent

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.AiActionLog
import java.util.Locale

/**
 * Visual styling specification for each tool type matching terminal/editor UI.
 */
data class ToolStyleSpec(
    val actionTitle: String,
    val targetLabel: String,
    val icon: ImageVector,
    val iconColor: Color,
    val isExecuting: Boolean,
    val isThought: Boolean = false,
    val details: String? = null
)

object ToolExecutionItemMapper {

    fun mapLog(log: AiActionLog, isGlobalThinking: Boolean): ToolStyleSpec {
        val title = log.title
        val lowerTitle = title.lowercase(Locale.ROOT)
        val details = log.details?.trim() ?: ""
        val lineRange = formatLineRange(log.lineRange)
        val isExecuting = (log.status.equals("thinking", ignoreCase = true) ||
                log.status.equals("executing", ignoreCase = true)) && isGlobalThinking

        return when {
            lowerTitle.startsWith("moved file") || lowerTitle.contains("move_file") || lowerTitle.contains("extracted code") -> {
                val target = extractPath(title, details, "Moved file:").ifBlank { "notes.txt" }
                val targetText = if (lineRange.isNotBlank()) "$target ($lineRange)" else target
                ToolStyleSpec(
                    actionTitle = "Moved / Extracted code",
                    targetLabel = targetText,
                    icon = Icons.Default.OpenWith,
                    iconColor = Color(0xFF79C0FF),
                    isExecuting = isExecuting,
                    details = log.details
                )
            }
            lowerTitle.startsWith("read:") || lowerTitle.contains("read file") || lowerTitle.contains("read files") -> {
                val target = extractPath(title, details, "Read:").ifBlank { "file" }
                val targetText = if (lineRange.isNotBlank()) "$target ($lineRange)" else "$target (all)"
                ToolStyleSpec(
                    actionTitle = "Read file",
                    targetLabel = targetText,
                    icon = Icons.Default.Description,
                    iconColor = Color(0xFF58A6FF),
                    isExecuting = isExecuting,
                    details = log.details
                )
            }
            lowerTitle.startsWith("edit:") || lowerTitle.startsWith("writing file") || lowerTitle.contains("edited file") || lowerTitle.contains("modified file") -> {
                val target = extractPath(title, details, "Edit:").ifBlank {
                    extractPath(title, details, "Writing file:").ifBlank { "file" }
                }
                val targetText = if (lineRange.isNotBlank()) "$target ($lineRange)" else "$target (all)"
                ToolStyleSpec(
                    actionTitle = "Edited file",
                    targetLabel = targetText,
                    icon = Icons.Default.Edit,
                    iconColor = Color(0xFF00D8A5),
                    isExecuting = isExecuting,
                    details = log.details
                )
            }
            lowerTitle.startsWith("append to file") || lowerTitle.startsWith("append") || lowerTitle.contains("appended to file") -> {
                val target = extractPath(title, details, "Append to file:").ifBlank {
                    extractPath(title, details, "Append:").ifBlank { "file" }
                }
                val targetText = if (lineRange.isNotBlank()) "$target ($lineRange)" else target
                ToolStyleSpec(
                    actionTitle = "Appended to file",
                    targetLabel = targetText,
                    icon = Icons.Default.AddCircleOutline,
                    iconColor = Color(0xFF39D353),
                    isExecuting = isExecuting,
                    details = log.details
                )
            }
            lowerTitle.startsWith("patch:") || lowerTitle.contains("patch file") || lowerTitle.contains("patched file") -> {
                val target = extractPath(title, details, "Patch:").ifBlank { "file" }
                val targetText = if (lineRange.isNotBlank()) "$target ($lineRange)" else target
                ToolStyleSpec(
                    actionTitle = "Patched file",
                    targetLabel = targetText,
                    icon = Icons.Default.Edit,
                    iconColor = Color(0xFF00D8A5),
                    isExecuting = isExecuting,
                    details = log.details
                )
            }
            lowerTitle.startsWith("ran command:") || lowerTitle.startsWith("shell:") || lowerTitle.contains("command") || lowerTitle.contains("bash") -> {
                val cmd = extractShellCommand(title, details)
                ToolStyleSpec(
                    actionTitle = "Executed shell command",
                    targetLabel = cmd.ifBlank { "bash command" },
                    icon = Icons.Default.Terminal,
                    iconColor = Color(0xFFD2A8FF),
                    isExecuting = isExecuting,
                    details = log.details
                )
            }
            lowerTitle.startsWith("created new file") || lowerTitle.startsWith("create file") || lowerTitle.contains("created file") -> {
                val target = extractPath(title, details, "Created new file:").ifBlank { "file" }
                ToolStyleSpec(
                    actionTitle = "Created file",
                    targetLabel = target,
                    icon = Icons.Default.NoteAdd,
                    iconColor = Color(0xFF39D353),
                    isExecuting = isExecuting,
                    details = log.details
                )
            }
            lowerTitle.startsWith("deleted file") || lowerTitle.contains("delete_file") -> {
                val target = extractPath(title, details, "Deleted file:").ifBlank { "file" }
                ToolStyleSpec(
                    actionTitle = "Deleted file",
                    targetLabel = target,
                    icon = Icons.Default.Delete,
                    iconColor = Color(0xFFFFA198),
                    isExecuting = isExecuting,
                    details = log.details
                )
            }
            lowerTitle.startsWith("copied file") || lowerTitle.contains("copy_file") -> {
                val target = extractPath(title, details, "Copied file:").ifBlank { "file" }
                ToolStyleSpec(
                    actionTitle = "Copied file",
                    targetLabel = target,
                    icon = Icons.Default.ContentCopy,
                    iconColor = Color(0xFF79C0FF),
                    isExecuting = isExecuting,
                    details = log.details
                )
            }
            lowerTitle.startsWith("search:") || lowerTitle.contains("find_in_files") || lowerTitle.contains("grep") -> {
                val query = extractPath(title, details, "Search:").ifBlank { details }
                ToolStyleSpec(
                    actionTitle = "Searched files",
                    targetLabel = query.ifBlank { "codebase" },
                    icon = Icons.Default.Search,
                    iconColor = Color(0xFFFF7B72),
                    isExecuting = isExecuting,
                    details = log.details
                )
            }
            lowerTitle.startsWith("resize image") || lowerTitle.contains("resize_image") || lowerTitle.contains("scale_image") || lowerTitle.contains("resized image") -> {
                val target = details.lineSequence().firstOrNull()?.trim() ?: "image"
                ToolStyleSpec(
                    actionTitle = "Resized image",
                    targetLabel = target,
                    icon = Icons.Default.Crop,
                    iconColor = Color(0xFFFFB86C),
                    isExecuting = isExecuting,
                    details = log.details
                )
            }
            lowerTitle.startsWith("generate image") || lowerTitle.startsWith("generate logo") || lowerTitle.contains("generate_image") || lowerTitle.contains("generate_logo") -> {
                val isLogo = lowerTitle.contains("logo")
                val target = details.lineSequence().firstOrNull()?.trim() ?: title
                ToolStyleSpec(
                    actionTitle = if (isLogo) "Generated logo" else "Generated image",
                    targetLabel = target,
                    icon = Icons.Default.Image,
                    iconColor = Color(0xFFFF79C6),
                    isExecuting = isExecuting,
                    details = log.details
                )
            }
            lowerTitle.startsWith("mcp:") || lowerTitle.contains("mcp") -> {
                ToolStyleSpec(
                    actionTitle = "Executed MCP tool",
                    targetLabel = title.removePrefix("MCP:").trim().ifBlank { details },
                    icon = Icons.Default.Extension,
                    iconColor = Color(0xFFD2A8FF),
                    isExecuting = isExecuting,
                    details = log.details
                )
            }
            lowerTitle.contains("thinking") || lowerTitle.contains("formulating logic") || lowerTitle.contains("thought process") -> {
                val thought = details.ifBlank { title }
                ToolStyleSpec(
                    actionTitle = "Formulating logic",
                    targetLabel = thought,
                    icon = Icons.Default.Psychology,
                    iconColor = Color(0xFF58A6FF),
                    isExecuting = isExecuting,
                    isThought = true,
                    details = log.details
                )
            }
            else -> {
                ToolStyleSpec(
                    actionTitle = title,
                    targetLabel = details.lineSequence().firstOrNull()?.trim() ?: "",
                    icon = Icons.Default.PlayArrow,
                    iconColor = Color(0xFF8B949E),
                    isExecuting = isExecuting,
                    details = log.details
                )
            }
        }
    }

    private fun formatLineRange(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        val clean = raw.trim()
        val lower = clean.lowercase(Locale.ROOT)
        return when {
            lower.startsWith("line ") || lower.startsWith("lines ") -> lower
            clean.all { it.isDigit() || it == '-' || it == ',' } -> "line $clean"
            else -> clean
        }
    }

    private fun extractPath(title: String, details: String, prefix: String): String {
        val target = title.substringAfter(prefix, "").trim()
        if (target.isNotBlank()) return target
        val firstLine = details.lineSequence().firstOrNull()?.trim() ?: ""
        return if (firstLine.isNotBlank() && firstLine.length <= 80 && !firstLine.contains("{") && !firstLine.contains("}")) {
            firstLine
        } else {
            ""
        }
    }

    private fun extractShellCommand(title: String, details: String): String {
        val afterPrefix = title.substringAfter("Ran command:", "").trim()
        if (afterPrefix.isNotBlank()) return afterPrefix
        val afterShell = title.substringAfter("Shell:", "").trim()
        if (afterShell.isNotBlank()) return afterShell
        val firstLine = details.lineSequence().firstOrNull()?.trim() ?: ""
        return if (firstLine.isNotBlank() && firstLine.length <= 80) firstLine else title
    }
}

/**
 * Pure, clean tool execution list matching the exact UI in the user screenshot.
 * Does NOT contain chronological step indices, offsets, or connecting rails.
 */
@Composable
fun ToolExecutionListView(
    logs: List<AiActionLog>,
    isThinking: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        logs.forEach { log ->
            ToolExecutionIndicatorRow(log = log, isGlobalThinking = isThinking)
        }
    }
}

/**
 * Single tool execution indicator row matching the screenshot layout:
 * [ Circular Icon Badge ]   Action Title (e.g. "Read file")
 *                           Target Label (e.g. "index.html (all)")
 */
@Composable
fun ToolExecutionIndicatorRow(
    log: AiActionLog,
    isGlobalThinking: Boolean,
    modifier: Modifier = Modifier
) {
    val spec = remember(log, isGlobalThinking) {
        ToolExecutionItemMapper.mapLog(log, isGlobalThinking)
    }
    var isExpanded by remember { mutableStateOf(false) }
    val context = LocalContext.current

    if (spec.isThought) {
        // Subtle thought rendering
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = Color(0xFF0D1117),
            border = BorderStroke(1.dp, Color(0xFF21262D)),
            modifier = modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(bottom = 2.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Psychology,
                        contentDescription = null,
                        tint = Color(0xFF58A6FF),
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "Thought",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF8B949E)
                    )
                }
                Text(
                    text = spec.targetLabel,
                    fontSize = 12.5.sp,
                    lineHeight = 17.sp,
                    color = Color(0xFFC9D1D9)
                )
            }
        }
    } else {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .clickable {
                    if (!spec.details.isNullOrBlank()) {
                        isExpanded = !isExpanded
                    }
                }
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Circular icon indicator
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF0D1117))
                        .border(BorderStroke(1.dp, spec.iconColor.copy(alpha = 0.35f)), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (spec.isExecuting) {
                        PulsingRing(color = spec.iconColor)
                    }
                    Icon(
                        imageVector = spec.icon,
                        contentDescription = spec.actionTitle,
                        tint = spec.iconColor,
                        modifier = Modifier.size(15.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Action Title and Subtitle Target
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = spec.actionTitle,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFFF0F6FC),
                        letterSpacing = 0.1.sp
                    )

                    if (spec.targetLabel.isNotBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = spec.targetLabel,
                            fontSize = 11.5.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Normal,
                            color = Color(0xFF8B949E),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (spec.isExecuting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(12.dp),
                        strokeWidth = 1.5.dp,
                        color = spec.iconColor
                    )
                }
            }

            // Expandable details (code diff, output, etc.)
            AnimatedVisibility(visible = isExpanded && !spec.details.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFF161B22),
                    border = BorderStroke(0.5.dp, Color(0xFF30363D)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, start = 40.dp)
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Output / Payload",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF8B949E)
                            )
                            Text(
                                text = "Copy",
                                fontSize = 10.sp,
                                color = Color(0xFF58A6FF),
                                modifier = Modifier.clickable {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("Tool Output", spec.details ?: ""))
                                    Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = spec.details ?: "",
                            fontSize = 10.5.sp,
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFFC9D1D9),
                            lineHeight = 14.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PulsingRing(color: Color) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse_ring")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 0.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Box(
        modifier = Modifier
            .size(26.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(color.copy(alpha = alpha))
    )
}
