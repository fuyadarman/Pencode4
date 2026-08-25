package com.example.agent.harness

import android.util.Log
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.util.UUID

/**
 * Parallel Sub-Agent / Teammate Spawning Coordinator (Jcode Harness Architecture).
 * Enables the Main Agent to spawn specialized child agents running subtasks in parallel
 * (e.g. Frontend Sub-Agent, Backend Sub-Agent, Testing Sub-Agent, Reviewer Sub-Agent).
 */
object ParallelSubAgentCoordinator {
    private const val TAG = "ParallelAgentCoord"

    enum class SubAgentRole(val displayName: String, val systemPersona: String) {
        FRONTEND(
            "Frontend Agent",
            "You are a specialized Frontend Sub-Agent. Focus on Jetpack Compose UI/UX, layouts, Material 3 theming, animations, responsiveness, and stateful widgets."
        ),
        BACKEND(
            "Backend Agent",
            "You are a specialized Backend & Data Sub-Agent. Focus on Room Database, REST/Ktor/Retrofit API integration, repositories, state managers, and business logic."
        ),
        TESTING(
            "Testing Agent",
            "You are a specialized Testing Sub-Agent. Focus on Robolectric unit tests, edge-case validations, error handling coverage, and regression prevention."
        ),
        CODE_REVIEWER(
            "Reviewer Agent",
            "You are a specialized Code Review & Quality Sub-Agent. Focus on code smell detection, type safety, memory leak prevention, and performance optimization."
        ),
        MCP_SPECIALIST(
            "MCP Specialist",
            "You are a specialized Model Context Protocol (MCP) Sub-Agent. Focus on MCP server tool calls, schema verification, database operations, and external service bridging."
        )
    }

    data class SubAgentTask(
        val id: String = UUID.randomUUID().toString().take(8),
        val title: String,
        val role: SubAgentRole,
        val prompt: String,
        var status: String = "pending", // pending, running, completed, failed
        var resultSummary: String? = null,
        var logs: MutableList<String> = mutableListOf()
    )

    data class MultiAgentExecutionReport(
        val taskId: String,
        val totalSubAgents: Int,
        val successfulSubAgents: Int,
        val reports: List<SubAgentTask>,
        val mergedSynthesis: String
    )

    /**
     * Identifies if a complex user prompt benefits from multi-agent parallel decomposition.
     */
    fun shouldDecomposeIntoParallelAgents(userPrompt: String): Boolean {
        val p = userPrompt.lowercase()
        val hasMultiAspects = (p.contains("frontend") && p.contains("backend")) ||
                (p.contains("ui") && p.contains("database")) ||
                (p.contains("test") && (p.contains("build") || p.contains("feature") || p.contains("create"))) ||
                (p.contains("fullstack") || p.contains("complete system") || p.contains("refactor and test") || p.contains("parallel"))
        return hasMultiAspects && userPrompt.length > 50
    }

    /**
     * Automatically decomposes a complex task into discrete parallel sub-agent plans.
     */
    fun planSubAgentTasks(userPrompt: String, projectName: String): List<SubAgentTask> {
        val p = userPrompt.lowercase()
        val tasks = mutableListOf<SubAgentTask>()

        if (p.contains("ui") || p.contains("screen") || p.contains("frontend") || p.contains("design") || p.contains("layout")) {
            tasks.add(
                SubAgentTask(
                    title = "Frontend & UI Implementation",
                    role = SubAgentRole.FRONTEND,
                    prompt = "Implement or polish all Compose UI components, layout structures, and styling for: $userPrompt"
                )
            )
        }

        if (p.contains("database") || p.contains("room") || p.contains("api") || p.contains("backend") || p.contains("repository") || p.contains("data") || p.contains("model")) {
            tasks.add(
                SubAgentTask(
                    title = "Backend & Data Layer Architecture",
                    role = SubAgentRole.BACKEND,
                    prompt = "Structure entities, DAOs, repositories, and API clients needed for: $userPrompt"
                )
            )
        }

        if (p.contains("test") || p.contains("unit test") || p.contains("robolectric") || p.contains("verify") || p.contains("quality")) {
            tasks.add(
                SubAgentTask(
                    title = "Testing & Verification Coverage",
                    role = SubAgentRole.TESTING,
                    prompt = "Design unit tests, Robolectric test cases, and assertion logic for: $userPrompt"
                )
            )
        }

        // Fallback default two-tier agent orchestration if not explicitly specified
        if (tasks.isEmpty()) {
            tasks.add(
                SubAgentTask(
                    title = "Core Architecture & Logic",
                    role = SubAgentRole.BACKEND,
                    prompt = "Develop core logic and structure for: $userPrompt"
                )
            )
            tasks.add(
                SubAgentTask(
                    title = "UI & User Experience",
                    role = SubAgentRole.FRONTEND,
                    prompt = "Build intuitive Jetpack Compose interface for: $userPrompt"
                )
            )
        }

        return tasks
    }

    /**
     * Executes sub-agents concurrently with individual scoped prompts and merges their synthesized findings.
     */
    suspend fun executeParallelSubAgents(
        tasks: List<SubAgentTask>,
        projectName: String,
        executor: suspend (subAgent: SubAgentTask) -> String
    ): MultiAgentExecutionReport = coroutineScope {
        Log.d(TAG, "Launching ${tasks.size} parallel sub-agents for project $projectName")

        val deferredResults = tasks.map { task ->
            async {
                task.status = "running"
                try {
                    val result = executor(task)
                    task.status = "completed"
                    task.resultSummary = result
                    task
                } catch (e: Exception) {
                    task.status = "failed"
                    task.resultSummary = "Sub-agent error: ${e.localizedMessage}"
                    task
                }
            }
        }

        val completedTasks = deferredResults.awaitAll()
        val successCount = completedTasks.count { it.status == "completed" }

        val synthesisBuilder = StringBuilder()
        synthesisBuilder.append("=== PARALLEL SUB-AGENT SYNTHESIS (Jcode Multi-Agent Coordination) ===\n")
        synthesisBuilder.append("Main Agent successfully orchestrated ${completedTasks.size} parallel teammate agents:\n\n")

        for (task in completedTasks) {
            val statusIcon = if (task.status == "completed") "✓" else "✗"
            synthesisBuilder.append("[$statusIcon ${task.role.displayName}] ${task.title}\n")
            synthesisBuilder.append("Status: ${task.status.uppercase()}\n")
            synthesisBuilder.append("Findings & Output:\n${task.resultSummary?.trim() ?: "No output generated"}\n\n")
        }
        synthesisBuilder.append("=== END SUB-AGENT SYNTHESIS ===\n")

        MultiAgentExecutionReport(
            taskId = "multi_${System.currentTimeMillis()}",
            totalSubAgents = completedTasks.size,
            successfulSubAgents = successCount,
            reports = completedTasks,
            mergedSynthesis = synthesisBuilder.toString()
        )
    }
}
