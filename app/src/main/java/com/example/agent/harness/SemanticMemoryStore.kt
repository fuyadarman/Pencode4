package com.example.agent.harness

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory & Persistent Semantic Memory Store.
 * Embeds conversation turns, past decisions, error fixes, and code changes as semantic vectors.
 * Performs cosine-similarity retrieval to inject only relevant memories into the LLM context.
 */
object SemanticMemoryStore {
    private const val TAG = "SemanticMemoryStore"

    data class MemoryEntry(
        val id: String,
        val projectName: String,
        val topic: String,
        val content: String,
        val tags: List<String> = emptyList(),
        val timestamp: Long = System.currentTimeMillis(),
        val embedding: FloatArray = FloatArray(0)
    )

    private val memoryByProject = ConcurrentHashMap<String, MutableList<MemoryEntry>>()
    private var isInitialized = false

    /**
     * Initializes the memory store from disk cache if available.
     */
    fun init(context: Context) {
        if (isInitialized) return
        try {
            val cacheDir = context.cacheDir
            val memoryFile = File(cacheDir, "jcode_semantic_memories.json")
            if (memoryFile.exists()) {
                val jsonStr = memoryFile.readText()
                loadFromJson(jsonStr)
            }
            isInitialized = true
            
            // Seed base core architectural memory if store is fresh
            seedDefaultSystemMemories()
        } catch (e: Exception) {
            Log.w(TAG, "Failed loading semantic memories: ${e.message}")
        }
    }

    private fun seedDefaultSystemMemories() {
        val defaultProjs = listOf("global", "default", "PenCode")
        for (p in defaultProjs) {
            if (memoryByProject[p].isNullOrEmpty()) {
                recordMemory(
                    projectName = p,
                    topic = "Architectural Patterns & Sub-Agent Orchestration",
                    content = "PenCode supports specialized sub-agent teammates (Frontend, Backend, Testing, Reviewer) and executes precise surgical edits with Compose M3.",
                    tags = listOf("architecture", "subagents", "compose", "harness")
                )
            }
        }
    }

    /**
     * Records a new semantic memory for a project.
     */
    fun recordMemory(
        projectName: String,
        topic: String,
        content: String,
        tags: List<String> = emptyList(),
        context: Context? = null
    ) {
        if (content.isBlank()) return
        val id = "mem_${System.currentTimeMillis()}_${(100..999).random()}"
        val textToEmbed = "$topic ${tags.joinToString(" ")} $content"
        val embedding = SemanticVectorEmbedder.embed(textToEmbed)

        val entry = MemoryEntry(
            id = id,
            projectName = projectName,
            topic = topic,
            content = content.trim(),
            tags = tags,
            timestamp = System.currentTimeMillis(),
            embedding = embedding
        )

        val list = memoryByProject.computeIfAbsent(projectName) { mutableListOf() }
        synchronized(list) {
            // Keep memory list bounded to recent 100 entries per project
            if (list.size >= 100) {
                list.removeAt(0)
            }
            list.add(entry)
        }

        if (context != null) {
            persist(context)
        }
    }

    /**
     * Retrieves the top-K most semantically relevant memories for the given query using Cosine Similarity.
     */
    fun retrieveRelevantMemories(
        query: String,
        projectName: String,
        topK: Int = 4,
        threshold: Float = 0.12f
    ): List<Pair<MemoryEntry, Float>> {
        val list = mutableListOf<MemoryEntry>()
        memoryByProject[projectName]?.let { synchronized(it) { list.addAll(it) } }
        memoryByProject["global"]?.let { synchronized(it) { list.addAll(it) } }
        memoryByProject["PenCode"]?.let { synchronized(it) { list.addAll(it) } }

        if (list.isEmpty() || query.isBlank()) return emptyList()

        val queryVector = SemanticVectorEmbedder.embed(query)
        val scoredList = mutableListOf<Pair<MemoryEntry, Float>>()

        for (entry in list.distinctBy { it.id }) {
            val sim = SemanticVectorEmbedder.cosineSimilarity(queryVector, entry.embedding)
            if (sim >= threshold) {
                scoredList.add(Pair(entry, sim))
            }
        }

        // Sort descending by similarity score
        scoredList.sortByDescending { it.second }
        return scoredList.take(topK)
    }

    /**
     * Formats retrieved memories into an injection-ready prompt block.
     */
    fun formatMemoriesForPrompt(memories: List<Pair<MemoryEntry, Float>>): String {
        if (memories.isEmpty()) return ""
        val sb = StringBuilder()
        sb.append("=== RELEVANT PROJECT MEMORY (Semantic Retrieval via Cosine Similarity) ===\n")
        sb.append("The harness retrieved the following historical knowledge & decisions relevant to the current request:\n\n")
        for ((idx, item) in memories.withIndex()) {
            val (entry, score) = item
            val scorePercent = (score * 100).toInt()
            sb.append("• [Memory #${idx + 1} | Topic: ${entry.topic} | Relevance: $scorePercent%]\n")
            sb.append("  ${entry.content.replace("\n", "\n  ")}\n\n")
        }
        sb.append("=== END PROJECT MEMORY ===\n\n")
        return sb.toString()
    }

    /**
     * Persists memories to local disk cache.
     */
    fun persist(context: Context) {
        try {
            val root = JSONObject()
            val projectsArray = JSONObject()
            for ((proj, entries) in memoryByProject) {
                val arr = JSONArray()
                synchronized(entries) {
                    for (e in entries) {
                        val obj = JSONObject()
                        obj.put("id", e.id)
                        obj.put("topic", e.topic)
                        obj.put("content", e.content)
                        obj.put("tags", JSONArray(e.tags))
                        obj.put("timestamp", e.timestamp)
                        arr.put(obj)
                    }
                }
                projectsArray.put(proj, arr)
            }
            root.put("projects", projectsArray)

            val memoryFile = File(context.cacheDir, "jcode_semantic_memories.json")
            memoryFile.writeText(root.toString())
        } catch (e: Exception) {
            Log.w(TAG, "Failed saving semantic memories: ${e.message}")
        }
    }

    private fun loadFromJson(jsonStr: String) {
        try {
            val root = JSONObject(jsonStr)
            val projectsObj = root.optJSONObject("projects") ?: return
            val keys = projectsObj.keys()
            while (keys.hasNext()) {
                val proj = keys.next()
                val arr = projectsObj.optJSONArray(proj) ?: continue
                val list = mutableListOf<MemoryEntry>()
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val id = obj.optString("id", "")
                    val topic = obj.optString("topic", "")
                    val content = obj.optString("content", "")
                    val tagsArr = obj.optJSONArray("tags")
                    val tags = mutableListOf<String>()
                    if (tagsArr != null) {
                        for (t in 0 until tagsArr.length()) {
                            tags.add(tagsArr.optString(t))
                        }
                    }
                    val timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                    val embedding = SemanticVectorEmbedder.embed("$topic ${tags.joinToString(" ")} $content")
                    list.add(MemoryEntry(id, proj, topic, content, tags, timestamp, embedding))
                }
                memoryByProject[proj] = list
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed parsing loaded memory json: ${e.message}")
        }
    }

    /**
     * Gets memory stats for diagnostics/UI.
     */
    fun getMemoryCount(projectName: String): Int {
        return memoryByProject[projectName]?.size ?: 0
    }
}
