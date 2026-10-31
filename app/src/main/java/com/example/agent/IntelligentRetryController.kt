package com.example.agent

import com.example.api.ToolArguments
import java.security.MessageDigest
import java.util.Locale

/**
 * IntelligentRetryController
 *
 * Implements a runtime/harness-level execution gate to prevent Agent Looping,
 * Repeated Tool Execution, and Context Regression.
 *
 * Features:
 * - Deterministic tool call fingerprinting (tool, target, arguments_hash, result, repeat_count).
 * - Distinguishes transient errors (e.g., network timeout, 429) from deterministic errors (e.g., permission denied, not found).
 * - Multi-stage intervention:
 *   - 1st failure: Normal execution & record fingerprint.
 *   - 2nd failure: Injects failure analysis requirement to force AI strategy change.
 *   - 3rd attempt: HARD RUNTIME BLOCK preventing re-execution of identical failing arguments.
 * - Forces AI to adopt alternative strategies or mark task step as blocked.
 */
object IntelligentRetryController {

    data class ToolFingerprint(
        val tool: String,
        val target: String,
        val argumentsHash: String,
        var lastError: String,
        var errorType: String,
        var isTransient: Boolean,
        var repeatCount: Int = 1,
        var stateChanged: Boolean = false,
        var timestamp: Long = System.currentTimeMillis()
    )

    sealed class GateVerdict {
        object Allow : GateVerdict()
        data class WarnAndAnalyze(val message: String) : GateVerdict()
        data class Block(val reason: String, val simulatedToolOutput: String) : GateVerdict()
    }

    private val failureRegistry = mutableMapOf<String, ToolFingerprint>()

    /**
     * Resets failure tracking state at the beginning of a new session/prompt.
     */
    fun resetSession() {
        failureRegistry.clear()
    }

    /**
     * Generates a stable SHA-256 hash of the tool's input arguments.
     */
    fun computeArgumentsHash(tool: String, args: ToolArguments?): String {
        if (args == null) return "empty"
        val payload = buildString {
            append(tool)
            append(":")
            append(args.path ?: args.targetFile ?: args.filePath ?: "")
            append(":")
            append(args.content ?: args.targetContent ?: args.replacementContent ?: "")
            append(":")
            append(args.command ?: args.query ?: args.selector ?: "")
            append(":")
            append(args.startLine ?: "")
            append(":")
            append(args.endLine ?: "")
        }
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(payload.toByteArray(Charsets.UTF_8))
            hash.joinToString("") { "%02x".format(it) }.take(12)
        } catch (_: Exception) {
            payload.hashCode().toString()
        }
    }

    /**
     * Resolves the primary target resource (file path, command, query).
     */
    fun resolveTarget(args: ToolArguments?): String {
        return (args?.path ?: args?.targetFile ?: args?.filePath ?: args?.command ?: args?.query ?: args?.destinationPath ?: "").trim()
    }

    /**
     * Determines whether an error message is transient (retryable) or deterministic (permanent).
     */
    fun isTransientError(errorText: String): Boolean {
        val lower = errorText.lowercase(Locale.ROOT)
        return lower.contains("timeout") ||
                lower.contains("timed out") ||
                lower.contains("econnreset") ||
                lower.contains("connection reset") ||
                lower.contains("temporary") ||
                lower.contains("429") ||
                lower.contains("503") ||
                lower.contains("502") ||
                lower.contains("504") ||
                lower.contains("rate limit") ||
                lower.contains("overloaded") ||
                lower.contains("resource_exhausted")
    }

    /**
     * Pre-execution Runtime Gate: Evaluates incoming tool call against failed fingerprints.
     */
    fun evaluatePreExecution(tool: String, args: ToolArguments?): GateVerdict {
        val normalizedTool = tool.trim().lowercase(Locale.ROOT)
        if (normalizedTool == "complete" || normalizedTool == "ai_think" || normalizedTool == "ai_response") {
            return GateVerdict.Allow
        }

        val target = resolveTarget(args)
        val argsHash = computeArgumentsHash(normalizedTool, args)
        val fingerprintKey = "$normalizedTool::$target::$argsHash"

        val recorded = failureRegistry[fingerprintKey] ?: return GateVerdict.Allow

        // Non-transient deterministic errors (e.g. Permission denied, File protected, File not found)
        if (!recorded.isTransient) {
            if (recorded.repeatCount >= 2) {
                // HARD RUNTIME BLOCK on 3rd attempt
                val blockedOutput = buildString {
                    appendLine("[RUNTIME HARNESS BLOCKED: REPEATED FAILURE]")
                    appendLine("Action: '$tool' on target '$target'")
                    appendLine("Arguments Hash: $argsHash")
                    appendLine("Consecutive Failures: ${recorded.repeatCount}")
                    appendLine("Last Error: ${recorded.lastError}")
                    appendLine("Status: PERMANENTLY BLOCKED AT HARNESS LEVEL.")
                    appendLine("Instruction: Executing the exact same call with identical arguments is forbidden because it produces deterministic errors. You MUST adopt an alternative approach (e.g. check directory, use a different path/tool, modify arguments) or invoke 'complete' with a clear explanation.")
                }.trim()

                return GateVerdict.Block(
                    reason = "Tool '$tool' on '$target' blocked after ${recorded.repeatCount} repeated failures.",
                    simulatedToolOutput = blockedOutput
                )
            } else if (recorded.repeatCount == 1) {
                // 2nd Attempt: Force Failure Analysis
                val warning = "[FAILURE ANALYSIS REQUIRED] The previous attempt of '$tool' on '$target' failed with: '${recorded.lastError}'. Analyze the failure reason before proceeding and do not repeat identical failing parameters."
                return GateVerdict.WarnAndAnalyze(warning)
            }
        } else {
            // Transient errors allow up to 3 retries before halting
            if (recorded.repeatCount >= 3) {
                val blockedOutput = "[RUNTIME HARNESS BLOCKED: TRANSIENT TIMEOUT LIMIT] Tool '$tool' on '$target' exceeded transient retry limits (${recorded.repeatCount} retries). Please switch to an alternative or conclude the task."
                return GateVerdict.Block(
                    reason = "Transient retry limit exceeded for '$tool' on '$target'.",
                    simulatedToolOutput = blockedOutput
                )
            }
        }

        return GateVerdict.Allow
    }

    /**
     * Post-execution Recorder: Records the actual outcome and updates the fingerprint registry.
     */
    fun recordOutcome(tool: String, args: ToolArguments?, result: String) {
        val normalizedTool = tool.trim().lowercase(Locale.ROOT)
        if (normalizedTool == "complete" || normalizedTool == "ai_think" || normalizedTool == "ai_response") {
            return
        }

        val target = resolveTarget(args)
        val argsHash = computeArgumentsHash(normalizedTool, args)
        val fingerprintKey = "$normalizedTool::$target::$argsHash"

        val isFailure = isExecutionFailure(result)

        if (isFailure) {
            val transient = isTransientError(result)
            val errType = if (transient) "transient_network_error" else categorizeError(result)

            val existing = failureRegistry[fingerprintKey]
            if (existing != null) {
                existing.repeatCount++
                existing.lastError = result.take(300)
                existing.timestamp = System.currentTimeMillis()
            } else {
                failureRegistry[fingerprintKey] = ToolFingerprint(
                    tool = normalizedTool,
                    target = target,
                    argumentsHash = argsHash,
                    lastError = result.take(300),
                    errorType = errType,
                    isTransient = transient,
                    repeatCount = 1,
                    stateChanged = false
                )
            }
        } else {
            // Success: clear failure fingerprint for this specific argument hash
            failureRegistry.remove(fingerprintKey)
        }
    }

    private fun isExecutionFailure(result: String): Boolean {
        val clean = result.trim()
        return clean.startsWith("Error", ignoreCase = true) ||
                clean.startsWith("Failed", ignoreCase = true) ||
                clean.contains("Permission denied", ignoreCase = true) ||
                clean.contains("does not exist", ignoreCase = true) ||
                clean.contains("TargetContent not found", ignoreCase = true) ||
                clean.contains("protected and cannot", ignoreCase = true) ||
                clean.contains("Exit code: 1", ignoreCase = true) ||
                clean.contains("exit status 1", ignoreCase = true) ||
                clean.contains("Tool Argument Validation Error", ignoreCase = true) ||
                clean.contains("could not find", ignoreCase = true)
    }

    private fun categorizeError(errorText: String): String {
        val lower = errorText.lowercase(Locale.ROOT)
        return when {
            lower.contains("permission") || lower.contains("eacces") -> "permission_denied"
            lower.contains("protected") -> "protected_resource"
            lower.contains("not found") || lower.contains("does not exist") -> "resource_not_found"
            lower.contains("syntax") || lower.contains("parse") -> "syntax_validation_error"
            lower.contains("conflict") || lower.contains("already exists") -> "state_conflict"
            else -> "generic_execution_failure"
        }
    }
}
