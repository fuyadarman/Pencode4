package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.util.SafeClipboardHelper

/**
 * Compact, modern action buttons for copying error details and triggering AI automated fixes.
 */
@Composable
fun ConsoleErrorFixButtons(
    hasErrors: Boolean,
    errorCount: Int,
    errorSummary: String,
    onFix: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    AnimatedVisibility(
        visible = hasErrors,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Copy Errors Button
            Surface(
                onClick = {
                    SafeClipboardHelper.copyToClipboard(
                        context = context,
                        text = errorSummary,
                        label = "Console Errors",
                        showToast = true,
                        toastMessage = "Copied $errorCount error(s) to clipboard"
                    )
                },
                shape = RoundedCornerShape(4.dp),
                color = Color(0xFF21262D),
                border = BorderStroke(1.dp, Color(0xFF388BFD).copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy Errors",
                        tint = Color(0xFF58A6FF),
                        modifier = Modifier.size(11.dp)
                    )
                    Text(
                        text = "Copy",
                        color = Color(0xFF58A6FF),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // Fix Button
            Surface(
                onClick = onFix,
                shape = RoundedCornerShape(4.dp),
                color = Color(0xFF238636),
                border = BorderStroke(1.dp, Color(0xFF2EA043))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoFixHigh,
                        contentDescription = "Fix with AI",
                        tint = Color.White,
                        modifier = Modifier.size(11.dp)
                    )
                    Text(
                        text = if (errorCount > 1) "Fix ($errorCount)" else "Fix",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

/**
 * Build tab error fix and copy action buttons.
 */
@Composable
fun BuildErrorFixButton(
    errorMessage: String,
    onFix: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Copy Error Button
        Surface(
            onClick = {
                SafeClipboardHelper.copyToClipboard(
                    context = context,
                    text = errorMessage,
                    label = "Build Error",
                    showToast = true,
                    toastMessage = "Copied build error to clipboard"
                )
            },
            shape = RoundedCornerShape(6.dp),
            color = Color(0xFF21262D),
            border = BorderStroke(1.dp, Color(0xFF30363D))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "Copy Build Error",
                    tint = Color(0xFF8B949E),
                    modifier = Modifier.size(12.dp)
                )
                Text(
                    text = "Copy",
                    color = Color(0xFFC9D1D9),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // Fix Button
        Surface(
            onClick = onFix,
            shape = RoundedCornerShape(6.dp),
            color = Color(0xFF238636),
            border = BorderStroke(1.dp, Color(0xFF2EA043))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.AutoFixHigh,
                    contentDescription = "Fix Build Error",
                    tint = Color.White,
                    modifier = Modifier.size(12.dp)
                )
                Text(
                    text = "Fix Error",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
