package com.example.agent

/**
 * AgentReadToEditTransitionEngine
 * 
 * Ensures that the AI swiftly transitions from reading code to editing code.
 * Prevents passive "read-only" loops where the model inspects file after file
 * without ever applying the requested modifications.
 */
object AgentReadToEditTransitionEngine {

    const val READ_TO_EDIT_DIRECTIVE = 
        "6. PROMPT-TO-EDIT PIPELINE (Read -> Edit): When asked to create, modify, add, or fix code, read ONLY the specific target file, and in your VERY NEXT TURN execute 'edit_file', 'multi_edit_file', or 'create_file'. Do NOT engage in passive browsing or read multiple files before applying changes."

    /**
     * Appends an immediate action cue to the end of a read_file output.
     * Guides the model to make its edit on the very next turn.
     */
    fun buildPostReadCue(filePath: String, consecutiveReads: Int): String {
        return if (consecutiveReads >= 2) {
            "\n\n[ACTION REQUIRED: You have read $consecutiveReads file(s). You have the necessary context. In your NEXT response, you MUST apply your code changes using 'edit_file' or 'create_file'. Do not read further files.]"
        } else {
            "\n\n[FILE LOADED: Target '$filePath' is ready for modification. In your next response, invoke 'edit_file' or 'multi_edit_file' to apply the required changes.]"
        }
    }

    /**
     * Checks if the model should be firmly nudged to write code instead of reading.
     */
    fun shouldNudgeToEdit(consecutiveReads: Int): Boolean {
        return consecutiveReads >= 2
    }
}
