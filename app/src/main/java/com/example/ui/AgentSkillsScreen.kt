package com.example.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

data class AgentSkill(
    val id: String,
    val name: String,
    val author: String,
    val installs: String,
    val description: String,
    val githubUrl: String = "https://github.com/vercel-labs/agent-skills",
    val isInstalled: Boolean = false,
    val isEnabled: Boolean = false,
    val isCustom: Boolean = false,
    val skillPrompt: String = "",
    val filePath: String = "SKILL.md",
    val rawFileUrl: String = ""
)

val defaultAgentSkills = com.example.agent.AgentSkillRegistry.curatedPlatformSkills

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentSkillsDialog(
    skills: List<AgentSkill>,
    onToggleSkill: (String, Boolean) -> Unit,
    onInstallSkill: (String) -> Unit,
    onUninstallSkill: (String) -> Unit,
    onAddCustomSkill: (AgentSkill) -> Unit,
    onFetchOnlineSkills: () -> Unit = {},
    isFetchingSkills: Boolean = false,
    onUpdateSkillContent: (String, String) -> Unit = { _, _ -> },
    onFetchSkillFileContent: (String, ((String) -> Unit)?) -> Unit = { _, _ -> },
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }
    var showAddCustomDialog by remember { mutableStateOf(false) }
    var inspectingSkill by remember { mutableStateOf<AgentSkill?>(null) }

    val categories = listOf("All", "Installed", "Google Gemini", "Android", "Anthropic", "Vercel", "Browser Use", "Composio", "LangChain", "Supabase", "Cloudflare")

    LaunchedEffect(Unit) {
        if (skills.isEmpty()) {
            onFetchOnlineSkills()
        }
    }

    val filteredSkills = remember(skills, searchQuery, selectedCategory) {
        skills.filter { skill ->
            val matchesQuery = searchQuery.isBlank() ||
                skill.name.contains(searchQuery, ignoreCase = true) ||
                skill.author.contains(searchQuery, ignoreCase = true) ||
                skill.description.contains(searchQuery, ignoreCase = true)

            val matchesCategory = when (selectedCategory) {
                "Installed" -> skill.isInstalled
                "Google Gemini" -> skill.author.contains("google", ignoreCase = true) || skill.author.contains("gemini", ignoreCase = true) || skill.name.contains("gemini", ignoreCase = true) || skill.id.contains("gemini", ignoreCase = true)
                "Android" -> skill.author.contains("android", ignoreCase = true) || skill.name.contains("android", ignoreCase = true) || skill.name.contains("compose", ignoreCase = true) || skill.id.contains("android", ignoreCase = true)
                "Anthropic" -> skill.author.contains("anthropic", ignoreCase = true) || skill.name.contains("claude", ignoreCase = true) || skill.id.contains("anthropic", ignoreCase = true)
                "Vercel" -> skill.author.contains("vercel", ignoreCase = true) || skill.name.contains("react", ignoreCase = true) || skill.name.contains("next", ignoreCase = true) || skill.id.contains("vercel", ignoreCase = true)
                "Browser Use" -> skill.author.contains("browser", ignoreCase = true) || skill.name.contains("browser", ignoreCase = true) || skill.id.contains("browser", ignoreCase = true)
                "Composio" -> skill.author.contains("composio", ignoreCase = true) || skill.name.contains("composio", ignoreCase = true) || skill.id.contains("composio", ignoreCase = true)
                "LangChain" -> skill.author.contains("langchain", ignoreCase = true) || skill.name.contains("langchain", ignoreCase = true) || skill.id.contains("langchain", ignoreCase = true)
                "Supabase" -> skill.author.contains("supabase", ignoreCase = true) || skill.name.contains("supabase", ignoreCase = true) || skill.id.contains("supabase", ignoreCase = true)
                "Cloudflare" -> skill.author.contains("cloudflare", ignoreCase = true) || skill.name.contains("cloudflare", ignoreCase = true) || skill.id.contains("cloudflare", ignoreCase = true)
                else -> true
            }

            matchesQuery && matchesCategory
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0B0C10))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Top Navigation Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF161822))
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        // Pill Badge for Agent Skills
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(Color(0xFF1A1D2C))
                                .border(BorderStroke(1.dp, Color(0xFF2A2E44)), RoundedCornerShape(20.dp))
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Extension,
                                    contentDescription = null,
                                    tint = Color(0xFF00F2FE),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Agent Skills",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Top Action Buttons
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IconButton(
                            onClick = {
                                onFetchOnlineSkills()
                                android.widget.Toast.makeText(context, "Fetching real skills from GitHub repositories...", android.widget.Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF161822))
                                .border(BorderStroke(1.dp, Color(0xFF00F2FE).copy(alpha = 0.5f)), CircleShape)
                        ) {
                            if (isFetchingSkills) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = Color(0xFF00F2FE),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Fetch Skills",
                                    tint = Color(0xFF00F2FE)
                                )
                            }
                        }

                        IconButton(
                            onClick = { showAddCustomDialog = true },
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF00F2FE))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Add Custom Skill",
                                tint = Color(0xFF0D0E15)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Discover Skills Header
                Text(
                    text = "Discover Agent Skills",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Text(
                    text = "Extend your Agent with real skills across Google Gemini, Android, Anthropic, Vercel, Browser Use, and more.",
                    fontSize = 13.sp,
                    color = Color(0xFF94A3B8),
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                )

                // Search Skills Field
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search skills (e.g., Gemini, Android, Claude, Browser)...", color = Color(0xFF64748B), fontSize = 13.sp) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = Color(0xFF64748B)
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Clear",
                                    tint = Color(0xFF64748B)
                                )
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF161822)),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF00F2FE),
                        unfocusedBorderColor = Color(0xFF222533),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Category Filter Chips
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(categories) { category ->
                        val isSelected = selectedCategory == category
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(if (isSelected) Color(0xFF00F2FE) else Color(0xFF161822))
                                .border(
                                    BorderStroke(1.dp, if (isSelected) Color(0xFF00F2FE) else Color(0xFF222533)),
                                    RoundedCornerShape(20.dp)
                                )
                                .clickable { selectedCategory = category }
                                .padding(horizontal = 14.dp, vertical = 7.dp)
                        ) {
                            Text(
                                text = category,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) Color(0xFF0D0E15) else Color(0xFFCBD5E1)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Skills List
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (filteredSkills.isEmpty()) {
                        item {
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF131520)),
                                border = BorderStroke(1.dp, Color(0xFF222533)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CloudDownload,
                                        contentDescription = null,
                                        tint = Color(0xFF00F2FE),
                                        modifier = Modifier.size(44.dp)
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = if (selectedCategory == "All") "No real skills loaded" else "No skills found for '$selectedCategory'",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "Fetch real agent skill files directly from public GitHub repositories.",
                                        fontSize = 13.sp,
                                        color = Color(0xFF94A3B8),
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Button(
                                        onClick = {
                                            onFetchOnlineSkills()
                                            android.widget.Toast.makeText(context, "Fetching real skills from GitHub...", android.widget.Toast.LENGTH_SHORT).show()
                                        },
                                        enabled = !isFetchingSkills,
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00F2FE)),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        if (isFetchingSkills) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(18.dp),
                                                color = Color(0xFF0D0E15),
                                                strokeWidth = 2.dp
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "Fetching from GitHub...",
                                                color = Color(0xFF0D0E15),
                                                fontWeight = FontWeight.Bold
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.Default.Refresh,
                                                contentDescription = null,
                                                tint = Color(0xFF0D0E15),
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "Fetch Real Skills",
                                                color = Color(0xFF0D0E15),
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        items(filteredSkills, key = { it.id }) { skill ->
                        var isExpanded by remember { mutableStateOf(false) }

                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF131520)),
                            border = BorderStroke(1.dp, if (skill.isInstalled && skill.isEnabled) Color(0xFF00F2FE).copy(alpha = 0.4f) else Color(0xFF222533)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                            ) {
                                // Title & Install/Toggle
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        val platform = com.example.agent.AgentSkillRegistry.getSkillPlatform(skill)
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                text = skill.name,
                                                fontSize = 17.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White,
                                                modifier = Modifier.weight(1f, fill = false)
                                            )
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(Color(platform.colorHex).copy(alpha = 0.15f))
                                                    .border(BorderStroke(0.5.dp, Color(platform.colorHex).copy(alpha = 0.5f)), RoundedCornerShape(6.dp))
                                                    .padding(horizontal = 7.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = platform.name,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = Color(platform.colorHex)
                                                )
                                            }
                                        }

                                        Text(
                                            text = "${skill.author} • ${skill.installs}",
                                            fontSize = 12.sp,
                                            color = Color(0xFF94A3B8),
                                            modifier = Modifier.padding(top = 2.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(8.dp))

                                    if (skill.isInstalled) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = if (skill.isEnabled) "ON" else "OFF",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (skill.isEnabled) Color(0xFF00F2FE) else Color(0xFF64748B),
                                                modifier = Modifier.padding(end = 6.dp)
                                            )
                                            Switch(
                                                checked = skill.isEnabled,
                                                onCheckedChange = { onToggleSkill(skill.id, it) },
                                                colors = SwitchDefaults.colors(
                                                    checkedThumbColor = Color(0xFF0D0E15),
                                                    checkedTrackColor = Color(0xFF00F2FE),
                                                    uncheckedThumbColor = Color(0xFF94A3B8),
                                                    uncheckedTrackColor = Color(0xFF222533)
                                                ),
                                                modifier = Modifier.scale(0.85f)
                                            )
                                        }
                                    } else {
                                        Button(
                                            onClick = { onInstallSkill(skill.id) },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF222638)),
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.FileDownload,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "Install",
                                                color = Color.White,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                // Description
                                Text(
                                    text = skill.description,
                                    fontSize = 13.sp,
                                    color = Color(0xFFCBD5E1),
                                    lineHeight = 18.sp
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                // Footer Row: View File, View on GitHub & Uninstall
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(
                                            onClick = { inspectingSkill = skill },
                                            shape = RoundedCornerShape(8.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E2638)),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                            modifier = Modifier.height(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Visibility,
                                                contentDescription = "Inspect File",
                                                tint = Color(0xFF00F2FE),
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "View File",
                                                fontSize = 12.sp,
                                                color = Color(0xFF00F2FE),
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }

                                        OutlinedButton(
                                            onClick = {
                                                try {
                                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(skill.githubUrl))
                                                    context.startActivity(intent)
                                                } catch (_: Exception) {}
                                            },
                                            shape = RoundedCornerShape(8.dp),
                                            border = BorderStroke(1.dp, Color(0xFF2E344A)),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                            modifier = Modifier.height(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Code,
                                                contentDescription = null,
                                                tint = Color(0xFF94A3B8),
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "GitHub",
                                                fontSize = 12.sp,
                                                color = Color(0xFFE2E8F0)
                                            )
                                        }
                                    }

                                    if (skill.isInstalled) {
                                        IconButton(
                                            onClick = { onUninstallSkill(skill.id) },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.DeleteOutline,
                                                contentDescription = "Uninstall Skill",
                                                tint = Color(0xFFFF5252),
                                                modifier = Modifier.size(18.dp)
                                            )
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
    }

    if (showAddCustomDialog) {
        AddCustomSkillDialog(
            onDismiss = { showAddCustomDialog = false },
            onAdd = { newSkill ->
                onAddCustomSkill(newSkill)
                showAddCustomDialog = false
            }
        )
    }

    inspectingSkill?.let { skill ->
        SkillFileInspectorDialog(
            skill = skill,
            onToggleSkill = onToggleSkill,
            onInstallSkill = onInstallSkill,
            onUpdateContent = onUpdateSkillContent,
            onFetchContent = onFetchSkillFileContent,
            onDismiss = { inspectingSkill = null }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkillFileInspectorDialog(
    skill: AgentSkill,
    onToggleSkill: (String, Boolean) -> Unit,
    onInstallSkill: (String) -> Unit,
    onUpdateContent: (String, String) -> Unit,
    onFetchContent: (String, ((String) -> Unit)?) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var isEditing by remember { mutableStateOf(false) }
    var contentText by remember { mutableStateOf(skill.skillPrompt) }
    var isLoadingFile by remember { mutableStateOf(false) }

    LaunchedEffect(skill.id) {
        if (skill.skillPrompt.isBlank() || skill.skillPrompt.startsWith("Skill source file") || skill.skillPrompt.startsWith("Skill live fetched")) {
            isLoadingFile = true
            onFetchContent(skill.id) { loaded ->
                contentText = loaded
                isLoadingFile = false
            }
        } else {
            contentText = skill.skillPrompt
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF08090E))
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFF0F131C))
                    .border(BorderStroke(1.dp, Color(0xFF1E2638)), RoundedCornerShape(20.dp))
                    .padding(16.dp)
            ) {
                // Top Header Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF00F2FE).copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Description,
                                contentDescription = null,
                                tint = Color(0xFF00F2FE),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                text = skill.name,
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "File: ${skill.filePath} • ${skill.author}",
                                color = Color(0xFF94A3B8),
                                fontSize = 11.sp
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF1E2638))
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Control Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF161B26))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (skill.isEnabled) Color(0xFF10B981).copy(alpha = 0.2f) else Color(0xFF334155))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (skill.isEnabled) "ACTIVE (ON)" else if (skill.isInstalled) "INSTALLED (OFF)" else "AVAILABLE",
                            color = if (skill.isEnabled) Color(0xFF10B981) else Color(0xFFCBD5E1),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (skill.isInstalled) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = if (skill.isEnabled) "ON" else "OFF",
                                    fontSize = 11.sp,
                                    color = if (skill.isEnabled) Color(0xFF00F2FE) else Color(0xFF64748B),
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(end = 4.dp)
                                )
                                Switch(
                                    checked = skill.isEnabled,
                                    onCheckedChange = { onToggleSkill(skill.id, it) },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color(0xFF0D0E15),
                                        checkedTrackColor = Color(0xFF00F2FE),
                                        uncheckedThumbColor = Color(0xFF94A3B8),
                                        uncheckedTrackColor = Color(0xFF222533)
                                    ),
                                    modifier = Modifier.scale(0.8f)
                                )
                            }
                        } else {
                            Button(
                                onClick = { onInstallSkill(skill.id) },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00F2FE)),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text("Install Skill", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        IconButton(
                            onClick = {
                                val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                val clip = android.content.ClipData.newPlainText("Skill Prompt", contentText)
                                clipboard.setPrimaryClip(clip)
                                android.widget.Toast.makeText(context, "Skill prompt copied!", android.widget.Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy Prompt", tint = Color(0xFF00F2FE), modifier = Modifier.size(16.dp))
                        }

                        IconButton(
                            onClick = {
                                if (isEditing) {
                                    onUpdateContent(skill.id, contentText)
                                    android.widget.Toast.makeText(context, "Skill content saved!", android.widget.Toast.LENGTH_SHORT).show()
                                }
                                isEditing = !isEditing
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = if (isEditing) Icons.Default.Check else Icons.Default.Edit,
                                contentDescription = if (isEditing) "Save" else "Edit",
                                tint = if (isEditing) Color(0xFF10B981) else Color(0xFF94A3B8),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Content Viewer / Editor
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF05070A))
                        .border(BorderStroke(1.dp, Color(0xFF1E2638)), RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    if (isLoadingFile) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(color = Color(0xFF00F2FE), modifier = Modifier.size(28.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Loading skill file from GitHub...", color = Color(0xFF94A3B8), fontSize = 12.sp)
                        }
                    } else if (isEditing) {
                        OutlinedTextField(
                            value = contentText,
                            onValueChange = { contentText = it },
                            modifier = Modifier.fillMaxSize(),
                            textStyle = androidx.compose.ui.text.TextStyle(
                                color = Color(0xFFE2E8F0),
                                fontSize = 12.sp,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent
                            )
                        )
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                        ) {
                            Text(
                                text = if (contentText.isBlank()) "No content in skill file." else contentText,
                                color = Color(0xFFE2E8F0),
                                fontSize = 12.sp,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                lineHeight = 18.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AddCustomSkillDialog(
    onDismiss: () -> Unit,
    onAdd: (AgentSkill) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var author by remember { mutableStateOf("user/custom-skill") }
    var description by remember { mutableStateOf("") }
    var prompt by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF131520)),
            border = BorderStroke(1.dp, Color(0xFF222533)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Upload / Create Custom Skill",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Skill Name", color = Color(0xFF94A3B8)) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF00F2FE),
                        unfocusedBorderColor = Color(0xFF222533),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    singleLine = true
                )

                OutlinedTextField(
                    value = author,
                    onValueChange = { author = it },
                    label = { Text("Author / Repository", color = Color(0xFF94A3B8)) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF00F2FE),
                        unfocusedBorderColor = Color(0xFF222533),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    singleLine = true
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Short Description", color = Color(0xFF94A3B8)) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF00F2FE),
                        unfocusedBorderColor = Color(0xFF222533),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    minLines = 2,
                    maxLines = 3
                )

                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    label = { Text("Skill Prompt / SKILL.md Content", color = Color(0xFF94A3B8)) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF00F2FE),
                        unfocusedBorderColor = Color(0xFF222533),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    minLines = 4,
                    maxLines = 6
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = Color(0xFF94A3B8))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (name.isNotBlank()) {
                                val id = name.lowercase().replace(" ", "-").replace(Regex("[^a-z0-9-]"), "")
                                onAdd(
                                    AgentSkill(
                                        id = if (id.isEmpty()) "custom-skill-${System.currentTimeMillis()}" else id,
                                        name = name.trim(),
                                        author = author.ifBlank { "custom/user" },
                                        installs = "Custom Skill",
                                        description = description.ifBlank { "Custom user-defined skill." },
                                        githubUrl = "https://github.com",
                                        isInstalled = true,
                                        isEnabled = false,
                                        isCustom = true,
                                        skillPrompt = prompt.ifBlank { "Skill $name: Follow user rules." }
                                    )
                                )
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00F2FE)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Add Skill", color = Color(0xFF0D0E15), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
