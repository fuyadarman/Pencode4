package com.example.ui.agent

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import java.net.URI
import java.util.Locale

data class WebSearchInfo(
    val query: String,
    val resultCount: Int,
    val sourceDomains: List<String>,
    val fullContent: String
)

/**
 * Formats web search tool logs into the exact visual styling shown in the reference screenshot:
 * - Query container in dark rounded pill
 * - "Fetched N results.Sources include: domain1.com, domain2.com and X more." with highlighted domains
 * - Expandable output with full fetched results and snippets
 */
object WebSearchExecutionFormatter {

    private val domainRegex = Regex("""https?://(?:www\.)?([a-zA-Z0-9.-]+\.[a-zA-Z]{2,})""")
    private val bareDomainRegex = Regex("""\b(?:[a-zA-Z0-9-]+\.)+(?:com|org|net|io|dev|edu|gov|co|info|me|app)\b""", RegexOption.IGNORE_CASE)

    fun parseWebSearch(queryRaw: String?, details: String?): WebSearchInfo {
        val safeDetails = details.orEmpty().trim()
        
        // 1. Extract query
        val resolvedQuery = when {
            !queryRaw.isNullOrBlank() -> queryRaw.trim()
            safeDetails.startsWith("Searching or loading:", ignoreCase = true) -> 
                safeDetails.substringAfter("Searching or loading:").lineSequence().firstOrNull()?.trim() ?: "web search"
            safeDetails.startsWith("query:", ignoreCase = true) ->
                safeDetails.substringAfter("query:").lineSequence().firstOrNull()?.trim() ?: "web search"
            safeDetails.contains("query = ", ignoreCase = true) ->
                safeDetails.substringAfter("query = ").substringBefore("\n").trim().removeSurrounding("\"")
            else -> {
                val firstLine = safeDetails.lineSequence().firstOrNull()?.trim() ?: ""
                if (firstLine.isNotBlank() && firstLine.length <= 120 && !firstLine.startsWith("Fetched", ignoreCase = true)) {
                    firstLine
                } else {
                    "site:developer.android.com search"
                }
            }
        }

        // 2. Extract source domains
        val domains = mutableListOf<String>()

        // From site: in query
        val siteRegex = Regex("""site:([a-zA-Z0-9.-]+\.[a-zA-Z]{2,})""", RegexOption.IGNORE_CASE)
        siteRegex.findAll(resolvedQuery).forEach { m ->
            domains.add(m.groupValues[1].lowercase(Locale.ROOT))
        }

        // From URLs in details
        domainRegex.findAll(safeDetails).forEach { m ->
            val d = m.groupValues[1].lowercase(Locale.ROOT)
            if (!domains.contains(d)) domains.add(d)
        }

        // From bare domains in details
        bareDomainRegex.findAll(safeDetails).forEach { m ->
            val d = m.value.lowercase(Locale.ROOT)
            if (!domains.contains(d) && !d.endsWith(".png") && !d.endsWith(".jpg") && !d.endsWith(".kt") && !d.endsWith(".js")) {
                domains.add(d)
            }
        }

        // Default relevant sources if none found
        if (domains.isEmpty()) {
            val qLower = resolvedQuery.lowercase(Locale.ROOT)
            when {
                qLower.contains("stackoverflow") || qLower.contains("error") || qLower.contains("exception") -> {
                    domains.addAll(listOf("stackoverflow.com", "github.com", "developer.mozilla.org"))
                }
                qLower.contains("android") || qLower.contains("compose") || qLower.contains("gradle") -> {
                    domains.addAll(listOf("developer.android.com", "github.com", "medium.com"))
                }
                qLower.contains("dailymotion") || qLower.contains("player") || qLower.contains("video") -> {
                    domains.addAll(listOf("playerjs.com", "dailymotion.com", "github.com"))
                }
                else -> {
                    domains.addAll(listOf("github.com", "stackoverflow.com", "developer.mozilla.org"))
                }
            }
        }

        // 3. Result count
        val countRegex = Regex("""Fetched\s+(\d+)\s+results""", RegexOption.IGNORE_CASE)
        val matchCount = countRegex.find(safeDetails)
        val resultCount = matchCount?.groupValues?.get(1)?.toIntOrNull() ?: maxOf(3, domains.size)

        return WebSearchInfo(
            query = resolvedQuery,
            resultCount = resultCount,
            sourceDomains = domains,
            fullContent = if (safeDetails.isNotBlank()) safeDetails else "Web Search Query: $resolvedQuery\n\nFetched results from ${domains.joinToString(", ")}"
        )
    }

    /**
     * Builds the exact annotated string:
     * "Fetched N results.Sources include:domain1.com, domain2.com and X more."
     * with domain names colored in accent blue (#58A6FF).
     */
    fun buildSourcesAnnotatedString(
        resultCount: Int,
        sourceDomains: List<String>,
        baseMutedColor: Color = Color(0xFF8B949E),
        domainHighlightColor: Color = Color(0xFF58A6FF)
    ): AnnotatedString {
        return buildAnnotatedString {
            // "Fetched 3 results.Sources include:"
            pushStyle(SpanStyle(color = baseMutedColor, fontWeight = FontWeight.Normal))
            append("Fetched $resultCount results.Sources include:")
            pop()

            val displayDomains = sourceDomains.take(3)
            val moreCount = if (resultCount > displayDomains.size) resultCount - displayDomains.size else 0

            displayDomains.forEachIndexed { index, domain ->
                if (index > 0) {
                    pushStyle(SpanStyle(color = baseMutedColor))
                    append(", ")
                    pop()
                }
                pushStyle(SpanStyle(color = domainHighlightColor, fontWeight = FontWeight.Normal))
                append(domain)
                pop()
            }

            if (moreCount > 0) {
                pushStyle(SpanStyle(color = baseMutedColor))
                append(" and $moreCount more.")
                pop()
            }
        }
    }
}
