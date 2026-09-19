package com.example.ui.agent

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.agent.AgentResponseStreamManager
import com.example.ui.FormattedMarkdownText

/**
 * Live Realtime Streaming Assistant Message Card.
 * Renders streamed tokens immediately as the model generates them so the user doesn't have to wait.
 */
@Composable
fun LiveStreamingResponseCard(
    modifier: Modifier = Modifier
) {
    val streamingMessage by AgentResponseStreamManager.streamingMessage.collectAsState()
    val isStreaming by AgentResponseStreamManager.isStreaming.collectAsState()

    if (isStreaming && streamingMessage.isNotBlank()) {
        Card(
            shape = RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 18.dp,
                bottomStart = 6.dp,
                bottomEnd = 18.dp
            ),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22)),
            border = BorderStroke(1.dp, Color(0xFF21262D)),
            modifier = modifier
                .fillMaxWidth()
                .widthIn(max = 600.dp)
                .padding(vertical = 4.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                androidx.compose.foundation.text.selection.SelectionContainer {
                    FormattedMarkdownText(text = streamingMessage)
                }
            }
        }
    }
}
