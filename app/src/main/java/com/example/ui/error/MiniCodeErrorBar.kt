package com.example.ui.error

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * MiniCodeErrorBar
 *
 * Compact error bar matching the exact design and size from Screenshot_20261004-223948_cropped.png.
 * Displays directly above the chat bar when web console errors or build tab errors occur.
 * Includes a sleek [Fix] button and a mini [X] close button.
 */
@Composable
fun MiniCodeErrorBar(
    errorCount: Int,
    errorType: String = "running the code",
    onFix: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (errorCount <= 0) return

    val errorText = "$errorCount ${if (errorCount == 1) "error" else "errors"} $errorType"

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF1E1E22),
        border = BorderStroke(1.dp, Color(0xFF2E3038))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.Terminal,
                    contentDescription = "Code Error",
                    tint = Color(0xFFA1A1AA),
                    modifier = Modifier.size(17.dp)
                )
                Text(
                    text = errorText,
                    color = Color(0xFFA1A1AA),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Fix pill button
                Surface(
                    onClick = onFix,
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF2E3036),
                    border = BorderStroke(1.dp, Color(0xFF3E424D))
                ) {
                    Text(
                        text = "Fix",
                        color = Color(0xFFF4F4F5),
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp)
                    )
                }

                // Mini close / cross button
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(26.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Dismiss Errors",
                        tint = Color(0xFF71717A),
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        }
    }
}
