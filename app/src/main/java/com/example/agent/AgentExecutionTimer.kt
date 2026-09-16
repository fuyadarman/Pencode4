package com.example.agent

/**
 * AgentExecutionTimer
 * Accurately calculates elapsed seconds and formatted timing strings for thoughts and tool actions.
 */
object AgentExecutionTimer {

    /**
     * Calculates the real duration in seconds based on durationMillis or startTime.
     */
    fun calculateDurationSeconds(
        durationMillis: Long?,
        startTime: Long,
        endTime: Long = System.currentTimeMillis()
    ): Long {
        if (durationMillis != null && durationMillis > 0) {
            return maxOf(1L, Math.round(durationMillis.toDouble() / 1000.0))
        }
        if (endTime > startTime && startTime > 0) {
            val diff = endTime - startTime
            return maxOf(1L, Math.round(diff.toDouble() / 1000.0))
        }
        return 1L
    }

    /**
     * Formats thought header label with accurate seconds count.
     */
    fun formatThoughtHeader(
        isExecuting: Boolean,
        durationSeconds: Long,
        liveElapsedSeconds: Long? = null
    ): String {
        return if (isExecuting) {
            val sec = liveElapsedSeconds ?: durationSeconds
            "Thinking (${sec}s)..."
        } else {
            val unit = if (durationSeconds == 1L) "second" else "seconds"
            "Thought for $durationSeconds $unit"
        }
    }
}
