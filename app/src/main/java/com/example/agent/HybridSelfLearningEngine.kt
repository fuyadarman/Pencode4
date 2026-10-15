package com.example.agent

import android.content.Context
import android.util.Log
import com.example.agent.harness.SemanticVectorEmbedder
import com.example.ui.AgentSkill
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * HybridSelfLearningEngine
 * 
 * Implements an autonomous self-learning memory for the PenCode AI agent:
 * 1. Error-Fix Pattern Extraction: When bugs or compilation failures are resolved,
 *    it memorizes the root cause, fix pattern, and preventive heuristics.
 * 2. Continuous Experience Vector Retrieval: Embeds and retrieves proven patterns
 *    via semantic similarity to inject into subsequent prompts.
 * 3. Autonomous Skill Synthesizer: Synthesizes reusable custom Agent Skills
 *    from frequently applied patterns and workflow solutions.
 */
object HybridSelfLearningEngine {
    private const val TAG = "HybridSelfLearning"
    private const val MEMORY_FILE_NAME = "hybrid_self_learning_memory.json"

    data class LearnedPattern(
        val id: String = UUID.randomUUID().toString(),
        val title: String,
        val category: String, // "bug_fix", "architecture", "convention", "performance"
        val issueDescription: String,
        val solutionRule: String,
        val tags: List<String> = emptyList(),
        val learnedAt: Long = System.currentTimeMillis(),
        val appliedCount: Int = 1,
        val successCount: Int = 1,
        val embedding: FloatArray = FloatArray(0)
    )

    private val _learnedPatterns = MutableStateFlow<List<LearnedPattern>>(emptyList())
    val learnedPatterns: StateFlow<List<LearnedPattern>> = _learnedPatterns.asStateFlow()

    private var isInitialized = false

    fun init(context: Context) {
        if (isInitialized) return
        try {
            val file = File(context.filesDir, MEMORY_FILE_NAME)
            if (file.exists()) {
                val json = file.readText()
                loadFromJson(json)
            } else {
                seedInitialPatterns(context)
            }
            isInitialized = true
        } catch (e: Exception) {
            Log.w(TAG, "Failed loading self-learning memory: ${e.message}")
            seedInitialPatterns(context)
            isInitialized = true
        }
    }

    private fun seedInitialPatterns(context: Context) {
        val seeds = listOf(
            LearnedPattern(
                title = "Surgical Replacement over Full Rewrites",
                category = "architecture",
                issueDescription = "Overwriting large files causes build regressions, lost imports, and token exhaustion.",
                solutionRule = "For files >30 lines, read target lines first, identify exact unique substrings, and perform surgical edits via 'edit_file'.",
                tags = listOf("surgical_edit", "code_safety", "compose")
            ),
            LearnedPattern(
                title = "Compose M3 AutoMirrored Icons",
                category = "convention",
                issueDescription = "Using Icons.Filled.ArrowBack or NoteAdd causes deprecation warnings in Compose M3.",
                solutionRule = "Prefer Icons.AutoMirrored.Filled.* for directional and navigation icons.",
                tags = listOf("compose", "m3", "icons", "deprecation")
            ),
            LearnedPattern(
                title = "Preview & Build Diagnostics First",
                category = "bug_fix",
                issueDescription = "Guessing error causes without checking runtime/build logs leads to repetitive failed attempts.",
                solutionRule = "Call 'read_preview_errors' for browser/preview crashes or 'read_build_errors' for compiler failures before editing.",
                tags = listOf("diagnostics", "logs", "build_fix")
            ),
            LearnedPattern(
                title = "StateFlow UI Collection Safety",
                category = "performance",
                issueDescription = "Direct StateFlow observation without lifecycle awareness causes unnecessary recompositions.",
                solutionRule = "Use collectAsState() or collectAsStateWithLifecycle() inside Composables with remember/derivedStateOf where applicable.",
                tags = listOf("stateflow", "compose", "reactivity")
            )
        )
        
        val embeddedSeeds = seeds.map { p ->
            val textToEmbed = "${p.title} ${p.issueDescription} ${p.solutionRule} ${p.tags.joinToString(" ")}"
            p.copy(embedding = SemanticVectorEmbedder.embed(textToEmbed))
        }
        
        _learnedPatterns.value = embeddedSeeds
        persist(context)
    }

    /**
     * Records a newly learned pattern explicitly or autonomously.
     */
    fun recordPattern(
        context: Context,
        title: String,
        category: String,
        issueDescription: String,
        solutionRule: String,
        tags: List<String>
    ): LearnedPattern {
        val textToEmbed = "$title $issueDescription $solutionRule ${tags.joinToString(" ")}"
        val embedding = SemanticVectorEmbedder.embed(textToEmbed)

        val newPattern = LearnedPattern(
            id = "learn_${System.currentTimeMillis()}_${(100..999).random()}",
            title = title.trim(),
            category = category.trim().ifBlank { "bug_fix" },
            issueDescription = issueDescription.trim(),
            solutionRule = solutionRule.trim(),
            tags = tags.filter { it.isNotBlank() },
            learnedAt = System.currentTimeMillis(),
            appliedCount = 1,
            successCount = 1,
            embedding = embedding
        )

        val current = _learnedPatterns.value.toMutableList()
        // Deduplicate or merge similar patterns
        val existingIndex = current.indexOfFirst {
            it.title.equals(newPattern.title, ignoreCase = true) ||
            SemanticVectorEmbedder.cosineSimilarity(it.embedding, newPattern.embedding) > 0.85f
        }

        if (existingIndex >= 0) {
            val old = current[existingIndex]
            current[existingIndex] = old.copy(
                solutionRule = newPattern.solutionRule,
                appliedCount = old.appliedCount + 1,
                successCount = old.successCount + 1,
                tags = (old.tags + newPattern.tags).distinct()
            )
        } else {
            current.add(0, newPattern)
        }

        _learnedPatterns.value = current
        persist(context)
        return newPattern
    }

    /**
     * Autonomously extracts and registers a learned pattern when a fix succeeds.
     */
    fun autoLearnFromFix(
        context: Context,
        issueSummary: String,
        fixSummary: String,
        targetComponent: String
    ) {
        if (issueSummary.isBlank() || fixSummary.isBlank()) return
        val title = "Fix: ${targetComponent.ifBlank { issueSummary.take(30) }}"
        val tags = listOf(
            targetComponent.substringAfterLast(".").lowercase(),
            "auto_learned",
            "self_correction"
        ).filter { it.isNotBlank() }

        recordPattern(
            context = context,
            title = title,
            category = "bug_fix",
            issueDescription = issueSummary.take(200),
            solutionRule = fixSummary.take(300),
            tags = tags
        )
    }

    /**
     * Autonomous Skill Synthesizer: Converts a learned pattern into a persistent Agent Skill.
     */
    fun synthesizeSkill(
        name: String,
        description: String,
        instructions: String,
        category: String = "Self-Learned"
    ): AgentSkill {
        val skillId = "synthesized_${System.currentTimeMillis()}_${name.lowercase().replace("\\s+".toRegex(), "_").take(20)}"
        return AgentSkill(
            id = skillId,
            name = name.trim(),
            author = "Self-Learning Engine",
            installs = "Autonomous",
            description = description.trim(),
            githubUrl = "local://hybrid-self-learning",
            isInstalled = true,
            isEnabled = true,
            isCustom = true,
            skillPrompt = instructions.trim()
        )
    }

    /**
     * Retrieves the most relevant learned patterns using Cosine Vector Similarity.
     */
    fun retrieveRelevantPatterns(query: String, topK: Int = 3, threshold: Float = 0.15f): List<LearnedPattern> {
        val patterns = _learnedPatterns.value
        if (patterns.isEmpty() || query.isBlank()) return emptyList()

        val queryVector = SemanticVectorEmbedder.embed(query)
        val scored = patterns.map { pattern ->
            val sim = SemanticVectorEmbedder.cosineSimilarity(queryVector, pattern.embedding)
            Pair(pattern, sim)
        }.filter { it.second >= threshold }
         .sortedByDescending { it.second }
         .take(topK)
         .map { it.first }

        return scored
    }

    /**
     * Formats the most relevant self-learned patterns for dynamic prompt injection.
     */
    fun formatLearnedRulesForPrompt(userPrompt: String): String {
        val relevant = retrieveRelevantPatterns(userPrompt, topK = 3, threshold = 0.18f)
        if (relevant.isEmpty()) return ""

        val sb = StringBuilder()
        sb.append("=== HYBRID SELF-LEARNED PATTERNS & MEMORY ===\n")
        sb.append("Apply proven solutions and guidelines learned from past project turns:\n")
        relevant.forEachIndexed { idx, p ->
            sb.append("${idx + 1}. [${p.title.uppercase()}]\n")
            sb.append("   - Learned Rule: ${p.solutionRule}\n")
            if (p.issueDescription.isNotBlank()) {
                sb.append("   - Known Pitfall: ${p.issueDescription}\n")
            }
        }
        sb.append("=== END SELF-LEARNED PATTERNS ===\n\n")
        return sb.toString()
    }

    fun deletePattern(context: Context, patternId: String) {
        _learnedPatterns.value = _learnedPatterns.value.filter { it.id != patternId }
        persist(context)
    }

    fun clearAllPatterns(context: Context) {
        _learnedPatterns.value = emptyList()
        persist(context)
        seedInitialPatterns(context)
    }

    private fun persist(context: Context) {
        try {
            val file = File(context.filesDir, MEMORY_FILE_NAME)
            val jsonArray = JSONArray()
            _learnedPatterns.value.forEach { p ->
                val obj = JSONObject().apply {
                    put("id", p.id)
                    put("title", p.title)
                    put("category", p.category)
                    put("issueDescription", p.issueDescription)
                    put("solutionRule", p.solutionRule)
                    put("tags", JSONArray(p.tags))
                    put("learnedAt", p.learnedAt)
                    put("appliedCount", p.appliedCount)
                    put("successCount", p.successCount)
                }
                jsonArray.put(obj)
            }
            file.writeText(jsonArray.toString())
        } catch (e: Exception) {
            Log.w(TAG, "Failed persisting self-learning memory: ${e.message}")
        }
    }

    private fun loadFromJson(json: String) {
        try {
            val array = JSONArray(json)
            val list = mutableListOf<LearnedPattern>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val tagsArr = obj.optJSONArray("tags")
                val tagsList = mutableListOf<String>()
                if (tagsArr != null) {
                    for (j in 0 until tagsArr.length()) {
                        tagsList.add(tagsArr.getString(j))
                    }
                }
                val title = obj.getString("title")
                val issue = obj.optString("issueDescription", "")
                val solution = obj.optString("solutionRule", "")
                val textToEmbed = "$title $issue $solution ${tagsList.joinToString(" ")}"
                
                list.add(
                    LearnedPattern(
                        id = obj.getString("id"),
                        title = title,
                        category = obj.optString("category", "bug_fix"),
                        issueDescription = issue,
                        solutionRule = solution,
                        tags = tagsList,
                        learnedAt = obj.optLong("learnedAt", System.currentTimeMillis()),
                        appliedCount = obj.optInt("appliedCount", 1),
                        successCount = obj.optInt("successCount", 1),
                        embedding = SemanticVectorEmbedder.embed(textToEmbed)
                    )
                )
            }
            _learnedPatterns.value = list
        } catch (e: Exception) {
            Log.w(TAG, "Failed parsing self-learning json: ${e.message}")
        }
    }
}
