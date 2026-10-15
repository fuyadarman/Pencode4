package com.example.ui.mcp

import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.example.data.McpPlatformType

object McpRealLogoResolver {

    fun extractCleanDomain(url: String?): String {
        if (url.isNullOrBlank()) return ""
        return try {
            val formatted = if (url.startsWith("http://") || url.startsWith("https://")) url else "https://$url"
            val uri = Uri.parse(formatted)
            val host = uri.host?.lowercase() ?: return ""
            val withoutWww = if (host.startsWith("www.")) host.substring(4) else host
            withoutWww
        } catch (e: Exception) {
            ""
        }
    }

    fun getWebsiteFaviconUrl(serverUrl: String?, platformType: McpPlatformType): String {
        val extractedDomain = extractCleanDomain(serverUrl)
        val domain = if (extractedDomain.isNotBlank() && extractedDomain.contains(".")) {
            // If it's a subdomain like mcp.vercel.com or api.supabase.com, get root domain for cleaner icon or use direct host
            if (extractedDomain.endsWith("vercel.com") || extractedDomain.endsWith("vercel.app")) "vercel.com"
            else if (extractedDomain.endsWith("cloudflare.com")) "cloudflare.com"
            else if (extractedDomain.endsWith("supabase.com") || extractedDomain.endsWith("supabase.co")) "supabase.com"
            else if (extractedDomain.endsWith("github.com")) "github.com"
            else extractedDomain
        } else {
            when (platformType) {
                McpPlatformType.VERCEL -> "vercel.com"
                McpPlatformType.CLOUDFLARE -> "cloudflare.com"
                McpPlatformType.SUPABASE -> "supabase.com"
                McpPlatformType.CUSTOM -> "json-rpc.org"
            }
        }

        // Google high-resolution 128px favicon fetcher - identical to Chrome address bar
        return "https://www.google.com/s2/favicons?domain=$domain&sz=128"
    }

    fun getSecondaryFaviconUrl(serverUrl: String?, platformType: McpPlatformType): String {
        val domain = when (platformType) {
            McpPlatformType.VERCEL -> "vercel.com"
            McpPlatformType.CLOUDFLARE -> "cloudflare.com"
            McpPlatformType.SUPABASE -> "supabase.com"
            McpPlatformType.CUSTOM -> extractCleanDomain(serverUrl).ifBlank { "json-rpc.org" }
        }
        return "https://icons.duckduckgo.com/ip3/$domain.ico"
    }
}

@Composable
fun RealWebsiteMcpLogo(
    platformType: McpPlatformType,
    serverUrl: String? = null,
    modifier: Modifier = Modifier,
    size: Dp = 38.dp
) {
    val context = LocalContext.current
    val primaryFaviconUrl = remember(serverUrl, platformType) {
        McpRealLogoResolver.getWebsiteFaviconUrl(serverUrl, platformType)
    }
    val fallbackFaviconUrl = remember(serverUrl, platformType) {
        McpRealLogoResolver.getSecondaryFaviconUrl(serverUrl, platformType)
    }

    val bgTint = when (platformType) {
        McpPlatformType.SUPABASE -> Color(0xFF0D281E)
        McpPlatformType.CLOUDFLARE -> Color(0xFF2E1C0C)
        McpPlatformType.VERCEL -> Color(0xFF0F1117)
        McpPlatformType.CUSTOM -> Color(0xFF1E1B2E)
    }

    val borderTint = when (platformType) {
        McpPlatformType.SUPABASE -> Color(0xFF3ECF8E).copy(alpha = 0.4f)
        McpPlatformType.CLOUDFLARE -> Color(0xFFF38020).copy(alpha = 0.4f)
        McpPlatformType.VERCEL -> Color(0xFF94A3B8).copy(alpha = 0.35f)
        McpPlatformType.CUSTOM -> Color(0xFFA78BFA).copy(alpha = 0.35f)
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(10.dp))
            .background(bgTint)
            .border(1.dp, borderTint, RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center
    ) {
        SubcomposeAsyncImage(
            model = ImageRequest.Builder(context)
                .data(primaryFaviconUrl)
                .crossfade(true)
                .setHeader("User-Agent", "Mozilla/5.0 (Linux; Android 14)")
                .build(),
            contentDescription = "${platformType.displayName} Real Logo",
            modifier = Modifier.size(size * 0.72f),
            error = {
                SubcomposeAsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(fallbackFaviconUrl)
                        .crossfade(true)
                        .setHeader("User-Agent", "Mozilla/5.0 (Linux; Android 14)")
                        .build(),
                    contentDescription = "${platformType.displayName} Fallback Logo",
                    modifier = Modifier.size(size * 0.72f),
                    error = {
                        FallbackCanvasLogo(platformType = platformType, size = size)
                    },
                    loading = {
                        FallbackCanvasLogo(platformType = platformType, size = size)
                    }
                )
            },
            loading = {
                FallbackCanvasLogo(platformType = platformType, size = size)
            }
        )
    }
}

@Composable
private fun FallbackCanvasLogo(
    platformType: McpPlatformType,
    size: Dp
) {
    Canvas(modifier = Modifier.size(size * 0.62f)) {
        val width = this.size.width
        val height = this.size.height

        when (platformType) {
            McpPlatformType.SUPABASE -> {
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
                val white = Color.White
                val path = Path().apply {
                    moveTo(width * 0.5f, 0f)
                    lineTo(width, height)
                    lineTo(0f, height)
                    close()
                }
                drawPath(path = path, color = white)
            }
            McpPlatformType.CUSTOM -> {
                val purple = Color(0xFFA78BFA)
                drawCircle(color = purple, radius = width * 0.38f)
            }
        }
    }
}
