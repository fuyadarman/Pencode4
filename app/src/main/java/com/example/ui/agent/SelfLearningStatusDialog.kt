package com.example.ui.agent

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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.agent.HybridSelfLearningEngine
import com.example.agent.HybridSelfLearningEngine.LearnedPattern

/**
 * SelfLearningStatusDialog
 *
 * Visual dashboard displaying active self-learned patterns, bug fix memory,
 * and cognitive rules acquired by the hybrid agent across projects.
 */
@Composable
fun SelfLearningStatusDialog(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val learnedPatterns by HybridSelfLearningEngine.learnedPatterns.collectAsState()
    var selectedPattern by remember { mutableStateOf<LearnedPattern?>(null) }
    var filterCategory by remember { mutableStateOf("all") }
    var searchQuery by remember { mutableStateOf("") }

    val bugFixCount = remember(learnedPatterns) { learnedPatterns.count { it.category == "bug_fix" } }
    val archCount = remember(learnedPatterns) { learnedPatterns.count { it.category == "architecture" } }
    val convCount = remember(learnedPatterns) { learnedPatterns.count { it.category == "convention" } }

    val filteredList = remember(learnedPatterns, filterCategory, searchQuery) {
        val byCat = if (filterCategory == "all") learnedPatterns
        else learnedPatterns.filter { it.category.equals(filterCategory, ignoreCase = true) }
        
        if (searchQuery.isBlank()) byCat
        else byCat.filter {
            it.title.contains(searchQuery, ignoreCase = true) ||
            it.solutionRule.contains(searchQuery, ignoreCase = true) ||
            it.tags.any { tag -> tag.contains(searchQuery, ignoreCase = true) }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF0D1117),
            border = BorderStroke(1.dp, Color(0xFF30363D)),
            modifier = modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Dialog Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF161B22))
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF8957E5).copy(alpha = 0.25f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Psychology,
                                contentDescription = "Self Learning",
                                tint = Color(0xFFD2A8FF),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "Self-Learning Memory",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFF0F6FC)
                                )
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF3FB950))
                                )
                            }
                            Text(
                                text = "${learnedPatterns.size} Autonomous Patterns Active across projects",
                                fontSize = 11.sp,
                                color = Color(0xFF7EE787)
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

                // Quick metrics banner
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF090D13))
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFF161B22),
                        border = BorderStroke(1.dp, Color(0xFF21262D)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("BUG FIXES", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFFF85149))
                            Text("$bugFixCount", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFFF0F6FC))
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFF161B22),
                        border = BorderStroke(1.dp, Color(0xFF21262D)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("ARCHITECTURE", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFFBC8CFF))
                            Text("$archCount", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFFF0F6FC))
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFF161B22),
                        border = BorderStroke(1.dp, Color(0xFF21262D)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("CONVENTIONS", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF58A6FF))
                            Text("$convCount", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFFF0F6FC))
                        }
                    }
                }

                // Filter tabs
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        "all" to "All (${learnedPatterns.size})",
                        "bug_fix" to "Bug Fix ($bugFixCount)",
                        "architecture" to "Arch ($archCount)",
                        "convention" to "Rules ($convCount)"
                    ).forEach { (catKey, catLabel) ->
                        val isSelected = filterCategory == catKey
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (isSelected) Color(0xFF238636).copy(alpha = 0.25f) else Color(0xFF161B22),
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) Color(0xFF2EA043) else Color(0xFF30363D)
                            ),
                            modifier = Modifier.clickable { filterCategory = catKey }
                        ) {
                            Text(
                                text = catLabel,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) Color(0xFF56D364) else Color(0xFF8B949E),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                            )
                        }
                    }
                }

                HorizontalDivider(color = Color(0xFF30363D), thickness = 1.dp)

                // Patterns List
                if (filteredList.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = Color(0xFF8B949E),
                                modifier = Modifier.size(32.dp)
                            )
                            Text(
                                text = "No learned patterns in this category yet.",
                                fontSize = 12.sp,
                                color = Color(0xFF8B949E)
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredList, key = { it.id }) { pattern ->
                            LearnedPatternItem(
                                pattern = pattern,
                                isSelected = selectedPattern?.id == pattern.id,
                                onClick = {
                                    selectedPattern = if (selectedPattern?.id == pattern.id) null else pattern
                                },
                                onDelete = {
                                    HybridSelfLearningEngine.deletePattern(context, pattern.id)
                                }
                            )
                        }
                    }
                }

                HorizontalDivider(color = Color(0xFF30363D), thickness = 1.dp)

                // Bottom actions
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = {
                            HybridSelfLearningEngine.clearAllPatterns(context)
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFF85149))
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Reset Memory", fontSize = 11.sp)
                    }

                    Button(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF238636)),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text("Done", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
private fun LearnedPatternItem(
    pattern: LearnedPattern,
    isSelected: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val categoryBadgeColor = when (pattern.category) {
        "bug_fix" -> Color(0xFFF85149)
        "architecture" -> Color(0xFFBC8CFF)
        "convention" -> Color(0xFF58A6FF)
        else -> Color(0xFF34D399)
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (isSelected) Color(0xFF161B22) else Color(0xFF0D1117),
        border = BorderStroke(
            1.dp,
            if (isSelected) categoryBadgeColor.copy(alpha = 0.6f) else Color(0xFF21262D)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
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
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(categoryBadgeColor)
                    )
                    Text(
                        text = pattern.title,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = categoryBadgeColor.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = pattern.category.replace("_", " ").uppercase(),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = categoryBadgeColor,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }

                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(22.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Delete",
                            tint = Color(0xFF6E7681),
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = pattern.solutionRule,
                fontSize = 11.sp,
                color = Color(0xFFC9D1D9),
                lineHeight = 15.sp,
                maxLines = if (isSelected) 10 else 2,
                overflow = TextOverflow.Ellipsis
            )

            if (isSelected && pattern.issueDescription.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color(0xFF1F242C),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(
                            text = "KNOWN ISSUE / REGRESSION:",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF8B949E)
                        )
                        Text(
                            text = pattern.issueDescription,
                            fontSize = 10.sp,
                            color = Color(0xFF8B949E),
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 13.sp
                        )
                    }
                }
            }

            if (pattern.tags.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    pattern.tags.take(4).forEach { tag ->
                        Surface(
                            shape = RoundedCornerShape(3.dp),
                            color = Color(0xFF21262D)
                        ) {
                            Text(
                                text = "#$tag",
                                fontSize = 9.sp,
                                color = Color(0xFF79C0FF),
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
