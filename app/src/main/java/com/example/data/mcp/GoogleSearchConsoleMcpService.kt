package com.example.data.mcp

import com.example.data.McpToolInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

/**
 * Native MCP Service for Google Search Console with full Read and Write operations:
 * - Sites management (List, Get, Add, Delete)
 * - Search Analytics Query (clicks, impressions, CTR, position, queries, pages)
 * - Sitemaps management (List, Submit, Delete)
 * - URL Inspection (Indexing status, Mobile usability, Rich results)
 */
class GoogleSearchConsoleMcpService(
    private val tokenStore: McpTokenStore
) {
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    companion object {
        const val SEARCH_CONSOLE_SCOPE = "https://www.googleapis.com/auth/webmasters"
        const val GSC_BASE_URL = "https://www.googleapis.com/webmasters/v3"
        const val GSC_INSPECTION_URL = "https://searchconsole.googleapis.com/v1/urlInspection/index:inspect"

        fun getAvailableTools(): List<McpToolInfo> {
            return listOf(
                McpToolInfo(
                    name = "gsc_list_sites",
                    description = "List all websites verified and monitored in Google Search Console with their permission levels.",
                    parametersJsonSchema = """{"type":"object","properties":{}}"""
                ),
                McpToolInfo(
                    name = "gsc_get_site",
                    description = "Get detailed information and verification status for a specific site in Google Search Console.",
                    parametersJsonSchema = """{"type":"object","properties":{"siteUrl":{"type":"string","description":"The URL of the site as registered in Search Console (e.g. 'https://example.com/' or 'sc-domain:example.com')"}},"required":["siteUrl"]}"""
                ),
                McpToolInfo(
                    name = "gsc_add_site",
                    description = "Add (register) a new website to Google Search Console (Write operation).",
                    parametersJsonSchema = """{"type":"object","properties":{"siteUrl":{"type":"string","description":"The URL of the site to add (e.g. 'https://example.com/')"}},"required":["siteUrl"]}"""
                ),
                McpToolInfo(
                    name = "gsc_delete_site",
                    description = "Remove a website from Google Search Console (Write operation).",
                    parametersJsonSchema = """{"type":"object","properties":{"siteUrl":{"type":"string","description":"The URL of the site to delete"}},"required":["siteUrl"]}"""
                ),
                McpToolInfo(
                    name = "gsc_search_analytics",
                    description = "Query search traffic performance metrics (clicks, impressions, CTR, position) across queries, pages, countries, devices, and dates (Read operation).",
                    parametersJsonSchema = """{"type":"object","properties":{"siteUrl":{"type":"string","description":"Site URL (e.g. 'https://example.com/')"},"startDate":{"type":"string","description":"Start date in YYYY-MM-DD format"},"endDate":{"type":"string","description":"End date in YYYY-MM-DD format"},"dimensions":{"type":"array","items":{"type":"string"},"description":"Dimensions to group by: 'query', 'page', 'country', 'device', 'date', 'searchAppearance'"},"searchType":{"type":"string","description":"Search type: 'web', 'image', 'video', 'news', 'discover', 'googleNews'"},"rowLimit":{"type":"integer","description":"Max number of rows to return (default 25)"}},"required":["siteUrl","startDate","endDate"]}"""
                ),
                McpToolInfo(
                    name = "gsc_list_sitemaps",
                    description = "List all XML sitemaps submitted for a site in Google Search Console.",
                    parametersJsonSchema = """{"type":"object","properties":{"siteUrl":{"type":"string","description":"The URL of the site"}},"required":["siteUrl"]}"""
                ),
                McpToolInfo(
                    name = "gsc_submit_sitemap",
                    description = "Submit a new XML sitemap to Google Search Console for crawling and indexing (Write operation).",
                    parametersJsonSchema = """{"type":"object","properties":{"siteUrl":{"type":"string","description":"The URL of the site"},"feedpath":{"type":"string","description":"The full URL of the XML sitemap (e.g. 'https://example.com/sitemap.xml')"}},"required":["siteUrl","feedpath"]}"""
                ),
                McpToolInfo(
                    name = "gsc_delete_sitemap",
                    description = "Delete a submitted sitemap from Google Search Console (Write operation).",
                    parametersJsonSchema = """{"type":"object","properties":{"siteUrl":{"type":"string","description":"The URL of the site"},"feedpath":{"type":"string","description":"The full URL of the sitemap to delete"}},"required":["siteUrl","feedpath"]}"""
                ),
                McpToolInfo(
                    name = "gsc_inspect_url",
                    description = "Inspect the Google indexing status, mobile usability, and rich results for a specific URL (URL Inspection API).",
                    parametersJsonSchema = """{"type":"object","properties":{"siteUrl":{"type":"string","description":"The URL of the property as defined in Search Console"},"inspectionUrl":{"type":"string","description":"The fully qualified URL to inspect"}},"required":["siteUrl","inspectionUrl"]}"""
                )
            )
        }
    }

    suspend fun executeTool(
        serverId: String,
        toolName: String,
        argumentsJson: String?
    ): String = withContext(Dispatchers.IO) {
        val tokens = tokenStore.getTokens(serverId)
        val accessToken = tokens?.accessToken
        if (accessToken.isNullOrBlank()) {
            return@withContext "Error: Google Search Console access token missing. Please connect with Google OAuth first."
        }

        val args = try {
            if (!argumentsJson.isNullOrBlank()) JSONObject(argumentsJson) else JSONObject()
        } catch (e: Exception) {
            JSONObject()
        }

        try {
            when (toolName) {
                "gsc_list_sites" -> listSites(accessToken)
                "gsc_get_site" -> {
                    val siteUrl = args.optString("siteUrl")
                    if (siteUrl.isBlank()) "Error: 'siteUrl' is required." else getSite(accessToken, siteUrl)
                }
                "gsc_add_site" -> {
                    val siteUrl = args.optString("siteUrl")
                    if (siteUrl.isBlank()) "Error: 'siteUrl' is required." else addSite(accessToken, siteUrl)
                }
                "gsc_delete_site" -> {
                    val siteUrl = args.optString("siteUrl")
                    if (siteUrl.isBlank()) "Error: 'siteUrl' is required." else deleteSite(accessToken, siteUrl)
                }
                "gsc_search_analytics" -> {
                    val siteUrl = args.optString("siteUrl")
                    val startDate = args.optString("startDate")
                    val endDate = args.optString("endDate")
                    if (siteUrl.isBlank() || startDate.isBlank() || endDate.isBlank()) {
                        "Error: 'siteUrl', 'startDate' and 'endDate' are required for search analytics."
                    } else {
                        querySearchAnalytics(accessToken, siteUrl, startDate, endDate, args)
                    }
                }
                "gsc_list_sitemaps" -> {
                    val siteUrl = args.optString("siteUrl")
                    if (siteUrl.isBlank()) "Error: 'siteUrl' is required." else listSitemaps(accessToken, siteUrl)
                }
                "gsc_submit_sitemap" -> {
                    val siteUrl = args.optString("siteUrl")
                    val feedpath = args.optString("feedpath")
                    if (siteUrl.isBlank() || feedpath.isBlank()) "Error: 'siteUrl' and 'feedpath' are required." else submitSitemap(accessToken, siteUrl, feedpath)
                }
                "gsc_delete_sitemap" -> {
                    val siteUrl = args.optString("siteUrl")
                    val feedpath = args.optString("feedpath")
                    if (siteUrl.isBlank() || feedpath.isBlank()) "Error: 'siteUrl' and 'feedpath' are required." else deleteSitemap(accessToken, siteUrl, feedpath)
                }
                "gsc_inspect_url" -> {
                    val siteUrl = args.optString("siteUrl")
                    val inspectionUrl = args.optString("inspectionUrl")
                    if (siteUrl.isBlank() || inspectionUrl.isBlank()) "Error: 'siteUrl' and 'inspectionUrl' are required." else inspectUrl(accessToken, siteUrl, inspectionUrl)
                }
                else -> "Error: Unknown Google Search Console tool '$toolName'."
            }
        } catch (e: Exception) {
            "Error executing Google Search Console tool '$toolName': ${e.message}"
        }
    }

    private fun listSites(token: String): String {
        val req = Request.Builder()
            .url("$GSC_BASE_URL/sites")
            .get()
            .addHeader("Authorization", "Bearer $token")
            .build()
        val resp = httpClient.newCall(req).execute()
        return resp.body?.string() ?: "{}"
    }

    private fun getSite(token: String, siteUrl: String): String {
        val encoded = URLEncoder.encode(siteUrl, StandardCharsets.UTF_8.toString())
        val req = Request.Builder()
            .url("$GSC_BASE_URL/sites/$encoded")
            .get()
            .addHeader("Authorization", "Bearer $token")
            .build()
        val resp = httpClient.newCall(req).execute()
        return resp.body?.string() ?: "{}"
    }

    private fun addSite(token: String, siteUrl: String): String {
        val encoded = URLEncoder.encode(siteUrl, StandardCharsets.UTF_8.toString())
        val req = Request.Builder()
            .url("$GSC_BASE_URL/sites/$encoded")
            .put("{}".toRequestBody("application/json".toMediaTypeOrNull()))
            .addHeader("Authorization", "Bearer $token")
            .build()
        val resp = httpClient.newCall(req).execute()
        return if (resp.isSuccessful) {
            """{"status":"success","message":"Site '$siteUrl' added to Google Search Console successfully."}"""
        } else {
            resp.body?.string() ?: "Error adding site (${resp.code})"
        }
    }

    private fun deleteSite(token: String, siteUrl: String): String {
        val encoded = URLEncoder.encode(siteUrl, StandardCharsets.UTF_8.toString())
        val req = Request.Builder()
            .url("$GSC_BASE_URL/sites/$encoded")
            .delete()
            .addHeader("Authorization", "Bearer $token")
            .build()
        val resp = httpClient.newCall(req).execute()
        return if (resp.isSuccessful) {
            """{"status":"success","message":"Site '$siteUrl' deleted from Google Search Console."}"""
        } else {
            resp.body?.string() ?: "Error deleting site (${resp.code})"
        }
    }

    private fun querySearchAnalytics(
        token: String,
        siteUrl: String,
        startDate: String,
        endDate: String,
        args: JSONObject
    ): String {
        val encoded = URLEncoder.encode(siteUrl, StandardCharsets.UTF_8.toString())
        val bodyObj = JSONObject().apply {
            put("startDate", startDate)
            put("endDate", endDate)
            if (args.has("dimensions")) {
                put("dimensions", args.getJSONArray("dimensions"))
            }
            if (args.has("searchType")) {
                put("type", args.getString("searchType"))
            }
            put("rowLimit", args.optInt("rowLimit", 25))
            if (args.has("startRow")) {
                put("startRow", args.getInt("startRow"))
            }
        }

        val req = Request.Builder()
            .url("$GSC_BASE_URL/sites/$encoded/searchAnalytics/query")
            .post(bodyObj.toString().toRequestBody("application/json".toMediaTypeOrNull()))
            .addHeader("Authorization", "Bearer $token")
            .addHeader("Content-Type", "application/json")
            .build()

        val resp = httpClient.newCall(req).execute()
        return resp.body?.string() ?: "{}"
    }

    private fun listSitemaps(token: String, siteUrl: String): String {
        val encoded = URLEncoder.encode(siteUrl, StandardCharsets.UTF_8.toString())
        val req = Request.Builder()
            .url("$GSC_BASE_URL/sites/$encoded/sitemaps")
            .get()
            .addHeader("Authorization", "Bearer $token")
            .build()
        val resp = httpClient.newCall(req).execute()
        return resp.body?.string() ?: "{}"
    }

    private fun submitSitemap(token: String, siteUrl: String, feedpath: String): String {
        val encodedSite = URLEncoder.encode(siteUrl, StandardCharsets.UTF_8.toString())
        val encodedFeed = URLEncoder.encode(feedpath, StandardCharsets.UTF_8.toString())
        val req = Request.Builder()
            .url("$GSC_BASE_URL/sites/$encodedSite/sitemaps/$encodedFeed")
            .put("{}".toRequestBody("application/json".toMediaTypeOrNull()))
            .addHeader("Authorization", "Bearer $token")
            .build()
        val resp = httpClient.newCall(req).execute()
        return if (resp.isSuccessful) {
            """{"status":"success","message":"Sitemap '$feedpath' submitted to Google Search Console successfully."}"""
        } else {
            resp.body?.string() ?: "Error submitting sitemap (${resp.code})"
        }
    }

    private fun deleteSitemap(token: String, siteUrl: String, feedpath: String): String {
        val encodedSite = URLEncoder.encode(siteUrl, StandardCharsets.UTF_8.toString())
        val encodedFeed = URLEncoder.encode(feedpath, StandardCharsets.UTF_8.toString())
        val req = Request.Builder()
            .url("$GSC_BASE_URL/sites/$encodedSite/sitemaps/$encodedFeed")
            .delete()
            .addHeader("Authorization", "Bearer $token")
            .build()
        val resp = httpClient.newCall(req).execute()
        return if (resp.isSuccessful) {
            """{"status":"success","message":"Sitemap '$feedpath' deleted from Google Search Console."}"""
        } else {
            resp.body?.string() ?: "Error deleting sitemap (${resp.code})"
        }
    }

    private fun inspectUrl(token: String, siteUrl: String, inspectionUrl: String): String {
        val bodyObj = JSONObject().apply {
            put("siteUrl", siteUrl)
            put("inspectionUrl", inspectionUrl)
        }

        val req = Request.Builder()
            .url(GSC_INSPECTION_URL)
            .post(bodyObj.toString().toRequestBody("application/json".toMediaTypeOrNull()))
            .addHeader("Authorization", "Bearer $token")
            .addHeader("Content-Type", "application/json")
            .build()

        val resp = httpClient.newCall(req).execute()
        return resp.body?.string() ?: "{}"
    }
}
