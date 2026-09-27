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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.window.DialogProperties
import com.example.data.ProjectEntity
import com.example.ui.theme.stitchPressFeedback

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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
                .background(Color(0xFF0D1117)),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 1100.dp)
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
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
                            .padding(16.dp),
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
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFFE6EDF3)
                            )
                        }
                        Text(
                            text = "Develop web applications, Android packages, and services using direct file operations, embedded terminal tools, and automated compilation pipelines.",
                            fontSize = 12.sp,
                            color = Color(0xFF8D96A0),
                            lineHeight = 17.sp
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.padding(top = 6.dp)
                        ) {
                            com.example.ui.theme.StitchPrimaryButton(
                                text = "New Project",
                                onClick = { showCreateDialog = true },
                                icon = Icons.Default.Add,
                                modifier = Modifier.height(42.dp)
                            )

                            com.example.ui.theme.StitchSecondaryButton(
                                text = "Clone Repository",
                                onClick = { showCloneDialog = true },
                                icon = Icons.Default.Share,
                                modifier = Modifier.height(42.dp)
                            )
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
                        fontSize = 17.sp,
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
                        columns = GridCells.Adaptive(minSize = 320.dp),
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
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

                // Legal Footer: Privacy Policy & Terms and Conditions
                LegalLinksFooter(
                    modifier = Modifier.padding(vertical = 4.dp)
                )
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
            title = { Text("Delete Project", color = Color(0xFFF0F6FC), fontWeight = FontWeight.Bold, fontSize = 18.sp) },
            text = { Text("Are you sure you want to delete '${projectToDelete}'? This action is permanent and will delete all project history and files.", color = Color(0xFF8B949E), fontSize = 13.sp) },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteProject(projectToDelete!!)
                        projectToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDA3633)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Delete", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
            },
            dismissButton = {
                TextButton(onClick = { projectToDelete = null }) {
                    Text("Cancel", color = Color(0xFF8B949E), fontSize = 13.sp)
                }
            },
            containerColor = Color(0xFF161B22),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.border(BorderStroke(1.dp, Color(0xFF30363D)), RoundedCornerShape(12.dp))
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
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF161B22),
        border = BorderStroke(1.dp, Color(0xFF30363D)),
        modifier = Modifier
            .fillMaxWidth()
            .stitchPressFeedback(scaleDown = 0.98f, onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF21262D))
                        .border(BorderStroke(1.dp, Color(0xFF30363D)), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Terminal,
                        contentDescription = "Project Code",
                        tint = Color(0xFF2F81F7),
                        modifier = Modifier.size(20.dp)
                    )
                }

                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = project.name,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFF0F6FC)
                        )
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFF21262D),
                            border = BorderStroke(1.dp, Color(0xFF30363D))
                        ) {
                            Text(
                                text = if (!project.templateKey.isNullOrBlank()) project.templateKey.uppercase() else "APP",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF8B949E),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    if (project.description.isNotBlank()) {
                        Text(
                            text = project.description,
                            fontSize = 12.sp,
                            color = Color(0xFF8B949E),
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
                        tint = Color(0xFF8B949E),
                        modifier = Modifier.size(17.dp)
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete Project",
                        tint = Color(0xFF8B949E),
                        modifier = Modifier.size(17.dp)
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
                .imePadding()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF161B22),
                border = BorderStroke(1.dp, Color(0xFF30363D)),
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 500.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Edit Workspace",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFF0F6FC)
                    )

                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Workspace Name") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF2F81F7),
                            unfocusedBorderColor = Color(0xFF30363D),
                            focusedTextColor = Color(0xFFF0F6FC),
                            unfocusedTextColor = Color(0xFFF0F6FC),
                            focusedLabelColor = Color(0xFF2F81F7),
                            unfocusedLabelColor = Color(0xFF8B949E)
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Short Description") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF2F81F7),
                            unfocusedBorderColor = Color(0xFF30363D),
                            focusedTextColor = Color(0xFFF0F6FC),
                            unfocusedTextColor = Color(0xFFF0F6FC),
                            focusedLabelColor = Color(0xFF2F81F7),
                            unfocusedLabelColor = Color(0xFF8B949E)
                        ),
                        shape = RoundedCornerShape(8.dp),
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
                            Text("Cancel", color = Color(0xFF8B949E), fontSize = 13.sp)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (name.isNotBlank()) {
                                    onUpdate(name.trim(), description.trim())
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF238636)
                            ),
                            shape = RoundedCornerShape(8.dp),
                            enabled = name.isNotBlank()
                        ) {
                            Text("Save Changes", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        }
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
        TemplateOption("chrome_extension", "Chrome Extension", "Manifest V3 extension with popup, background worker, content scripts, live preview hub & GitHub Actions build."),
        TemplateOption("react", "React CDN", "Babel-powered interactive React Hello World with count state."),
        TemplateOption("vanilla", "Vanilla JS", "Pure HTML, CSS & JS centered Hello World screen."),
        TemplateOption("vanilla_three", "Vanilla Three.js", "Interactive 3D Globe canvas powered by Three.js for 3D models, games, websites & objects.")
    )

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
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 20.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF161B22),
                border = BorderStroke(1.dp, Color(0xFF30363D)),
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 520.dp)
                    .heightIn(max = 600.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    Text(
                        text = "New Workspace",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFF0F6FC),
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Name Input
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text("Workspace Name") },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color(0xFFF0F6FC),
                                unfocusedTextColor = Color(0xFFF0F6FC),
                                focusedBorderColor = Color(0xFF2F81F7),
                                unfocusedBorderColor = Color(0xFF30363D),
                                focusedLabelColor = Color(0xFF2F81F7),
                                unfocusedLabelColor = Color(0xFF8B949E)
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Description Input
                        OutlinedTextField(
                            value = description,
                            onValueChange = { description = it },
                            label = { Text("Short Description") },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color(0xFFF0F6FC),
                                unfocusedTextColor = Color(0xFFF0F6FC),
                                focusedBorderColor = Color(0xFF2F81F7),
                                unfocusedBorderColor = Color(0xFF30363D),
                                focusedLabelColor = Color(0xFF2F81F7),
                                unfocusedLabelColor = Color(0xFF8B949E)
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Text(
                            text = "Select Starter Template",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF8B949E)
                        )

                        // Import from Device Button
                        OutlinedButton(
                            onClick = { filePickerLauncher.launch("*/*") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = Color(0xFF21262D),
                                contentColor = Color(0xFFF0F6FC)
                            ),
                            border = BorderStroke(1.dp, Color(0xFF30363D))
                        ) {
                            Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color(0xFF8B949E))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                if (selectedUris.isEmpty()) "Import from Device" else "${selectedUris.size} files selected",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
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
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            if (selectedTemplate == template.key) Color(0xFF21262D)
                                            else Color(0xFF161B22)
                                        )
                                        .border(
                                            1.dp,
                                            if (selectedTemplate == template.key) Color(0xFF2F81F7)
                                            else Color(0xFF30363D),
                                            RoundedCornerShape(8.dp)
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
                                            selectedColor = Color(0xFF2F81F7),
                                            unselectedColor = Color(0xFF484F58)
                                        )
                                    )
                                    TemplateIcon(key = template.key)
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = template.title,
                                            color = Color(0xFFF0F6FC),
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = template.description,
                                            color = Color(0xFF8B949E),
                                            fontSize = 11.5.sp
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 14.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = onDismiss) {
                            Text("Cancel", color = Color(0xFF8B949E), fontSize = 13.sp)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Button(
                            onClick = {
                                if (name.isNotBlank()) {
                                    onCreate(name, description, selectedTemplate, selectedUris)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF238636),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(8.dp),
                            enabled = name.isNotBlank()
                        ) {
                            Text("Create Workspace", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        }
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
    var branch by remember { mutableStateOf("") }

    Dialog(
        onDismissRequest = { if (gitProgress.isEmpty() || gitProgress.contains("complete", ignoreCase = true) || gitProgress.contains("failed", ignoreCase = true)) onDismiss() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 20.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF161B22),
                border = BorderStroke(1.dp, Color(0xFF30363D)),
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 520.dp)
                    .heightIn(max = 600.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    Text(
                        text = "Clone GitHub Repository",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFF0F6FC)
                    )

                    Text(
                        text = "Specify repository details to clone and load files directly into your workspace.",
                        color = Color(0xFF8B949E),
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text("Workspace Name") },
                            placeholder = { Text("e.g. My Website") },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color(0xFFF0F6FC),
                                unfocusedTextColor = Color(0xFFF0F6FC),
                                focusedBorderColor = Color(0xFF2F81F7),
                                unfocusedBorderColor = Color(0xFF30363D),
                                focusedLabelColor = Color(0xFF2F81F7),
                                unfocusedLabelColor = Color(0xFF8B949E)
                            ),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        )

                        OutlinedTextField(
                            value = repo,
                            onValueChange = { input ->
                                repo = input
                                if (name.isBlank() && input.isNotBlank()) {
                                    val guessedName = input.trim()
                                        .removeSuffix(".git")
                                        .removeSuffix("/")
                                        .substringAfterLast("/")
                                        .substringAfterLast(":")
                                    if (guessedName.isNotBlank()) {
                                        name = guessedName
                                    }
                                }
                            },
                            label = { Text("Repository (owner/repo or URL)") },
                            placeholder = { Text("e.g. octocat/Hello-World") },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color(0xFFF0F6FC),
                                unfocusedTextColor = Color(0xFFF0F6FC),
                                focusedBorderColor = Color(0xFF2F81F7),
                                unfocusedBorderColor = Color(0xFF30363D),
                                focusedLabelColor = Color(0xFF2F81F7),
                                unfocusedLabelColor = Color(0xFF8B949E)
                            ),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        )

                        OutlinedTextField(
                            value = token,
                            onValueChange = { token = it },
                            label = { Text("GitHub Access Token (Optional)") },
                            placeholder = { Text("ghp_xxxxxxxxxxxx") },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color(0xFFF0F6FC),
                                unfocusedTextColor = Color(0xFFF0F6FC),
                                focusedBorderColor = Color(0xFF2F81F7),
                                unfocusedBorderColor = Color(0xFF30363D),
                                focusedLabelColor = Color(0xFF2F81F7),
                                unfocusedLabelColor = Color(0xFF8B949E)
                            ),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        )

                        OutlinedTextField(
                            value = branch,
                            onValueChange = { branch = it },
                            label = { Text("Branch (Optional)") },
                            placeholder = { Text("Leave empty for default branch") },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color(0xFFF0F6FC),
                                unfocusedTextColor = Color(0xFFF0F6FC),
                                focusedBorderColor = Color(0xFF2F81F7),
                                unfocusedBorderColor = Color(0xFF30363D),
                                focusedLabelColor = Color(0xFF2F81F7),
                                unfocusedLabelColor = Color(0xFF8B949E)
                            ),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        )

                        if (gitProgress.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF21262D), RoundedCornerShape(8.dp))
                                    .border(BorderStroke(1.dp, Color(0xFF30363D)), RoundedCornerShape(8.dp))
                                    .padding(12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    CircularProgressIndicator(
                                        color = Color(0xFF2F81F7),
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp
                                    )
                                    Text(
                                        text = gitProgress,
                                        color = Color(0xFFF0F6FC),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 14.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = onDismiss,
                            enabled = gitProgress.isEmpty() || gitProgress.contains("complete", ignoreCase = true) || gitProgress.contains("failed", ignoreCase = true)
                        ) {
                            Text("Cancel", color = Color(0xFF8B949E), fontSize = 13.sp)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Button(
                            onClick = {
                                if (name.isNotBlank() && repo.isNotBlank()) {
                                    onClone(name, repo, token.ifBlank { null }, branch.trim())
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF238636),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(8.dp),
                            enabled = name.isNotBlank() && repo.isNotBlank() && (gitProgress.isEmpty() || gitProgress.contains("complete", ignoreCase = true) || gitProgress.contains("failed", ignoreCase = true))
                        ) {
                            Text("Clone Repository", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        }
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
            "chrome_extension" -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(Color(0xFF38BDF8).copy(alpha = 0.2f), Color(0xFF0D0F14))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Extension,
                        contentDescription = "Chrome Extension",
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
