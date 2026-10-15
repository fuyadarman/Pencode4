package com.example.agent

import java.util.concurrent.ConcurrentLinkedQueue

/**
 * DynamicTaskInjectionEngine
 *
 * Allows users to dynamically inject new tasks, forgot-to-mention sub-prompts,
 * or requirement additions into the AI Agent's active execution loop in real-time.
 */
object DynamicTaskInjectionEngine {

    private val pendingTasks = ConcurrentLinkedQueue<String>()

    fun queueTask(taskText: String) {
        val clean = taskText.trim()
        if (clean.isNotBlank()) {
            pendingTasks.add(clean)
        }
    }

    fun hasPendingTasks(): Boolean {
        return pendingTasks.isNotEmpty()
    }

    fun drainTasks(): List<String> {
        val list = mutableListOf<String>()
        while (true) {
            val item = pendingTasks.poll() ?: break
            list.add(item)
        }
        return list
    }

    fun clear() {
        pendingTasks.clear()
    }
}
