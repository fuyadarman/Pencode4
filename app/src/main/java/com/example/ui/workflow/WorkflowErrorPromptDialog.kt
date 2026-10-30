package com.example.ui.workflow

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.workflow.BuildTriggerHelper

/**
 * WorkflowErrorPromptDialog
 * 
 * Presents workflow execution errors in a professional modal with explicit "Allow" and "Deny"
 * buttons as required by RULE[AGENTS_md].
 */
@Composable
fun WorkflowErrorPromptDialog() {
    val prompt by BuildTriggerHelper.activeErrorPrompt.collectAsState()

    prompt?.let { currentPrompt ->
        Dialog(onDismissRequest = { currentPrompt.onDeny() }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, Color(0xFF30363D), RoundedCornerShape(12.dp)),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22))
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Workflow Issue",
                            tint = Color(0xFFD29922),
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = "Workflow Action Required",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFF0F6FC)
                        )
                    }

                    Text(
                        text = "Workflow '${currentPrompt.workflowName}' encountered an issue while running:",
                        fontSize = 13.sp,
                        color = Color(0xFF8B949E)
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF0D1117))
                            .border(1.dp, Color(0xFF21262D), RoundedCornerShape(8.dp))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = currentPrompt.errorMessage,
                            fontSize = 12.sp,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            color = Color(0xFFE6EDF3)
                        )
                    }

                    Text(
                        text = "Would you like AI Assistant to analyze and proceed with troubleshooting?",
                        fontSize = 12.sp,
                        color = Color(0xFF8B949E)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = { currentPrompt.onDeny() },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF8B949E)),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF30363D)),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text("Deny", fontSize = 12.sp)
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Button(
                            onClick = { currentPrompt.onAllow() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF238636),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text("Allow", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}
