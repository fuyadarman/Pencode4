package com.example.ui.chat

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.MentionChipItem
import com.example.ui.theme.AppTheme

/**
 * UserPromptBubbleView
 * Distinct right-aligned gray card for user prompts.
 * If user prompt exceeds 10 lines, offers "Show more" / "Show less" toggle.
 */
@Composable
fun UserPromptBubbleView(
    text: String,
    mentionChips: List<MentionChipItem> = emptyList(),
    modifier: Modifier = Modifier,
    contentRenderer: @Composable (String) -> Unit
) {
    val isDark = AppTheme.isDark
    val bgGray = if (isDark) Color(0xFF262C36) else Color(0xFFE2E8F0)
    val borderGray = if (isDark) Color(0xFF3B4350) else Color(0xFFCBD5E1)

    val lines = remember(text) { text.lines() }
    val isLongPrompt = lines.size > 10
    var isExpanded by remember { mutableStateOf(false) }

    val displayText = remember(text, isExpanded, isLongPrompt) {
        if (isLongPrompt && !isExpanded) {
            lines.take(10).joinToString("\n")
        } else {
            text
        }
    }

    Card(
        shape = RoundedCornerShape(
            topStart = 16.dp,
            topEnd = 16.dp,
            bottomStart = 16.dp,
            bottomEnd = 4.dp
        ),
        colors = CardDefaults.cardColors(containerColor = bgGray),
        border = BorderStroke(1.dp, borderGray),
        modifier = modifier
            .fillMaxWidth(0.55f)
            .animateContentSize()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Mention chips if present
            if (mentionChips.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(bottom = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    mentionChips.forEach { chip ->
                        com.example.ui.MentionChipBadge(chip = chip)
                    }
                }
            }

            // Render Markdown text
            if (displayText.isNotBlank()) {
                contentRenderer(displayText)
            }

            // "Show more" / "Show less" toggle if > 10 lines
            if (isLongPrompt) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .clickable { isExpanded = !isExpanded }
                        .padding(top = 4.dp)
                ) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (isExpanded) "Show less" else "Show more",
                        tint = Color(0xFF58A6FF),
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = if (isExpanded) "Show less" else "Show more (${lines.size - 10} more lines)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF58A6FF)
                    )
                }
            }
        }
    }
}
