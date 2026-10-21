package com.example.ui.chat

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
import com.example.auth.github.GitHubModelsService

@Composable
fun GitHubModelsDialog(
    isOpen: Boolean,
    currentSelectedModel: String,
    onDismiss: () -> Unit,
    onModelSelected: (modelId: String, provider: String, baseUrl: String, apiKey: String) -> Unit,
    onOpenConnectGitHub: () -> Unit
) {
    if (!isOpen) return

    val context = LocalContext.current
    val authManager = remember { GitHubAuthManager.getInstance(context) }
    val authState by authManager.authState.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }
    var customModelInput by remember { mutableStateOf("") }
    val categories = listOf("All", "NVIDIA", "OpenAI", "DeepSeek", "Meta", "Mistral", "Microsoft", "Reasoning", "Coding")

    val models = remember(selectedCategory, searchQuery) {
        val baseList = when (selectedCategory) {
            "All" -> GitHubModelsService.AVAILABLE_MODELS
            "NVIDIA", "OpenAI", "DeepSeek", "Meta", "Mistral", "Microsoft" ->
                GitHubModelsService.AVAILABLE_MODELS.filter { it.publisher.contains(selectedCategory, ignoreCase = true) }
            else ->
                GitHubModelsService.AVAILABLE_MODELS.filter { it.category.equals(selectedCategory, ignoreCase = true) }
        }
        if (searchQuery.isBlank()) {
            baseList
        } else {
            baseList.filter {
                it.id.contains(searchQuery, ignoreCase = true) ||
                it.name.contains(searchQuery, ignoreCase = true) ||
                it.publisher.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.96f)
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
                            modifier = Modifier.size(34.dp),
                            shape = CircleShape,
                            color = Color(0xFF24292E)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(18.dp))
                            }
                        }
                        Column {
                            Text(
                                text = "GitHub AI Models",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (authState.isConnected) "@${authState.username} (Authorized)" else "GitHub Account Not Connected",
                                color = if (authState.isConnected) Color(0xFF34D399) else Color(0xFFF87171),
                                fontSize = 11.sp
                            )
                        }
                    }

                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF94A3B8), modifier = Modifier.size(18.dp))
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Connect banner if not connected
                if (!authState.isConnected) {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E2235)),
                        border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Default.WarningAmber, contentDescription = null, tint = Color(0xFFFBBF24), modifier = Modifier.size(20.dp))
                            Text(
                                text = "Connect your GitHub account to enable free access to GitHub Models.",
                                color = Color(0xFFE2E8F0),
                                fontSize = 11.sp,
                                modifier = Modifier.weight(1f)
                            )
                            Button(
                                onClick = {
                                    onDismiss()
                                    onOpenConnectGitHub()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF24292E)),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text("Connect", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // Search Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search 30+ GitHub AI models (nemotron, gpt-4o, llama...)", fontSize = 11.sp, color = Color(0xFF64748B)) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFF64748B), modifier = Modifier.size(16.dp)) },
                    trailingIcon = {
                        if (searchQuery.isNotBlank()) {
                            IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "Clear", tint = Color(0xFF94A3B8), modifier = Modifier.size(14.dp))
                            }
                        }
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF38BDF8),
                        unfocusedBorderColor = Color(0xFF2E3248),
                        focusedContainerColor = Color(0xFF1A1D2D),
                        unfocusedContainerColor = Color(0xFF1A1D2D)
                    ),
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(10.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Custom Model Entry (Allows using ANY custom model name directly)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedTextField(
                        value = customModelInput,
                        onValueChange = { customModelInput = it },
                        placeholder = { Text("Or type any model ID (e.g. nemotron-3-ultra)", fontSize = 11.sp, color = Color(0xFF64748B)) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF38BDF8),
                            unfocusedBorderColor = Color(0xFF2E3248),
                            focusedContainerColor = Color(0xFF1A1D2D),
                            unfocusedContainerColor = Color(0xFF1A1D2D)
                        ),
                        modifier = Modifier.weight(1f).height(44.dp),
                        shape = RoundedCornerShape(8.dp)
                    )
                    Button(
                        onClick = {
                            val targetId = customModelInput.trim()
                            if (targetId.isNotBlank()) {
                                onModelSelected(
                                    targetId,
                                    GitHubModelsService.GITHUB_MODELS_PROVIDER,
                                    GitHubModelsService.GITHUB_MODELS_BASE_URL,
                                    authState.accessToken
                                )
                                onDismiss()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(44.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp)
                    ) {
                        Text("Use", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Category chips with horizontal scroll
                androidx.compose.foundation.lazy.LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(categories) { cat ->
                        val isSelected = selectedCategory == cat
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (isSelected) Color(0xFF38BDF8) else Color(0xFF1A1D2D),
                            border = BorderStroke(1.dp, if (isSelected) Color(0xFF38BDF8) else Color(0xFF2E3248)),
                            modifier = Modifier.clickable { selectedCategory = cat }
                        ) {
                            Text(
                                text = cat,
                                color = if (isSelected) Color.Black else Color(0xFF94A3B8),
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Models list
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(models) { model ->
                        val isSelected = currentSelectedModel.equals(model.id, ignoreCase = true)

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
                                    val token = authState.accessToken
                                    onModelSelected(
                                        model.id,
                                        GitHubModelsService.GITHUB_MODELS_PROVIDER,
                                        GitHubModelsService.GITHUB_MODELS_BASE_URL,
                                        token
                                    )
                                    onDismiss()
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Surface(
                                    modifier = Modifier.size(34.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFF252A40)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = model.publisher.take(2).uppercase(),
                                            color = Color(0xFF38BDF8),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp
                                        )
                                    }
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = model.name,
                                            color = Color.White,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = Color(0xFF334155)
                                        ) {
                                            Text(
                                                text = model.category,
                                                color = Color(0xFF94A3B8),
                                                fontSize = 9.sp,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                            )
                                        }
                                        Text(
                                            text = model.publisher,
                                            color = Color(0xFF64748B),
                                            fontSize = 10.sp
                                        )
                                    }

                                    Text(
                                        text = model.description,
                                        color = Color(0xFF94A3B8),
                                        fontSize = 11.sp,
                                        maxLines = 2
                                    )
                                }

                                if (isSelected) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = "Active", tint = Color(0xFF38BDF8), modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
