package com.example.browser

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Intelligent detector that monitors AI responses.
 * If the AI asks the user for credentials, login info, passwords, OTP, API keys, or required inputs
 * in text without using a tool call, this detector automatically creates the interactive input/credential bar
 * and feeds the submitted data back to the AI session.
 */
object AgentInputPromptDetector {

    private val credentialKeywords = listOf(
        "password",
        "username",
        "credentials",
        "login details",
        "email and password",
        "enter your api key",
        "provide your token",
        "enter your otp",
        "verification code",
        "secret key"
    )

    fun checkAndTriggerInputPrompt(
        aiMessage: String,
        scope: CoroutineScope,
        onInputSubmitted: (String) -> Unit
    ) {
        if (AgentCredentialRequestManager.activeRequest.value != null) return
        val lower = aiMessage.lowercase()

        // Check if message is a prompt/question asking user for sensitive info
        val isAskingForCredentials = credentialKeywords.any { lower.contains(it) } &&
                (lower.contains("please provide") ||
                 lower.contains("enter your") ||
                 lower.contains("need your") ||
                 lower.contains("give me your") ||
                 lower.contains("what is your") ||
                 lower.contains("login to") ||
                 lower.contains("sign in to") ||
                 lower.contains("type your") ||
                 lower.contains("input your"))

        if (isAskingForCredentials) {
            val hasPassword = lower.contains("password") || lower.contains("secret") || lower.contains("pin")
            val hasApiKey = lower.contains("api key") || lower.contains("token") || lower.contains("key")
            val hasOtp = lower.contains("otp") || lower.contains("code")

            val fields = when {
                hasOtp -> listOf(
                    CredentialField("otp", "OTP / Verification Code", isSecret = false, placeholder = "e.g. 123456")
                )
                hasApiKey -> listOf(
                    CredentialField("api_key", "API Key / Token", isSecret = true, placeholder = "Enter API Key...")
                )
                else -> listOf(
                    CredentialField("username", "Email or Username", isSecret = false, placeholder = "user@example.com"),
                    CredentialField("password", "Password", isSecret = true, placeholder = "••••••••")
                )
            }

            val title = when {
                hasOtp -> "Verification Code Required"
                hasApiKey -> "API Key / Token Required"
                else -> "Credentials Required"
            }

            scope.launch(Dispatchers.Main) {
                val result = AgentCredentialRequestManager.requestCredentials(
                    title = title,
                    description = aiMessage.take(160),
                    serviceName = "Agent Task",
                    fields = fields
                )

                if (result != null && result.isNotEmpty()) {
                    val formatted = result.entries.joinToString(", ") { "${it.key}: ${it.value}" }
                    onInputSubmitted("[User Provided Input]: $formatted")
                }
            }
        }
    }
}
