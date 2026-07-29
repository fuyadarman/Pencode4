package com.example.ui

import android.content.Context
import android.util.Log
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.ProjectFileEntity
import com.example.data.VibeRepository
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class BackupFileItem(
    val path: String,
    val content: String
)

data class BackupVersion(
    val id: String,
    val projectName: String,
    val promptText: String,
    val timestamp: Long,
    val fileCount: Int,
    val files: List<BackupFileItem>
)

object RestoreManager {
    private const val TAG = "RestoreManager"
    private const val MAX_BACKUPS = 3
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val backupAdapter = moshi.adapter(BackupVersion::class.java)

    private fun getBackupDir(context: Context, projectName: String): File {
        val sanitizedProject = projectName.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        val dir = File(context.filesDir, "project_backups/$sanitizedProject")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    suspend fun saveBackup(
        context: Context,
        projectName: String,
        promptText: String,
        files: List<ProjectFileEntity>
    ): BackupVersion? = withContext(Dispatchers.IO) {
        try {
            if (projectName.isBlank() || files.isEmpty()) return@withContext null

            val backupDir = getBackupDir(context, projectName)
            val timestamp = System.currentTimeMillis()
            val backupId = "backup_$timestamp"

            val fileItems = files.map { BackupFileItem(path = it.path, content = it.content) }
            val backup = BackupVersion(
                id = backupId,
                projectName = projectName,
                promptText = promptText.take(200).trim(),
                timestamp = timestamp,
                fileCount = fileItems.size,
                files = fileItems
            )

            val file = File(backupDir, "$backupId.json")
            val json = backupAdapter.toJson(backup)
            file.writeText(json)

            // Keep strictly the latest MAX_BACKUPS (3)
            cleanOldBackups(backupDir)

            Log.d(TAG, "Successfully created project backup version: $backupId for $projectName")
            backup
        } catch (e: Exception) {
            Log.e(TAG, "Error saving version backup for project $projectName", e)
            null
        }
    }

    suspend fun getBackups(context: Context, projectName: String): List<BackupVersion> = withContext(Dispatchers.IO) {
        try {
            val backupDir = getBackupDir(context, projectName)
            val jsonFiles = backupDir.listFiles { _, name -> name.endsWith(".json") } ?: emptyArray()

            jsonFiles.mapNotNull { file ->
                try {
                    val json = file.readText()
                    backupAdapter.fromJson(json)
                } catch (e: Exception) {
                    null
                }
            }.sortedByDescending { it.timestamp }.take(MAX_BACKUPS)
        } catch (e: Exception) {
            Log.e(TAG, "Error reading backups for project $projectName", e)
            emptyList()
        }
    }

    private fun cleanOldBackups(backupDir: File) {
        try {
            val jsonFiles = backupDir.listFiles { _, name -> name.endsWith(".json") } ?: return
            val sorted = jsonFiles.mapNotNull { file ->
                val ts = file.nameWithoutExtension.removePrefix("backup_").toLongOrNull() ?: 0L
                file to ts
            }.sortedByDescending { it.second }

            if (sorted.size > MAX_BACKUPS) {
                sorted.drop(MAX_BACKUPS).forEach { (file, _) ->
                    file.delete()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning old backups", e)
        }
    }

    suspend fun restoreBackup(
        context: Context,
        backup: BackupVersion,
        repository: VibeRepository
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val currentFiles = repository.getFilesForProject(backup.projectName)
            val backupPaths = backup.files.map { it.path }.toSet()

            // 1. Delete files that were added after this backup
            currentFiles.forEach { file ->
                if (!backupPaths.contains(file.path)) {
                    repository.deleteFile(backup.projectName, file.path)
                }
            }

            // 2. Restore/overwrite all files in backup
            backup.files.forEach { fileItem ->
                repository.saveFile(backup.projectName, fileItem.path, fileItem.content)
            }

            Log.d(TAG, "Successfully restored project ${backup.projectName} to backup ${backup.id}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restore backup ${backup.id}", e)
            false
        }
    }
}

@Composable
fun RestoreDialog(
    projectName: String,
    backups: List<BackupVersion>,
    onDismiss: () -> Unit,
    onRestoreConfirmed: (BackupVersion) -> Unit
) {
    var selectedBackupForConfirm by remember { mutableStateOf<BackupVersion?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF1E1E2E),
            border = BorderStroke(1.dp, Color(0xFF313244))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF89B4FA).copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = null,
                                tint = Color(0xFF89B4FA),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Project Version Backups",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Last 3 prompt snapshots",
                                color = Color(0xFFA6ADC8),
                                fontSize = 11.sp
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
                            tint = Color(0xFFA6ADC8),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (backups.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Backup,
                                contentDescription = null,
                                tint = Color(0xFF585B70),
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "No backups stored yet",
                                color = Color(0xFFA6ADC8),
                                fontSize = 13.sp
                            )
                            Text(
                                text = "Backups are saved automatically when you send prompts",
                                color = Color(0xFF6C7086),
                                fontSize = 11.sp,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(weight = 1f, fill = false)
                    ) {
                        items(backups) { backup ->
                            BackupVersionItem(
                                backup = backup,
                                isLatest = backup == backups.firstOrNull(),
                                onRestoreClick = { selectedBackupForConfirm = backup }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF313244),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Close", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }

    selectedBackupForConfirm?.let { backup ->
        AlertDialog(
            onDismissRequest = { selectedBackupForConfirm = null },
            title = {
                Text(
                    text = "Confirm Version Restore",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to restore project '$projectName' to the snapshot from ${formatTimestamp(backup.timestamp)}?\n\nThis will replace current files with ${backup.fileCount} files from that backup.",
                    color = Color(0xFFCDD6F4),
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val toRestore = backup
                        selectedBackupForConfirm = null
                        onRestoreConfirmed(toRestore)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFF38BA8),
                        contentColor = Color(0xFF11111B)
                    )
                ) {
                    Text("Restore Version", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedBackupForConfirm = null }) {
                    Text("Cancel", color = Color(0xFFA6ADC8))
                }
            },
            containerColor = Color(0xFF1E1E2E),
            shape = RoundedCornerShape(16.dp)
        )
    }
}

@Composable
private fun BackupVersionItem(
    backup: BackupVersion,
    isLatest: Boolean,
    onRestoreClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF181825)),
        border = BorderStroke(
            1.dp,
            if (isLatest) Color(0xFF89B4FA).copy(alpha = 0.5f) else Color(0xFF313244)
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = if (isLatest) Color(0xFF89B4FA).copy(alpha = 0.2f) else Color(0xFF45475A),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = if (isLatest) "Latest Version" else "Backup Snapshot",
                            color = if (isLatest) Color(0xFF89B4FA) else Color(0xFFBAC2DE),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = "${backup.fileCount} files",
                        color = Color(0xFFA6ADC8),
                        fontSize = 10.sp
                    )
                }

                Text(
                    text = formatTimestamp(backup.timestamp),
                    color = Color(0xFF6C7086),
                    fontSize = 10.sp
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = if (backup.promptText.isNotBlank()) backup.promptText else "Automated prompt snapshot",
                color = Color(0xFFCDD6F4),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onRestoreClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(34.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF89B4FA),
                    contentColor = Color(0xFF11111B)
                ),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Restore,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Restore to this Version",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

private fun formatTimestamp(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    if (diff < 60_000) return "Just now"
    if (diff < 3600_000) return "${diff / 60_000} mins ago"
    val sdf = SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
