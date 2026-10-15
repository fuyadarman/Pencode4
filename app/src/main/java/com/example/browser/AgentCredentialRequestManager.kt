package com.example.browser

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Data model for a single credential or sensitive input field.
 */
data class CredentialField(
    val id: String,
    val label: String,
    val isSecret: Boolean = false,
    val placeholder: String = "",
    val defaultValue: String = ""
)

/**
 * Request payload when AI agent creates a dynamic input bar asking user for credentials, OTP, personal data, shipping address, API keys, etc.
 */
data class AgentCredentialRequest(
    val requestId: String,
    val title: String,
    val description: String,
    val serviceName: String = "",
    val fields: List<CredentialField> = listOf(
        CredentialField("username", "Email / Username", isSecret = false),
        CredentialField("password", "Password", isSecret = true)
    ),
    val deferredResponse: CompletableDeferred<Map<String, String>?> = CompletableDeferred()
)

/**
 * Global singleton managing in-flight input & credential requests from AI agent.
 */
object AgentCredentialRequestManager {

    private val _activeRequest = MutableStateFlow<AgentCredentialRequest?>(null)
    val activeRequest: StateFlow<AgentCredentialRequest?> = _activeRequest.asStateFlow()

    /**
     * Parses flexible field specifications provided by AI agent.
     */
    fun parseDynamicFields(fieldStrings: List<String>?, defaultService: String = ""): List<CredentialField> {
        if (fieldStrings.isNullOrEmpty()) {
            return listOf(
                CredentialField("username", "Email / Username", isSecret = false, placeholder = "user@example.com"),
                CredentialField("password", "Password", isSecret = true, placeholder = "••••••••")
            )
        }

        return fieldStrings.mapIndexed { idx, spec ->
            val clean = spec.trim()
            val isSecret = clean.contains("password", ignoreCase = true) ||
                           clean.contains("pin", ignoreCase = true) ||
                           clean.contains("otp", ignoreCase = true) ||
                           clean.contains("secret", ignoreCase = true) ||
                           clean.contains("token", ignoreCase = true) ||
                           clean.contains("cvv", ignoreCase = true) ||
                           clean.contains("key", ignoreCase = true)

            val id = clean.lowercase().replace(Regex("[^a-z0-9_]"), "_").ifBlank { "field_$idx" }
            val label = clean.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            val placeholder = if (isSecret) "••••••••" else "Enter $label"

            CredentialField(
                id = id,
                label = label,
                isSecret = isSecret,
                placeholder = placeholder
            )
        }
    }

    /**
     * Called by the AI agent to request dynamic user inputs/credentials.
     * Suspends until user submits the interactive bar or cancels.
     */
    suspend fun requestCredentials(
        title: String,
        description: String,
        serviceName: String = "",
        fields: List<CredentialField> = listOf(
            CredentialField("username", "Email / Username", isSecret = false),
            CredentialField("password", "Password", isSecret = true)
        )
    ): Map<String, String>? {
        val reqId = "cred_${System.currentTimeMillis()}"
        val deferred = CompletableDeferred<Map<String, String>?>()
        val request = AgentCredentialRequest(
            requestId = reqId,
            title = title,
            description = description,
            serviceName = serviceName,
            fields = fields,
            deferredResponse = deferred
        )

        _activeRequest.value = request

        return try {
            deferred.await()
        } finally {
            if (_activeRequest.value?.requestId == reqId) {
                _activeRequest.value = null
            }
        }
    }

    /**
     * Called when the user clicks 'Submit' in the interactive credential prompt bar.
     */
    fun submitCredentials(requestId: String, data: Map<String, String>) {
        val current = _activeRequest.value
        if (current != null && current.requestId == requestId) {
            current.deferredResponse.complete(data)
            _activeRequest.value = null
        }
    }

    /**
     * Called when user dismisses or declines the credential prompt.
     */
    fun dismiss(requestId: String) {
        val current = _activeRequest.value
        if (current != null && current.requestId == requestId) {
            current.deferredResponse.complete(null)
            _activeRequest.value = null
        }
    }
}
