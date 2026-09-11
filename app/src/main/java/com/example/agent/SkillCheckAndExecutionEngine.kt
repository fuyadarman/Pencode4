package com.example.agent

import com.example.ui.AgentSkill

/**
 * Skill check and agent skill inspection engine.
 * Allows the agent to query available skills, check system skills, and load specific instructions dynamically.
 */
object SkillCheckAndExecutionEngine {

    fun performSkillCheck(
        query: String?,
        activeSkills: List<AgentSkill>
    ): String {
        val q = query?.lowercase()?.trim() ?: ""

        val matchedSkills = if (q.isBlank()) {
            activeSkills
        } else {
            activeSkills.filter {
                it.name.lowercase().contains(q) ||
                it.description.lowercase().contains(q) ||
                it.author.lowercase().contains(q)
            }
        }

        if (matchedSkills.isEmpty()) {
            val allList = activeSkills.joinToString(", ") { it.name }
            return "No specific skill matched query '$query'. Available skills in workspace: [$allList]"
        }

        val sb = StringBuilder()
        sb.append("=== SKILL CHECK RESULTS (${matchedSkills.size} skills) ===\n")
        matchedSkills.forEach { skill ->
            sb.append("\n• Skill: [${skill.name}] (Author: ${skill.author}, Enabled: ${skill.isEnabled})\n")
            sb.append("  Description: ${skill.description}\n")
            if (skill.skillPrompt.isNotBlank()) {
                sb.append("  Guidelines: ${skill.skillPrompt}\n")
            }
        }
        return sb.toString()
    }
}
