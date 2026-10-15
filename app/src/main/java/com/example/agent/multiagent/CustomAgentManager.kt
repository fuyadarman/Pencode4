package com.example.agent.multiagent

import android.content.Context
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages all autonomous AI Agents in PenCode:
 * - Default specialized agents (Shopping, Job Hunting, Social Media, Online Worker, Full-Stack Dev)
 * - User and AI created custom agents
 * - Inter-agent delegation & messaging
 */
object CustomAgentManager {

    private const val PREFS_NAME = "pencode_agents_prefs"
    private const val KEY_AGENTS = "custom_agents_list"
    private const val KEY_MESSAGES = "agent_inter_messages"

    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val agentListType = Types.newParameterizedType(List::class.java, CustomAgent::class.java)
    private val agentAdapter = moshi.adapter<List<CustomAgent>>(agentListType)

    private val msgListType = Types.newParameterizedType(List::class.java, AgentInterMessage::class.java)
    private val msgAdapter = moshi.adapter<List<AgentInterMessage>>(msgListType)

    private val _agents = MutableStateFlow<List<CustomAgent>>(emptyList())
    val agents: StateFlow<List<CustomAgent>> = _agents.asStateFlow()

    private val _interMessages = MutableStateFlow<List<AgentInterMessage>>(emptyList())
    val interMessages: StateFlow<List<AgentInterMessage>> = _interMessages.asStateFlow()

    private var appContext: Context? = null

    val DEFAULT_AGENTS = listOf(
        CustomAgent(
            id = "agent_shopper",
            name = "Smart Shopper Agent",
            role = "Autonomous E-Commerce & Buying Specialist",
            description = "Finds products, compares best prices, applies coupons, adds to cart, and assists checkout.",
            systemPrompt = "You are an autonomous Shopping and E-Commerce agent. You can search online stores, compare prices, inspect item details, navigate checkout flows, and request user payment credentials when needed.",
            capabilities = listOf("online_shopping", "price_comparison", "cart_checkout", "coupon_hunting"),
            iconEmoji = "🛍️",
            isCustom = false
        ),
        CustomAgent(
            id = "agent_job_hunter",
            name = "Job Hunter & Career Agent",
            role = "Automated Job Application Specialist",
            description = "Searches job boards (LinkedIn, Indeed, remote sites), filters roles, matches resume, and fills out applications.",
            systemPrompt = "You are an autonomous Career & Job Hunting agent. You search job boards, analyze job descriptions, auto-fill application forms, write custom cover letters, and track submitted applications.",
            capabilities = listOf("job_search", "resume_matching", "auto_apply", "cover_letter_generation"),
            iconEmoji = "💼",
            isCustom = false
        ),
        CustomAgent(
            id = "agent_social_poster",
            name = "Social Media Growth Agent",
            role = "Content Creation & Autonomous Poster",
            description = "Crafts viral posts, schedules updates, and engages on Twitter/X, LinkedIn, Reddit, and forums.",
            systemPrompt = "You are an autonomous Social Media Agent. You create engaging posts, schedule content, research trending topics, and manage social interactions.",
            capabilities = listOf("social_posting", "trend_research", "content_scheduling", "community_engagement"),
            iconEmoji = "📱",
            isCustom = false
        ),
        CustomAgent(
            id = "agent_web_worker",
            name = "Autonomous Web Worker Agent",
            role = "Online Remote Task & Micro-Worker",
            description = "Executes online micro-tasks, research, surveys, lead generation, and web-based freelance tasks.",
            systemPrompt = "You are an autonomous Web Worker agent capable of doing online work: web research, data entry, form submissions, lead generation, and executing online tasks like a human assistant.",
            capabilities = listOf("web_tasks", "lead_generation", "data_extraction", "form_filling"),
            iconEmoji = "🌐",
            isCustom = false
        ),
        CustomAgent(
            id = "agent_coder",
            name = "Master Full-Stack Coder",
            role = "Autonomous Software Architect & Developer",
            description = "Builds full-stack features, fixes complex bugs, executes commands, and optimizes code.",
            systemPrompt = "You are a master Full-Stack Software Engineer agent specializing in building resilient, bug-free applications.",
            capabilities = listOf("code_generation", "refactoring", "debugging", "terminal_operations"),
            iconEmoji = "💻",
            isCustom = false
        )
    )

    fun initialize(context: Context) {
        appContext = context.applicationContext
        loadAgents()
        loadInterMessages()
    }

    private fun loadAgents() {
        val ctx = appContext ?: return
        val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_AGENTS, null)
        if (!json.isNullOrBlank()) {
            try {
                val saved = agentAdapter.fromJson(json) ?: emptyList()
                val merged = DEFAULT_AGENTS.filter { def -> saved.none { it.id == def.id } } + saved
                _agents.value = merged
                return
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        _agents.value = DEFAULT_AGENTS
    }

    private fun saveAgents() {
        val ctx = appContext ?: return
        try {
            val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val json = agentAdapter.toJson(_agents.value)
            prefs.edit().putString(KEY_AGENTS, json).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun loadInterMessages() {
        val ctx = appContext ?: return
        val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_MESSAGES, null)
        if (!json.isNullOrBlank()) {
            try {
                _interMessages.value = msgAdapter.fromJson(json) ?: emptyList()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun saveInterMessages() {
        val ctx = appContext ?: return
        try {
            val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val json = msgAdapter.toJson(_interMessages.value)
            prefs.edit().putString(KEY_MESSAGES, json).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun addOrUpdateAgent(agent: CustomAgent) {
        val current = _agents.value.toMutableList()
        val index = current.indexOfFirst { it.id == agent.id }
        if (index != -1) {
            current[index] = agent
        } else {
            current.add(agent)
        }
        _agents.value = current
        saveAgents()
    }

    fun deleteAgent(agentId: String) {
        val current = _agents.value.filter { it.id != agentId }
        _agents.value = current
        saveAgents()
    }

    fun toggleAgentEnabled(agentId: String, isEnabled: Boolean) {
        val current = _agents.value.map {
            if (it.id == agentId) it.copy(isEnabled = isEnabled) else it
        }
        _agents.value = current
        saveAgents()
    }

    fun getAgentById(agentId: String): CustomAgent? {
        return _agents.value.find { it.id == agentId }
    }

    /**
     * Send message or delegate task from one agent to another.
     */
    fun sendInterAgentMessage(
        fromAgentId: String,
        toAgentId: String,
        taskTitle: String,
        content: String
    ): AgentInterMessage {
        val msg = AgentInterMessage(
            fromAgentId = fromAgentId,
            toAgentId = toAgentId,
            taskTitle = taskTitle,
            content = content
        )
        val current = _interMessages.value.toMutableList()
        current.add(0, msg)
        _interMessages.value = current.take(100)
        saveInterMessages()
        return msg
    }

    fun updateMessageStatus(messageId: String, status: String, result: String?) {
        val current = _interMessages.value.map {
            if (it.id == messageId) it.copy(status = status, result = result) else it
        }
        _interMessages.value = current
        saveInterMessages()
    }
}
