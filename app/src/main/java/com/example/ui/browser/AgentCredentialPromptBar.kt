package com.example.ui.browser

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.browser.AgentCredentialRequest
import com.example.browser.AgentCredentialRequestManager

/**
 * Interactive Credential Request Bar shown when AI Agent needs user login/checkout data.
 */
@Composable
fun AgentCredentialPromptBar(
    modifier: Modifier = Modifier
) {
    val activeRequest by AgentCredentialRequestManager.activeRequest.collectAsState()

    AnimatedVisibility(
        visible = activeRequest != null,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
        modifier = modifier
    ) {
        activeRequest?.let { request ->
            AgentCredentialContent(request = request)
        }
    }
}

@Composable
private fun AgentCredentialContent(request: AgentCredentialRequest) {
    val fieldValues = remember(request.requestId) {
        mutableStateMapOf<String, String>().apply {
            request.fields.forEach { field ->
                put(field.id, field.defaultValue)
            }
        }
    }

    val passwordVisibility = remember(request.requestId) {
        mutableStateMapOf<String, Boolean>()
    }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22)),
        border = BorderStroke(1.5.dp, Color(0xFF38BDF8)),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF38BDF8).copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Security",
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(18.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = request.title.ifBlank { "AI Agent Login Required" },
                        color = Color.White,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = request.description.ifBlank { "Please provide login credentials for AI agent to continue task." },
                        color = Color(0xFF8B949E),
                        fontSize = 11.sp,
                        lineHeight = 14.sp
                    )
                }

                IconButton(
                    onClick = { AgentCredentialRequestManager.dismiss(request.requestId) },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Cancel",
                        tint = Color(0xFF8B949E),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Input Fields
            request.fields.forEach { field ->
                val currentValue = fieldValues[field.id] ?: ""
                val isVisible = passwordVisibility[field.id] ?: false

                OutlinedTextField(
                    value = currentValue,
                    onValueChange = { fieldValues[field.id] = it },
                    label = { Text(field.label, fontSize = 11.sp) },
                    placeholder = {
                        Text(
                            field.placeholder.ifBlank { "Enter ${field.label}" },
                            fontSize = 11.sp,
                            color = Color(0xFF6E7681)
                        )
                    },
                    singleLine = true,
                    visualTransformation = if (field.isSecret && !isVisible) PasswordVisualTransformation() else VisualTransformation.None,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = if (field.isSecret) KeyboardType.Password else KeyboardType.Text,
                        imeAction = ImeAction.Next
                    ),
                    trailingIcon = {
                        if (field.isSecret) {
                            IconButton(
                                onClick = { passwordVisibility[field.id] = !isVisible },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = if (isVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = "Toggle password visibility",
                                    tint = Color(0xFF8B949E),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF38BDF8),
                        unfocusedBorderColor = Color(0xFF30363D),
                        focusedContainerColor = Color(0xFF0D1117),
                        unfocusedContainerColor = Color(0xFF0D1117)
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = { AgentCredentialRequestManager.dismiss(request.requestId) },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF8B949E))
                ) {
                    Text("Skip", fontSize = 12.sp)
                }

                Spacer(modifier = Modifier.width(6.dp))

                Button(
                    onClick = {
                        AgentCredentialRequestManager.submitCredentials(
                            request.requestId,
                            fieldValues.toMap()
                        )
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF238636),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Authorize AI", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
