package com.example.ui.agent

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ChatMessageEntity
import com.example.ui.AiActionLog
import com.example.ui.MentionChipBadge
import com.example.ui.MentionChipItem
import com.example.ui.parseInlineMarkdown
import com.example.ui.parseUserMessageDisplay
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Professional Codex-style Chat Message Bubble.
 * Designed with modern AI coding agent aesthetics (OpenAI Codex / Cursor / Claude Code):
 * - Distinct author headers (Codex Assistant / You) with status & model pill
 * - Multi-line syntax-highlighted code blocks with dedicated header & one-click copy button
 * - Clean terminal/activity feed integration
 * - Elegant obsidian/dark-slate palette with crisp 1.dp hairline borders
 */
@Composable
fun CodexChatCard(
    message: ChatMessageEntity,
    onDeleteMessage: (ChatMessageEntity) -> Unit,
    onEditMessage: (ChatMessageEntity, String) -> Unit,
    onRegenerate: (ChatMessageEntity) -> Unit,
    onFileTagClick: (String) -> Unit,
    assistantModelName: String? = null,
    assistantDuration: String? = null,
    isCurrentlyActiveThinking: Boolean = false,
    currentRunningModelName: String = "",
    executionElapsedTimeSeconds: Long = 0L,
    liveSystemTokens: Int = 0,
    liveUserTokens: Int = 0,
    liveToolTokens: Int = 0,
    liveTotalInputTokens: Int = 0,
    liveTotalOutputTokens: Int = 0,
    modifier: Modifier = Modifier
) {
    val isUser = message.role == "user"
    val clipboardManager = LocalClipboardManager.current

    val (userDisplayText, mentionChips) = remember(message.content) {
        if (isUser) parseUserMessageDisplay(message.content) else Pair(message.content, emptyList())
    }

    var isEditing by remember { mutableStateOf(false) }
    var editedContent by remember { mutableStateOf(if (isUser) userDisplayText else message.content) }

    val logs = remember(message.aiActionLogsJson) {
        if (!message.aiActionLogsJson.isNullOrBlank()) {
            try {
                val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
                val listType = Types.newParameterizedType(List::class.java, AiActionLog::class.java)
                moshi.adapter<List<AiActionLog>>(listType).fromJson(message.aiActionLogsJson) ?: emptyList()
            } catch (e: Exception) {
                emptyList<AiActionLog>()
            }
        } else {
            emptyList<AiActionLog>()
        }
    }

    SelectionContainer {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
        ) {
            // Activity / Tool feed before assistant response
            if (!isUser && logs.isNotEmpty()) {
                val filteredLogs = logs.filter { log ->
                    !log.title.contains("finished task execution", ignoreCase = true)
                }
                if (filteredLogs.isNotEmpty()) {
                    AgentActivityFeed(
                        displayLogs = filteredLogs,
                        isThinking = false
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                }
            }

            // Outer Codex message container
            Surface(
                shape = RoundedCornerShape(
                    topStart = 14.dp,
                    topEnd = 14.dp,
                    bottomStart = if (isUser) 14.dp else 4.dp,
                    bottomEnd = if (isUser) 4.dp else 14.dp
                ),
                color = if (isUser) Color(0xFF1E232E) else Color(0xFF12151D),
                border = BorderStroke(
                    1.dp,
                    if (isUser) Color(0xFF2E3646) else Color(0xFF262C3A)
                ),
                modifier = Modifier
                    .widthIn(max = 680.dp)
                    .fillMaxWidth(if (isUser) 0.92f else 1f)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Header Row: Author Badge & Metadata Pill
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (isUser) {
                                Box(
                                    modifier = Modifier
                                        .size(22.dp)
                                        .background(Color(0xFF38BDF8).copy(alpha = 0.2f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = "User",
                                        tint = Color(0xFF38BDF8),
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                                Text(
                                    text = "You",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFE2E8F0)
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(22.dp)
                                        .background(Color(0xFF8B5CF6).copy(alpha = 0.25f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Bolt,
                                        contentDescription = "Codex",
                                        tint = Color(0xFFA78BFA),
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                                Text(
                                    text = "Codex Agent",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFF1F5F9)
                                )
                                
                                val modelLabel = message.modelName ?: assistantModelName ?: "gemini-2.0-flash"
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = Color(0xFF1E2433),
                                    border = BorderStroke(1.dp, Color(0xFF333B4F))
                                ) {
                                    Text(
                                        text = modelLabel.take(24),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Medium,
                                        fontFamily = FontFamily.Monospace,
                                        color = Color(0xFF94A3B8),
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }

                        // Right side info: duration or token count
                        val duration = if (!isUser) assistantDuration else null
                        if (duration != null) {
                            Text(
                                text = duration,
                                fontSize = 11.sp,
                                color = Color(0xFF64748B),
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    // Mention chips (if any)
                    if (isUser && mentionChips.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            mentionChips.forEach { chip ->
                                MentionChipBadge(chip = chip)
                            }
                        }
                    }

                    // Content Area: Editing Mode or Formatted Codex View
                    if (isEditing) {
                        OutlinedTextField(
                            value = editedContent,
                            onValueChange = { editedContent = it },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFF8B5CF6),
                                unfocusedBorderColor = Color(0xFF38BDF8).copy(alpha = 0.5f)
                            ),
                            textStyle = TextStyle(fontSize = 13.sp, color = Color.White, fontFamily = FontFamily.Default)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = { isEditing = false }) {
                                Text("Cancel", color = Color(0xFF94A3B8), fontSize = 11.sp)
                            }
                            Button(
                                onClick = {
                                    onEditMessage(message, editedContent)
                                    isEditing = false
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6)),
                                shape = RoundedCornerShape(6.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Text("Save", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    } else {
                        val textToRender = if (isUser) userDisplayText else message.content
                        CodexMarkdownContent(
                            markdown = textToRender,
                            onFileTagClick = onFileTagClick
                        )
                    }
                }
            }

            // Token metrics for user messages
            if (isUser) {
                if (isCurrentlyActiveThinking) {
                    val liveMetrics = com.example.data.TokenMetrics(
                        modelName = currentRunningModelName.ifBlank { "gemini-3.1-flash-lite" },
                        executionTimeSeconds = executionElapsedTimeSeconds,
                        isLive = true,
                        systemTokens = liveSystemTokens,
                        userTokens = liveUserTokens,
                        historyTokens = 420,
                        skillTokens = liveToolTokens,
                        totalInputTokens = liveTotalInputTokens,
                        totalOutputTokens = liveTotalOutputTokens
                    )
                    com.example.ui.CollapsibleTokenMonitor(
                        metrics = liveMetrics,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                } else {
                    val displayModel = message.modelName ?: assistantModelName ?: "gemini-3.1-flash-lite"
                    val displaySecs = if (message.executionTimeSeconds > 0) message.executionTimeSeconds else (assistantDuration?.removeSuffix("s")?.toLongOrNull() ?: 0L)
                    val sysTok = if (message.systemTokens > 0) message.systemTokens else 2440
                    val usrTok = if (message.userTokens > 0) message.userTokens else maxOf(1, message.content.length / 4)
                    val toolTok = message.toolTokens
                    val histTok = if (message.historyTokens > 0) message.historyTokens else 320
                    val skillTok = if (message.skillTokens > 0) message.skillTokens else toolTok
                    val inTok = if (message.totalInputTokens > 0) message.totalInputTokens else (sysTok + usrTok + histTok + skillTok)
                    val outTok = message.totalOutputTokens

                    val storedMetrics = com.example.data.TokenMetrics(
                        modelName = displayModel,
                        executionTimeSeconds = displaySecs,
                        isLive = false,
                        systemTokens = sysTok,
                        userTokens = usrTok,
                        historyTokens = histTok,
                        skillTokens = skillTok,
                        totalInputTokens = inTok,
                        totalOutputTokens = outTok
                    )
                    com.example.ui.CollapsibleTokenMonitor(
                        metrics = storedMetrics,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            // Bottom Actions row (Copy, Edit, Regenerate, Delete)
            Row(
                modifier = Modifier.padding(top = 4.dp, start = 4.dp, end = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isUser && !isEditing) {
                    CodexActionButton(Icons.Default.Edit, "Edit") { isEditing = true }
                    CodexActionButton(Icons.Default.Refresh, "Regenerate") { onRegenerate(message) }
                }
                CodexActionButton(Icons.Default.ContentCopy, "Copy Text") {
                    clipboardManager.setText(AnnotatedString(if (isUser) userDisplayText else message.content))
                }
                CodexActionButton(Icons.Default.DeleteOutline, "Delete") {
                    onDeleteMessage(message)
                }
            }
        }
    }
}

/**
 * Parses markdown into text paragraphs and multi-line code fences (```lang ... ```).
 * Renders code blocks inside professional Codex syntax cards.
 */
@Composable
fun CodexMarkdownContent(
    markdown: String,
    onFileTagClick: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val blocks = remember(markdown) { parseMarkdownBlocks(markdown) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        blocks.forEach { block ->
            when (block) {
                is MarkdownBlock.CodeBlock -> {
                    CodexCodeCard(
                        language = block.language,
                        code = block.code
                    )
                }
                is MarkdownBlock.TextBlock -> {
                    CodexTextParagraph(text = block.text)
                }
            }
        }
    }
}

sealed class MarkdownBlock {
    data class TextBlock(val text: String) : MarkdownBlock()
    data class CodeBlock(val language: String, val code: String) : MarkdownBlock()
}

/**
 * Splits text into MarkdownBlock.TextBlock and MarkdownBlock.CodeBlock.
 */
fun parseMarkdownBlocks(text: String): List<MarkdownBlock> {
    val result = mutableListOf<MarkdownBlock>()
    val lines = text.split("\n")
    val currentText = StringBuilder()
    var inCodeBlock = false
    var codeLanguage = ""
    val currentCode = StringBuilder()

    for (line in lines) {
        val trimmed = line.trim()
        if (trimmed.startsWith("```")) {
            if (inCodeBlock) {
                // Closing code block
                result.add(MarkdownBlock.CodeBlock(codeLanguage, currentCode.toString().trimEnd()))
                currentCode.clear()
                codeLanguage = ""
                inCodeBlock = false
            } else {
                // Opening code block
                if (currentText.isNotEmpty()) {
                    result.add(MarkdownBlock.TextBlock(currentText.toString().trimEnd()))
                    currentText.clear()
                }
                codeLanguage = trimmed.removePrefix("```").trim().ifBlank { "code" }
                inCodeBlock = true
            }
        } else {
            if (inCodeBlock) {
                if (currentCode.isNotEmpty()) currentCode.append("\n")
                currentCode.append(line)
            } else {
                if (currentText.isNotEmpty()) currentText.append("\n")
                currentText.append(line)
            }
        }
    }

    if (inCodeBlock && currentCode.isNotEmpty()) {
        result.add(MarkdownBlock.CodeBlock(codeLanguage, currentCode.toString().trimEnd()))
    } else if (currentText.isNotEmpty()) {
        result.add(MarkdownBlock.TextBlock(currentText.toString().trimEnd()))
    }

    return result
}

/**
 * Professional Codex Code Card with language header, one-click copy, and syntax styling.
 */
@Composable
fun CodexCodeCard(
    language: String,
    code: String,
    modifier: Modifier = Modifier
) {
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()
    var isCopied by remember { mutableStateOf(false) }

    val highlightedCode = remember(code, language) {
        highlightCodexSyntax(code)
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFF0A0D14),
        border = BorderStroke(1.dp, Color(0xFF262C3A)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF131722))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Code,
                        contentDescription = null,
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = language.uppercase(),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFF94A3B8)
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable {
                            clipboardManager.setText(AnnotatedString(code))
                            isCopied = true
                            coroutineScope.launch {
                                delay(2000L)
                                isCopied = false
                            }
                        }
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                ) {
                    Icon(
                        imageVector = if (isCopied) Icons.Default.Check else Icons.Default.ContentCopy,
                        contentDescription = "Copy code",
                        tint = if (isCopied) Color(0xFF4ADE80) else Color(0xFF94A3B8),
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isCopied) "Copied" else "Copy",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (isCopied) Color(0xFF4ADE80) else Color(0xFF94A3B8)
                    )
                }
            }

            // Code Content
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
                    .horizontalScroll(rememberScrollState())
            ) {
                Text(
                    text = highlightedCode,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 18.sp
                )
            }
        }
    }
}

/**
 * Highlights code text with a rich IDE color scheme (Keywords, Strings, Comments, Numbers).
 */
fun highlightCodexSyntax(code: String): AnnotatedString {
    return buildAnnotatedString {
        append(code)

        // Keywords (val, var, fun, return, import, class, if, else, etc.)
        val keywordRegex = "\\b(package|import|class|interface|object|fun|val|var|return|if|else|when|for|while|do|break|continue|try|catch|finally|throw|new|public|private|protected|internal|override|suspend|data|sealed|enum|const|companion|true|false|null|this|super|typealias)\\b".toRegex()
        for (match in keywordRegex.findAll(code)) {
            addStyle(
                SpanStyle(color = Color(0xFFFF7B72), fontWeight = FontWeight.SemiBold),
                match.range.first,
                match.range.last + 1
            )
        }

        // Strings ("...")
        val stringRegex = "\"[^\"]*\"".toRegex()
        for (match in stringRegex.findAll(code)) {
            addStyle(
                SpanStyle(color = Color(0xFFA5D6FF)),
                match.range.first,
                match.range.last + 1
            )
        }

        // Numbers (\b\d+\b)
        val numberRegex = "\\b(\\d+)(\\.\\d+)?([fFL])?\\b".toRegex()
        for (match in numberRegex.findAll(code)) {
            addStyle(
                SpanStyle(color = Color(0xFF79C0FF)),
                match.range.first,
                match.range.last + 1
            )
        }

        // Annotations (@Composable, @Inject, etc.)
        val annotationRegex = "@[a-zA-Z0-9_]+".toRegex()
        for (match in annotationRegex.findAll(code)) {
            addStyle(
                SpanStyle(color = Color(0xFFD2A8FF)),
                match.range.first,
                match.range.last + 1
            )
        }

        // Comments (// ... or /* ... */)
        val commentRegex = "(//[^\n]*)|(/\\*[\\s\\S]*?\\*/)".toRegex()
        for (match in commentRegex.findAll(code)) {
            addStyle(
                SpanStyle(color = Color(0xFF8B949E)),
                match.range.first,
                match.range.last + 1
            )
        }
    }
}

@Composable
fun CodexTextParagraph(text: String, modifier: Modifier = Modifier) {
    val lines = text.split("\n")
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        lines.forEach { rawLine ->
            val trimmed = rawLine.trim()
            if (trimmed.isEmpty()) {
                Spacer(modifier = Modifier.height(3.dp))
            } else if (trimmed.startsWith("###")) {
                Text(
                    text = parseInlineMarkdown(trimmed.removePrefix("###").trim()),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFF3F4F6),
                    modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
                )
            } else if (trimmed.startsWith("##")) {
                Text(
                    text = parseInlineMarkdown(trimmed.removePrefix("##").trim()),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFF3F4F6),
                    modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
                )
            } else if (trimmed.startsWith("#")) {
                Text(
                    text = parseInlineMarkdown(trimmed.removePrefix("#").trim()),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)
                )
            } else if (trimmed.startsWith("-") || trimmed.startsWith("*")) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("•", color = Color(0xFF8B5CF6), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text(
                        text = parseInlineMarkdown(trimmed.substring(1).trim()),
                        fontSize = 13.sp,
                        color = Color(0xFFE2E8F0),
                        lineHeight = 19.sp,
                        modifier = Modifier.weight(1f)
                    )
                }
            } else {
                Text(
                    text = parseInlineMarkdown(trimmed),
                    fontSize = 13.sp,
                    color = Color(0xFFE2E8F0),
                    lineHeight = 19.sp
                )
            }
        }
    }
}

@Composable
fun CodexActionButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = Color.Transparent,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = Color(0xFF64748B),
                modifier = Modifier.size(13.dp)
            )
            Text(
                text = label,
                fontSize = 10.sp,
                color = Color(0xFF64748B),
                fontWeight = FontWeight.Medium
            )
        }
    }
}
