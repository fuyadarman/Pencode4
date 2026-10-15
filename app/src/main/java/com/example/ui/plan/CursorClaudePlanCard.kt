package com.example.ui.plan

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.plan.PlanFeatureSurvey
import com.example.plan.SurveyFeatureItem

/**
 * CursorClaudePlanCard
 *
 * Pixel-perfect implementation of the Cursor / Claude Code Plan Mode Card as shown
 * in the reference screenshot. Features:
 * - Top header with plan filename (.plan.md) and action icons
 * - Bold title and overview description
 * - Dark inner card with "N To-dos" and circular checklist items
 * - Collapsible / expandable "... X more"
 * - Action footer with "View Plan", active model badge, and the gold "Build ⌘↩" button
 */
@Composable
fun CursorClaudePlanCard(
    survey: PlanFeatureSurvey,
    onExecutePlan: (List<SurveyFeatureItem>, String) -> Unit,
    onCancelPlan: () -> Unit = {},
    onViewPlan: () -> Unit = {},
    activeModelName: String = "Sonnet 4.5",
    modifier: Modifier = Modifier
) {
    val clipboardManager = LocalClipboardManager.current
    var isExpanded by remember { mutableStateOf(false) }
    var isExecuted by remember { mutableStateOf(false) }
    // Prepare to-do items from features and steps
    val todoItems = remember(survey) {
        if (survey.suggestedFeatures.isNotEmpty()) {
            survey.suggestedFeatures
        } else if (survey.steps.isNotEmpty()) {
            survey.steps.mapIndexed { idx, s ->
                SurveyFeatureItem(id = "step_$idx", title = s, description = "", isSelected = true)
            }
        } else {
            listOf(
                SurveyFeatureItem("1", "Setup project architecture & dependencies", "", true),
                SurveyFeatureItem("2", "Create models & state logic", "", true),
                SurveyFeatureItem("3", "Implement UI screens & navigation", "", true)
            )
        }
    }

    var selectedFeatureIds by remember(survey, todoItems) {
        mutableStateOf(todoItems.filter { it.isSelected }.map { it.id }.toSet())
    }
    var showFullPlanDialog by remember { mutableStateOf(false) }

    // Derive slug filename e.g. "custom-fields-implementation.plan.md"
    val planFileName = remember(survey.title) {
        val cleanSlug = survey.title.lowercase()
            .replace(Regex("""[^a-z0-9]+"""), "-")
            .trim('-')
            .ifBlank { "implementation" }
        "$cleanSlug.plan.md"
    }

    val visibleLimit = 3
    val hasMore = todoItems.size > visibleLimit
    val displayedItems = if (isExpanded || !hasMore) todoItems else todoItems.take(visibleLimit)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF181A20),
        border = BorderStroke(1.dp, Color(0xFF2B303C))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. Top Header Row: [ -o- filename.plan.md ] [ Download ] [ Expand ]
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
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = "Plan File",
                        tint = Color(0xFF8B949E),
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = planFileName,
                        color = Color(0xFF8B949E),
                        fontSize = 12.5.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(
                        onClick = {
                            val planMarkdown = buildString {
                                appendLine("# ${survey.title}")
                                appendLine()
                                appendLine(survey.overviewPlan)
                                appendLine()
                                appendLine("## To-dos")
                                todoItems.forEach { appendLine("- [ ] ${it.title}") }
                            }
                            clipboardManager.setText(AnnotatedString(planMarkdown))
                        },
                        modifier = Modifier.size(26.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FileDownload,
                            contentDescription = "Export Plan",
                            tint = Color(0xFF8B949E),
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    IconButton(
                        onClick = { isExpanded = !isExpanded },
                        modifier = Modifier.size(26.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.UnfoldMore,
                            contentDescription = "Toggle To-dos",
                            tint = Color(0xFF8B949E),
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    if (!isExecuted) {
                        IconButton(
                            onClick = onCancelPlan,
                            modifier = Modifier.size(26.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Cancel Plan",
                                tint = Color(0xFF8B949E),
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }
            }

            // 2. Large Bold Title
            Text(
                text = survey.title,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFF0F6FC),
                lineHeight = 24.sp
            )

            // 3. Description
            if (survey.overviewPlan.isNotBlank()) {
                Text(
                    text = survey.overviewPlan,
                    fontSize = 13.sp,
                    color = Color(0xFF94A3B8),
                    lineHeight = 19.sp
                )
            }

            // 4. Nested To-dos Card
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                color = Color(0xFF11141A),
                border = BorderStroke(1.dp, Color(0xFF232834))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // "N To-dos"
                    Text(
                        text = "${todoItems.size} To-dos",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF8B949E)
                    )

                    // To-do checklist items
                    displayedItems.forEach { item ->
                        val isSelected = selectedFeatureIds.contains(item.id)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedFeatureIds = if (isSelected) {
                                        selectedFeatureIds - item.id
                                    } else {
                                        selectedFeatureIds + item.id
                                    }
                                },
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Circular Checkbox Indicator
                            Box(
                                modifier = Modifier
                                    .padding(top = 2.dp)
                                    .size(15.dp)
                                    .clip(CircleShape)
                                    .background(if (isSelected) Color(0xFFF59E0B).copy(alpha = 0.2f) else Color.Transparent)
                                    .padding(1.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isSelected) Icons.Outlined.CheckCircle else Icons.Outlined.Circle,
                                    contentDescription = null,
                                    tint = if (isSelected) Color(0xFFF59E0B) else Color(0xFF6E7681),
                                    modifier = Modifier.size(14.dp)
                                )
                            }

                            Text(
                                text = item.title,
                                fontSize = 13.sp,
                                color = if (isSelected) Color(0xFFE6EDF3) else Color(0xFF8B949E),
                                lineHeight = 18.sp
                            )
                        }
                    }

                    // "... X more" toggle
                    if (hasMore && !isExpanded) {
                        Text(
                            text = "... ${todoItems.size - visibleLimit} more",
                            color = Color(0xFF8B949E),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier
                                .clickable { isExpanded = true }
                                .padding(vertical = 2.dp)
                        )
                    } else if (hasMore && isExpanded) {
                        Text(
                            text = "Show less",
                            color = Color(0xFF60A5FA),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier
                                .clickable { isExpanded = false }
                                .padding(vertical = 2.dp)
                        )
                    }
                }
            }

            // 5. Action Footer: [ View Plan ] [ Model ▾ ] [ Build ⌘↩ ▾ ]
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // View Plan link
                Text(
                    text = "View Plan",
                    color = Color(0xFF8B949E),
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clickable { showFullPlanDialog = true }
                        .padding(vertical = 4.dp)
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Gold / Amber "Build ⌘↩" Action Button (switches to non-clickable "Plan Executed ✓")
                    Button(
                        onClick = {
                            if (!isExecuted) {
                                isExecuted = true
                                val chosenFeatures = todoItems.filter { selectedFeatureIds.contains(it.id) }
                                onExecutePlan(chosenFeatures, "")
                            }
                        },
                        enabled = !isExecuted,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isExecuted) Color(0xFF222734) else Color(0xFFF59E0B),
                            contentColor = if (isExecuted) Color(0xFF94A3B8) else Color(0xFF0F172A),
                            disabledContainerColor = Color(0xFF222734),
                            disabledContentColor = Color(0xFF94A3B8)
                        ),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Text(
                            text = if (isExecuted) "Plan Executed ✓" else "Build ⌘↩",
                            color = if (isExecuted) Color(0xFF94A3B8) else Color(0xFF0F172A),
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.5.sp
                        )
                        if (!isExecuted) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = null,
                                tint = Color(0xFF0F172A),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    // Modal dialog to view full plan markdown
    if (showFullPlanDialog) {
        AlertDialog(
            onDismissRequest = { showFullPlanDialog = false },
            confirmButton = {
                TextButton(onClick = { showFullPlanDialog = false }) {
                    Text("Close", color = Color(0xFF60A5FA))
                }
            },
            title = {
                Text(
                    text = planFileName,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontFamily = FontFamily.Monospace
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(survey.title, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
                    Text(survey.overviewPlan, color = Color(0xFFCBD5E1), fontSize = 12.5.sp)
                    HorizontalDivider(color = Color(0xFF30363D))
                    Text("Implementation Plan To-dos:", fontWeight = FontWeight.SemiBold, color = Color(0xFF93C5FD), fontSize = 12.sp)
                    todoItems.forEachIndexed { i, item ->
                        Text("${i + 1}. ${item.title}", color = Color(0xFFE2E8F0), fontSize = 12.sp)
                    }
                }
            },
            containerColor = Color(0xFF1E222D),
            shape = RoundedCornerShape(16.dp)
        )
    }
}
