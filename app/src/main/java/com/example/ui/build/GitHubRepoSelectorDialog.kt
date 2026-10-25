package com.example.ui.build

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.auth.github.GitHubAuthManager
import com.example.auth.github.GitHubRepoItem
import com.example.git.GitHubRepoCreationEngine
import com.example.ui.theme.AppTheme
import kotlinx.coroutines.launch

/**
 * GitHubRepoSelectorDialog
 * Allows selecting existing public/private GitHub repositories, creating brand new
 * repositories (public or private), or entering a custom repository path.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GitHubRepoSelectorDialog(
    isOpen: Boolean,
    authManager: GitHubAuthManager,
    onDismiss: () -> Unit,
    onRepoSelected: (repoName: String, branch: String) -> Unit
) {
    if (!isOpen) return

    val authState by authManager.authState.collectAsState()
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableStateOf(0) } // 0: Browse, 1: Create New, 2: Custom
    var filterType by remember { mutableStateOf("all") } // all, private, public
    var searchQuery by remember { mutableStateOf("") }

    // Create New Repo state
    var newRepoName by remember { mutableStateOf("") }
    var isNewRepoPrivate by remember { mutableStateOf(true) }
    var newRepoDescription by remember { mutableStateOf("") }
    var isCreatingRepo by remember { mutableStateOf(false) }
    var createRepoError by remember { mutableStateOf<String?>(null) }

    // Custom Name state
    var customRepoInput by remember { mutableStateOf(authState.selectedRepo) }
    var customBranchInput by remember { mutableStateOf(authState.selectedBranch.ifBlank { "main" }) }

    LaunchedEffect(isOpen) {
        if (authState.isConnected && authState.userRepos.isEmpty()) {
            authManager.refreshUserProfileAndRepos()
        }
    }

    val filteredRepos = remember(authState.userRepos, searchQuery, filterType) {
        authState.userRepos.filter { repo ->
            val matchesFilter = when (filterType) {
                "private" -> repo.isPrivate
                "public" -> !repo.isPrivate
                else -> true
            }
            val matchesSearch = searchQuery.isBlank() ||
                repo.name.contains(searchQuery, ignoreCase = true) ||
                repo.fullName.contains(searchQuery, ignoreCase = true) ||
                repo.description.contains(searchQuery, ignoreCase = true)
            matchesFilter && matchesSearch
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
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
                        Surface(
                            modifier = Modifier.size(36.dp),
                            shape = CircleShape,
                            color = Color(0xFF24292E)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.FolderOpen,
                                    contentDescription = null,
                                    tint = Color(0xFF38BDF8),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                text = "Select GitHub Repository",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            val userLabel = if (authState.username.isNotBlank()) "@${authState.username}" else "GitHub Account"
                            Text(
                                text = "$userLabel • ${authState.userRepos.size} repos loaded",
                                color = Color(0xFF94A3B8),
                                fontSize = 11.sp
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = {
                                scope.launch {
                                    authManager.refreshUserProfileAndRepos()
                                }
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh",
                                tint = Color(0xFF38BDF8),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Color(0xFF94A3B8),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Navigation Tabs: Browse, Create New, Custom
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Color(0xFF1A1D2B),
                    contentColor = Color.White,
                    divider = {}
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Default.List, contentDescription = null, modifier = Modifier.size(14.dp))
                                Text("Browse (${authState.userRepos.size})", fontSize = 11.sp)
                            }
                        }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Default.AddCircleOutline, contentDescription = null, modifier = Modifier.size(14.dp))
                                Text("Create New", fontSize = 11.sp)
                            }
                        }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                                Text("Custom", fontSize = 11.sp)
                            }
                        }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Tab Content
                when (selectedTab) {
                    0 -> {
                        // BROWSE REPOSITORIES
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search repositories...", fontSize = 12.sp, color = Color(0xFF64748B)) },
                            leadingIcon = {
                                Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFF64748B), modifier = Modifier.size(16.dp))
                            },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(24.dp)) {
                                        Icon(Icons.Default.Close, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.size(14.dp))
                                    }
                                }
                            },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFF38BDF8),
                                unfocusedBorderColor = Color(0xFF2E3248),
                                focusedContainerColor = Color(0xFF1A1D2B),
                                unfocusedContainerColor = Color(0xFF1A1D2B)
                            )
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Filter Chips
                        val privateCount = remember(authState.userRepos) { authState.userRepos.count { it.isPrivate } }
                        val publicCount = remember(authState.userRepos) { authState.userRepos.count { !it.isPrivate } }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            FilterChip(
                                selected = filterType == "all",
                                onClick = { filterType = "all" },
                                label = { Text("All (${authState.userRepos.size})", fontSize = 11.sp) }
                            )
                            FilterChip(
                                selected = filterType == "private",
                                onClick = { filterType = "private" },
                                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(12.dp)) },
                                label = { Text("Private ($privateCount)", fontSize = 11.sp) }
                            )
                            FilterChip(
                                selected = filterType == "public",
                                onClick = { filterType = "public" },
                                leadingIcon = { Icon(Icons.Default.Public, contentDescription = null, modifier = Modifier.size(12.dp)) },
                                label = { Text("Public ($publicCount)", fontSize = 11.sp) }
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        if (authState.isLoading) {
                            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    CircularProgressIndicator(color = Color(0xFF38BDF8), modifier = Modifier.size(28.dp))
                                    Text("Loading repositories...", color = Color(0xFF94A3B8), fontSize = 12.sp)
                                }
                            }
                        } else if (filteredRepos.isEmpty()) {
                            Box(
                                modifier = Modifier.weight(1f).fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.padding(20.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.FolderOff,
                                        contentDescription = null,
                                        tint = Color(0xFF64748B),
                                        modifier = Modifier.size(44.dp)
                                    )
                                    Text(
                                        text = if (searchQuery.isNotBlank()) "No repositories match '$searchQuery'" else "No repositories loaded yet",
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "You can create a brand new repo or enter a custom name below.",
                                        color = Color(0xFF94A3B8),
                                        fontSize = 11.sp
                                    )
                                    Button(
                                        onClick = { selectedTab = 1 },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Create New Repository", fontSize = 12.sp)
                                    }
                                }
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.weight(1f).fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(filteredRepos, key = { it.fullName }) { repo ->
                                    val isCurrent = authState.selectedRepo.equals(repo.fullName, ignoreCase = true)
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                authManager.selectRepo(repo.fullName, repo.defaultBranch)
                                                onRepoSelected(repo.fullName, repo.defaultBranch)
                                                onDismiss()
                                            },
                                        shape = RoundedCornerShape(10.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (isCurrent) Color(0xFF1E293B) else Color(0xFF161926)
                                        ),
                                        border = BorderStroke(
                                            1.dp,
                                            if (isCurrent) Color(0xFF38BDF8) else Color(0xFF25293C)
                                        )
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (repo.isPrivate) Icons.Default.Lock else Icons.Default.Public,
                                                contentDescription = if (repo.isPrivate) "Private" else "Public",
                                                tint = if (repo.isPrivate) Color(0xFFFBBF24) else Color(0xFF38BDF8),
                                                modifier = Modifier.size(18.dp)
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
                                                        fontWeight = FontWeight.Bold,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    Surface(
                                                        shape = RoundedCornerShape(4.dp),
                                                        color = if (repo.isPrivate) Color(0xFFFBBF24).copy(alpha = 0.15f) else Color(0xFF38BDF8).copy(alpha = 0.15f)
                                                    ) {
                                                        Text(
                                                            text = if (repo.isPrivate) "Private" else "Public",
                                                            color = if (repo.isPrivate) Color(0xFFFBBF24) else Color(0xFF38BDF8),
                                                            fontSize = 9.sp,
                                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                        )
                                                    }
                                                }
                                                if (repo.description.isNotBlank()) {
                                                    Text(
                                                        text = repo.description,
                                                        color = Color(0xFF94A3B8),
                                                        fontSize = 11.sp,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                                Text(
                                                    text = "${repo.fullName} • branch: ${repo.defaultBranch}",
                                                    color = Color(0xFF64748B),
                                                    fontSize = 10.sp
                                                )
                                            }

                                            if (isCurrent) {
                                                Icon(
                                                    imageVector = Icons.Default.CheckCircle,
                                                    contentDescription = "Selected",
                                                    tint = Color(0xFF10B981),
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    1 -> {
                        // CREATE NEW REPOSITORY
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "Create Brand New GitHub Repository",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "This repository will be created directly on your connected GitHub account (@${authState.username}) and set as the active build target.",
                                color = Color(0xFF94A3B8),
                                fontSize = 11.sp,
                                lineHeight = 15.sp
                            )

                            OutlinedTextField(
                                value = newRepoName,
                                onValueChange = {
                                    newRepoName = it.replace(" ", "-").replace(Regex("[^a-zA-Z0-9_.-]"), "")
                                    createRepoError = null
                                },
                                label = { Text("Repository Name", fontSize = 11.sp) },
                                placeholder = { Text("e.g. my-awesome-app", fontSize = 11.sp) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = Color(0xFF38BDF8),
                                    unfocusedBorderColor = Color(0xFF2E3248),
                                    focusedContainerColor = Color(0xFF1A1D2B),
                                    unfocusedContainerColor = Color(0xFF1A1D2B)
                                )
                            )

                            // Visibility Selector: Private vs Public
                            Card(
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1D2B)),
                                border = BorderStroke(1.dp, Color(0xFF2E3248)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("Visibility", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { isNewRepoPrivate = true },
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        RadioButton(
                                            selected = isNewRepoPrivate,
                                            onClick = { isNewRepoPrivate = true },
                                            colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF38BDF8))
                                        )
                                        Column {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFFFBBF24), modifier = Modifier.size(14.dp))
                                                Text("Private (Recommended)", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                            }
                                            Text("Only you and invited collaborators can view this repository.", color = Color(0xFF94A3B8), fontSize = 10.sp)
                                        }
                                    }

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { isNewRepoPrivate = false },
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        RadioButton(
                                            selected = !isNewRepoPrivate,
                                            onClick = { isNewRepoPrivate = false },
                                            colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF38BDF8))
                                        )
                                        Column {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                Icon(Icons.Default.Public, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(14.dp))
                                                Text("Public", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                            }
                                            Text("Anyone on GitHub can see this repository.", color = Color(0xFF94A3B8), fontSize = 10.sp)
                                        }
                                    }
                                }
                            }

                            OutlinedTextField(
                                value = newRepoDescription,
                                onValueChange = { newRepoDescription = it },
                                label = { Text("Description (Optional)", fontSize = 11.sp) },
                                placeholder = { Text("Project description...", fontSize = 11.sp) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = Color(0xFF38BDF8),
                                    unfocusedBorderColor = Color(0xFF2E3248),
                                    focusedContainerColor = Color(0xFF1A1D2B),
                                    unfocusedContainerColor = Color(0xFF1A1D2B)
                                )
                            )

                            if (createRepoError != null) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFF7F1D1D),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = createRepoError.orEmpty(),
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(10.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.weight(1f))

                            Button(
                                onClick = {
                                    if (newRepoName.isBlank()) {
                                        createRepoError = "Please enter a repository name."
                                        return@Button
                                    }
                                    val token = authState.accessToken
                                    if (token.isBlank()) {
                                        createRepoError = "GitHub token is missing. Please reconnect your account."
                                        return@Button
                                    }
                                    isCreatingRepo = true
                                    createRepoError = null
                                    scope.launch {
                                        val result = GitHubRepoCreationEngine.createRepository(
                                            token = token,
                                            name = newRepoName,
                                            isPrivate = isNewRepoPrivate,
                                            description = newRepoDescription,
                                            autoInit = true
                                        )
                                        isCreatingRepo = false
                                        result.fold(
                                            onSuccess = { createdRepo ->
                                                authManager.selectRepo(createdRepo.fullName, createdRepo.defaultBranch)
                                                authManager.refreshUserProfileAndRepos()
                                                onRepoSelected(createdRepo.fullName, createdRepo.defaultBranch)
                                                onDismiss()
                                            },
                                            onFailure = { err ->
                                                createRepoError = err.message ?: "Failed to create repository."
                                            }
                                        )
                                    }
                                },
                                enabled = newRepoName.isNotBlank() && !isCreatingRepo,
                                modifier = Modifier.fillMaxWidth().height(46.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                if (isCreatingRepo) {
                                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Creating on GitHub...", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                } else {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Create & Select Repository", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    2 -> {
                        // CUSTOM REPO PATH
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Text(
                                text = "Enter Custom Repository",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "If your repository isn't showing up in the list, type its name or 'owner/repo' path directly below.",
                                color = Color(0xFF94A3B8),
                                fontSize = 11.sp,
                                lineHeight = 15.sp
                            )

                            OutlinedTextField(
                                value = customRepoInput,
                                onValueChange = { customRepoInput = it },
                                label = { Text("Repository Path or Name", fontSize = 11.sp) },
                                placeholder = { Text("e.g. owner/repo-name or repo-name", fontSize = 11.sp) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = Color(0xFF38BDF8),
                                    unfocusedBorderColor = Color(0xFF2E3248),
                                    focusedContainerColor = Color(0xFF1A1D2B),
                                    unfocusedContainerColor = Color(0xFF1A1D2B)
                                )
                            )

                            OutlinedTextField(
                                value = customBranchInput,
                                onValueChange = { customBranchInput = it },
                                label = { Text("Branch", fontSize = 11.sp) },
                                placeholder = { Text("main", fontSize = 11.sp) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = Color(0xFF38BDF8),
                                    unfocusedBorderColor = Color(0xFF2E3248),
                                    focusedContainerColor = Color(0xFF1A1D2B),
                                    unfocusedContainerColor = Color(0xFF1A1D2B)
                                )
                            )

                            Spacer(modifier = Modifier.weight(1f))

                            Button(
                                onClick = {
                                    val cleanRepo = customRepoInput.trim()
                                    val cleanBranch = customBranchInput.trim().ifBlank { "main" }
                                    if (cleanRepo.isNotBlank()) {
                                        val fullRepo = if (!cleanRepo.contains("/") && authState.username.isNotBlank()) {
                                            "${authState.username}/$cleanRepo"
                                        } else {
                                            cleanRepo
                                        }
                                        authManager.selectRepo(fullRepo, cleanBranch)
                                        onRepoSelected(fullRepo, cleanBranch)
                                        onDismiss()
                                    }
                                },
                                enabled = customRepoInput.isNotBlank(),
                                modifier = Modifier.fillMaxWidth().height(46.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Use This Repository", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}
