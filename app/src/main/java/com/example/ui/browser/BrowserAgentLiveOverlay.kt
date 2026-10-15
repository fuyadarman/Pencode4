package com.example.ui.browser

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.browser.LivePreviewBrowserManager
import com.example.browser.RealBrowserHistoryManager

/**
 * Live HUD overlay showing AI Agent's real-time actions on the Preview Browser.
 */
@Composable
fun BrowserAgentLiveOverlay(
    currentUrl: String,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val agentAction by LivePreviewBrowserManager.agentActionStatus.collectAsState()
    val bookmarks by RealBrowserHistoryManager.bookmarks.collectAsState()
    var showBookmarksBar by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        // AI Autonomous Agent Activity Banner
        AnimatedVisibility(
            visible = agentAction != null,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Surface(
                color = Color(0xFF0F172A).copy(alpha = 0.95f),
                border = BorderStroke(1.dp, Color(0xFF38BDF8)),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF38BDF8))
                    )

                    Text(
                        text = agentAction ?: "AI Agent is operating browser...",
                        color = Color(0xFFE2E8F0),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )

                    TextButton(
                        onClick = { LivePreviewBrowserManager.clearActionStatus() },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(26.dp)
                    ) {
                        Text("Pause", fontSize = 11.sp, color = Color(0xFFF43F5E))
                    }
                }
            }
        }

        // Quick Bookmarks Bar
        if (showBookmarksBar || currentUrl.isBlank() || currentUrl.startsWith("http://localhost")) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0B0F17))
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.BookmarkBorder,
                    contentDescription = null,
                    tint = Color(0xFF64748B),
                    modifier = Modifier.size(14.dp)
                )

                bookmarks.take(8).forEach { bm ->
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFF1E293B),
                        border = BorderStroke(0.5.dp, Color(0xFF334155)),
                        modifier = Modifier.clickable { onNavigate(bm.url) }
                    ) {
                        Text(
                            text = bm.title,
                            color = Color(0xFF94A3B8),
                            fontSize = 10.5.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }
        }
    }
}
