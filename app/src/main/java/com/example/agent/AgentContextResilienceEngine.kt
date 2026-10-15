package com.example.agent

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * AgentContextResilienceEngine
 * 
 * Strengthens the AI agent's context window and prevents infinite loops:
 * 1. Maintains an accurate, live Working Set of inspected and modified files in the active session.
 * 2. Injects a high-clarity Context State Banner so the model always knows exactly what files
 *    are in its context and what changes have already succeeded.
 * 3. Provides a Robust Anti-Loop Circuit Breaker that detects repetitive read/search cycles
 *    and firmly directs the model to either edit code or call 'complete'.
 */
object AgentContextResilienceEngine {

    private val inspectedFiles = ConcurrentHashMap.newKeySet<String>()
    private val modifiedFiles = ConcurrentHashMap.newKeySet<String>()
    private val consecutiveReadsWithoutEdit = AtomicInteger(0)
    private val actionSignatures = mutableListOf<String>()

    fun resetSession() {
        inspectedFiles.clear()
        modifiedFiles.clear()
        consecutiveReadsWithoutEdit.set(0)
        actionSignatures.clear()
    }

    fun recordFileRead(path: String) {
        val normalized = normalize(path)
        if (normalized.isNotBlank()) {
            inspectedFiles.add(normalized)
            consecutiveReadsWithoutEdit.incrementAndGet()
        }
    }

    fun recordFileModified(path: String) {
        val normalized = normalize(path)
        if (normalized.isNotBlank()) {
            modifiedFiles.add(normalized)
            consecutiveReadsWithoutEdit.set(0)
        }
    }

    /**
     * Builds a high-clarity context state banner to be appended to conversation history.
     */
    fun buildContextStateBanner(): String {
        if (inspectedFiles.isEmpty() && modifiedFiles.isEmpty()) return ""

        val sb = StringBuilder()
        sb.append("\n\n=== ACTIVE SESSION CONTEXT STATE ===\n")
        if (inspectedFiles.isNotEmpty()) {
            sb.append("• Inspected files in current session: ")
                .append(inspectedFiles.take(8).joinToString(", "))
                .append(if (inspectedFiles.size > 8) " (+${inspectedFiles.size - 8} more)" else "")
                .append("\n")
        }
        if (modifiedFiles.isNotEmpty()) {
            sb.append("• Successfully modified files: ")
                .append(modifiedFiles.joinToString(", "))
                .append("\n")
        }
        val uneditedReads = consecutiveReadsWithoutEdit.get()
        if (uneditedReads >= 3) {
            sb.append("• Notice: You have performed $uneditedReads consecutive file inspections without applying edits. All necessary code context is already provided above. Proceed directly to 'edit_file' or 'complete'.\n")
        }
        sb.append("=====================================\n")
        return sb.toString()
    }

    /**
     * Checks for infinite repetition loops across actions.
     * Returns a targeted anti-loop intervention message if a loop is detected.
     */
    fun evaluateAntiLoop(tool: String, target: String?): String? {
        val normTarget = normalize(target ?: "")
        val sig = "$tool:$normTarget"
        actionSignatures.add(sig)

        // Check if the exact same tool + target was invoked 3 times in the last 4 actions
        if (actionSignatures.size >= 3) {
            val lastThree = actionSignatures.takeLast(3)
            if (lastThree.all { it == sig }) {
                return "[ANTI-LOOP CIRCUIT BREAKER: You have executed '$tool' on '$normTarget' 3 times in a row. Stop repeating this action. You have the context needed. Proceed immediately to apply code changes with 'edit_file' or 'create_file', or invoke 'complete' if done.]"
            }
        }

        // Check if 5+ reads occurred without any edits
        if (consecutiveReadsWithoutEdit.get() >= 5) {
            return "[ANTI-LOOP NOTICE: 5 consecutive file reads reached without modifications. The full code is visible in your context window. Do not read further files. Execute 'edit_file' now.]"
        }

        return null
    }

    private fun normalize(path: String): String {
        return path.replace("\\", "/").trim().removePrefix("./").removePrefix("/")
    }
}
