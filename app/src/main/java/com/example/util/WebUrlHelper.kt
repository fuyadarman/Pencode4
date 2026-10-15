package com.example.util

import android.net.Uri

/**
 * Utility for normalizing user-input URLs in the browser/preview address bar.
 * Supports domain names, full URLs, localhost URLs, search queries, and YouTube/Google shortcuts.
 */
object WebUrlHelper {

    /**
     * Normalizes a raw input string into a valid HTTP/HTTPS URL.
     */
    fun normalizeUrl(rawInput: String): String {
        val trimmed = rawInput.trim()
        if (trimmed.isBlank()) return "https://www.google.com"

        // If it starts with http://, https://, or file://
        if (trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true) ||
            trimmed.startsWith("file://", ignoreCase = true)
        ) {
            return trimmed
        }

        // Localhost special case
        if (trimmed.startsWith("localhost", ignoreCase = true) ||
            trimmed.startsWith("127.0.0.1", ignoreCase = true)
        ) {
            return "http://$trimmed"
        }

        // Check if it's a domain name (contains dot and no spaces)
        if (trimmed.contains(".") && !trimmed.contains(" ")) {
            return "https://$trimmed"
        }

        // Otherwise treat as a Google search query
        val encodedQuery = Uri.encode(trimmed)
        return "https://www.google.com/search?q=$encodedQuery"
    }
}
