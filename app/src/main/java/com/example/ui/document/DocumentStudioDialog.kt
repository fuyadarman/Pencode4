package com.example.ui.document

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FormatColorFill
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.document.DocumentShareService
import com.example.document.DocumentStyleConfig
import com.example.document.DocumentTheme
import com.example.document.DocumentType
import com.example.document.GeneratedDocumentResult
import com.example.document.UniversalDocumentManager
import java.io.File

@Composable
fun DocumentStudioDialog(
    initialTitle: String = "Project Summary & Notes",
    initialContent: String = "# Overview\nThis document was generated directly from your project requirements.\n\n## Key Features\n• Custom Style and Typography\n• Direct PDF & Multi-format Export\n• In-app Editing and Customization",
    projectDirectory: File,
    onDismiss: () -> Unit,
    onDocumentGenerated: (GeneratedDocumentResult) -> Unit = {}
) {
    val context = LocalContext.current
    var docTitle by remember { mutableStateOf(initialTitle) }
    var docContent by remember { mutableStateOf(initialContent) }
    var selectedType by remember { mutableStateOf(DocumentType.PDF) }
    var selectedTheme by remember { mutableStateOf(DocumentTheme.MODERN) }
    var authorName by remember { mutableStateOf("PenCode User") }
    var fontSize by remember { mutableStateOf(14) }
    var showHeader by remember { mutableStateOf(true) }
    var showFooter by remember { mutableStateOf(true) }
    var showPageNumbers by remember { mutableStateOf(true) }
    var isLandscape by remember { mutableStateOf(false) }

    var lastResult by remember { mutableStateOf<GeneratedDocumentResult?>(null) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = {
                    val style = DocumentStyleConfig(
                        theme = selectedTheme,
                        fontSize = fontSize,
                        showHeader = showHeader,
                        showFooter = showFooter,
                        showPageNumbers = showPageNumbers,
                        author = authorName,
                        orientationLandscape = isLandscape
                    )
                    try {
                        val result = UniversalDocumentManager.generateDocument(
                            context = context,
                            type = selectedType,
                            title = docTitle,
                            content = docContent,
                            style = style,
                            destinationDir = projectDirectory
                        )
                        lastResult = result
                        statusMessage = "Created: ${result.fileName}"
                        onDocumentGenerated(result)
                    } catch (e: Exception) {
                        statusMessage = "Failed: ${e.localizedMessage}"
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Generate Document")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Close")
            }
        },
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.PictureAsPdf,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Document & PDF Studio", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Close", modifier = Modifier.size(20.dp))
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text("Create, style and edit PDFs, HTML, or Markdown files per your requirements.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                Spacer(modifier = Modifier.height(14.dp))

                // Document Format Selector
                Text("Format:", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(DocumentType.PDF, DocumentType.HTML, DocumentType.MARKDOWN, DocumentType.PLAIN_TEXT).forEach { type ->
                        val isSelected = selectedType == type
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { selectedType = type }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = type.extension.uppercase(),
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Title Input
                OutlinedTextField(
                    value = docTitle,
                    onValueChange = { docTitle = it },
                    label = { Text("Document Title") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Author
                OutlinedTextField(
                    value = authorName,
                    onValueChange = { authorName = it },
                    label = { Text("Author / Creator") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Theme / Style Selection
                Text("Theme & Visual Style:", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DocumentTheme.values().forEach { theme ->
                        val isSelected = selectedTheme == theme
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                .border(
                                    1.dp,
                                    if (isSelected) MaterialTheme.colorScheme.primary else Color.LightGray.copy(alpha = 0.4f),
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable { selectedTheme = theme }
                                .padding(vertical = 6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .background(Color(theme.primaryColor.toInt())),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = theme.id.replaceFirstChar { it.uppercase() },
                                fontSize = 10.sp,
                                maxLines = 1
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Content Editor
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Content (Markdown supported):", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Text("${docContent.length} chars", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                }
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = docContent,
                    onValueChange = { docContent = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    placeholder = { Text("Write or edit your document content here...") }
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Formatting toggles
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Include Header & Metadata", fontSize = 12.sp)
                    Switch(checked = showHeader, onCheckedChange = { showHeader = it })
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Page Numbers", fontSize = 12.sp)
                    Switch(checked = showPageNumbers, onCheckedChange = { showPageNumbers = it })
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Landscape Layout (A4)", fontSize = 12.sp)
                    Switch(checked = isLandscape, onCheckedChange = { isLandscape = it })
                }

                // Action buttons if generated
                if (lastResult != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("Ready: ${lastResult?.fileName}", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        val f = File(lastResult!!.filePath)
                                        DocumentShareService.openDocument(context, f, lastResult!!.type.mimeType)
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Open")
                                }
                                OutlinedButton(
                                    onClick = {
                                        val f = File(lastResult!!.filePath)
                                        DocumentShareService.shareDocument(context, f, lastResult!!.type.mimeType, docTitle)
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Share")
                                }
                            }
                        }
                    }
                }

                if (statusMessage != null && lastResult == null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(statusMessage!!, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                }
            }
        }
    )
}
