package com.example.ui.browser

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.browser.BrowserHistoryItem
import com.example.browser.RealBrowserHistoryManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * ChromeBrowserHistoryDialog
 * Clean implementation without @Composable context violations.
 */
@Composable
fun ChromeBrowserHistoryDialog(
    isOpen: Boolean,
    onDismiss: () -> Unit,
    onUrlSelected: (String) -> Unit
) {
    if (!isOpen) return

    val historyItems by RealBrowserHistoryManager.history.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var showClearConfirmation by remember { mutableStateOf(false) }

    val filteredItems = remember(historyItems, searchQuery) {
        if (searchQuery.isBlank()) {
            historyItems
        } else {
            historyItems.filter {
                it.title.contains(searchQuery, ignoreCase = true) ||
                it.url.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    val timeFormatter = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val dateFormatter = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF101216))
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Back",
                                tint = Color(0xFFE8EAED),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Text(
                            text = "History",
                            color = Color(0xFFE8EAED),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    if (historyItems.isNotEmpty()) {
                        TextButton(
                            onClick = { showClearConfirmation = true },
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = Color(0xFF8AB4F8)
                            )
                        ) {
                            Text("Clear browsing data", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                // Search Bar
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xFF24272D),
                    border = BorderStroke(1.dp, Color(0xFF383C45)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                        .height(42.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search History",
                            tint = Color(0xFF9AA0A6),
                            modifier = Modifier.size(18.dp)
                        )

                        androidx.compose.foundation.text.BasicTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            textStyle = androidx.compose.ui.text.TextStyle(
                                color = Color(0xFFE8EAED),
                                fontSize = 13.sp
                            ),
                            cursorBrush = androidx.compose.ui.graphics.SolidColor(Color(0xFF8AB4F8)),
                            decorationBox = { innerTextField ->
                                if (searchQuery.isEmpty()) {
                                    Text(
                                        text = "Search history",
                                        color = Color(0xFF80868B),
                                        fontSize = 13.sp
                                    )
                                }
                                innerTextField()
                            }
                        )

                        if (searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = { searchQuery = "" },
                                modifier = Modifier.size(22.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear",
                                    tint = Color(0xFF9AA0A6),
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // History List
                if (filteredItems.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = null,
                                tint = Color(0xFF5F6368),
                                modifier = Modifier.size(46.dp)
                            )
                            Text(
                                text = if (searchQuery.isNotBlank()) "No matching history found" else "You have no browsing history yet",
                                color = Color(0xFF9AA0A6),
                                fontSize = 13.sp
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(filteredItems, key = { it.timestamp.toString() + it.url }) { item ->
                            HistoryRowCard(
                                item = item,
                                timeString = timeFormatter.format(Date(item.timestamp)),
                                dateString = dateFormatter.format(Date(item.timestamp)),
                                onSelect = {
                                    onUrlSelected(item.url)
                                    onDismiss()
                                },
                                onRemove = {
                                    val current = RealBrowserHistoryManager.history.value.filterNot { it.url == item.url && it.timestamp == item.timestamp }
                                    RealBrowserHistoryManager.clearHistory()
                                    current.reversed().forEach { RealBrowserHistoryManager.recordHistory(it.title, it.url) }
                                }
                            )
                        }
                    }
                }
            }

            // Clear Confirmation Alert
            if (showClearConfirmation) {
                AlertDialog(
                    onDismissRequest = { showClearConfirmation = false },
                    title = { Text("Clear browsing data?", color = Color(0xFFE8EAED), fontSize = 16.sp, fontWeight = FontWeight.Bold) },
                    text = { Text("This will remove all visited URLs, search queries, and timestamps from your browser history.", color = Color(0xFFB0B6C0), fontSize = 13.sp) },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                RealBrowserHistoryManager.clearHistory()
                                showClearConfirmation = false
                            }
                        ) {
                            Text("Clear data", color = Color(0xFFF28B82), fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showClearConfirmation = false }) {
                            Text("Cancel", color = Color(0xFF8AB4F8))
                        }
                    },
                    containerColor = Color(0xFF282C34)
                )
            }
        }
    }
}

@Composable
private fun HistoryRowCard(
    item: BrowserHistoryItem,
    timeString: String,
    dateString: String,
    onSelect: () -> Unit,
    onRemove: () -> Unit
) {
    Surface(
        onClick = onSelect,
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF1E2128),
        border = BorderStroke(1.dp, Color(0xFF2E333D)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = if (item.url.startsWith("https://")) Icons.Default.Lock else Icons.Default.Language,
                contentDescription = null,
                tint = Color(0xFF8AB4F8),
                modifier = Modifier.size(16.dp)
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title.ifBlank { item.url },
                    color = Color(0xFFE8EAED),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = item.url,
                    color = Color(0xFF9AA0A6),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "$dateString at $timeString",
                    color = Color(0xFF5F6368),
                    fontSize = 10.sp
                )
            }

            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Remove from history",
                    tint = Color(0xFF717886),
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}
