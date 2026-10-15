package com.example.agent.schedule

/**
 * Data model for an autonomous scheduled task assigned to an AI agent.
 */
data class ScheduledAgentTask(
    val id: String = "task_${System.currentTimeMillis()}",
    val title: String,
    val prompt: String,
    val assignedAgentId: String = "agent_shopper",
    val triggerTimeMillis: Long,
    val intervalMinutes: Long? = null, // null for one-time, >0 for recurring
    val isRecurring: Boolean = false,
    val isActive: Boolean = true,
    val lastExecutedMillis: Long? = null,
    val lastExecutionResult: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
