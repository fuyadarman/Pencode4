package com.example.agent.multiagent

/**
 * Data model for an AI Agent that can be created, configured, or contacted by the system.
 */
data class CustomAgent(
    val id: String,
    val name: String,
    val role: String,
    val description: String,
    val systemPrompt: String,
    val capabilities: List<String> = emptyList(),
    val iconEmoji: String = "🤖",
    val modelId: String = "gemini-2.5-flash",
    val isEnabled: Boolean = true,
    val isCustom: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Inter-Agent message model when AI agents communicate with each other.
 */
data class AgentInterMessage(
    val id: String = "msg_${System.currentTimeMillis()}",
    val fromAgentId: String,
    val toAgentId: String,
    val taskTitle: String,
    val content: String,
    val status: String = "pending", // pending, in_progress, completed, failed
    val result: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)
