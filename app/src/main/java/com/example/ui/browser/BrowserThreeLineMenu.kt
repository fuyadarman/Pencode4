package com.example.ui.browser

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun BrowserThreeLineMenu(
    tabCount: Int,
    onOpenTabsGrid: () -> Unit,
    onOpenHistory: () -> Unit,
    onNewTab: () -> Unit,
    onReload: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        IconButton(
            onClick = { expanded = true },
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Menu,
                contentDescription = "Browser Menu",
                tint = Color(0xFFC9D1D9),
                modifier = Modifier.size(18.dp)
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(Color(0xFF1E222B))
        ) {
            DropdownMenuItem(
                text = { Text("Tabs ($tabCount)", color = Color(0xFFE6EDF3), fontSize = 13.sp) },
                leadingIcon = {
                    Icon(Icons.Default.GridView, contentDescription = null, tint = Color(0xFF58A6FF), modifier = Modifier.size(18.dp))
                },
                onClick = {
                    expanded = false
                    onOpenTabsGrid()
                }
            )
            DropdownMenuItem(
                text = { Text("History", color = Color(0xFFE6EDF3), fontSize = 13.sp) },
                leadingIcon = {
                    Icon(Icons.Default.History, contentDescription = null, tint = Color(0xFF58A6FF), modifier = Modifier.size(18.dp))
                },
                onClick = {
                    expanded = false
                    onOpenHistory()
                }
            )
            DropdownMenuItem(
                text = { Text("New tab", color = Color(0xFFE6EDF3), fontSize = 13.sp) },
                leadingIcon = {
                    Icon(Icons.Default.Add, contentDescription = null, tint = Color(0xFF8B949E), modifier = Modifier.size(18.dp))
                },
                onClick = {
                    expanded = false
                    onNewTab()
                }
            )
            DropdownMenuItem(
                text = { Text("Reload page", color = Color(0xFFE6EDF3), fontSize = 13.sp) },
                leadingIcon = {
                    Icon(Icons.Default.Refresh, contentDescription = null, tint = Color(0xFF8B949E), modifier = Modifier.size(18.dp))
                },
                onClick = {
                    expanded = false
                    onReload()
                }
            )
        }
    }
}
