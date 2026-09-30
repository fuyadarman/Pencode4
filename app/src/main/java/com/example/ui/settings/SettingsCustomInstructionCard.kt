package com.example.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.settings.CustomInstructionEngine
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SettingsCustomInstructionCard(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var instructionText by remember { mutableStateOf("") }
    var isSavedNoticeVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        CustomInstructionEngine.initialize(context)
        instructionText = CustomInstructionEngine.getCustomInstructions(context)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(com.example.ui.theme.AppTheme.bgSurfaceElevated, RoundedCornerShape(8.dp))
            .border(BorderStroke(1.dp, com.example.ui.theme.AppTheme.border), RoundedCornerShape(8.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Psychology,
                    contentDescription = null,
                    tint = com.example.ui.theme.AppTheme.accentBlue,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "Custom AI Instructions",
                    color = com.example.ui.theme.AppTheme.textPrimary,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Text(
            text = "Enter persistent instructions or rules for the AI. These instructions will be included in every AI task.",
            color = com.example.ui.theme.AppTheme.textSecondary,
            fontSize = 11.sp,
            lineHeight = 14.sp
        )

        OutlinedTextField(
            value = instructionText,
            onValueChange = { instructionText = it },
            placeholder = {
                Text(
                    "e.g. Always write code using Jetpack Compose, respond concisely, and format all comments in English...",
                    color = com.example.ui.theme.AppTheme.textMuted,
                    fontSize = 12.sp
                )
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = com.example.ui.theme.AppTheme.textPrimary,
                unfocusedTextColor = com.example.ui.theme.AppTheme.textPrimary,
                focusedBorderColor = com.example.ui.theme.AppTheme.accentBlue,
                unfocusedBorderColor = com.example.ui.theme.AppTheme.border,
                focusedContainerColor = com.example.ui.theme.AppTheme.inputBg,
                unfocusedContainerColor = com.example.ui.theme.AppTheme.inputBg
            ),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 100.dp, max = 180.dp),
            shape = RoundedCornerShape(6.dp),
            maxLines = 8
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isSavedNoticeVisible) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Saved",
                        tint = Color(0xFF3FB950),
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "Instructions saved!",
                        color = Color(0xFF3FB950),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }

            Button(
                onClick = {
                    CustomInstructionEngine.saveCustomInstructions(context, instructionText)
                    isSavedNoticeVisible = true
                    coroutineScope.launch {
                        delay(2500)
                        isSavedNoticeVisible = false
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF238636),
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(6.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                modifier = Modifier.height(32.dp)
            ) {
                Text(
                    text = "Save Instructions",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
