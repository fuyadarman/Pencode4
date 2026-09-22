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
            currentCount == 2 -> {
                "\n\n[CRITICAL SYSTEM DIRECTIVE: You have now read '$normalized' twice. The entire contents and structure of this file are ALREADY fully provided in your conversation context above. You are STRICTLY FORBIDDEN from reading this file again. You MUST proceed immediately to either:\n1. Apply your necessary code modifications using 'edit_file', 'multi_edit_file', or 'create_file', OR\n2. Call 'complete' if no code edits are needed.\nDo NOT call read_file or read_file_range on this file again.]"
            }
            currentCount >= 3 -> {
                "\n\n[STRICT REPETITION BLOCK: You have read '$normalized' $currentCount times. Reading is blocked. You MUST immediately execute your edits via 'edit_file'/'create_file' or call 'complete' now.]"
            }
            else -> null
        }
    }

    private fun normalize(path: String?): String {
        if (path.isNullOrBlank()) return ""
        return path.replace("\\", "/").trim().removePrefix("./").removePrefix("/")
    }
}
