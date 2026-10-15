package com.example.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

/**
 * Dedicated dialog for directly configuring and adding a new AI model provider.
 * Opened directly from the home page "+ Add New Model Configuration" button.
 */
@Composable
fun AddNewModelConfigDialog(
    onDismiss: () -> Unit,
    onAddModel: (alias: String, provider: String, apiKey: String, baseUrl: String, modelId: String) -> Unit,
    scannedModels: List<String> = emptyList(),
    isScanningModels: Boolean = false,
    scanError: String? = null,
    onScanModels: (provider: String, apiKey: String, baseUrl: String) -> Unit = { _, _, _ -> },
    onClearScannedModels: () -> Unit = {}
) {
    var pInput by remember { mutableStateOf("gemini") }
    var keyInput by remember { mutableStateOf("") }
    var baseInput by remember { mutableStateOf("") }
    var modelInput by remember { mutableStateOf("gemini-2.5-flash") }
    var aliasInput by remember { mutableStateOf("") }
    var modelSearchQuery by remember { mutableStateOf("") }
    val uriHandler = LocalUriHandler.current

    val providers = listOf(
        "gemini", "cline", "opencode_zen", "openai", "claude",
        "mistral", "groq", "cohere", "openrouter", "ollama_cloud", "cloudflare", "custom"
    )

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22)),
            border = BorderStroke(1.dp, Color(0xFF30363D)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
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
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFF21262D),
                            border = BorderStroke(1.dp, Color(0xFF30363D)),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Tune,
                                    contentDescription = null,
                                    tint = Color(0xFFF0F6FC),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                text = "New Model Configuration",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFF0F6FC)
                            )
                            Text(
                                text = "Connect custom API key or provider",
                                fontSize = 11.sp,
                                color = Color(0xFF8B949E)
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color(0xFF8B949E),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                HorizontalDivider(color = Color(0xFF21262D))

                // Provider Picker
                Text(
                    text = "Select Provider",
                    color = Color(0xFF8B949E),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    providers.forEach { p ->
                        val isSelected = pInput == p
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) Color(0xFF21262D) else Color(0xFF0D1117))
                                .border(
                                    BorderStroke(1.dp, if (isSelected) Color(0xFFF0F6FC) else Color(0xFF30363D)),
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable {
                                    pInput = p
                                    modelInput = when (p) {
                                        "cline" -> "claude-3-7-sonnet-20250219"
                                        "opencode_zen" -> "opencode-zen"
                                        "gemini" -> "gemini-2.5-flash"
                                        "openai" -> "gpt-4o"
                                        "claude" -> "claude-3-7-sonnet-20250219"
                                        "mistral" -> "mistral-large-latest"
                                        "groq" -> "llama-3.3-70b-versatile"
                                        "cohere" -> "command-r-plus"
                                        "openrouter" -> "google/gemini-2.5-flash"
                                        "ollama_cloud" -> "llama3.3"
                                        "cloudflare" -> "@cf/meta/llama-3.3-70b-instruct"
                                        else -> ""
                                    }
                                    baseInput = when (p) {
                                        "cline" -> "https://api.cline.bot/v1"
                                        "opencode_zen" -> "https://opencode.ai/zen/v1"
                                        "groq" -> "https://api.groq.com/openai"
                                        "cohere" -> "https://api.cohere.com"
                                        "openrouter" -> "https://openrouter.ai/api/v1"
                                        "ollama_cloud" -> "https://api.ollama.com"
                                        "cloudflare" -> "https://api.cloudflare.com/client/v4/accounts/YOUR_ACCOUNT_ID/ai/run"
                                        "custom" -> "https://api.example.com/v1"
                                        else -> ""
                                    }
                                }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            val displayName = when (p) {
                                "cline" -> "Cline"
                                "opencode_zen" -> "OpenCode Zen"
                                "ollama_cloud" -> "Ollama Cloud"
                                "cloudflare" -> "Cloudflare"
                                "cohere" -> "Cohere"
                                "groq" -> "Groq"
                                "openrouter" -> "OpenRouter"
                                else -> p.replaceFirstChar { it.uppercase() }
                            }
                            Text(
                                text = displayName,
                                color = if (isSelected) Color(0xFFF0F6FC) else Color(0xFF8B949E),
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }

                // Base URL if needed
                if (pInput in setOf("custom", "cline", "opencode_zen", "groq", "cohere", "openrouter", "ollama_cloud", "cloudflare")) {
                    OutlinedTextField(
                        value = baseInput,
                        onValueChange = { baseInput = it },
                        label = { Text("API Base URL") },
                        placeholder = { Text("e.g. https://api.groq.com/openai/v1") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color(0xFFF0F6FC),
                            unfocusedTextColor = Color(0xFFF0F6FC),
                            focusedBorderColor = Color(0xFFF0F6FC),
                            unfocusedBorderColor = Color(0xFF30363D)
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )
                }

                // Model ID
                OutlinedTextField(
                    value = modelInput,
                    onValueChange = { modelInput = it },
                    label = { Text("Model ID") },
                    placeholder = { Text("e.g. gemini-2.5-flash or gpt-4o") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color(0xFFF0F6FC),
                        unfocusedTextColor = Color(0xFFF0F6FC),
                        focusedBorderColor = Color(0xFFF0F6FC),
                        unfocusedBorderColor = Color(0xFF30363D)
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                // API Key
                OutlinedTextField(
                    value = keyInput,
                    onValueChange = { keyInput = it },
                    label = { Text("API Key") },
                    placeholder = { Text("Paste your API key here") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color(0xFFF0F6FC),
                        unfocusedTextColor = Color(0xFFF0F6FC),
                        focusedBorderColor = Color(0xFFF0F6FC),
                        unfocusedBorderColor = Color(0xFF30363D)
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                // Link to generate key
                val createKeyUrl = when (pInput) {
                    "cline" -> "https://openrouter.ai/keys"
                    "opencode_zen" -> "https://opencode.ai/zen"
                    "gemini" -> "https://aistudio.google.com/app/apikey"
                    "openai" -> "https://platform.openai.com/api-keys"
                    "claude" -> "https://console.anthropic.com/settings/keys"
                    "mistral" -> "https://console.mistral.ai/api-keys/"
                    "groq" -> "https://console.groq.com/keys"
                    "cohere" -> "https://dashboard.cohere.com/api-keys"
                    "openrouter" -> "https://openrouter.ai/keys"
                    "cloudflare" -> "https://dash.cloudflare.com/profile/api-tokens"
                    "ollama_cloud" -> "https://ollama.com"
                    else -> "https://aistudio.google.com/app/apikey"
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (keyInput.isBlank()) "Don't have an API key?" else "Key ready. Need another?",
                        color = Color(0xFF8B949E),
                        fontSize = 11.sp
                    )
                    OutlinedButton(
                        onClick = {
                            try {
                                uriHandler.openUri(createKeyUrl)
                            } catch (_: Exception) {}
                        },
                        border = BorderStroke(1.dp, Color(0xFF30363D)),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.OpenInNew,
                            contentDescription = null,
                            tint = Color(0xFFF0F6FC),
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Get API Key",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFFF0F6FC)
                        )
                    }
                }

                // Scan Models button
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = {
                            onScanModels(pInput, keyInput, baseInput)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF21262D),
                            contentColor = Color(0xFFF0F6FC)
                        ),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, Color(0xFF30363D)),
                        modifier = Modifier.weight(1f).height(38.dp)
                    ) {
                        if (isScanningModels) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                color = Color(0xFFF0F6FC),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Scanning...", fontSize = 11.5.sp)
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Scan Models", fontSize = 11.5.sp)
                        }
                    }

                    if (scannedModels.isNotEmpty()) {
                        OutlinedButton(
                            onClick = onClearScannedModels,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF8B949E)),
                            border = BorderStroke(1.dp, Color(0xFF30363D)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(38.dp)
                        ) {
                            Text("Clear", fontSize = 11.5.sp)
                        }
                    }
                }

                if (scanError != null) {
                    Text(
                        text = scanError,
                        color = Color(0xFFF85149),
                        fontSize = 11.sp,
                        lineHeight = 14.sp
                    )
                }

                if (scannedModels.isNotEmpty()) {
                    val filteredModels = remember(scannedModels, modelSearchQuery) {
                        if (modelSearchQuery.isBlank()) scannedModels
                        else scannedModels.filter { it.contains(modelSearchQuery.trim(), ignoreCase = true) }
                    }

                    OutlinedTextField(
                        value = modelSearchQuery,
                        onValueChange = { modelSearchQuery = it },
                        placeholder = { Text("Filter ${scannedModels.size} available models...", fontSize = 11.sp, color = Color(0xFF8B949E)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color(0xFFF0F6FC),
                            unfocusedTextColor = Color(0xFFF0F6FC),
                            focusedBorderColor = Color(0xFF30363D),
                            unfocusedBorderColor = Color(0xFF21262D)
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        trailingIcon = {
                            if (modelSearchQuery.isNotEmpty()) {
                                IconButton(onClick = { modelSearchQuery = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear", tint = Color(0xFF8B949E), modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 130.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        filteredModels.take(20).forEach { modelName ->
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFF21262D),
                                border = BorderStroke(1.dp, Color(0xFF30363D)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { modelInput = modelName }
                            ) {
                                Text(
                                    text = modelName,
                                    fontSize = 11.sp,
                                    color = Color(0xFFF0F6FC),
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }

                // Friendly Name
                OutlinedTextField(
                    value = aliasInput,
                    onValueChange = { aliasInput = it },
                    label = { Text("Friendly Name (Optional)") },
                    placeholder = { Text("e.g. My Fast Flash") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color(0xFFF0F6FC),
                        unfocusedTextColor = Color(0xFFF0F6FC),
                        focusedBorderColor = Color(0xFFF0F6FC),
                        unfocusedBorderColor = Color(0xFF30363D)
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Actions: Cancel & Save
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        border = BorderStroke(1.dp, Color(0xFF30363D)),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF8B949E)),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                    ) {
                        Text("Cancel")
                    }

                    Button(
                        onClick = {
                            if (modelInput.isNotBlank() && keyInput.isNotBlank()) {
                                val finalAlias = if (aliasInput.isBlank()) {
                                    modelInput.split("-", "_", ".").joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } }
                                } else {
                                    aliasInput
                                }
                                onAddModel(finalAlias, pInput, keyInput, baseInput, modelInput)
                                onDismiss()
                            }
                        },
                        enabled = modelInput.isNotBlank() && keyInput.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF238636),
                            contentColor = Color.White,
                            disabledContainerColor = Color(0xFF21262D),
                            disabledContentColor = Color(0xFF484F58)
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Save & Activate", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}
