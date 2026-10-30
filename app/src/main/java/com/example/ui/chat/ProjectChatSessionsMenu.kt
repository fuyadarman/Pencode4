package com.example.ui.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.example.chat.ChatSessionHeader
import com.example.chat.ProjectChatSessionManager
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ProjectChatSessionsMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    onStartNewChat: () -> Unit,
    onSelectSession: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (!expanded) return

    val sessions by ProjectChatSessionManager.currentSessions.collectAsState()
    val activeId by ProjectChatSessionManager.activeSessionId.collectAsState()
    val dateFormatter = remember { SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()) }

    Popup(
        alignment = Alignment.TopStart,
        offset = androidx.compose.ui.unit.IntOffset(0, 48),
        onDismissRequest = onDismissRequest,
        properties = PopupProperties(focusable = true)
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFF1E222B),
            border = BorderStroke(1.dp, Color(0xFF333842)),
            tonalElevation = 8.dp,
            modifier = modifier
                .widthIn(min = 200.dp, max = 260.dp)
                .wrapContentHeight()
        ) {
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                // "Start new chat" row (matching Screenshot 1)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onDismissRequest()
                            onStartNewChat()
                        }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        tint = Color(0xFFE6EDF3),
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Start new chat",
                        color = Color(0xFFE6EDF3),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                HorizontalDivider(
                    color = Color(0xFF2D333F),
                    thickness = 1.dp,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )

                if (sessions.isEmpty()) {
                    Text(
                        text = "No previous chats",
                        color = Color(0xFF8B949E),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp)
                    ) {
                        items(sessions, key = { it.id }) { session ->
                            val isActive = session.id == activeId
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(if (isActive) Color(0xFF262B36) else Color.Transparent)
                                    .clickable {
                                        onDismissRequest()
                                        onSelectSession(session.id)
                                    }
                                    .padding(horizontal = 14.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = session.title,
                                    color = if (isActive) Color(0xFF58A6FF) else Color(0xFFE6EDF3),
                                    fontSize = 13.5.sp,
                                    fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = dateFormatter.format(Date(session.timestamp)),
                                    color = Color(0xFF8B949E),
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
