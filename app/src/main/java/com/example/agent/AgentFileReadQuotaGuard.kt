package com.example.agent

/**
 * AgentFileReadQuotaGuard
 *
 * Tracks how many times each file is read during an agent prompt session.
 * When an AI reads the same file 2 times, it automatically injects a strict,
 * authoritative background directive to the model instructing it to stop reading
 * and proceed immediately with 'edit_file', 'multi_edit_file', or 'complete'.
 *
 * This message is delivered purely in the model's background conversation context
 * and is NEVER shown as an error in the user UI.
 */
object AgentFileReadQuotaGuard {

    private val readCounts = mutableMapOf<String, Int>()

    fun resetSession() {
        readCounts.clear()
    }

    fun onFileModified(filePath: String) {
        val normalized = normalize(filePath)
        readCounts.remove(normalized)
    }

    /**
     * Records a read of the given file path and returns a strict background directive
     * if the file has been read 2 or more times.
     */
    fun recordAndGetDirective(filePath: String): String? {
        val normalized = normalize(filePath)
        if (normalized.isBlank()) return null

        val currentCount = (readCounts[normalized] ?: 0) + 1
        readCounts[normalized] = currentCount

        return when {
            currentCount in 4..6 -> {
                "\n\n[ADVISORY: You have reviewed '$normalized' $currentCount times. The target lines should now be clear. Proceed with 'edit_file' or 'create_file' to apply your modifications.]"
            }
            else -> null
        }
    }

    private fun normalize(path: String?): String {
        if (path.isNullOrBlank()) return ""
        return path.replace("\\", "/").trim().removePrefix("./").removePrefix("/")
    }
}
