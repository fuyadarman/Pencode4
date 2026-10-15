package com.example.ui.plan

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.example.plan.PlanFeatureSurvey
import com.example.plan.SurveyFeatureItem

/**
 * PlanSurveyBox
 *
 * Interactive Survey Box displayed when the AI generates a Plan and Feature suggestions.
 * Allows the user to select/deselect features, continue execution, or cancel the survey.
 */
@Composable
fun PlanSurveyBox(
    survey: PlanFeatureSurvey,
    onContinue: (List<SurveyFeatureItem>, String) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedFeatureIds by remember(survey) {
        mutableStateOf(survey.suggestedFeatures.filter { it.isSelected }.map { it.id }.toSet())
    }
    var showCustomInput by remember { mutableStateOf(false) }
    var customInstructionText by remember { mutableStateOf("") }
    var isExpanded by remember { mutableStateOf(true) }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF1E222D),
        border = BorderStroke(1.5.dp, Color(0xFF3B82F6)),
        tonalElevation = 6.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // Header: Title + Plan Badge + Collapse toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF2563EB).copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Ballot,
                            contentDescription = "Plan & Survey",
                            tint = Color(0xFF60A5FA),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = survey.title,
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color(0xFF2563EB).copy(alpha = 0.3f),
                                border = BorderStroke(0.5.dp, Color(0xFF60A5FA))
                            ) {
                                Text(
                                    text = "Plan Mode",
                                    color = Color(0xFF93C5FD),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                        Text(
                            text = "Select features to include before code generation",
                            color = Color(0xFF94A3B8),
                            fontSize = 11.5.sp
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { isExpanded = !isExpanded },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = "Expand/Collapse",
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(
                        onClick = onCancel,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Dismiss Survey",
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
                    // Plan Overview
                    if (survey.overviewPlan.isNotBlank()) {
                        Text(
                            text = survey.overviewPlan,
                            color = Color(0xFFCBD5E1),
                            fontSize = 12.5.sp,
                            lineHeight = 17.sp,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }

                    // Steps Roadmap (if any)
                    if (survey.steps.isNotEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF141720), RoundedCornerShape(8.dp))
                                .padding(8.dp)
                        ) {
                            Text(
                                text = "Implementation Steps:",
                                color = Color(0xFFA5B4FC),
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                            survey.steps.forEach { step ->
                                Row(
                                    verticalAlignment = Alignment.Top,
                                    modifier = Modifier.padding(vertical = 1.dp)
                                ) {
                                    Text("• ", color = Color(0xFF818CF8), fontSize = 11.5.sp)
                                    Text(step, color = Color(0xFFE2E8F0), fontSize = 11.5.sp)
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    // Feature Survey Checklist Header
                    if (survey.suggestedFeatures.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Feature Survey (${selectedFeatureIds.size}/${survey.suggestedFeatures.size} Selected):",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = if (selectedFeatureIds.size == survey.suggestedFeatures.size) "Deselect All" else "Select All",
                                color = Color(0xFF60A5FA),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.clickable {
                                    selectedFeatureIds = if (selectedFeatureIds.size == survey.suggestedFeatures.size) {
                                        emptySet()
                                    } else {
                                        survey.suggestedFeatures.map { it.id }.toSet()
                                    }
                                }
                            )
                        }

                        // Feature Items List
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            survey.suggestedFeatures.forEach { feature ->
                                val isChecked = selectedFeatureIds.contains(feature.id)
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isChecked) Color(0xFF252B3B) else Color(0xFF171A23),
                                    border = BorderStroke(
                                        1.dp,
                                        if (isChecked) Color(0xFF3B82F6).copy(alpha = 0.6f) else Color(0xFF2C3240)
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selectedFeatureIds = if (isChecked) {
                                                selectedFeatureIds - feature.id
                                            } else {
                                                selectedFeatureIds + feature.id
                                            }
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Checkbox(
                                            checked = isChecked,
                                            onCheckedChange = { checked ->
                                                selectedFeatureIds = if (checked) {
                                                    selectedFeatureIds + feature.id
                                                } else {
                                                    selectedFeatureIds - feature.id
                                                }
                                            },
                                            colors = CheckboxDefaults.colors(
                                                checkedColor = Color(0xFF3B82F6),
                                                uncheckedColor = Color(0xFF64748B),
                                                checkmarkColor = Color.White
                                            ),
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = feature.title,
                                                color = if (isChecked) Color.White else Color(0xFF94A3B8),
                                                fontSize = 12.5.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            if (feature.description.isNotBlank()) {
                                                Text(
                                                    text = feature.description,
                                                    color = Color(0xFF64748B),
                                                    fontSize = 11.sp,
                                                    lineHeight = 14.sp
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Optional Custom Instruction Row
                    AnimatedVisibility(visible = showCustomInput) {
                        OutlinedTextField(
                            value = customInstructionText,
                            onValueChange = { customInstructionText = it },
                            placeholder = { Text("Add custom preferences or instructions...", color = Color(0xFF64748B), fontSize = 12.sp) },
                            textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 12.sp),
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            shape = RoundedCornerShape(8.dp),
                            maxLines = 3
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Action Buttons Row: [Cancel] [+ Note] [Continue / Execute Plan ->]
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(
                                onClick = onCancel,
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, Color(0xFF374151)),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Text("Cancel", color = Color(0xFF94A3B8), fontSize = 12.sp)
                            }

                            TextButton(
                                onClick = { showCustomInput = !showCustomInput },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Icon(
                                    imageVector = if (showCustomInput) Icons.Default.Close else Icons.Default.EditNote,
                                    contentDescription = null,
                                    tint = Color(0xFF60A5FA),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    if (showCustomInput) "Hide Note" else "+ Note",
                                    color = Color(0xFF60A5FA),
                                    fontSize = 12.sp
                                )
                            }
                        }

                        Button(
                            onClick = {
                                val chosenFeatures = survey.suggestedFeatures.filter { selectedFeatureIds.contains(it.id) }
                                onContinue(chosenFeatures, customInstructionText)
                            },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Continue (${selectedFeatureIds.size} Feats)",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}
