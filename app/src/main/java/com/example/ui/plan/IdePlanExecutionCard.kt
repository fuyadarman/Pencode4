package com.example.ui.plan

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.plan.PlanFeatureSurvey
import com.example.plan.SurveyFeatureItem

/**
 * IdePlanExecutionCard
 *
 * Pixel-perfect professional IDE style Plan Execution Card matching Cursor / VS Code.
 * Features:
 * - Top tab header: PLAN: <TITLE> with blue indicator
 * - Segmented progress bars for steps
 * - Active focus highlight card with "Generate Code ⌄" action
 * - Compact, non-intrusive height with smooth scrolling
 * - Cancel / Dismiss capability
 */
@Composable
fun IdePlanExecutionCard(
    survey: PlanFeatureSurvey,
    onExecutePlan: (List<SurveyFeatureItem>, String) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedFeatureIds by remember(survey) {
        mutableStateOf(survey.suggestedFeatures.filter { it.isSelected }.map { it.id }.toSet())
    }
    var currentFocusIndex by remember { mutableStateOf(1) } // 0-indexed or 1st in-progress step
    var showFeatureSurveyDropdown by remember { mutableStateOf(false) }

    // Normalize steps from survey or features
    val steps = remember(survey) {
        if (survey.steps.isNotEmpty()) {
            survey.steps
        } else if (survey.suggestedFeatures.isNotEmpty()) {
            survey.suggestedFeatures.map { it.title }
        } else {
            listOf(
                "Setup project architecture & dependencies",
                "Create data models & repository",
                "Implement UI screens & navigation",
                "Connect state & interactive logic",
                "Final polish, theme & verification"
            )
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 4.dp),
        shape = RoundedCornerShape(10.dp),
        color = Color(0xFF11141A),
        border = BorderStroke(1.dp, Color(0xFF282E3D)),
        tonalElevation = 4.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // 1. IDE Top Tab Bar: [ PLAN: <TITLE> ] [ X ]
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0E1015))
                    .padding(start = 12.dp, end = 6.dp, top = 2.dp, bottom = 0.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Active Tab pill
                Column {
                    Box(
                        modifier = Modifier
                            .height(2.dp)
                            .width(110.dp)
                            .background(Color(0xFF388BFD))
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(vertical = 8.dp)
                    ) {
                        Text(
                            text = "PLAN: ${survey.title.uppercase().take(28)}",
                            color = Color(0xFFE6EDF3),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 0.5.sp
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (survey.suggestedFeatures.isNotEmpty()) {
                        IconButton(
                            onClick = { showFeatureSurveyDropdown = !showFeatureSurveyDropdown },
                            modifier = Modifier.size(26.dp)
                        ) {
                            Icon(
                                imageVector = if (showFeatureSurveyDropdown) Icons.Default.ChecklistRtl else Icons.Default.Checklist,
                                contentDescription = "Feature Checklist",
                                tint = if (showFeatureSurveyDropdown) Color(0xFF388BFD) else Color(0xFF8B949E),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    IconButton(
                        onClick = onCancel,
                        modifier = Modifier.size(26.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Plan",
                            tint = Color(0xFF8B949E),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            HorizontalDivider(color = Color(0xFF21262D), thickness = 1.dp)

            // 2. Scrollable Steps Body (Restricted height so it never blocks the app screen)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 240.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // If Feature survey dropdown is open, show selectable features
                if (showFeatureSurveyDropdown && survey.suggestedFeatures.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF161B22), RoundedCornerShape(6.dp))
                            .padding(8.dp)
                    ) {
                        Text(
                            text = "SELECT FEATURES TO INCLUDE:",
                            color = Color(0xFF7EE787),
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        survey.suggestedFeatures.forEach { feat ->
                            val isChecked = selectedFeatureIds.contains(feat.id)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedFeatureIds = if (isChecked) selectedFeatureIds - feat.id else selectedFeatureIds + feat.id
                                    }
                                    .padding(vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = if (isChecked) "[✓]" else "[ ]",
                                    color = if (isChecked) Color(0xFF3FB950) else Color(0xFF8B949E),
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = feat.title,
                                    color = if (isChecked) Color.White else Color(0xFF8B949E),
                                    fontSize = 11.5.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }

                // Steps list matching screenshot
                steps.forEachIndexed { index, stepText ->
                    val stepNum = index + 1
                    val isCompleted = stepNum < currentFocusIndex + 1
                    val isFocus = stepNum == currentFocusIndex + 1
                    val isPending = stepNum > currentFocusIndex + 1

                    if (isFocus) {
                        // Focused Step with Highlight Border and "Generate Code" action button
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFF141923),
                            border = BorderStroke(1.2.dp, Color(0xFF1F6FEB)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(
                                            text = "➤ • $stepNum. [~]",
                                            color = Color(0xFF58A6FF),
                                            fontSize = 12.sp,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "$stepText (Current Focus)",
                                            color = Color(0xFFF0F6FC),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    // Crisp blue "Generate Code ⌄" button
                                    Button(
                                        onClick = {
                                            val chosen = survey.suggestedFeatures.filter { selectedFeatureIds.contains(it.id) }
                                            onExecutePlan(chosen, "")
                                        },
                                        shape = RoundedCornerShape(4.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1F6FEB)),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                        modifier = Modifier.height(28.dp)
                                    ) {
                                        Text(
                                            text = "Generate Code",
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Icon(
                                            imageVector = Icons.Default.KeyboardArrowDown,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(13.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))
                                // Segmented progress bar (in-progress state: 2 filled, 2 pending)
                                SegmentedProgressBar(filledSegments = 2, totalSegments = 4, activeColor = Color(0xFF58A6FF))
                            }
                        }
                    } else {
                        // Non-focus step (Completed or Pending)
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { currentFocusIndex = index }
                                .padding(vertical = 3.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = if (isCompleted) "• $stepNum. [✓]" else "• $stepNum. [ ]",
                                    color = if (isCompleted) Color(0xFF3FB950) else Color(0xFF8B949E),
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = stepText,
                                    color = if (isCompleted) Color(0xFFE6EDF3) else Color(0xFF8B949E),
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Spacer(modifier = Modifier.height(4.dp))
                            // Segmented Progress Bar
                            SegmentedProgressBar(
                                filledSegments = if (isCompleted) 4 else 0,
                                totalSegments = 4,
                                activeColor = Color(0xFF3FB950)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * SegmentedProgressBar
 *
 * Renders the 4 segmented pill bars matching the user's screenshot.
 */
@Composable
private fun SegmentedProgressBar(
    filledSegments: Int,
    totalSegments: Int = 4,
    activeColor: Color = Color(0xFF3FB950),
    inactiveColor: Color = Color(0xFF21262D)
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 14.dp, end = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        for (i in 0 until totalSegments) {
            val isFilled = i < filledSegments
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(5.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(if (isFilled) activeColor else inactiveColor)
            )
        }
    }
}
