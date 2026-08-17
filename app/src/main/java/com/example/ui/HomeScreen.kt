package com.example.ui

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.ProjectEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    projects: List<ProjectEntity>,
    gitProgress: String,
    onCreateProject: (String, String, String?, List<android.net.Uri>) -> Unit,
    onUpdateProject: (String, String, String) -> Unit,
    onDeleteProject: (String) -> Unit,
    onSelectProject: (ProjectEntity) -> Unit,
    onCloneProject: (String, String, String?, String, (Result<Unit>) -> Unit) -> Unit
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var showCloneDialog by remember { mutableStateOf(false) }
    var projectToDelete by remember { mutableStateOf<String?>(null) }
    var projectToEdit by remember { mutableStateOf<ProjectEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF21262D),
                            modifier = Modifier.size(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Code,
                                    contentDescription = "Code Icon",
                                    tint = Color(0xFF2F81F7),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Text(
                            text = "PenCode Studio",
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 17.sp,
                            color = Color(0xFFE6EDF3)
                        )
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFF30363D),
                            border = BorderStroke(1.dp, Color(0xFF30363D))
                        ) {
                            Text(
                                text = "IDE Workspace",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF8D96A0),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0D1117),
                    titleContentColor = Color(0xFFE6EDF3)
                )
            )
        },
        containerColor = Color(0xFF0D1117)
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color(0xFF0D1117))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Intro Hero Banner - Professional Slate Developer Card
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22)),
                    border = BorderStroke(1.dp, Color(0xFF30363D)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = Color(0xFF2F81F7),
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Autonomous Software Agent Workspace",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFFE6EDF3)
                            )
                        }
                        Text(
                            text = "Develop web applications, Android packages, and services using direct file operations, embedded terminal tools, and automated compilation pipelines.",
                            fontSize = 12.5.sp,
                            color = Color(0xFF8D96A0),
                            lineHeight = 18.sp
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.padding(top = 6.dp)
                        ) {
                            Button(
                                onClick = { showCreateDialog = true },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF238636),
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Add", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("New Project", fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp)
                            }

                            OutlinedButton(
                                onClick = { showCloneDialog = true },
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = Color(0xFFE6EDF3)
                                ),
                                border = BorderStroke(1.dp, Color(0xFF30363D)),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                            ) {
                                Icon(Icons.Default.Share, contentDescription = "Clone", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Clone Repository", fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp)
                            }
                        }
                    }
                }

                // Projects Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Your Projects",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "${projects.size} active",
                        fontSize = 12.sp,
                        color = Color(0xFF80809B)
                    )
                }

                if (projects.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FolderOpen,
                                contentDescription = "Empty",
                                tint = Color(0xFF3B4056),
                                modifier = Modifier.size(64.dp)
                            )
                            Text(
                                text = "No projects yet",
                                color = Color(0xFF80809B),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Click the button above to start your first vibe project!",
                                color = Color(0xFF4F5575),
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(1), // Large vertical cards
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(projects) { project ->
                            ProjectCard(
                                project = project,
                                onClick = { onSelectProject(project) },
                                onEdit = { projectToEdit = project },
                                onDelete = { projectToDelete = project.name }
                            )
                        }
                    }
                }
            }
        }
    }

    if (projectToEdit != null) {
        EditProjectDialog(
            project = projectToEdit!!,
            onDismiss = { projectToEdit = null },
            onUpdate = { newName, newDesc ->
                onUpdateProject(projectToEdit!!.name, newName, newDesc)
                projectToEdit = null
            }
        )
    }

    if (projectToDelete != null) {
        AlertDialog(
            onDismissRequest = { projectToDelete = null },
            title = { Text("Delete Project", color = Color.White, fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to delete '${projectToDelete}'? This action is permanent and will delete all project history and files.", color = Color(0xFF80809B)) },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteProject(projectToDelete!!)
                        projectToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEE5253))
                ) {
                    Text("Delete", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { projectToDelete = null }) {
                    Text("Cancel", color = Color.Gray)
                }
            },
            containerColor = Color(0xFF12131A),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.border(BorderStroke(1.dp, Color(0xFF222533)), RoundedCornerShape(16.dp))
        )
    }

    if (showCreateDialog) {
        CreateProjectDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { name, desc, template, uris ->
                onCreateProject(name, desc, template, uris)
                showCreateDialog = false
            }
        )
    }

    if (showCloneDialog) {
        CloneProjectDialog(
            gitProgress = gitProgress,
            onDismiss = { showCloneDialog = false },
            onClone = { name, repo, token, branch ->
                onCloneProject(name, repo, token, branch) { result ->
                    if (result.isSuccess) {
                        showCloneDialog = false
                    }
                }
            }
        )
    }
}

@Composable
fun ProjectCard(
    project: ProjectEntity,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0E111A)),
        border = BorderStroke(1.dp, Color(0xFF1F2437)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(Color(0xFF6366F1), Color(0xFF00FFCC))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Terminal,
                        contentDescription = "Project Code",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Column(
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = project.name,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFF1F5F9)
                    )
                    if (project.description.isNotBlank()) {
                        Text(
                            text = project.description,
                            fontSize = 12.sp,
                            color = Color(0xFF94A3B8),
                            maxLines = 1
                        )
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onEdit) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit Project",
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete Project",
                        tint = Color(0xFFEF4444),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun EditProjectDialog(
    project: ProjectEntity,
    onDismiss: () -> Unit,
    onUpdate: (String, String) -> Unit
) {
    var name by remember { mutableStateOf(project.name) }
    var description by remember { mutableStateOf(project.description) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF12131A),
            border = BorderStroke(1.dp, Color(0xFF222533)),
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Edit Workspace",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Workspace Name") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF6366F1),
                        unfocusedBorderColor = Color(0xFF222533),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Short Description") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF6366F1),
                        unfocusedBorderColor = Color(0xFF222533),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 3
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = Color(0xFF80809B))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (name.isNotBlank()) {
                                onUpdate(name.trim(), description.trim())
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF6366F1)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        enabled = name.isNotBlank()
                    ) {
                        Text("Save Changes", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}


@Composable
fun CreateProjectDialog(
    onDismiss: () -> Unit,
    onCreate: (String, String, String?, List<android.net.Uri>) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var selectedTemplate by remember { mutableStateOf<String?>("android_kotlin") } // Default android_kotlin
    var selectedUris by remember { mutableStateOf<List<android.net.Uri>>(emptyList()) }

    val filePickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        selectedUris = uris
    }

    val templates = listOf(
        TemplateOption("android_kotlin", "Android Kotlin", "Native Android App scaffold with Jetpack Compose & Github Action Build."),
        TemplateOption("flutter", "Flutter", "Flutter App scaffold with main.dart, pubspec.yaml & Github Action Build."),
        TemplateOption("react_vite", "React Vite", "React + Vite App scaffold with Tailwind, package.json & GitHub Action Build."),
        TemplateOption("react", "React CDN", "Babel-powered interactive React Hello World with count state."),
        TemplateOption("vanilla", "Vanilla JS", "Pure HTML, CSS & JS centered Hello World screen."),
        TemplateOption("vanilla_three", "Vanilla Three.js", "Interactive 3D Globe canvas powered by Three.js for 3D models, games, websites & objects.")
    )

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF12131A),
            border = BorderStroke(1.dp, Color(0xFF222533)),
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 560.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "New Workspace",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                // Name Input
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Workspace Name") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF00FFCC),
                        unfocusedBorderColor = Color(0xFF222533),
                        focusedLabelColor = Color(0xFF00FFCC),
                        unfocusedLabelColor = Color(0xFF80809B)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                // Description Input
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Short Description") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF00FFCC),
                        unfocusedBorderColor = Color(0xFF222533),
                        focusedLabelColor = Color(0xFF00FFCC),
                        unfocusedLabelColor = Color(0xFF80809B)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "Select Starter Template",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF80809B)
                )

                // Import from Device Button
                OutlinedButton(
                    onClick = { filePickerLauncher.launch("*/*") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF38BDF8)),
                    border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.4f))
                ) {
                    Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (selectedUris.isEmpty()) "Import from Device" else "${selectedUris.size} files selected")
                }

                if (selectedUris.isNotEmpty()) {
                    Text(
                        text = "Importing files will skip template generation for those files.",
                        fontSize = 11.sp,
                        color = Color(0xFF8B949E),
                        style = TextStyle(fontStyle = FontStyle.Italic)
                    )
                }

                // Templates Picker List
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    templates.forEach { template ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (selectedTemplate == template.key) Color(0xFF1E2130)
                                    else Color(0xFF161822)
                                )
                                .border(
                                    1.dp,
                                    if (selectedTemplate == template.key) Color(0xFF6C5CE7)
                                    else Color(0xFF222533),
                                    RoundedCornerShape(12.dp)
                                )
                                .clickable { selectedTemplate = template.key }
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedTemplate == template.key,
                                onClick = { selectedTemplate = template.key },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = Color(0xFF6C5CE7),
                                    unselectedColor = Color(0xFF3B4056)
                                )
                            )
                            TemplateIcon(key = template.key)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = template.title,
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = template.description,
                                    color = Color(0xFF80809B),
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = Color(0xFF80809B))
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Button(
                        onClick = {
                            if (name.isNotBlank()) {
                                onCreate(name, description, selectedTemplate, selectedUris)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF6C5CE7),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp),
                        enabled = name.isNotBlank()
                    ) {
                        Text("Launch", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

data class TemplateOption(
    val key: String,
    val title: String,
    val description: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloneProjectDialog(
    gitProgress: String,
    onDismiss: () -> Unit,
    onClone: (String, String, String?, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var repo by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }
    var branch by remember { mutableStateOf("main") }

    Dialog(onDismissRequest = { if (gitProgress.isEmpty() || gitProgress.contains("complete", ignoreCase = true) || gitProgress.contains("failed", ignoreCase = true)) onDismiss() }) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0D0E15)),
            border = BorderStroke(1.dp, Color(0xFF1E2230)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 560.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Clone GitHub Repository",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Text(
                    text = "Specify repository details to clone and overwrite or load files into your localized memory.",
                    color = Color(0xFF80809B),
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Workspace Name") },
                    placeholder = { Text("e.g. My Website") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF00FFCC),
                        unfocusedBorderColor = Color(0xFF222533),
                        focusedLabelColor = Color(0xFF00FFCC),
                        unfocusedLabelColor = Color(0xFF80809B)
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = repo,
                    onValueChange = { repo = it },
                    label = { Text("Repository (owner/repo or URL)") },
                    placeholder = { Text("e.g. octocat/Hello-World") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF00FFCC),
                        unfocusedBorderColor = Color(0xFF222533),
                        focusedLabelColor = Color(0xFF00FFCC),
                        unfocusedLabelColor = Color(0xFF80809B)
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it },
                    label = { Text("GitHub Access Token (Optional)") },
                    placeholder = { Text("ghp_xxxxxxxxxxxx") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF00FFCC),
                        unfocusedBorderColor = Color(0xFF222533),
                        focusedLabelColor = Color(0xFF00FFCC),
                        unfocusedLabelColor = Color(0xFF80809B)
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = branch,
                    onValueChange = { branch = it },
                    label = { Text("Branch") },
                    placeholder = { Text("main") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF00FFCC),
                        unfocusedBorderColor = Color(0xFF222533),
                        focusedLabelColor = Color(0xFF00FFCC),
                        unfocusedLabelColor = Color(0xFF80809B)
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                if (gitProgress.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF1E293B), RoundedCornerShape(12.dp))
                            .padding(12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(
                                color = Color(0xFF00FFCC),
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                            Text(
                                text = gitProgress,
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = onDismiss,
                        enabled = gitProgress.isEmpty() || gitProgress.contains("complete", ignoreCase = true) || gitProgress.contains("failed", ignoreCase = true)
                    ) {
                        Text("Cancel", color = Color(0xFF80809B))
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Button(
                        onClick = {
                            if (name.isNotBlank() && repo.isNotBlank()) {
                                onClone(name, repo, token.ifBlank { null }, branch.ifBlank { "main" })
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF00FFCC),
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(12.dp),
                        enabled = name.isNotBlank() && repo.isNotBlank() && (gitProgress.isEmpty() || gitProgress.contains("complete", ignoreCase = true) || gitProgress.contains("failed", ignoreCase = true))
                    ) {
                        Text("Clone", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun TemplateIcon(key: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(36.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF0D0F14)),
        contentAlignment = Alignment.Center
    ) {
        when (key) {
            "android_kotlin" -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(Color(0xFF3DDC84).copy(alpha = 0.12f), Color(0xFF0D0F14))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Android,
                        contentDescription = "Android",
                        tint = Color(0xFF3DDC84),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            "flutter" -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(Color(0xFF0175C2).copy(alpha = 0.12f), Color(0xFF0D0F14))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.foundation.Canvas(modifier = Modifier.size(20.dp)) {
                        val w = size.width
                        val h = size.height
                        
                        // Top Cyan Blade
                        val pathTop = androidx.compose.ui.graphics.Path().apply {
                            moveTo(0.596f * w, 0f)
                            lineTo(1f * w, 0f)
                            lineTo(0.5f * w, 0.5f * h)
                            lineTo(0.096f * w, 0.5f * h)
                            close()
                        }
                        drawPath(pathTop, color = Color(0xFF54C5F8))
                        
                        // Bottom Medium Blue Blade
                        val pathBottom = androidx.compose.ui.graphics.Path().apply {
                            moveTo(0.5f * w, 0.5f * h)
                            lineTo(1f * w, 1f * h)
                            lineTo(0.596f * w, 1f * h)
                            lineTo(0.096f * w, 0.5f * h)
                            close()
                        }
                        drawPath(pathBottom, color = Color(0xFF29B6F6))
                        
                        // Bottom Deep Blue Shadow Fold
                        val pathShadow = androidx.compose.ui.graphics.Path().apply {
                            moveTo(0.5f * w, 0.5f * h)
                            lineTo(0.805f * w, 0.805f * h)
                            lineTo(0.596f * w, 1f * h)
                            lineTo(0.29f * w, 0.71f * h)
                            close()
                        }
                        drawPath(pathShadow, color = Color(0xFF01579B))
                    }
                }
            }
            "react" -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(Color(0xFF00D8FF).copy(alpha = 0.12f), Color(0xFF0D0F14))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.foundation.Canvas(modifier = Modifier.size(20.dp)) {
                        val w = size.width
                        val h = size.height
                        val center = androidx.compose.ui.geometry.Offset(w / 2f, h / 2f)
                        
                        drawCircle(
                            color = Color(0xFF00D8FF),
                            radius = 2f * (w / 20f),
                            center = center
                        )
                        
                        val ellipseWidth = 18f * (w / 20f)
                        val ellipseHeight = 6.5f * (h / 20f)
                        
                        for (angle in listOf(0f, 60f, 120f)) {
                            rotate(degrees = angle, pivot = center) {
                                val path = androidx.compose.ui.graphics.Path().apply {
                                    addOval(
                                        androidx.compose.ui.geometry.Rect(
                                            left = center.x - ellipseWidth / 2f,
                                            top = center.y - ellipseHeight / 2f,
                                            right = center.x + ellipseWidth / 2f,
                                            bottom = center.y + ellipseHeight / 2f
                                        )
                                    )
                                }
                                drawPath(
                                    path = path,
                                    color = Color(0xFF00D8FF),
                                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.2f * (w / 20f))
                                )
                            }
                        }
                    }
                }
            }
            "vanilla" -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(Color(0xFFF7DF1E).copy(alpha = 0.12f), Color(0xFF0D0F14))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(Color(0xFFF7DF1E)),
                        contentAlignment = Alignment.BottomEnd
                    ) {
                        Text(
                            text = "JS",
                            color = Color.Black,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.padding(end = 1.dp, bottom = 0.5.dp),
                            lineHeight = 9.sp
                        )
                    }
                }
            }
            "vanilla_three" -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(Color(0xFF00F2FE).copy(alpha = 0.18f), Color(0xFF0D0F14))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Public,
                        contentDescription = "3D Globe",
                        tint = Color(0xFF00F2FE),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            "react_vite" -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(Color(0xFFA855F7).copy(alpha = 0.2f), Color(0xFF0D0F14))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.foundation.Canvas(modifier = Modifier.size(22.dp)) {
                        val w = size.width
                        val h = size.height

                        val shieldPath = androidx.compose.ui.graphics.Path().apply {
                            moveTo(w * 0.06f, h * 0.12f)
                            lineTo(w * 0.94f, h * 0.12f)
                            lineTo(w * 0.50f, h * 0.92f)
                            close()
                        }
                        drawPath(
                            path = shieldPath,
                            brush = Brush.linearGradient(
                                colors = listOf(Color(0xFFBD34FE), Color(0xFF41D1FF)),
                                start = androidx.compose.ui.geometry.Offset(0f, 0f),
                                end = androidx.compose.ui.geometry.Offset(w, h)
                            )
                        )

                        val boltPath = androidx.compose.ui.graphics.Path().apply {
                            moveTo(w * 0.54f, h * 0.18f)
                            lineTo(w * 0.32f, h * 0.52f)
                            lineTo(w * 0.48f, h * 0.52f)
                            lineTo(w * 0.44f, h * 0.82f)
                            lineTo(w * 0.68f, h * 0.46f)
                            lineTo(w * 0.52f, h * 0.46f)
                            close()
                        }
                        drawPath(
                            path = boltPath,
                            brush = Brush.linearGradient(
                                colors = listOf(Color(0xFFFFEA83), Color(0xFFFFDD35)),
                                start = androidx.compose.ui.geometry.Offset(w * 0.3f, h * 0.18f),
                                end = androidx.compose.ui.geometry.Offset(w * 0.7f, h * 0.82f)
                            )
                        )
                    }
                }
            }
        }
    }
}
