package com.example.settings

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * CustomInstructionEngine
 * Dedicated engine for managing and persisting user-defined custom AI instructions.
 * These instructions are automatically injected into AI prompts to customize behavior,
 * coding style, language, and guidelines.
 */
object CustomInstructionEngine {

    private const val PREFS_NAME = "vibe_custom_instructions_prefs"
    private const val KEY_CUSTOM_INSTRUCTIONS = "user_custom_instructions"

    private val _customInstructions = MutableStateFlow("")
    val customInstructions: StateFlow<String> = _customInstructions.asStateFlow()

    private var isInitialized = false

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun initialize(context: Context) {
        if (!isInitialized) {
            val saved = getPrefs(context).getString(KEY_CUSTOM_INSTRUCTIONS, "") ?: ""
            _customInstructions.value = saved
            isInitialized = true
        }
    }

    fun getCustomInstructions(context: Context): String {
        initialize(context)
        return _customInstructions.value
    }

    fun saveCustomInstructions(context: Context, instructions: String) {
        val trimmed = instructions.trim()
        getPrefs(context).edit().putString(KEY_CUSTOM_INSTRUCTIONS, trimmed).apply()
        _customInstructions.value = trimmed
    }

    fun formatCustomInstructionsForPrompt(context: Context? = null): String {
        val instructions = if (context != null) {
            getCustomInstructions(context)
        } else {
            _customInstructions.value
        }

        if (instructions.isBlank()) return ""

        return buildString {
            append("\n=== USER CUSTOM INSTRUCTIONS (MANDATORY GUIDELINES) ===\n")
            append("The user has specified the following persistent custom instructions. Follow them strictly:\n")
            append(instructions)
            append("\n\n")
        }
    }
}
