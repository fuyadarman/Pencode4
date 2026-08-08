package com.example.data

import com.squareup.moshi.JsonClass

enum class McpPlatformType(
    val displayName: String,
    val defaultUrlPlaceholder: String,
    val description: String,
    val defaultConsoleUrl: String,
    val logoUrl: String
) {
    SUPABASE(
        "Supabase",
        "https://api.supabase.com/mcp",
        "Supabase Postgres & Edge Functions MCP",
        "https://supabase.com/dashboard/account/tokens",
        "https://cdn.simpleicons.org/supabase/3ECF8E"
    ),
    CLOUDFLARE(
        "Cloudflare",
        "https://mcp.cloudflare.com",
        "Cloudflare Workers & D1/KV MCP",
        "https://dash.cloudflare.com/profile/api-tokens",
        "https://cdn.simpleicons.org/cloudflare/F38020"
    ),
    VERCEL(
        "Vercel",
        "https://mcp.vercel.com",
        "Vercel Serverless & Storage MCP",
        "https://vercel.com/account/tokens",
        "https://cdn.simpleicons.org/vercel/FFFFFF"
    ),
    GOOGLE_STITCH(
        "Google Stitch",
        "https://stitch.googleapis.com/mcp",
        "Google Stitch Integration MCP",
        "https://console.cloud.google.com/apis/credentials",
        "https://cdn.simpleicons.org/google/4285F4"
    ),
    CUSTOM(
        "Custom MCP",
        "https://my-mcp-server.com/mcp",
        "Custom Remote JSON-RPC/SSE MCP Server",
        "https://oauth.net/2/",
        "https://cdn.simpleicons.org/json/9D4EDD"
    );

    fun buildAuthUrl(clientId: String? = null): String {
        val cid = clientId?.trim()
        if (cid.isNullOrEmpty()) return defaultConsoleUrl
        return when (this) {
            SUPABASE -> "https://api.supabase.com/v1/oauth/authorize?client_id=$cid&response_type=code"
            CLOUDFLARE -> "https://dash.cloudflare.com/oauth2/auth?client_id=$cid&response_type=code"
            VERCEL -> "https://vercel.com/oauth/authorize?client_id=$cid"
            GOOGLE_STITCH -> "https://accounts.google.com/o/oauth2/v2/auth?response_type=code&client_id=$cid&redirect_uri=http://localhost&scope=https://www.googleapis.com/auth/cloud-platform"
            CUSTOM -> defaultConsoleUrl
        }
    }

    companion object {
        fun fromString(type: String?): McpPlatformType {
            return entries.find { it.name.equals(type, ignoreCase = true) || it.displayName.equals(type, ignoreCase = true) }
                ?: CUSTOM
        }
    }
}

@JsonClass(generateAdapter = true)
data class McpServer(
    val id: String,
    val name: String,
    val url: String,
    val platform: String = "CUSTOM",
    val apiKey: String? = null,
    val enabledWorkspaces: List<String> = emptyList(),
    val status: String = "Disconnected",
    val availableTools: List<McpToolInfo> = emptyList()
)

@JsonClass(generateAdapter = true)
data class McpToolInfo(
    val name: String,
    val description: String? = null,
    val parametersJsonSchema: String? = null
)

@JsonClass(generateAdapter = true)
data class McpServerListWrapper(
    val servers: List<McpServer> = emptyList()
)
