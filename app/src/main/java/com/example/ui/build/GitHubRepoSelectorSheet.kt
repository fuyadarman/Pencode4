package com.example.ui.build

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
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

    val authState by authManager.authState.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    var searchQuery by remember { mutableStateOf("") }

    val filteredRepos = remember(authState.userRepos, searchQuery) {
        if (searchQuery.isBlank()) {
            authState.userRepos
        } else {
            val q = searchQuery.trim().lowercase()
            authState.userRepos.filter {
                it.name.lowercase().contains(q) ||
                it.fullName.lowercase().contains(q) ||
                it.description.lowercase().contains(q)
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f),
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
                        if (authState.avatarUrl.isNotBlank()) {
                            AsyncImage(
                                model = authState.avatarUrl,
                                contentDescription = "Avatar",
                                modifier = Modifier.size(32.dp).clip(CircleShape)
                            )
                        } else {
                            Icon(Icons.Default.FolderSpecial, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(24.dp))
                        }
                        Column {
                            Text(
                                text = "Select GitHub Repository",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "@${authState.username.ifBlank { "User" }} (${authState.userRepos.size} repos)",
                                color = Color(0xFF94A3B8),
                                fontSize = 11.sp
                            )
                        }
                    }

                    Row {
                        IconButton(
                            onClick = {
                                coroutineScope.launch {
                                    authManager.refreshUserProfileAndRepos()
                                }
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = Color(0xFF94A3B8), modifier = Modifier.size(18.dp))
                        }
                        IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF94A3B8), modifier = Modifier.size(18.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Search field
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search your repositories...", fontSize = 12.sp, color = Color(0xFF64748B)) },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.size(18.dp))
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(20.dp)) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear", tint = Color(0xFF94A3B8), modifier = Modifier.size(14.dp))
                            }
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF38BDF8),
                        unfocusedBorderColor = Color(0xFF2E3248),
                        focusedContainerColor = Color(0xFF1A1D2D),
                        unfocusedContainerColor = Color(0xFF1A1D2D)
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Repos list
                if (filteredRepos.isEmpty()) {
                    Box(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.FolderOpen, contentDescription = null, tint = Color(0xFF475569), modifier = Modifier.size(36.dp))
                            Text(
                                text = if (authState.isLoading) "Loading repositories..." else "No repositories found",
                                color = Color(0xFF94A3B8),
                                fontSize = 13.sp
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredRepos) { repo ->
                            val isSelected = authState.selectedRepo.equals(repo.name, ignoreCase = true) ||
                                            authState.selectedRepo.equals(repo.fullName, ignoreCase = true)

                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected) Color(0xFF1E3A8A).copy(alpha = 0.5f) else Color(0xFF1A1D2D)
                                ),
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) Color(0xFF38BDF8) else Color(0xFF2E3248)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        authManager.selectRepo(repo.fullName, repo.defaultBranch)
                                        // Just use repo.name or repo.fullName for the build tab
                                        onRepoSelected(repo.name, repo.defaultBranch)
                                        onDismiss()
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = if (repo.isPrivate) Icons.Default.Lock else Icons.Default.Folder,
                                        contentDescription = null,
                                        tint = if (isSelected) Color(0xFF38BDF8) else Color(0xFF94A3B8),
                                        modifier = Modifier.size(20.dp)
                                    )

                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = repo.name,
                                                color = Color.White,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            if (repo.isPrivate) {
                                                Surface(
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = Color(0xFF334155)
                                                ) {
                                                    Text(
                                                        text = "Private",
                                                        color = Color(0xFFCBD5E1),
                                                        fontSize = 9.sp,
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
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            Text(
                                                text = "branch: ${repo.defaultBranch}",
                                                color = Color(0xFF64748B),
                                                fontSize = 10.sp
                                            )
                                            if (repo.starsCount > 0) {
                                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                                    Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFBBF24), modifier = Modifier.size(11.dp))
                                                    Text(text = "${repo.starsCount}", color = Color(0xFF94A3B8), fontSize = 10.sp)
                                                }
                                            }
                                        }
                                    }

                                    if (isSelected) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = "Selected", tint = Color(0xFF38BDF8), modifier = Modifier.size(20.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
