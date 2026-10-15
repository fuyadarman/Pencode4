package com.example.ui.agent

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.agent.multiagent.CustomAgentManager
import com.example.agent.schedule.ScheduledAgentTask
import com.example.agent.schedule.ScheduledAgentTaskManager
import java.text.SimpleDateFormat
import java.util.*

/**
 * ScheduledTaskManagerDialog
 * Allows setting up automated jobs for AI (e.g., job searches, shopping checks, social media posts).
 */
@Composable
fun ScheduledTaskManagerDialog(
    onDismiss: () -> Unit
) {
    val tasks by ScheduledAgentTaskManager.tasks.collectAsState()
    val agents by CustomAgentManager.agents.collectAsState()
    var isCreating by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22)),
            border = BorderStroke(1.dp, Color(0xFF30363D)),
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .padding(vertical = 12.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("⏰", fontSize = 22.sp)
                        Column {
                            Text(
                                "Autonomous Tasks & Scheduler",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Set recurring jobs for AI to execute on schedule",
                                color = Color(0xFF8B949E),
                                fontSize = 11.sp
                            )
                        }
                    }

                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF8B949E))
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (isCreating) {
                    CreateScheduledTaskForm(
                        agents = agents,
                        onSave = { newTask ->
                            ScheduledAgentTaskManager.addTask(newTask)
                            isCreating = false
                        },
                        onCancel = { isCreating = false }
                    )
                } else {
                    Button(
                        onClick = { isCreating = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF238636)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.AddAlarm, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Schedule New Autonomous Task", fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (tasks.isEmpty()) {
                        Box(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("No scheduled tasks yet.", color = Color(0xFF8B949E), fontSize = 13.sp)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "E.g., 'Apply for 5 remote jobs every morning' or 'Check price drop on Amazon'",
                                    color = Color(0xFF6E7681),
                                    fontSize = 11.sp
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(tasks) { task ->
                                ScheduledTaskItemCard(
                                    task = task,
                                    onToggle = { ScheduledAgentTaskManager.toggleTaskActive(task.id, it) },
                                    onDelete = { ScheduledAgentTaskManager.removeTask(task.id) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScheduledTaskItemCard(
    task: ScheduledAgentTask,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    val timeFormat = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
    val nextRunStr = timeFormat.format(Date(task.triggerTimeMillis))

    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0D1117)),
        border = BorderStroke(1.dp, if (task.isActive) Color(0xFF30363D) else Color(0xFF21262D)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(task.title, color = Color.White, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                    Text(
                        if (task.isRecurring) "Recurring: Every ${task.intervalMinutes} mins • Next: $nextRunStr" else "One-time • Due: $nextRunStr",
                        color = Color(0xFF38BDF8),
                        fontSize = 11.sp
                    )
                }

                Switch(
                    checked = task.isActive,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(0xFF238636)
                    ),
                    modifier = Modifier.height(24.dp)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(task.prompt, color = Color(0xFF8B949E), fontSize = 11.5.sp, maxLines = 2)

            task.lastExecutionResult?.let { result ->
                Spacer(modifier = Modifier.height(4.dp))
                Text("Last Result: $result", color = Color(0xFF2ED573), fontSize = 10.5.sp)
            }

            Spacer(modifier = Modifier.height(6.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(
                    onClick = onDelete,
                    colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFF43F5E)),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(13.dp))
                    Spacer(modifier = Modifier.width(3.dp))
                    Text("Delete", fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun CreateScheduledTaskForm(
    agents: List<com.example.agent.multiagent.CustomAgent>,
    onSave: (ScheduledAgentTask) -> Unit,
    onCancel: () -> Unit
) {
    var title by remember { mutableStateOf("") }
    var prompt by remember { mutableStateOf("") }
    var intervalMinutes by remember { mutableStateOf("60") }
    var isRecurring by remember { mutableStateOf(true) }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Schedule Autonomous AI Task", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)

        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("Task Title") },
            placeholder = { Text("e.g. Check Indeed React Jobs") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = Color(0xFF38BDF8),
                unfocusedBorderColor = Color(0xFF30363D)
            )
        )

        OutlinedTextField(
            value = prompt,
            onValueChange = { prompt = it },
            label = { Text("AI Instructions / Prompt") },
            placeholder = { Text("What should the AI do in the browser at scheduled time?") },
            modifier = Modifier.fillMaxWidth().weight(1f),
            shape = RoundedCornerShape(8.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = Color(0xFF38BDF8),
                unfocusedBorderColor = Color(0xFF30363D)
            )
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = isRecurring, onCheckedChange = { isRecurring = it })
                Text("Repeat", color = Color.White, fontSize = 12.sp)
            }

            if (isRecurring) {
                OutlinedTextField(
                    value = intervalMinutes,
                    onValueChange = { intervalMinutes = it.filter { c -> c.isDigit() } },
                    label = { Text("Every (Minutes)") },
                    modifier = Modifier.width(130.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF38BDF8),
                        unfocusedBorderColor = Color(0xFF30363D)
                    )
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onCancel) {
                Text("Cancel", color = Color(0xFF8B949E))
            }

            Spacer(modifier = Modifier.width(8.dp))

            Button(
                onClick = {
                    if (title.isNotBlank() && prompt.isNotBlank()) {
                        val minutes = intervalMinutes.toLongOrNull() ?: 60L
                        val task = ScheduledAgentTask(
                            title = title.trim(),
                            prompt = prompt.trim(),
                            triggerTimeMillis = System.currentTimeMillis() + (minutes * 60 * 1000),
                            intervalMinutes = if (isRecurring) minutes else null,
                            isRecurring = isRecurring
                        )
                        onSave(task)
                    }
                },
                enabled = title.isNotBlank() && prompt.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF238636)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Save Schedule", fontWeight = FontWeight.Bold)
            }
        }
    }
}
