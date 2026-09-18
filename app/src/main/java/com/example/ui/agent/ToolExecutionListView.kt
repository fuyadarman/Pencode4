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
        val title = com.example.agent.AgentLogPrivacySanitizer.sanitizeTitle(log.title)
        val lowerTitle = title.lowercase(Locale.ROOT)
        val rawDetails = com.example.agent.AgentLogPrivacySanitizer.sanitizeDetails(log.details)
        val details = rawDetails.trim()
        val lineRange = formatLineRange(log.lineRange)
        val isExecuting = (log.status.equals("thinking", ignoreCase = true) ||
                log.status.equals("executing", ignoreCase = true)) && isGlobalThinking

        return when {
            lowerTitle.contains("moved code block") || lowerTitle.contains("move_code") || lowerTitle.contains("transfer_code") || lowerTitle.contains("transfer code") -> {
                val targetText = details.lineSequence().firstOrNull()?.trim()?.take(80) ?: title
                ToolStyleSpec(
                    actionTitle = "Moved code block",
                    targetLabel = targetText,
                    icon = Icons.Default.OpenWith,
                    iconColor = Color(0xFF79C0FF),
                    isExecuting = isExecuting,
                    details = log.details
                )
            }
            lowerTitle.contains("copied code block") || lowerTitle.contains("copy_code_chunk") || lowerTitle.contains("copy_code_block") || lowerTitle.contains("copy_code") -> {
                val targetText = details.lineSequence().firstOrNull()?.trim()?.take(80) ?: title
                ToolStyleSpec(
                    actionTitle = "Copied code block",
                    targetLabel = targetText,
                    icon = Icons.Default.ContentCopy,
                    iconColor = Color(0xFF79C0FF),
                    isExecuting = isExecuting,
                    details = log.details
                )
            }
            lowerTitle.contains("deleted code block") || lowerTitle.contains("delete_code") || lowerTitle.contains("remove_code") -> {
                val target = extractPath(title, details, "Deleted code block:").ifBlank { details.lineSequence().firstOrNull()?.trim() ?: "code block" }
                ToolStyleSpec(
                    actionTitle = "Deleted code block",
                    targetLabel = target.take(80),
                    icon = Icons.Default.DeleteSweep,
                    iconColor = Color(0xFFFFA198),
                    isExecuting = isExecuting,
                    details = log.details
                )
            }
            lowerTitle.startsWith("renamed file") || lowerTitle.contains("rename_file") || lowerTitle.startsWith("rename") -> {
                val target = details.lineSequence().firstOrNull()?.trim() ?: extractPath(title, details, "Renamed file:")
                ToolStyleSpec(
                    actionTitle = "Renamed file",
                    targetLabel = target.ifBlank { "file" },
                    icon = Icons.Default.DriveFileRenameOutline,
                    iconColor = Color(0xFF58A6FF),
                    isExecuting = isExecuting,
                    details = log.details
                )
            }
            lowerTitle.startsWith("moved file") || lowerTitle.contains("move_file") || lowerTitle.contains("extracted code") -> {
                val target = extractPath(title, details, "Moved file:").ifBlank { "notes.txt" }
                val targetText = if (lineRange.isNotBlank()) "$target ($lineRange)" else target
                ToolStyleSpec(
                    actionTitle = "Moved file",
                    targetLabel = targetText,
                    icon = Icons.Default.DriveFileMove,
                    iconColor = Color(0xFF79C0FF),
                    isExecuting = isExecuting,
                    details = log.details
                )
            }
            lowerTitle.contains("browser controller") || lowerTitle.contains("browser action") || lowerTitle.contains("browser_controller") || lowerTitle.contains("browser_action") -> {
                val target = details.lineSequence().firstOrNull()?.trim() ?: "Action executed"
                ToolStyleSpec(
                    actionTitle = "Browser action",
                    targetLabel = target.take(80),
                    icon = Icons.Default.TouchApp,
                    iconColor = Color(0xFF00D8A5),
                    isExecuting = isExecuting,
                    details = log.details
                )
            }
            lowerTitle.contains("browser interactive snapshot") || lowerTitle.contains("browser snapshot") || lowerTitle.contains("browser_snapshot") -> {
                ToolStyleSpec(
                    actionTitle = "Browser snapshot",
                    targetLabel = "Scanned clickable, typable & form elements",
                    icon = Icons.Default.FilterCenterFocus,
                    iconColor = Color(0xFF39D353),
                    isExecuting = isExecuting,
                    details = log.details
                )
            }
            lowerTitle.contains("deep clone web ui") || lowerTitle.contains("clone web ui") || lowerTitle.contains("clone_web_ui") || lowerTitle.contains("scrape_web_ui") -> {
                val target = details.lineSequence().firstOrNull()?.trim() ?: title
                ToolStyleSpec(
                    actionTitle = "Cloned Web UI",
                    targetLabel = target.take(80),
                    icon = Icons.Default.DashboardCustomize,
                    iconColor = Color(0xFFD2A8FF),
                    isExecuting = isExecuting,
                    details = log.details
                )
            }
            lowerTitle.contains("open web url") || lowerTitle.contains("open_url") || lowerTitle.contains("navigate") || lowerTitle.contains("browse_url") -> {
                val target = details.lineSequence().firstOrNull()?.trim() ?: title.removePrefix("Open Web URL:").trim()
                ToolStyleSpec(
                    actionTitle = "Navigated to URL",
                    targetLabel = target.ifBlank { "webpage" }.take(80),
                    icon = Icons.Default.Language,
                    iconColor = Color(0xFF58A6FF),
                    isExecuting = isExecuting,
                    details = log.details
                )
            }
            lowerTitle.contains("click element") || lowerTitle.contains("click web") || (lowerTitle.startsWith("click") && !lowerTitle.contains("code")) -> {
                val target = details.lineSequence().firstOrNull()?.trim() ?: "element"
                ToolStyleSpec(
                    actionTitle = "Clicked element",
                    targetLabel = target.take(80),
                    icon = Icons.Default.TouchApp,
                    iconColor = Color(0xFF79C0FF),
                    isExecuting = isExecuting,
                    details = log.details
                )
            }
            lowerTitle.contains("type text") || (lowerTitle.startsWith("type") && !lowerTitle.contains("code")) -> {
                val target = details.lineSequence().firstOrNull()?.trim() ?: "input field"
                ToolStyleSpec(
                    actionTitle = "Typed text",
                    targetLabel = target.take(80),
                    icon = Icons.Default.Keyboard,
                    iconColor = Color(0xFF7EE787),
                    isExecuting = isExecuting,
                    details = log.details
                )
            }
            lowerTitle.contains("scroll web") || lowerTitle.startsWith("scroll") -> {
                val target = details.lineSequence().firstOrNull()?.trim() ?: "page"
                ToolStyleSpec(
                    actionTitle = "Scrolled page",
                    targetLabel = target.take(80),
                    icon = Icons.Default.SwapVert,
                    iconColor = Color(0xFF8B949E),
                    isExecuting = isExecuting,
                    details = log.details
                )
            }
            lowerTitle.contains("get links") || lowerTitle.contains("get_links") -> {
                ToolStyleSpec(
                    actionTitle = "Extracted links",
                    targetLabel = "Navigation links & hrefs",
                    icon = Icons.Default.Link,
                    iconColor = Color(0xFF58A6FF),
                    isExecuting = isExecuting,
                    details = log.details
                )
            }
            lowerTitle.contains("get images") || lowerTitle.contains("get_images") -> {
                ToolStyleSpec(
                    actionTitle = "Extracted images",
                    targetLabel = "Page images, icons & SVGs",
                    icon = Icons.Default.Image,
                    iconColor = Color(0xFFFF79C6),
                    isExecuting = isExecuting,
                    details = log.details
                )
            }
            lowerTitle.contains("get fonts") || lowerTitle.contains("get_fonts") -> {
                ToolStyleSpec(
                    actionTitle = "Extracted typography",
                    targetLabel = "Font families & CSS tokens",
                    icon = Icons.Default.TextFields,
                    iconColor = Color(0xFFFFB86C),
                    isExecuting = isExecuting,
                    details = log.details
                )
            }
            lowerTitle.contains("run javascript") || lowerTitle.contains("execute_javascript") || lowerTitle.contains("eval_js") -> {
                ToolStyleSpec(
                    actionTitle = "Executed JavaScript",
                    targetLabel = details.lineSequence().firstOrNull()?.trim()?.take(80) ?: "script",
                    icon = Icons.Default.Code,
                    iconColor = Color(0xFFE3B341),
                    isExecuting = isExecuting,
                    details = log.details
                )
            }
            lowerTitle.contains("screenshot") -> {
                ToolStyleSpec(
                    actionTitle = "Captured screenshot",
                    targetLabel = details.lineSequence().firstOrNull()?.trim()?.take(80) ?: "viewport",
                    icon = Icons.Default.CameraAlt,
                    iconColor = Color(0xFF79C0FF),
                    isExecuting = isExecuting,
                    details = log.details
                )
            }
            lowerTitle.contains("browser search") || lowerTitle.contains("browser_search") -> {
                val query = details.lineSequence().firstOrNull()?.trim() ?: title
                ToolStyleSpec(
                    actionTitle = "Searched web",
                    targetLabel = query.take(80),
                    icon = Icons.Default.TravelExplore,
                    iconColor = Color(0xFF58A6FF),
                    isExecuting = isExecuting,
                    details = log.details
                )
            }
            lowerTitle.contains("browser read") || lowerTitle.contains("fetch_url") || lowerTitle.contains("read_url") -> {
                val target = details.lineSequence().firstOrNull()?.trim() ?: "web content"
                ToolStyleSpec(
                    actionTitle = "Read webpage",
                    targetLabel = target.take(80),
                    icon = Icons.Default.Article,
                    iconColor = Color(0xFF58A6FF),
                    isExecuting = isExecuting,
                    details = log.details
                )
            }
            lowerTitle.contains("pdf") || lowerTitle.contains("generate_document") || lowerTitle.contains("generated document") -> {
                val doc = details.lineSequence().firstOrNull()?.trim() ?: "document"
                ToolStyleSpec(
                    actionTitle = "Generated document",
                    targetLabel = doc.take(80),
                    icon = Icons.Default.PictureAsPdf,
                    iconColor = Color(0xFFFF7B72),
                    isExecuting = isExecuting,
                    details = log.details
                )
            }
            lowerTitle.contains("todo") -> {
                val isComplete = lowerTitle.contains("complete")
                ToolStyleSpec(
                    actionTitle = if (isComplete) "Completed todo task" else "Created todo list",
                    targetLabel = details.lineSequence().firstOrNull()?.trim()?.take(80) ?: "todo item",
                    icon = Icons.Default.Checklist,
                    iconColor = Color(0xFF39D353),
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
            lowerTitle.startsWith("deleted file") || lowerTitle.startsWith("delete file") || lowerTitle.contains("delete_file") -> {
                val target = extractPath(title, details, "Deleted file:").ifBlank {
                    extractPath(title, details, "Delete file:").ifBlank { "file" }
                }
                ToolStyleSpec(
                    actionTitle = "Deleted file",
                    targetLabel = target,
                    icon = Icons.Default.Delete,
                    iconColor = Color(0xFFFFA198),
                    isExecuting = isExecuting,
                    details = log.details
                )
            }
            lowerTitle.startsWith("copied file") || lowerTitle.startsWith("copy file") || lowerTitle.contains("copy_file") || lowerTitle.contains("duplicate") -> {
                val target = extractPath(title, details, "Copied file:").ifBlank {
                    extractPath(title, details, "Copy file:").ifBlank { "file" }
                }
                ToolStyleSpec(
                    actionTitle = if (lowerTitle.contains("duplicate")) "Duplicated file" else "Copied file",
                    targetLabel = target,
                    icon = Icons.Default.ContentCopy,
                    iconColor = Color(0xFF79C0FF),
                    isExecuting = isExecuting,
                    details = log.details
                )
            }
            lowerTitle.startsWith("search:") || lowerTitle.contains("search files") || lowerTitle.contains("searched files") || lowerTitle.contains("find_in_files") || lowerTitle.contains("grep") -> {
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
            lowerTitle.contains("ask user") || lowerTitle.contains("ask_user") -> {
                ToolStyleSpec(
                    actionTitle = "Asked user",
                    targetLabel = details.lineSequence().firstOrNull()?.trim()?.take(80) ?: "question",
                    icon = Icons.Default.QuestionAnswer,
                    iconColor = Color(0xFFFFB86C),
                    isExecuting = isExecuting,
                    details = log.details
                )
            }
            lowerTitle.contains("preview error") -> {
                val target = details.lineSequence().firstOrNull()?.trim() ?: "Preview errors"
                ToolStyleSpec(
                    actionTitle = "Read preview errors",
                    targetLabel = target.take(80),
                    icon = Icons.Default.BugReport,
                    iconColor = Color(0xFFFF5555),
                    isExecuting = isExecuting,
                    details = log.details
                )
            }
            lowerTitle.contains("build error") -> {
                val target = details.lineSequence().firstOrNull()?.trim() ?: "Build errors"
                ToolStyleSpec(
                    actionTitle = "Read build errors",
                    targetLabel = target.take(80),
                    icon = Icons.Default.ErrorOutline,
                    iconColor = Color(0xFFFF5555),
                    isExecuting = isExecuting,
                    details = log.details
                )
            }
            lowerTitle.contains("all tools") || lowerTitle.contains("tool registry") -> {
                ToolStyleSpec(
                    actionTitle = "Listed all tools",
                    targetLabel = "PenCode tools catalog",
                    icon = Icons.Default.MenuBook,
                    iconColor = Color(0xFFBD93F9),
                    isExecuting = isExecuting,
                    details = log.details
                )
            }
            lowerTitle.contains("console") -> {
                val target = details.lineSequence().firstOrNull()?.trim() ?: "Preview console"
                ToolStyleSpec(
                    actionTitle = "Read console logs",
                    targetLabel = target.take(80),
                    icon = Icons.Default.Terminal,
                    iconColor = Color(0xFF58A6FF),
                    isExecuting = isExecuting,
                    details = log.details
                )
            }
            lowerTitle.contains("build log") || lowerTitle.contains("action log") || lowerTitle.contains("workflow log") || lowerTitle.contains("github log") -> {
                val target = details.lineSequence().firstOrNull()?.trim() ?: "GitHub Actions"
                ToolStyleSpec(
                    actionTitle = "Read build logs",
                    targetLabel = target.take(80),
                    icon = Icons.Default.Build,
                    iconColor = Color(0xFFF1E05A),
                    isExecuting = isExecuting,
                    details = log.details
                )
            }
            lowerTitle.contains("directory") || lowerTitle.contains("scan_dir") || lowerTitle.contains("list_dir") -> {
                val target = details.lineSequence().firstOrNull()?.trim() ?: "workspace folder"
                ToolStyleSpec(
                    actionTitle = "Scanned directory",
                    targetLabel = target.take(80),
                    icon = Icons.Default.FolderOpen,
                    iconColor = Color(0xFF79C0FF),
                    isExecuting = isExecuting,
                    details = log.details
                )
            }
            lowerTitle.contains("task completed") || lowerTitle.contains("complete") -> {
                ToolStyleSpec(
                    actionTitle = "Task completed",
                    targetLabel = details.lineSequence().firstOrNull()?.trim()?.take(80) ?: "Finished",
                    icon = Icons.Default.CheckCircle,
                    iconColor = Color(0xFF39D353),
                    isExecuting = isExecuting,
                    details = log.details
                )
            }
            lowerTitle.contains("skill") -> {
                ToolStyleSpec(
                    actionTitle = "Agent Skill",
                    targetLabel = details.lineSequence().firstOrNull()?.trim()?.take(80) ?: title,
                    icon = Icons.Default.AutoAwesome,
                    iconColor = Color(0xFFD2A8FF),
                    isExecuting = isExecuting,
                    details = log.details
                )
            }
            lowerTitle.contains("thinking") || lowerTitle.contains("formulating logic") || lowerTitle.contains("thought process") || lowerTitle.contains("thought") || lowerTitle.contains("reasoning") || lowerTitle.contains("planning") || lowerTitle.contains("ai_think") || com.example.agent.AgentReasoningDetector.isReasoningText(details) -> {
                val thought = details.ifBlank { title }
                val isReasoningTitle = lowerTitle.contains("reasoning") || com.example.agent.AgentReasoningDetector.isReasoningText(details)
                ToolStyleSpec(
                    actionTitle = if (isReasoningTitle) "Reasoning process" else "Formulating logic",
                    targetLabel = thought,
                    icon = Icons.Default.Psychology,
                    iconColor = Color(0xFF58A6FF),
                    isExecuting = isExecuting,
                    isThought = true,
                    details = log.details
                )
            }
            else -> {
                val fallbackIcon = when {
                    lowerTitle.contains("read") || lowerTitle.contains("get") || lowerTitle.contains("fetch") || lowerTitle.contains("inspect") -> Icons.Default.Visibility
                    lowerTitle.contains("delete") || lowerTitle.contains("remove") -> Icons.Default.Delete
                    lowerTitle.contains("copy") -> Icons.Default.ContentCopy
                    lowerTitle.contains("move") -> Icons.Default.DriveFileMove
                    lowerTitle.contains("write") || lowerTitle.contains("create") || lowerTitle.contains("edit") -> Icons.Default.Edit
                    lowerTitle.contains("search") || lowerTitle.contains("find") -> Icons.Default.Search
                    else -> Icons.Default.Code
                }
                val fallbackColor = when {
                    lowerTitle.contains("delete") || lowerTitle.contains("remove") -> Color(0xFFFFA198)
                    lowerTitle.contains("write") || lowerTitle.contains("create") || lowerTitle.contains("edit") -> Color(0xFF00D8A5)
                    lowerTitle.contains("search") || lowerTitle.contains("find") -> Color(0xFFFF7B72)
                    else -> Color(0xFF58A6FF)
                }
                ToolStyleSpec(
                    actionTitle = title,
                    targetLabel = details.lineSequence().firstOrNull()?.trim() ?: "",
                    icon = fallbackIcon,
                    iconColor = fallbackColor,
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
        AgentThoughtCollapsibleView(
            log = log,
            spec = spec,
            isGlobalThinking = isGlobalThinking,
            modifier = modifier
        )
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
                } else if (log.status.equals("failed", ignoreCase = true) || log.status.equals("error", ignoreCase = true)) {
                    Icon(
                        imageVector = Icons.Default.Cancel,
                        contentDescription = "Failed",
                        tint = Color(0xFFF85149),
                        modifier = Modifier.size(14.dp)
                    )
                } else if (log.status.equals("success", ignoreCase = true)) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Success",
                        tint = Color(0xFF3FB950),
                        modifier = Modifier.size(14.dp)
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
