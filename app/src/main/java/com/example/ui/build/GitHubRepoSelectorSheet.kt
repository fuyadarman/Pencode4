package com.example.ui.build

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.auth.github.GitHubAuthManager
import com.example.auth.github.GitHubRepoItem
import kotlinx.coroutines.launch

@Composable
fun GitHubRepoSelectorDialog(
    isOpen: Boolean,
    authManager: GitHubAuthManager,
    onDismiss: () -> Unit,
    onRepoSelected: (repoName: String, branch: String) -> Unit
) {
    if (!isOpen) return

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val authState by authManager.authState.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("All") } // All, Private, Public
    var showCustomInput by remember { mutableStateOf(false) }
    var customRepoName by remember { mutableStateOf("") }
    var customBranchName by remember { mutableStateOf("main") }

    val allRepos = authState.userRepos
    val filteredRepos = remember(allRepos, searchQuery, selectedFilter) {
        allRepos.filter { repo ->
            val matchesFilter = when (selectedFilter) {
                "Private" -> repo.isPrivate
                "Public" -> !repo.isPrivate
                else -> true
            }
            val matchesSearch = searchQuery.isBlank() ||
                    repo.fullName.contains(searchQuery, ignoreCase = true) ||
                    repo.description.contains(searchQuery, ignoreCase = true)
            matchesFilter && matchesSearch
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.88f),
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF13151F),
            border = BorderStroke(1.dp, Color(0xFF2E3248))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            modifier = Modifier.size(36.dp),
                            shape = CircleShape,
                            color = Color(0xFF24292E)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Book, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                            }
                        }
                        Column {
                            Text(
                                text = "Select GitHub Repository",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (authState.isConnected) "@${authState.username} • ${allRepos.size} repos loaded" else "Connecting...",
                                color = Color(0xFF94A3B8),
                                fontSize = 11.sp
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(
                            onClick = {
                                scope.launch {
                                    authManager.refreshUserProfileAndRepos()
                                }
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = Color(0xFF38BDF8), modifier = Modifier.size(18.dp))
                        }
                        IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF94A3B8), modifier = Modifier.size(20.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Search Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search repositories...", fontSize = 12.sp, color = Color(0xFF64748B)) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFF64748B), modifier = Modifier.size(18.dp)) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF38BDF8),
                        unfocusedBorderColor = Color(0xFF2E3248),
                        focusedContainerColor = Color(0xFF1A1D2D),
                        unfocusedContainerColor = Color(0xFF1A1D2D)
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Filter Chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val privateCount = allRepos.count { it.isPrivate }
                    val publicCount = allRepos.count { !it.isPrivate }

                    listOf(
                        "All" to allRepos.size,
                        "Private" to privateCount,
                        "Public" to publicCount
                    ).forEach { (label, count) ->
                        val isSelected = selectedFilter == label
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) Color(0xFF2563EB) else Color(0xFF1E2132),
                            border = BorderStroke(1.dp, if (isSelected) Color(0xFF38BDF8) else Color(0xFF2E3248)),
                            modifier = Modifier.clickable { selectedFilter = label }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (label == "Private") {
                                    Icon(Icons.Default.Lock, contentDescription = null, tint = if (isSelected) Color.White else Color(0xFFFBBF24), modifier = Modifier.size(12.dp))
                                }
                                Text(
                                    text = "$label ($count)",
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) Color.White else Color(0xFF94A3B8)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // Toggle Manual Custom Repo Input
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (showCustomInput) Color(0xFF047857) else Color(0xFF1E2132),
                        border = BorderStroke(1.dp, if (showCustomInput) Color(0xFF34D399) else Color(0xFF2E3248)),
                        modifier = Modifier.clickable { showCustomInput = !showCustomInput }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, tint = Color(0xFF34D399), modifier = Modifier.size(12.dp))
                            Text("Custom", fontSize = 11.sp, color = Color.White)
                        }
                    }
                }

                // Custom Input Section (For directly selecting any private repo)
                if (showCustomInput) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1D2D)),
                        border = BorderStroke(1.dp, Color(0xFF34D399).copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("Directly enter any repository (Private or Public):", fontSize = 11.sp, color = Color(0xFF34D399), fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedTextField(
                                    value = customRepoName,
                                    onValueChange = { customRepoName = it },
                                    placeholder = { Text("owner/repo-name", fontSize = 11.sp, color = Color(0xFF64748B)) },
                                    singleLine = true,
                                    modifier = Modifier.weight(2f),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                OutlinedTextField(
                                    value = customBranchName,
                                    onValueChange = { customBranchName = it },
                                    placeholder = { Text("main", fontSize = 11.sp, color = Color(0xFF64748B)) },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    val r = customRepoName.trim()
                                    if (r.isNotBlank()) {
                                        onRepoSelected(r, customBranchName.trim().ifBlank { "main" })
                                        onDismiss()
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().height(36.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Use This Repository", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Repositories List
                if (filteredRepos.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(20.dp)
                        ) {
                            Icon(Icons.Default.FolderOff, contentDescription = null, tint = Color(0xFF64748B), modifier = Modifier.size(36.dp))
                            Text(
                                text = if (selectedFilter == "Private") "No private repositories found under this authorization." else "No repositories match your search.",
                                color = Color(0xFF94A3B8),
                                fontSize = 12.sp
                            )
                            Text(
                                text = "GitHub App requires permission to access private repositories. Tap below to install on all or selected private repos:",
                                color = Color(0xFF64748B),
                                fontSize = 11.sp
                            )
                            Button(
                                onClick = {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/apps/pencode/installations/new"))
                                    context.startActivity(intent)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.White)
                                    Text("Grant Access to Private Repos", fontSize = 11.sp, color = Color.White)
                                }
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredRepos) { repo ->
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1D2D)),
                                border = BorderStroke(1.dp, if (repo.isPrivate) Color(0xFFF59E0B).copy(alpha = 0.3f) else Color(0xFF2E3248)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onRepoSelected(repo.fullName, repo.defaultBranch)
                                        onDismiss()
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Surface(
                                        modifier = Modifier.size(32.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (repo.isPrivate) Color(0xFF78350F).copy(alpha = 0.5f) else Color(0xFF1E293B)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = if (repo.isPrivate) Icons.Default.Lock else Icons.Default.Public,
                                                contentDescription = null,
                                                tint = if (repo.isPrivate) Color(0xFFFBBF24) else Color(0xFF38BDF8),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = repo.fullName,
                                                color = Color.White,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            if (repo.isPrivate) {
                                                Surface(
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = Color(0xFFF59E0B).copy(alpha = 0.2f),
                                                    border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.4f))
                                                ) {
                                                    Text(
                                                        text = "Private",
                                                        color = Color(0xFFFBBF24),
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                    )
                                                }
                                            }
                                        }

                                        if (repo.description.isNotBlank()) {
                                            Text(
                                                text = repo.description,
                                                color = Color(0xFF94A3B8),
                                                fontSize = 11.sp,
                                                maxLines = 1
                                            )
                                        }

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text("Branch: ${repo.defaultBranch}", fontSize = 10.sp, color = Color(0xFF64748B))
                                            if (repo.starsCount > 0) {
                                                Text("★ ${repo.starsCount}", fontSize = 10.sp, color = Color(0xFFFBBF24))
                                            }
                                        }
                                    }

                                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color(0xFF64748B), modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Bottom help hint for installing GitHub app on private repos
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF1E2132))
                        .clickable {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/apps/pencode/installations/new"))
                            context.startActivity(intent)
                        }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Security, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(16.dp))
                    Text(
                        text = "Need another private repo? Tap to configure GitHub App access permissions",
                        color = Color(0xFF38BDF8),
                        fontSize = 10.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(Icons.Default.OpenInNew, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(12.dp))
                }
            }
        }
    }
}
