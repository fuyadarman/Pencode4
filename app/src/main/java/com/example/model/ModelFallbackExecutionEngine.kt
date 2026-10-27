package com.example.model

import android.content.Context
import com.example.api.Content
import com.example.api.Part
import com.example.ui.AiActionLog
import com.example.ui.CustomModelConfig

/**
 * ModelFallbackExecutionEngine
 * Extracted execution engine to perform seamless fallback switching
 * without bloating ViewModel bytecode method sizes.
 */
object ModelFallbackExecutionEngine {

    data class SwitchResult(
        val newConfig: CustomModelConfig,
        val switchLog: AiActionLog,
        val systemNotice: Content
    )

    fun attemptAutoSwitch(
        context: Context,
        currentModelId: String,
        currentRunningModelName: String,
        availableModels: List<CustomModelConfig>,
        triedModelIds: MutableSet<String>,
        errorMessage: String?,
        createAiLog: (String, String, String) -> AiActionLog
    ): SwitchResult? {
        if (!ModelFallbackManager.isAutoSwitchEnabled(context)) return null

        val fallback = ModelFallbackManager.getNextFallbackModel(
            context = context,
            currentModelId = currentModelId,
            availableModels = availableModels,
            triedModelIds = triedModelIds
        ) ?: return null

        triedModelIds.add(fallback.id)
        triedModelIds.add(fallback.modelId)

        val errDesc = errorMessage ?: "error"
        val log = createAiLog(
            "Auto-Switching Model",
            "thinking",
            "Model '$currentRunningModelName' encountered issue: $errDesc. Auto-switching to fallback model '${fallback.alias}' (${fallback.modelId})."
        )
        val notice = Content(
            role = "user",
            parts = listOf(Part(text = "System Notice: Switched to fallback model '${fallback.alias}' (${fallback.modelId}). Please resume fulfilling the user's request."))
        )

        return SwitchResult(
            newConfig = fallback,
            switchLog = log,
            systemNotice = notice
        )
    }
}
