package com.example.ui.browser

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.browser.RealBrowserTab
import com.example.browser.RealBrowserTabManager

@Composable
fun ChromeTabsGridView(
    isOpen: Boolean,
    tabs: List<RealBrowserTab>,
    activeTabId: String,
    onTabSelected: (String) -> Unit,
    onTabClosed: (String) -> Unit,
    onNewTab: () -> Unit,
    onOpenHistory: () -> Unit,
    onDismiss: () -> Unit
) {
    if (!isOpen) return

    var searchQuery by remember { mutableStateOf("") }
    var showMenu by remember { mutableStateOf(false) }

    val filteredTabs = remember(tabs, searchQuery) {
        if (searchQuery.isBlank()) tabs
        else tabs.filter {
            it.title.contains(searchQuery, ignoreCase = true) ||
            it.url.contains(searchQuery, ignoreCase = true)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF121214))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                // Top Action Bar matching Screenshot 2
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // New tab plus button
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF20232B),
                        border = BorderStroke(1.dp, Color(0xFF333842)),
                        modifier = Modifier
                            .size(38.dp)
                            .clickable {
                                onNewTab()
                                onDismiss()
                            }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Add, contentDescription = "New Tab", tint = Color(0xFFE6EDF3), modifier = Modifier.size(20.dp))
                        }
                    }

                    // Center: Tab counter badge & Grid icon
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFF20232B),
                            border = BorderStroke(1.5.dp, Color(0xFF8B949E)),
                            modifier = Modifier.size(28.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "${tabs.size}",
                                    color = Color(0xFFE6EDF3),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Icon(
                            imageVector = Icons.Default.GridView,
                            contentDescription = "Grid layout",
                            tint = Color(0xFF8B949E),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Three-dot menu button
                    Box {
                        IconButton(
                            onClick = { showMenu = true },
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Menu", tint = Color(0xFFE6EDF3), modifier = Modifier.size(22.dp))
                        }

                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                            modifier = Modifier.background(Color(0xFF1E222B))
                        ) {
                            DropdownMenuItem(
                                text = { Text("New tab", color = Color(0xFFE6EDF3), fontSize = 13.sp) },
                                leadingIcon = { Icon(Icons.Default.Add, contentDescription = null, tint = Color(0xFF8B949E), modifier = Modifier.size(18.dp)) },
                                onClick = {
                                    showMenu = false
                                    onNewTab()
                                    onDismiss()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("History", color = Color(0xFFE6EDF3), fontSize = 13.sp) },
                                leadingIcon = { Icon(Icons.Default.History, contentDescription = null, tint = Color(0xFF8B949E), modifier = Modifier.size(18.dp)) },
                                onClick = {
                                    showMenu = false
                                    onOpenHistory()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Close all tabs", color = Color(0xFFF85149), fontSize = 13.sp) },
                                leadingIcon = { Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = Color(0xFFF85149), modifier = Modifier.size(18.dp)) },
                                onClick = {
                                    showMenu = false
                                    tabs.forEach { RealBrowserTabManager.closeTab(it.id) }
                                }
                            )
                            HorizontalDivider(color = Color(0xFF30363D))
                            DropdownMenuItem(
                                text = { Text("Done", color = Color(0xFF58A6FF), fontSize = 13.sp, fontWeight = FontWeight.SemiBold) },
                                onClick = {
                                    showMenu = false
                                    onDismiss()
                                }
                            )
                        }
                    }
                }

                // Chrome-style "Search your tabs" search bar
                Surface(
                    color = Color(0xFF20232B),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, Color(0xFF30363D)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 14.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            textStyle = TextStyle(color = Color(0xFFE6EDF3), fontSize = 14.sp),
                            cursorBrush = SolidColor(Color(0xFF58A6FF)),
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            decorationBox = { innerTextField ->
                                if (searchQuery.isEmpty()) {
                                    Text("Search your tabs", color = Color(0xFF8B949E), fontSize = 14.sp)
                                }
                                innerTextField()
                            }
                        )
                        if (searchQuery.isNotEmpty()) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear",
                                tint = Color(0xFF8B949E),
                                modifier = Modifier
                                    .size(16.dp)
                                    .clickable { searchQuery = "" }
                            )
                        }
                    }
                }

                // 2-Column Tabs Grid
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredTabs, key = { it.id }) { tab ->
                        val isActive = tab.id == activeTabId
                        ChromeTabCard(
                            tab = tab,
                            isActive = isActive,
                            onClick = {
                                onTabSelected(tab.id)
                                onDismiss()
                            },
                            onClose = {
                                onTabClosed(tab.id)
                            }
                        )
                    }
                }
            }

            // Floating plus & minus zoom/tab buttons on right edge (matching Screenshot 2)
            Column(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color(0xFF20232B),
                    border = BorderStroke(1.dp, Color(0xFF38BDF8)),
                    modifier = Modifier
                        .size(44.dp)
                        .clickable {
                            onNewTab()
                            onDismiss()
                        }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Add, contentDescription = "Add tab", tint = Color(0xFF38BDF8), modifier = Modifier.size(22.dp))
                    }
                }

                Surface(
                    shape = CircleShape,
                    color = Color(0xFF20232B),
                    border = BorderStroke(1.dp, Color(0xFF30363D)),
                    modifier = Modifier
                        .size(44.dp)
                        .clickable {
                            if (tabs.isNotEmpty()) {
                                onTabClosed(activeTabId)
                            }
                        }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Remove, contentDescription = "Close tab", tint = Color(0xFF8B949E), modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ChromeTabCard(
    tab: RealBrowserTab,
    isActive: Boolean,
    onClick: () -> Unit,
    onClose: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF1E222B),
        border = BorderStroke(
            if (isActive) 2.dp else 1.dp,
            if (isActive) Color(0xFF38BDF8) else Color(0xFF30363D)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header: favicon, title, close X
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF262B36))
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = if (tab.url.contains("github.com")) Icons.Default.Code
                        else if (tab.url.startsWith("https://")) Icons.Default.Language
                        else Icons.Default.Web,
                        contentDescription = null,
                        tint = if (isActive) Color(0xFF38BDF8) else Color(0xFF8B949E),
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = tab.title.ifBlank { "New Tab" },
                        color = Color(0xFFE6EDF3),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = Color(0xFF8B949E),
                    modifier = Modifier
                        .size(14.dp)
                        .clickable(onClick = onClose)
                )
            }

            // Thumbnail / Body Preview area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color(0xFF121418))
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                if (tab.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = Color(0xFF38BDF8)
                    )
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Public,
                            contentDescription = null,
                            tint = Color(0xFF30363D),
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (tab.url.isNotBlank()) tab.url.removePrefix("https://").removePrefix("http://").take(22) else "Blank",
                            color = Color(0xFF6E7681),
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}
