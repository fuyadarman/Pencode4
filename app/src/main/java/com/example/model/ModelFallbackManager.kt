package com.example.model

import android.content.Context
import android.content.SharedPreferences
import com.example.ui.CustomModelConfig

/**
 * ModelFallbackManager
 * Manages model auto-switching and fallback on execution errors/problems.
 * Allows users to configure fallback models in Settings so that if a model encounters
 * an error, rate limit, timeout, or repeated failure, the agent automatically tries
 * each configured fallback model sequentially.
 */
object ModelFallbackManager {

    private const val PREFS_NAME = "vibe_model_fallback_prefs"
    private const val KEY_AUTO_SWITCH_ENABLED = "key_auto_switch_on_error"
    private const val KEY_FALLBACK_MODEL_IDS = "key_selected_fallback_model_ids"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Checks whether automatic model switching on error is enabled.
     */
    fun isAutoSwitchEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_AUTO_SWITCH_ENABLED, false)
    }

    /**
     * Sets whether automatic model switching on error is enabled.
     */
    fun setAutoSwitchEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_AUTO_SWITCH_ENABLED, enabled).apply()
    }

    /**
     * Returns the set of selected fallback model IDs or aliases.
     */
    fun getSelectedFallbackModelIds(context: Context): Set<String> {
        val set = getPrefs(context).getStringSet(KEY_FALLBACK_MODEL_IDS, emptySet())
        return set?.toSet() ?: emptySet()
    }

    /**
     * Sets the enabled fallback model IDs.
     */
    fun setSelectedFallbackModelIds(context: Context, modelIds: Set<String>) {
        getPrefs(context).edit().putStringSet(KEY_FALLBACK_MODEL_IDS, modelIds).apply()
    }

    /**
     * Toggles a specific model ID in the fallback list.
     */
    fun toggleFallbackModel(context: Context, modelId: String, selected: Boolean) {
        val current = getSelectedFallbackModelIds(context).toMutableSet()
        if (selected) {
            current.add(modelId)
        } else {
            current.remove(modelId)
        }
        setSelectedFallbackModelIds(context, current)
    }

    /**
     * Resolves the next untried fallback model from the user's selected list.
     * Returns null if no eligible fallback model is remaining.
     */
    fun getNextFallbackModel(
        context: Context,
        currentModelId: String,
        availableModels: List<CustomModelConfig>,
        triedModelIds: Set<String>
    ): CustomModelConfig? {
        if (!isAutoSwitchEnabled(context)) return null

        val selectedIds = getSelectedFallbackModelIds(context)
        if (selectedIds.isEmpty()) return null

        // Filter models that are selected, exist in available configurations, and have not yet been tried
        return availableModels.firstOrNull { model ->
            (selectedIds.contains(model.id) || selectedIds.contains(model.modelId) || selectedIds.contains(model.alias)) &&
                    model.id != currentModelId &&
                    model.modelId != currentModelId &&
                    !triedModelIds.contains(model.id) &&
                    !triedModelIds.contains(model.modelId)
        }
    }
}
