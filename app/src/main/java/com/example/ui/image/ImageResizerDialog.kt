package com.example.ui.image

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.agent.ImageResizeEngine
import com.example.data.ProjectFileEntity
import com.example.data.VibeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ImageResizerDialog
 *
 * Dedicated interactive UI studio for resizing, scaling, format conversion,
 * and compressing images inside the project workspace.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageResizerDialog(
    projectName: String,
    files: List<ProjectFileEntity>,
    initialFilePath: String? = null,
    onSaveFile: (path: String, content: String) -> Unit,
    onDismiss: () -> Unit,
    onFileUpdated: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    // Filter project image files
    val imageFiles = remember(files) {
        files.filter { file ->
            val p = file.path.lowercase()
            p.endsWith(".png") || p.endsWith(".jpg") || p.endsWith(".jpeg") ||
            p.endsWith(".webp") || p.endsWith(".ico") || file.content.startsWith("data:image/")
        }
    }

    var selectedFilePath by remember(initialFilePath) {
        mutableStateOf(
            if (!initialFilePath.isNullOrBlank() && imageFiles.any { it.path.equals(initialFilePath, ignoreCase = true) }) {
                initialFilePath
            } else {
                imageFiles.firstOrNull()?.path ?: ""
            }
        )
    }

    val selectedFile = remember(selectedFilePath, files) {
        files.find { it.path.equals(selectedFilePath, ignoreCase = true) }
    }

    var previewBitmap by remember(selectedFile) {
        mutableStateOf<Bitmap?>(null)
    }

    var origWidth by remember { mutableIntStateOf(0) }
    var origHeight by remember { mutableIntStateOf(0) }
    var origSizeKb by remember { mutableIntStateOf(0) }

    // Decode selected image info & preview
    LaunchedEffect(selectedFile) {
        withContext(Dispatchers.IO) {
            val content = selectedFile?.content ?: return@withContext
            try {
                val isDataUri = content.startsWith("data:") && content.contains(";base64,")
                val rawBase64 = if (isDataUri) content.substringAfter(";base64,") else content
                val bytes = Base64.decode(rawBase64, Base64.DEFAULT)
                if (bytes.isNotEmpty()) {
                    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
                    origWidth = opts.outWidth
                    origHeight = opts.outHeight
                    origSizeKb = bytes.size / 1024

                    // Create preview thumbnail
                    val sampleSize = maxOf(1, maxOf(opts.outWidth, opts.outHeight) / 512)
                    val decodeOpts = BitmapFactory.Options().apply {
                        inSampleSize = sampleSize
                        inPreferredConfig = Bitmap.Config.ARGB_8888
                    }
                    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOpts)
                    withContext(Dispatchers.Main) {
                        previewBitmap = bmp
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("ImageResizerDialog", "Failed to decode preview: ${e.message}")
            }
        }
    }

    var widthInput by remember { mutableStateOf("") }
    var heightInput by remember { mutableStateOf("") }
    var maintainAspect by remember { mutableStateOf(true) }
    var selectedFormat by remember { mutableStateOf("png") }
    var quality by remember { mutableFloatStateOf(90f) }
    var isProcessing by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var isSuccessMessage by remember { mutableStateOf(true) }

    // Preset configurations
    val presets = listOf(
        "Icon (512x512)" to (512 to 512),
        "Banner (1200x630)" to (1200 to 630),
        "Feature (1024x500)" to (1024 to 500),
        "Avatar (256x256)" to (256 to 256),
        "Thumbnail (320x180)" to (320 to 180),
        "Half Size" to ((origWidth / 2).coerceAtLeast(1) to (origHeight / 2).coerceAtLeast(1))
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.92f)
                .padding(8.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF131722)),
            border = BorderStroke(1.dp, Color(0xFF2E384D))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Transform,
                            contentDescription = null,
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Image Resizer Studio",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color(0xFF94A3B8)
                        )
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 10.dp),
                    color = Color(0xFF1E293B)
                )

                if (imageFiles.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.ImageNotSupported,
                                contentDescription = null,
                                tint = Color(0xFF64748B),
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "No image files found in current project.",
                                color = Color(0xFF94A3B8),
                                fontSize = 14.sp
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // 1. Image File Selector
                        item {
                            Text(
                                text = "Select Project Image",
                                color = Color(0xFFCBD5E1),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(imageFiles) { file ->
                                    val isSelected = file.path == selectedFilePath
                                    val fileName = file.path.substringAfterLast("/")
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isSelected) Color(0xFF0284C7).copy(alpha = 0.25f) else Color(0xFF1E293B),
                                        border = BorderStroke(
                                            1.dp,
                                            if (isSelected) Color(0xFF38BDF8) else Color(0xFF334155)
                                        ),
                                        modifier = Modifier.clickable {
                                            selectedFilePath = file.path
                                            widthInput = ""
                                            heightInput = ""
                                            statusMessage = null
                                        }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Image,
                                                contentDescription = null,
                                                tint = if (isSelected) Color(0xFF38BDF8) else Color(0xFF94A3B8),
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = fileName,
                                                color = if (isSelected) Color.White else Color(0xFFCBD5E1),
                                                fontSize = 12.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // 2. Image Preview & Dimensions Badge
                        item {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(180.dp),
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F141C)),
                                border = BorderStroke(1.dp, Color(0xFF1F2937))
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (previewBitmap != null) {
                                        Image(
                                            bitmap = previewBitmap!!.asImageBitmap(),
                                            contentDescription = "Selected image preview",
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(8.dp),
                                            contentScale = ContentScale.Fit
                                        )
                                    } else {
                                        Text(
                                            text = "Loading preview...",
                                            color = Color(0xFF64748B),
                                            fontSize = 12.sp
                                        )
                                    }

                                    // Dimension details overlay badge
                                    if (origWidth > 0 && origHeight > 0) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = Color.Black.copy(alpha = 0.75f),
                                            modifier = Modifier
                                                .align(Alignment.BottomStart)
                                                .padding(8.dp)
                                        ) {
                                            Text(
                                                text = "${origWidth} x ${origHeight} px • ${origSizeKb} KB",
                                                color = Color(0xFFE2E8F0),
                                                fontSize = 11.sp,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // 3. Quick Presets
                        item {
                            Text(
                                text = "Quick Resolution Presets",
                                color = Color(0xFFCBD5E1),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(presets) { (label, dims) ->
                                    val (w, h) = dims
                                    val isCurrent = widthInput == w.toString() && heightInput == h.toString()
                                    OutlinedButton(
                                        onClick = {
                                            widthInput = w.toString()
                                            heightInput = h.toString()
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            containerColor = if (isCurrent) Color(0xFF0369A1).copy(alpha = 0.3f) else Color.Transparent
                                        ),
                                        border = BorderStroke(
                                            1.dp,
                                            if (isCurrent) Color(0xFF38BDF8) else Color(0xFF334155)
                                        ),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = label,
                                            fontSize = 11.sp,
                                            color = if (isCurrent) Color(0xFF38BDF8) else Color(0xFF94A3B8)
                                        )
                                    }
                                }
                            }
                        }

                        // 4. Target Width & Height Fields
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedTextField(
                                    value = widthInput,
                                    onValueChange = { newVal ->
                                        widthInput = newVal.filter { it.isDigit() }
                                        if (maintainAspect && origWidth > 0 && origHeight > 0) {
                                            val w = widthInput.toIntOrNull()
                                            if (w != null && w > 0) {
                                                val calcH = (w.toFloat() / origWidth * origHeight).toInt()
                                                heightInput = calcH.toString()
                                            }
                                        }
                                    },
                                    label = { Text("Width (px)", fontSize = 12.sp) },
                                    placeholder = { Text(origWidth.toString(), fontSize = 12.sp) },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = Color(0xFF38BDF8),
                                        unfocusedBorderColor = Color(0xFF334155),
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color(0xFFE2E8F0)
                                    ),
                                    modifier = Modifier.weight(1f)
                                )

                                OutlinedTextField(
                                    value = heightInput,
                                    onValueChange = { newVal ->
                                        heightInput = newVal.filter { it.isDigit() }
                                        if (maintainAspect && origWidth > 0 && origHeight > 0) {
                                            val h = heightInput.toIntOrNull()
                                            if (h != null && h > 0) {
                                                val calcW = (h.toFloat() / origHeight * origWidth).toInt()
                                                widthInput = calcW.toString()
                                            }
                                        }
                                    },
                                    label = { Text("Height (px)", fontSize = 12.sp) },
                                    placeholder = { Text(origHeight.toString(), fontSize = 12.sp) },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = Color(0xFF38BDF8),
                                        unfocusedBorderColor = Color(0xFF334155),
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color(0xFFE2E8F0)
                                    ),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        // 5. Maintain Aspect Ratio Switch & Format
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(
                                        checked = maintainAspect,
                                        onCheckedChange = { maintainAspect = it },
                                        colors = CheckboxDefaults.colors(
                                            checkedColor = Color(0xFF38BDF8),
                                            uncheckedColor = Color(0xFF64748B)
                                        )
                                    )
                                    Text(
                                        text = "Maintain Aspect Ratio",
                                        color = Color(0xFFE2E8F0),
                                        fontSize = 12.sp
                                    )
                                }

                                // Format selection tabs
                                Row(
                                    modifier = Modifier
                                        .background(Color(0xFF1E293B), RoundedCornerShape(8.dp))
                                        .padding(2.dp)
                                ) {
                                    listOf("png", "jpg", "webp").forEach { fmt ->
                                        val isSel = selectedFormat == fmt
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(if (isSel) Color(0xFF0284C7) else Color.Transparent)
                                                .clickable { selectedFormat = fmt }
                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Text(
                                                text = fmt.uppercase(),
                                                color = if (isSel) Color.White else Color(0xFF94A3B8),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // 6. Quality Slider (for JPG & WEBP)
                        if (selectedFormat == "jpg" || selectedFormat == "webp") {
                            item {
                                Column {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Compression Quality", color = Color(0xFF94A3B8), fontSize = 12.sp)
                                        Text("${quality.toInt()}%", color = Color(0xFF38BDF8), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Slider(
                                        value = quality,
                                        onValueChange = { quality = it },
                                        valueRange = 10f..100f,
                                        steps = 18,
                                        colors = SliderDefaults.colors(
                                            thumbColor = Color(0xFF38BDF8),
                                            activeTrackColor = Color(0xFF0284C7)
                                        )
                                    )
                                }
                            }
                        }

                        // Status notification banner
                        if (statusMessage != null) {
                            item {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSuccessMessage) Color(0xFF065F46).copy(alpha = 0.35f) else Color(0xFF7F1D1D).copy(alpha = 0.35f),
                                    border = BorderStroke(
                                        1.dp,
                                        if (isSuccessMessage) Color(0xFF10B981) else Color(0xFFEF4444)
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = if (isSuccessMessage) Icons.Default.CheckCircle else Icons.Default.Error,
                                            contentDescription = null,
                                            tint = if (isSuccessMessage) Color(0xFF10B981) else Color(0xFFEF4444),
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = statusMessage!!,
                                            color = Color.White,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Bottom Action Buttons
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, Color(0xFF334155))
                        ) {
                            Text("Cancel", color = Color(0xFF94A3B8))
                        }

                        Button(
                            onClick = {
                                val targetW = widthInput.toIntOrNull()
                                val targetH = heightInput.toIntOrNull()

                                if (targetW == null && targetH == null) {
                                    statusMessage = "Please enter width or height in pixels."
                                    isSuccessMessage = false
                                    return@Button
                                }

                                isProcessing = true
                                statusMessage = null

                                coroutineScope.launch {
                                    val destFileName = selectedFilePath.substringBeforeLast(".") +
                                            "_${targetW ?: origWidth}x${targetH ?: origHeight}." + selectedFormat

                                    val base64Content = selectedFile?.content ?: ""
                                    val resized = withContext(Dispatchers.IO) {
                                        ImageResizeEngine.resizeBase64Image(
                                            base64Content = base64Content,
                                            targetWidth = targetW,
                                            targetHeight = targetH,
                                            format = selectedFormat,
                                            maintainAspectRatio = maintainAspect,
                                            quality = quality.toInt()
                                        )
                                    }

                                    isProcessing = false
                                    if (resized != null) {
                                        val (resBase64, summary) = resized
                                        onSaveFile(destFileName, resBase64)
                                        statusMessage = "Successfully resized: $summary -> $destFileName"
                                        isSuccessMessage = true
                                        onFileUpdated()
                                    } else {
                                        statusMessage = "Error: Failed to resize image '$selectedFilePath'."
                                        isSuccessMessage = false
                                    }
                                }
                            },
                            modifier = Modifier.weight(2f),
                            shape = RoundedCornerShape(8.dp),
                            enabled = !isProcessing,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7))
                        ) {
                            if (isProcessing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Resizing...", color = Color.White)
                            } else {
                                Icon(Icons.Default.AspectRatio, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Resize & Save", color = Color.White, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }
    }
}
