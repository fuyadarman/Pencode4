package com.example.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import com.example.data.McpPlatformType

@Composable
fun McpPlatformLogo(
    platformType: McpPlatformType,
    modifier: Modifier = Modifier,
    size: Dp = 36.dp
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(
                when (platformType) {
                    McpPlatformType.SUPABASE -> Color(0xFF121212)
                    McpPlatformType.CLOUDFLARE -> Color(0xFF1E1E1E)
                    McpPlatformType.VERCEL -> Color(0xFF000000)
                    McpPlatformType.GOOGLE_SEARCH_CONSOLE -> Color(0xFF1F2937)
                    McpPlatformType.GOOGLE_STITCH -> Color(0xFFF8F9FA)
                    McpPlatformType.CUSTOM -> Color(0xFF1E1B2E)
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        SubcomposeAsyncImage(
            model = platformType.logoUrl,
            contentDescription = platformType.displayName,
            modifier = Modifier.size(size * 0.7f),
            error = {
                CanvasLogoFallback(platformType = platformType, size = size)
            },
            loading = {
                CanvasLogoFallback(platformType = platformType, size = size)
            }
        )
    }
}

@Composable
private fun CanvasLogoFallback(
    platformType: McpPlatformType,
    size: Dp
) {
    Canvas(modifier = Modifier.size(size * 0.65f)) {
            val width = this.size.width
            val height = this.size.height

            when (platformType) {
                McpPlatformType.SUPABASE -> {
                    // Supabase emerald green lightning bolt (#3ECF8E)
                    val green = Color(0xFF3ECF8E)
                    val path = Path().apply {
                        moveTo(width * 0.55f, 0f)
                        lineTo(width * 0.1f, height * 0.55f)
                        lineTo(width * 0.5f, height * 0.55f)
                        lineTo(width * 0.45f, height)
                        lineTo(width * 0.9f, height * 0.45f)
                        lineTo(width * 0.5f, height * 0.45f)
                        close()
                    }
                    drawPath(path = path, color = green)
                }
                McpPlatformType.CLOUDFLARE -> {
                    // Cloudflare orange cloud (#F38020)
                    val orange = Color(0xFFF38020)
                    val path = Path().apply {
                        moveTo(width * 0.2f, height * 0.75f)
                        arcTo(
                            rect = androidx.compose.ui.geometry.Rect(0f, height * 0.4f, width * 0.45f, height * 0.85f),
                            startAngleDegrees = 90f,
                            sweepAngleDegrees = 180f,
                            forceMoveTo = false
                        )
                        arcTo(
                            rect = androidx.compose.ui.geometry.Rect(width * 0.25f, height * 0.15f, width * 0.85f, height * 0.75f),
                            startAngleDegrees = 180f,
                            sweepAngleDegrees = 160f,
                            forceMoveTo = false
                        )
                        lineTo(width * 0.95f, height * 0.75f)
                        close()
                    }
                    drawPath(path = path, color = orange)
                }
                McpPlatformType.VERCEL -> {
                    // Vercel white triangle logo
                    val white = Color.White
                    val path = Path().apply {
                        moveTo(width * 0.5f, 0f)
                        lineTo(width, height)
                        lineTo(0f, height)
                        close()
                    }
                    drawPath(path = path, color = white)
                }
                McpPlatformType.GOOGLE_SEARCH_CONSOLE -> {
                    // Google Search Console Blue/White analytics bars & magnifier
                    val gBlue = Color(0xFF4285F4)
                    val gGreen = Color(0xFF34A853)
                    val gYellow = Color(0xFFFBBC05)
                    drawRect(gBlue, Offset(width * 0.18f, height * 0.55f), Size(width * 0.16f, height * 0.35f))
                    drawRect(gYellow, Offset(width * 0.42f, height * 0.35f), Size(width * 0.16f, height * 0.55f))
                    drawRect(gGreen, Offset(width * 0.66f, height * 0.15f), Size(width * 0.16f, height * 0.75f))
                }
                McpPlatformType.GOOGLE_STITCH -> {
                    // Google 4-color dots
                    val blue = Color(0xFF4285F4)
                    val red = Color(0xFFEA4335)
                    val yellow = Color(0xFFFBBC05)
                    val green = Color(0xFF34A853)

                    val r = width * 0.22f
                    drawCircle(color = blue, radius = r, center = Offset(width * 0.3f, height * 0.3f))
                    drawCircle(color = red, radius = r, center = Offset(width * 0.7f, height * 0.3f))
                    drawCircle(color = yellow, radius = r, center = Offset(width * 0.3f, height * 0.7f))
                    drawCircle(color = green, radius = r, center = Offset(width * 0.7f, height * 0.7f))
                }
                McpPlatformType.CUSTOM -> {
                    // Node Connection Purple symbol
                    val purple = Color(0xFF9D4EDD)
                    val strokeW = width * 0.12f

                    drawLine(purple, Offset(width * 0.2f, height * 0.5f), Offset(width * 0.8f, height * 0.2f), strokeWidth = strokeW)
                    drawLine(purple, Offset(width * 0.2f, height * 0.5f), Offset(width * 0.8f, height * 0.8f), strokeWidth = strokeW)

                    drawCircle(purple, radius = width * 0.18f, center = Offset(width * 0.2f, height * 0.5f))
                    drawCircle(Color(0xFF00E5FF), radius = width * 0.15f, center = Offset(width * 0.8f, height * 0.2f))
                    drawCircle(Color(0xFFFF007F), radius = width * 0.15f, center = Offset(width * 0.8f, height * 0.8f))
                }
            }
        }
}
