package com.example.ui.browser

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.browser.RealBrowserTab
import com.example.browser.RealBrowserTabManager

/**
 * Chrome-Style Tab Switcher Overview Screen
 * Matches modern Chrome Android grid layout as shown in user screenshot:
 * - Top header with '+' new tab, tab count badge '[N]', grid icon, overflow menu
 * - 'Search your tabs' search bar
 * - 2-Column Grid of Tab Cards with website favicon, title, 'X' close button, and preview
 * - Floating '+' and '-' action buttons
 */
@Composable
fun ChromeTabSwitcherDialog(
    isOpen: Boolean,
    onDismiss: () -> Unit,
    onTabSelected: (String) -> Unit,
    onNewTab: () -> Unit
) {
    if (!isOpen) return

    val tabs by RealBrowserTabManager.tabs.collectAsState()
    val activeTabId by RealBrowserTabManager.activeTabId.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var showMenu by remember { mutableStateOf(false) }

    val filteredTabs = remember(tabs, searchQuery) {
        if (searchQuery.isBlank()) {
            tabs
        } else {
            tabs.filter {
                it.title.contains(searchQuery, ignoreCase = true) ||
                it.url.contains(searchQuery, ignoreCase = true)
            }
        }
    }

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
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Top Action Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // New Tab (+) Button
                    FilledIconButton(
                        onClick = {
                            onNewTab()
                            onDismiss()
                        },
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = Color(0xFF8AB4F8),
                            contentColor = Color(0xFF041E49)
                        ),
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "New Tab",
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Tab count badge: e.g. [11]
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF202124),
                            border = BorderStroke(1.5.dp, Color(0xFF5F6368)),
                            modifier = Modifier
                                .height(32.dp)
                                .widthIn(min = 32.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(horizontal = 6.dp)
                            ) {
                                Text(
                                    text = "${tabs.size}",
                                    color = Color(0xFFE8EAED),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }

                        // Grid layout toggle icon
                        IconButton(
                            onClick = { /* Toggle layout */ },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.GridView,
                                contentDescription = "Grid View",
                                tint = Color(0xFFE8EAED),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Three-dots overflow menu
                        Box {
                            IconButton(
                                onClick = { showMenu = !showMenu },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "More Options",
                                    tint = Color(0xFFE8EAED),
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false },
                                modifier = Modifier
                                    .background(Color(0xFF282A2E))
                            ) {
                                DropdownMenuItem(
                                    text = { Text("New tab", color = Color(0xFFE8EAED), fontSize = 13.sp) },
                                    leadingIcon = { Icon(Icons.Default.Add, contentDescription = null, tint = Color(0xFF8AB4F8), modifier = Modifier.size(16.dp)) },
                                    onClick = {
                                        showMenu = false
                                        onNewTab()
                                        onDismiss()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Close all tabs", color = Color(0xFFF28B82), fontSize = 13.sp) },
                                    leadingIcon = { Icon(Icons.Default.Close, contentDescription = null, tint = Color(0xFFF28B82), modifier = Modifier.size(16.dp)) },
                                    onClick = {
                                        showMenu = false
                                        val activeId = RealBrowserTabManager.activeTabId.value
                                        tabs.forEach { tab ->
                                            if (tab.id != activeId) RealBrowserTabManager.closeTab(tab.id)
                                        }
                                        onDismiss()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Back to browser", color = Color(0xFFE8EAED), fontSize = 13.sp) },
                                    leadingIcon = { Icon(Icons.Default.ArrowBack, contentDescription = null, tint = Color(0xFF9AA0A6), modifier = Modifier.size(16.dp)) },
                                    onClick = {
                                        showMenu = false
                                        onDismiss()
                                    }
                                )
                            }
                        }

                        // Close switcher button
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close Tab Switcher",
                                tint = Color(0xFF9AA0A6),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                // "Search your tabs" search bar
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = Color(0xFF24272D),
                    border = BorderStroke(1.dp, Color(0xFF383C45)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                        .height(44.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search Tabs",
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
                                fontSize = 13.5.sp
                            ),
                            cursorBrush = androidx.compose.ui.graphics.SolidColor(Color(0xFF8AB4F8)),
                            decorationBox = { innerTextField ->
                                if (searchQuery.isEmpty()) {
                                    Text(
                                        text = "Search your tabs",
                                        color = Color(0xFF80868B),
                                        fontSize = 13.5.sp
                                    )
                                }
                                innerTextField()
                            }
                        )

                        if (searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = { searchQuery = "" },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear search",
                                    tint = Color(0xFF9AA0A6),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // 2-Column Tabs Grid
                if (filteredTabs.isEmpty()) {
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
                                imageVector = Icons.Default.Tab,
                                contentDescription = null,
                                tint = Color(0xFF5F6368),
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                text = if (searchQuery.isNotBlank()) "No tabs found matching '$searchQuery'" else "No tabs open",
                                color = Color(0xFF9AA0A6),
                                fontSize = 13.sp
                            )
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        items(filteredTabs, key = { it.id }) { tab ->
                            val isActive = tab.id == activeTabId
                            ChromeTabCard(
                                tab = tab,
                                isActive = isActive,
                                onSelect = {
                                    onTabSelected(tab.id)
                                    onDismiss()
                                },
                                onClose = {
                                    RealBrowserTabManager.closeTab(tab.id)
                                }
                            )
                        }
                    }
                }
            }

            // Floating '+' and '-' Action Buttons on the right edge (as shown in the screenshot!)
            Column(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color.White,
                    shadowElevation = 6.dp,
                    border = BorderStroke(1.dp, Color(0xFFDADCE0)),
                    modifier = Modifier
                        .size(44.dp)
                        .clickable {
                            onNewTab()
                            onDismiss()
                        }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "New Tab",
                            tint = Color(0xFF1967D2),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Surface(
                    shape = CircleShape,
                    color = Color.White,
                    shadowElevation = 6.dp,
                    border = BorderStroke(1.dp, Color(0xFFDADCE0)),
                    modifier = Modifier
                        .size(44.dp)
                        .clickable {
                            // Zoom out or close active tab
                            RealBrowserTabManager.getActiveTab()?.id?.let {
                                RealBrowserTabManager.closeTab(it)
                            }
                        }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Remove,
                            contentDescription = "Close current tab",
                            tint = Color(0xFF1967D2),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Individual Chrome Tab Grid Card Component
 */
@Composable
private fun ChromeTabCard(
    tab: RealBrowserTab,
    isActive: Boolean,
    onSelect: () -> Unit,
    onClose: () -> Unit
) {
    Surface(
        onClick = onSelect,
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF1E2126),
        border = BorderStroke(
            if (isActive) 2.dp else 1.dp,
            if (isActive) Color(0xFF8AB4F8) else Color(0xFF383C45)
        ),
        shadowElevation = if (isActive) 6.dp else 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Card Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF282C34))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    // Site Favicon or generic icon
                    val iconVector = when {
                        tab.url.contains("github.com", ignoreCase = true) -> Icons.Default.Code
                        tab.url.contains("google.com", ignoreCase = true) -> Icons.Default.Search
                        tab.url.startsWith("https://") -> Icons.Default.Lock
                        else -> Icons.Default.Language
                    }

                    Icon(
                        imageVector = iconVector,
                        contentDescription = null,
                        tint = if (isActive) Color(0xFF8AB4F8) else Color(0xFFB0B6C0),
                        modifier = Modifier.size(14.dp)
                    )

                    Text(
                        text = tab.title.ifBlank { "New Tab" },
                        color = if (isActive) Color(0xFFFFFFFF) else Color(0xFFE8EAED),
                        fontSize = 12.sp,
                        fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // 'X' Close Tab Button
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(20.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close tab",
                        tint = Color(0xFF9AA0A6),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            // Card Body (Tab Preview / Snapshot Simulation)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(
                        if (tab.url.isBlank()) Color(0xFF14171C)
                        else Color(0xFF16191F)
                    )
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                if (tab.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                        color = Color(0xFF8AB4F8)
                    )
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF232832),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(60.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(4.dp)
                            ) {
                                Text(
                                    text = if (tab.url.isNotBlank()) tab.url.removePrefix("https://").removePrefix("http://") else "Empty Tab",
                                    color = Color(0xFF717886),
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        // Simulated placeholder lines resembling Chrome's tab card preview
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.85f)
                                .height(6.dp)
                                .background(Color(0xFF2C323E), RoundedCornerShape(3.dp))
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.65f)
                                .height(6.dp)
                                .background(Color(0xFF252B36), RoundedCornerShape(3.dp))
                        )
                    }
                }

                // Active Tab indicator badge
                if (isActive) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color(0xFF1967D2),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(4.dp)
                    ) {
                        Text(
                            text = "CURRENT",
                            color = Color.White,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}
