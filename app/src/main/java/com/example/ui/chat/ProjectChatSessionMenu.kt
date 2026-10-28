package com.example.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chat.ProjectChatSession
import com.example.chat.ProjectChatSessionManager

/**
 * Pixel-perfect popup dropdown matching the user's screenshot:
 * Header action: "Start new chat"
 * Divider
 * List of previous chat sessions with title and formatted date/time ("28 Oct, 14:52")
 */
@Composable
fun ProjectChatSessionMenu(
    chatSessions: List<ProjectChatSession>,
    activeSessionId: String?,
    onStartNewChat: () -> Unit,
    onSelectChatSession: (String) -> Unit,
    onDeleteChatSession: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF1E222D))
            .padding(vertical = 4.dp)
    ) {
        // "Start new chat" Action Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onStartNewChat() }
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Start new chat",
                tint = Color(0xFF60A5FA),
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = "Start new chat",
                color = Color(0xFFF0F6FC),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        HorizontalDivider(
            color = Color(0xFF2E3342),
            thickness = 1.dp,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        )

        // List of previous chat sessions
        if (chatSessions.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "No previous chats",
                    color = Color(0xFF8B949E),
                    fontSize = 12.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 240.dp)
            ) {
                items(chatSessions, key = { it.id }) { session ->
                    val isActive = session.id == activeSessionId
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (isActive) Color(0xFF282F3E) else Color.Transparent)
                            .clickable { onSelectChatSession(session.id) }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(
                            modifier = Modifier.weight(1f)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                if (isActive) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(RoundedCornerShape(3.dp))
                                            .background(Color(0xFF38BDF8))
                                    )
                                }
                                Text(
                                    text = session.title,
                                    color = if (isActive) Color(0xFF60A5FA) else Color(0xFFE2E8F0),
                                    fontSize = 13.5.sp,
                                    fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = ProjectChatSessionManager.formatSessionDate(session.timestamp),
                                color = Color(0xFF8E95A5),
                                fontSize = 11.sp
                            )
                        }

                        // Subtle delete button for previous sessions if more than 1
                        if (chatSessions.size > 1) {
                            IconButton(
                                onClick = { onDeleteChatSession(session.id) },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteOutline,
                                    contentDescription = "Delete chat",
                                    tint = Color(0xFF6B7280),
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
