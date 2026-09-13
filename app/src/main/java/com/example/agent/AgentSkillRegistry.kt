package com.example.agent

import com.example.ui.AgentSkill
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Registry and manager for Agent Skills across multiple platforms:
 * - Google Gemini (Optimization, Function Calling, Multimodal)
 * - Android & Kotlin (Compose MVVM, Room DB, Material 3)
 * - Anthropic Claude (Prompt Engineering, Tool Use)
 * - Vercel / Web (React & Next.js Composition, App Router)
 * - Browser Use (Web Automation, DOM Inspection)
 * - Composio (App Integrations & Action Routing)
 * - LangChain (RAG, Vector Search, Chains)
 * - Supabase (PostgreSQL, RLS Security, Realtime)
 * - Cloudflare (Edge Workers, KV, Vectorize)
 *
 * Removes dummy/demo skills and ensures reliable fetching and fallback.
 */
object AgentSkillRegistry {

    val curatedPlatformSkills: List<AgentSkill> = listOf(
        // --- GOOGLE GEMINI PLATFORM ---
        AgentSkill(
            id = "google-gemini-cookbook",
            name = "Gemini API Optimization",
            author = "google-gemini/cookbook",
            installs = "Google Skill",
            description = "Production patterns for Gemini 1.5/2.0 Pro and Flash models, structured JSON outputs, multimodal reasoning, and function calling.",
            githubUrl = "https://github.com/google-gemini/cookbook",
            rawFileUrl = "https://raw.githubusercontent.com/google-gemini/cookbook/main/README.md",
            skillPrompt = """
                Skill: Gemini API Cookbook & Optimization
                - Leverage Gemini 2.0 Flash / Pro capabilities with system instructions and responseSchema.
                - Use structured JSON outputs with strict type definitions for predictable extraction.
                - Optimize multi-turn chat sessions and token context windows efficiently.
            """.trimIndent()
        ),
        AgentSkill(
            id = "gemini-function-calling",
            name = "Gemini Tool & Function Calling",
            author = "google-gemini/cookbook",
            installs = "Google Skill",
            description = "Architect tool calling agents with Gemini function declarations, automatic argument validation, and parallel function execution.",
            githubUrl = "https://github.com/google-gemini/cookbook",
            rawFileUrl = "https://raw.githubusercontent.com/google-gemini/cookbook/main/examples/Function_calling.ipynb",
            skillPrompt = """
                Skill: Gemini Tool & Function Calling
                - Define precise JSON schema for tool parameters with descriptions.
                - Handle function call responses cleanly and send tool results back in conversation history.
                - Implement defensive fallback when models return malformed tool arguments.
            """.trimIndent()
        ),
        AgentSkill(
            id = "gemini-multimodal-vision",
            name = "Gemini Multimodal Vision & Audio",
            author = "google-gemini/cookbook",
            installs = "Google Skill",
            description = "Deep image analysis, OCR, video frame extraction, and voice processing using Gemini multimodal input channels.",
            githubUrl = "https://github.com/google-gemini/cookbook",
            rawFileUrl = "https://raw.githubusercontent.com/google-gemini/cookbook/main/examples/Multimodal_Live_API.ipynb",
            skillPrompt = """
                Skill: Gemini Multimodal Vision & Audio
                - Send high-resolution images and diagrams using inlineData or File API.
                - Instruct model to reference specific coordinate bounding boxes or UI elements.
                - Extract dense text, code screenshots, and wireframes into clean Kotlin/Compose code.
            """.trimIndent()
        ),

        // --- ANDROID & KOTLIN PLATFORM ---
        AgentSkill(
            id = "android-compose-architecture",
            name = "Jetpack Compose MVVM",
            author = "android/architecture-samples",
            installs = "Android Skill",
            description = "Clean Architecture with Jetpack Compose, ViewModels, StateFlow, Coroutines, and Material 3 design system.",
            githubUrl = "https://github.com/android/architecture-samples",
            rawFileUrl = "https://raw.githubusercontent.com/android/architecture-samples/main/README.md",
            skillPrompt = """
                Skill: Jetpack Compose MVVM & Clean Architecture
                - Unidirectional data flow with StateFlow and collectAsStateWithLifecycle.
                - Material 3 design system compliance with 48dp minimum touch targets.
                - Hoist state to ViewModels and keep Composables stateless and previewable.
            """.trimIndent()
        ),
        AgentSkill(
            id = "android-room-database",
            name = "Room Database & Offline Storage",
            author = "android/architecture-samples",
            installs = "Android Skill",
            description = "Robust local persistence with Android Room: SQLite schema migrations, DAO queries, Flow streams, and offline-first cache.",
            githubUrl = "https://github.com/android/architecture-samples",
            rawFileUrl = "https://raw.githubusercontent.com/android/architecture-samples/main/app/src/main/java/com/example/android/architecture/blueprints/todoapp/data/source/local/ToDoDatabase.kt",
            skillPrompt = """
                Skill: Android Room Database & Offline Storage
                - Structure entities with @Entity and auto-generated primary keys.
                - Expose reactive queries as Flow<List<Entity>> in DAOs for real-time UI updates.
                - Run all database writes asynchronously on Dispatchers.IO.
            """.trimIndent()
        ),
        AgentSkill(
            id = "android-network-coroutines",
            name = "Retrofit & Coroutine Networking",
            author = "android/architecture-samples",
            installs = "Android Skill",
            description = "High-performance networking with Retrofit, OkHttp, Kotlin Coroutines, and Moshi JSON parsing.",
            githubUrl = "https://github.com/android/architecture-samples",
            rawFileUrl = "https://raw.githubusercontent.com/android/architecture-samples/main/README.md",
            skillPrompt = """
                Skill: Retrofit & Coroutine Networking
                - Implement suspend functions in Retrofit interfaces for seamless async execution.
                - Catch network exceptions gracefully with sealed Result<T> wrappers.
                - Configure connection timeouts and interceptors for logging and auth headers.
            """.trimIndent()
        ),

        // --- ANTHROPIC CLAUDE PLATFORM ---
        AgentSkill(
            id = "anthropic-claude-prompt-engineering",
            name = "Claude Prompt Engineering",
            author = "anthropics/courses",
            installs = "Anthropic Skill",
            description = "Advanced prompt design for AI models: XML tags structuring, chain-of-thought reasoning, and system prompt optimization.",
            githubUrl = "https://github.com/anthropics/courses",
            rawFileUrl = "https://raw.githubusercontent.com/anthropics/courses/main/README.md",
            skillPrompt = """
                Skill: Anthropic Claude Prompt Engineering
                - Structure context using XML tags like <context>, <instructions>, and <examples>.
                - Request step-by-step thinking inside <thinking> tags before producing final output.
                - Provide clear few-shot examples showing input-to-output transformations.
            """.trimIndent()
        ),
        AgentSkill(
            id = "anthropic-tool-use-workflow",
            name = "Claude Tool Use & Multi-Step Workflows",
            author = "anthropics/anthropic-cookbook",
            installs = "Anthropic Skill",
            description = "Multi-step tool calling patterns with Claude 3.5 Sonnet / Haiku, error handling, and sequential tool execution.",
            githubUrl = "https://github.com/anthropics/anthropic-cookbook",
            rawFileUrl = "https://raw.githubusercontent.com/anthropics/anthropic-cookbook/main/tool_use/calculator.ipynb",
            skillPrompt = """
                Skill: Claude Tool Use & Multi-Step Workflows
                - Define tools with clear descriptions indicating exact scenarios when to invoke them.
                - Execute tool calls step-by-step and feed tool execution results back to the model.
                - Ensure tool parameters are strictly typed.
            """.trimIndent()
        ),

        // --- VERCEL & WEB PLATFORM ---
        AgentSkill(
            id = "vercel-react-best-practices",
            name = "React & Next.js Composition",
            author = "vercel-labs/agent-skills",
            installs = "Vercel Skill",
            description = "React composition patterns that scale. Eliminate boolean prop proliferation, build flexible component libraries, and optimize state rendering.",
            githubUrl = "https://github.com/vercel-labs/agent-skills",
            rawFileUrl = "https://raw.githubusercontent.com/vercel-labs/agent-skills/main/skills/react-best-practices/SKILL.md",
            skillPrompt = """
                Skill: React & Next.js Composition Best Practices
                - Avoid boolean prop proliferation; use composition and sub-components.
                - Ensure state stays local where possible and lift state cleanly.
                - Use memoization and callback stability to prevent unnecessary re-renders.
            """.trimIndent()
        ),
        AgentSkill(
            id = "vercel-nextjs-app-router",
            name = "Next.js App Router & Server Components",
            author = "vercel-labs/agent-skills",
            installs = "Vercel Skill",
            description = "Master Next.js App Router, React Server Components (RSC), Server Actions, dynamic routes, and streaming UI with Suspense.",
            githubUrl = "https://github.com/vercel-labs/agent-skills",
            rawFileUrl = "https://raw.githubusercontent.com/vercel-labs/agent-skills/main/skills/next-cache/SKILL.md",
            skillPrompt = """
                Skill: Next.js App Router & Server Components
                - Default to Server Components for data fetching and keep client code at the leaves.
                - Use Server Actions with progressive enhancement for form mutations.
                - Implement error boundaries and loading skeletons for fast perceived latency.
            """.trimIndent()
        ),

        // --- BROWSER USE PLATFORM ---
        AgentSkill(
            id = "browser-use-web-automation",
            name = "Browser Automation & DOM Navigation",
            author = "browser-use/browser-use",
            installs = "Browser Skill",
            description = "Autonomous browser agents that navigate webpages, click buttons, fill input forms, extract DOM elements, and take screenshots.",
            githubUrl = "https://github.com/browser-use/browser-use",
            rawFileUrl = "https://raw.githubusercontent.com/browser-use/browser-use/main/README.md",
            skillPrompt = """
                Skill: Browser Use Web Automation
                - Target interactive elements using semantic selectors or index numbers.
                - Always inspect page structure or take snapshot before clicking or typing.
                - Handle navigation delays, page transitions, and dynamic modals safely.
            """.trimIndent()
        ),
        AgentSkill(
            id = "browser-web-scraping-clone",
            name = "Web Scraping & UI Cloning",
            author = "browser-use/browser-use",
            installs = "Browser Skill",
            description = "Extract layout structures, color palettes, fonts, and assets from live websites and convert them into native UI components.",
            githubUrl = "https://github.com/browser-use/browser-use",
            rawFileUrl = "https://raw.githubusercontent.com/browser-use/browser-use/main/README.md",
            skillPrompt = """
                Skill: Web Scraping & UI Cloning
                - Extract computed styles, typography tokens, and spacing scales from target sites.
                - Map CSS flexbox and grid layouts directly to Compose Row/Column and Lazy grids.
                - Download or reference high-fidelity web icons and SVGs cleanly.
            """.trimIndent()
        ),

        // --- COMPOSIO PLATFORM ---
        AgentSkill(
            id = "composio-tool-integrations",
            name = "Composio App Actions & Tool Routing",
            author = "composiohq/composio",
            installs = "Composio Skill",
            description = "Connect AI agents to 150+ software tools including GitHub, Slack, Google Calendar, Discord, Jira, and Notion with verified auth.",
            githubUrl = "https://github.com/composiohq/composio",
            rawFileUrl = "https://raw.githubusercontent.com/composiohq/composio/master/README.md",
            skillPrompt = """
                Skill: Composio App Actions & Tool Routing
                - Authenticate user credentials safely via OAuth2 and API tokens.
                - Execute external actions (create GitHub issues, send Slack messages, schedule events).
                - Verify action execution status and handle API errors idempotently.
            """.trimIndent()
        ),

        // --- LANGCHAIN PLATFORM ---
        AgentSkill(
            id = "langchain-rag-pipeline",
            name = "LangChain RAG & Vector Search",
            author = "langchain-ai/langchain",
            installs = "LangChain Skill",
            description = "Build production Retrieval-Augmented Generation (RAG) pipelines with document loaders, text splitters, embeddings, and vector stores.",
            githubUrl = "https://github.com/langchain-ai/langchain",
            rawFileUrl = "https://raw.githubusercontent.com/langchain-ai/langchain/master/README.md",
            skillPrompt = """
                Skill: LangChain RAG & Vector Search
                - Chunk documents intelligently with semantic boundaries and token overlap.
                - Store vector embeddings in efficient stores and query with cosine similarity.
                - Augment generation prompts with retrieved context and cite source documents.
            """.trimIndent()
        ),
        AgentSkill(
            id = "langchain-agent-memory",
            name = "LangChain Stateful Agent Memory",
            author = "langchain-ai/langchain",
            installs = "LangChain Skill",
            description = "Maintain long-term agent memory across multi-turn sessions with summary buffers, conversation history pruning, and entity stores.",
            githubUrl = "https://github.com/langchain-ai/langchain",
            rawFileUrl = "https://raw.githubusercontent.com/langchain-ai/langchain/master/README.md",
            skillPrompt = """
                Skill: LangChain Stateful Agent Memory
                - Summarize long chat sessions into condensed memory checkpoints.
                - Track extracted user entities, preferences, and project goals across interactions.
                - Evict stale or obsolete messages to preserve prompt context budget.
            """.trimIndent()
        ),

        // --- SUPABASE PLATFORM ---
        AgentSkill(
            id = "supabase-postgres-security",
            name = "Supabase PostgreSQL & RLS Security",
            author = "supabase/supabase",
            installs = "Supabase Skill",
            description = "Design secure PostgreSQL databases on Supabase with Row Level Security (RLS) policies, foreign keys, triggers, and real-time streams.",
            githubUrl = "https://github.com/supabase/supabase",
            rawFileUrl = "https://raw.githubusercontent.com/supabase/supabase/master/README.md",
            skillPrompt = """
                Skill: Supabase PostgreSQL & RLS Security
                - Always enable ROW LEVEL SECURITY on sensitive tables.
                - Write granular RLS policies for auth.uid() matching user IDs.
                - Use Postgres stored functions and triggers for atomic data operations.
            """.trimIndent()
        ),

        // --- CLOUDFLARE PLATFORM ---
        AgentSkill(
            id = "cloudflare-workers-edge",
            name = "Cloudflare Workers & Edge APIs",
            author = "cloudflare/workers-sdk",
            installs = "Cloudflare Skill",
            description = "Deploy low-latency serverless APIs on Cloudflare Workers edge network with KV storage, D1 SQL, and Vectorize AI search.",
            githubUrl = "https://github.com/cloudflare/workers-sdk",
            rawFileUrl = "https://raw.githubusercontent.com/cloudflare/workers-sdk/main/README.md",
            skillPrompt = """
                Skill: Cloudflare Workers & Edge APIs
                - Handle fetch events with minimal cold start latency using standard Web APIs.
                - Store caching tokens in Cloudflare KV and query structured data in D1.
                - Route incoming requests with fast TypeScript router frameworks like Hono.
            """.trimIndent()
        )
    )

    /**
     * Determines if a skill is a dummy, demo, or placeholder item that should be removed.
     */
    fun isDummyOrDemoSkill(skill: AgentSkill): Boolean {
        val lowerId = skill.id.lowercase()
        val lowerName = skill.name.lowercase()
        val lowerAuthor = skill.author.lowercase()

        // Match dummy, demo, placeholder, or leftover test categories
        if (lowerId.contains("dummy") || lowerName.contains("dummy") || lowerAuthor.contains("dummy")) return true
        if (lowerId.contains("demo") || lowerName.contains("demo") || lowerAuthor.contains("demo")) return true
        if (lowerAuthor.contains("cloudai-x") || lowerAuthor.contains("nextlevelbuilder") || lowerAuthor.contains("expo")) return true
        if (lowerId.contains("sample-skill") || lowerName.contains("sample skill")) return true
        if (lowerId.contains("test-skill") || lowerName.contains("test skill")) return true

        // Reject skills that are completely empty
        if (skill.name.isBlank() || skill.description.isBlank()) return true

        return false
    }

    /**
     * Filters out all dummy/demo skills and returns a clean list.
     */
    fun filterCleanSkills(skills: List<AgentSkill>): List<AgentSkill> {
        return skills.filterNot { isDummyOrDemoSkill(it) }
    }

    /**
     * Fetches real skills from online GitHub repositories, falling back to curated platform skills
     * so that platform skills never fail to show even under GitHub API rate limits or network issues.
     */
    suspend fun fetchOnlinePlatformSkills(
        existingSkills: List<AgentSkill>
    ): List<AgentSkill> = withContext(Dispatchers.IO) {
        val existingIds = existingSkills.map { it.id }.toSet()
        val fetchedList = mutableListOf<AgentSkill>()

        // Add any curated platform skills that aren't already present
        for (curated in curatedPlatformSkills) {
            if (curated.id !in existingIds && fetchedList.none { it.id == curated.id }) {
                fetchedList.add(curated)
            }
        }

        // Targeted list of real online repositories with active agent skills
        val repos = listOf(
            Triple("vercel-labs", "agent-skills", "https://github.com/vercel-labs/agent-skills"),
            Triple("anthropics", "anthropic-cookbook", "https://github.com/anthropics/anthropic-cookbook"),
            Triple("google-gemini", "cookbook", "https://github.com/google-gemini/cookbook")
        )

        for ((orgName, repo, githubUrl) in repos) {
            try {
                val branches = listOf("main", "master")
                var fetched = false

                for (branch in branches) {
                    if (fetched) break
                    try {
                        val treeApiUrl = "https://api.github.com/repos/$orgName/$repo/git/trees/$branch?recursive=1"
                        val url = URL(treeApiUrl)
                        val conn = url.openConnection() as HttpURLConnection
                        conn.requestMethod = "GET"
                        conn.setRequestProperty("User-Agent", "Mozilla/5.0 PenCode-Android-Agent")
                        conn.setRequestProperty("Accept", "application/vnd.github.v3+json")
                        conn.connectTimeout = 4000
                        conn.readTimeout = 4000

                        if (conn.responseCode == 200) {
                            val responseText = conn.inputStream.bufferedReader().use { it.readText() }
                            val jsonObj = JSONObject(responseText)
                            val treeArray = jsonObj.optJSONArray("tree") ?: JSONArray()

                            for (i in 0 until treeArray.length()) {
                                val item = treeArray.getJSONObject(i)
                                val path = item.optString("path", "")

                                val isSkillFile = (path.endsWith("SKILL.md", ignoreCase = true) ||
                                        path.endsWith("skill.json", ignoreCase = true) ||
                                        (path.startsWith("skills/") && path.endsWith(".md", ignoreCase = true))) &&
                                        !path.contains(".github/")

                                if (isSkillFile) {
                                    val fileName = path.substringAfterLast("/")
                                    val rawName = if (fileName.equals("SKILL.md", ignoreCase = true) || fileName.equals("skill.json", ignoreCase = true)) {
                                        val segs = path.split("/")
                                        if (segs.size >= 2) segs[segs.size - 2] else fileName.removeSuffix(".md")
                                    } else {
                                        fileName.removeSuffix(".md")
                                    }

                                    val cleanName = rawName.replace("-", " ").replace("_", " ").trim()
                                    if (cleanName.isBlank()) continue

                                    val skillId = "$orgName-${path.lowercase().replace("/", "-").replace(".", "-")}"
                                    if (skillId !in existingIds && fetchedList.none { it.id == skillId }) {
                                        val formattedName = cleanName.split(" ")
                                            .filter { it.isNotBlank() }
                                            .joinToString(" ") { word -> word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() } }

                                        val rawUrl = "https://raw.githubusercontent.com/$orgName/$repo/$branch/$path"

                                        fetchedList.add(
                                            AgentSkill(
                                                id = skillId,
                                                name = "$formattedName ($orgName)",
                                                author = "$orgName/$repo",
                                                installs = "GitHub Skill",
                                                description = "Real agent skill from $orgName/$repo ($path).",
                                                githubUrl = githubUrl,
                                                isInstalled = false,
                                                isEnabled = false,
                                                skillPrompt = "Skill source file: $path ($githubUrl)\nRaw URL: $rawUrl",
                                                filePath = path,
                                                rawFileUrl = rawUrl
                                            )
                                        )
                                    }
                                }
                            }
                            if (treeArray.length() > 0) {
                                fetched = true
                            }
                        }
                    } catch (e: Exception) {
                        // Rate limit or network timeout - handled gracefully
                    }
                }
            } catch (e: Exception) {
                // Graceful fallback
            }
        }

        fetchedList
    }

    data class PlatformBadge(val name: String, val colorHex: Long)

    fun getSkillPlatform(skill: AgentSkill): PlatformBadge {
        val a = skill.author.lowercase()
        val n = skill.name.lowercase()
        val id = skill.id.lowercase()
        return when {
            a.contains("google") || a.contains("gemini") || n.contains("gemini") || id.contains("gemini") ->
                PlatformBadge("Google Gemini", 0xFF4285F4)
            a.contains("android") || n.contains("android") || n.contains("compose") || id.contains("android") ->
                PlatformBadge("Android", 0xFF3DDC84)
            a.contains("anthropic") || n.contains("claude") || id.contains("anthropic") ->
                PlatformBadge("Claude", 0xFFE07A5F)
            a.contains("vercel") || n.contains("react") || n.contains("next") || id.contains("vercel") ->
                PlatformBadge("Vercel", 0xFF00F2FE)
            a.contains("browser") || n.contains("browser") || id.contains("browser") ->
                PlatformBadge("Browser Use", 0xFFEC4899)
            a.contains("composio") || n.contains("composio") || id.contains("composio") ->
                PlatformBadge("Composio", 0xFF8B5CF6)
            a.contains("langchain") || n.contains("langchain") || id.contains("langchain") ->
                PlatformBadge("LangChain", 0xFF10B981)
            a.contains("supabase") || n.contains("supabase") || id.contains("supabase") ->
                PlatformBadge("Supabase", 0xFF3ECF8E)
            a.contains("cloudflare") || n.contains("cloudflare") || id.contains("cloudflare") ->
                PlatformBadge("Cloudflare", 0xFFF38020)
            else -> PlatformBadge("Skill", 0xFF94A3B8)
        }
    }
}
