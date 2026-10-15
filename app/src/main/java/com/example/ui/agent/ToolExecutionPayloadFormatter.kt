package com.example.ui.agent

data class FormattedToolPayload(
    val hasInput: Boolean = false,
    val inputTitle: String = "Input",
    val inputContent: String = "",
    val hasOutput: Boolean = true,
    val outputTitle: String = "Output / Payload",
    val outputContent: String = "",
    val codeBlockLanguage: String? = null
)

/**
 * Modular formatter for detailed expandable tool execution cards.
 * Ensures the user sees complete code chunks, line ranges, inputs, and outputs.
 */
object ToolExecutionPayloadFormatter {

    fun format(
        title: String,
        details: String?,
        inputPayload: String? = null,
        outputPayload: String? = null,
        lineRange: String? = null
    ): FormattedToolPayload {
        val safeDetails = details.orEmpty().trim()
        val safeInput = inputPayload.orEmpty().trim()
        val safeOutput = outputPayload.orEmpty().trim()
        val lowerTitle = title.lowercase()

        // 1. If explicit input and output payloads were provided
        if (safeInput.isNotBlank() && safeOutput.isNotBlank()) {
            val inputHeader = when {
                lowerTitle.contains("edit") || lowerTitle.contains("patch") -> "Target & Replacement Chunks"
                lowerTitle.contains("read") -> "Read Target"
                lowerTitle.contains("command") || lowerTitle.contains("shell") -> "Command"
                else -> "Input Parameters"
            }
            val outputHeader = when {
                lowerTitle.contains("read") -> "Code Block ($lineRange)"
                lowerTitle.contains("edit") || lowerTitle.contains("patch") -> "Execution Result"
                lowerTitle.contains("all tools") -> "Tools Specifications"
                lowerTitle.contains("scan") || lowerTitle.contains("directory") -> "Directory Contents"
                else -> "Output / Payload"
            }
            return FormattedToolPayload(
                hasInput = true,
                inputTitle = inputHeader,
                inputContent = safeInput,
                hasOutput = true,
                outputTitle = outputHeader,
                outputContent = safeOutput
            )
        }

        // 2. If only outputPayload is provided
        if (safeOutput.isNotBlank()) {
            return FormattedToolPayload(
                hasInput = safeInput.isNotBlank(),
                inputTitle = "Input",
                inputContent = safeInput,
                hasOutput = true,
                outputTitle = "Output / Payload",
                outputContent = safeOutput
            )
        }

        // 3. Fallback: Parse from title, details and lineRange
        return when {
            lowerTitle.contains("all tools") || lowerTitle.contains("tool registry") -> {
                FormattedToolPayload(
                    hasInput = false,
                    hasOutput = true,
                    outputTitle = "Tools Catalog & Specifications",
                    outputContent = if (safeDetails.isNotBlank() && !safeDetails.equals("Retrieved all tools specifications", ignoreCase = true)) {
                        safeDetails
                    } else {
                        com.example.agent.AgentToolRegistryEngine.ALL_TOOLS_DOCUMENTATION
                    }
                )
            }
            lowerTitle.contains("read") -> {
                FormattedToolPayload(
                    hasInput = lineRange != null && lineRange.isNotBlank(),
                    inputTitle = "Range",
                    inputContent = lineRange.orEmpty(),
                    hasOutput = true,
                    outputTitle = "Read Code Block",
                    outputContent = safeDetails
                )
            }
            lowerTitle.contains("edit") || lowerTitle.contains("patch") -> {
                FormattedToolPayload(
                    hasInput = true,
                    inputTitle = "Code Modification",
                    inputContent = if (safeInput.isNotBlank()) safeInput else safeDetails,
                    hasOutput = true,
                    outputTitle = "Status",
                    outputContent = if (safeOutput.isNotBlank()) safeOutput else "Code changes applied to workspace."
                )
            }
            lowerTitle.contains("command") || lowerTitle.contains("shell") -> {
                if (safeDetails.startsWith("> ")) {
                    val cmd = safeDetails.lineSequence().firstOrNull()?.removePrefix("> ") ?: ""
                    val output = safeDetails.substringAfter("\n\n", "(no output)")
                    FormattedToolPayload(
                        hasInput = true,
                        inputTitle = "Shell Command",
                        inputContent = cmd,
                        hasOutput = true,
                        outputTitle = "Terminal Output",
                        outputContent = output
                    )
                } else {
                    FormattedToolPayload(
                        hasInput = false,
                        hasOutput = true,
                        outputTitle = "Terminal Output",
                        outputContent = safeDetails
                    )
                }
            }
            else -> {
                FormattedToolPayload(
                    hasInput = false,
                    hasOutput = safeDetails.isNotBlank(),
                    outputTitle = "Output / Payload",
                    outputContent = safeDetails
                )
            }
        }
    }
}
