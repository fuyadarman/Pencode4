package com.example.data.mcp

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * McpBrandHelper
 *
 * Provides official branding metadata (display name, icon, brand tint)
 * for each MCP connector service (GitHub, Cloudflare, Supabase, Vercel, Neon, Google Search Console, etc.)
 */
object McpBrandHelper {

    data class BrandInfo(
        val serviceName: String,
        val icon: ImageVector,
        val tint: Color,
        val badgeText: String
    )

    fun resolveBrand(toolOrServerName: String): BrandInfo {
        val lower = toolOrServerName.lowercase()
        return when {
            lower.contains("github") || lower.contains("git_") -> {
                BrandInfo(
                    serviceName = "GitHub",
                    icon = Icons.Default.Code,
                    tint = Color(0xFFC9D1D9),
                    badgeText = "GitHub MCP"
                )
            }
            lower.contains("cloudflare") || lower.contains("worker") -> {
                BrandInfo(
                    serviceName = "Cloudflare",
                    icon = Icons.Default.Cloud,
                    tint = Color(0xFFF38020),
                    badgeText = "Cloudflare MCP"
                )
            }
            lower.contains("supabase") || lower.contains("postgres") || lower.contains("sql") -> {
                BrandInfo(
                    serviceName = "Supabase",
                    icon = Icons.Default.Storage,
                    tint = Color(0xFF3ECF8E),
                    badgeText = "Supabase MCP"
                )
            }
            lower.contains("vercel") -> {
                BrandInfo(
                    serviceName = "Vercel",
                    icon = Icons.Default.ChangeHistory,
                    tint = Color(0xFFFFFFFF),
                    badgeText = "Vercel MCP"
                )
            }
            lower.contains("search") || lower.contains("gsc") || lower.contains("google") -> {
                BrandInfo(
                    serviceName = "Google Search Console",
                    icon = Icons.Default.Search,
                    tint = Color(0xFF4285F4),
                    badgeText = "Search Console MCP"
                )
            }
            lower.contains("neon") -> {
                BrandInfo(
                    serviceName = "Neon",
                    icon = Icons.Default.DataObject,
                    tint = Color(0xFF00E599),
                    badgeText = "Neon MCP"
                )
            }
            lower.contains("notion") -> {
                BrandInfo(
                    serviceName = "Notion",
                    icon = Icons.Default.Book,
                    tint = Color(0xFFE2E8F0),
                    badgeText = "Notion MCP"
                )
            }
            lower.contains("slack") -> {
                BrandInfo(
                    serviceName = "Slack",
                    icon = Icons.Default.Forum,
                    tint = Color(0xFFECB22E),
                    badgeText = "Slack MCP"
                )
            }
            else -> {
                BrandInfo(
                    serviceName = "MCP Service",
                    icon = Icons.Default.Extension,
                    tint = Color(0xFFA855F7),
                    badgeText = "MCP Tool"
                )
            }
        }
    }
}
