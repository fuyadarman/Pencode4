package com.example.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

data class AgentSkill(
    val id: String,
    val name: String,
    val author: String,
    val installs: String,
    val description: String,
    val githubUrl: String = "https://github.com/vercel-labs/agent-skills",
    val isInstalled: Boolean = false,
    val isEnabled: Boolean = true,
    val isCustom: Boolean = false,
    val skillPrompt: String = ""
)

val defaultAgentSkills = listOf(
    // --- Vercel Labs (vercel-labs/agent-skills, vercel-labs/skills) ---
    AgentSkill(
        id = "find-skills",
        name = "Find Skills",
        author = "vercel-labs/skills",
        installs = "24.1K installs",
        description = "Helps users discover and install agent skills when asking questions like \"how do I do X\", \"find a skill for X\", or expressing interest in extending capabilities.",
        githubUrl = "https://github.com/vercel-labs/skills",
        isInstalled = true,
        isEnabled = true,
        skillPrompt = """
            Skill: Find Skills
            When the user asks how to accomplish a task or search for capabilities, inspect installed agent skills and recommend or utilize relevant agent skills automatically.
        """.trimIndent()
    ),
    AgentSkill(
        id = "vercel-react-best-practices",
        name = "Vercel React Best Practices",
        author = "vercel-labs/agent-skills",
        installs = "19.8K installs",
        description = "React composition patterns that scale. Eliminate boolean prop proliferation, build flexible component libraries, and optimize hooks & state rendering.",
        githubUrl = "https://github.com/vercel-labs/agent-skills",
        isInstalled = true,
        isEnabled = true,
        skillPrompt = """
            Skill: React & Next.js Composition Best Practices
            - Avoid boolean prop proliferation; use composition and sub-components.
            - Ensure state stays local where possible and lift state cleanly.
            - Use modern React 18/19 hooks, useMemo, and useCallback appropriately.
        """.trimIndent()
    ),
    AgentSkill(
        id = "vercel-nextjs-app-router",
        name = "Next.js App Router Architecture",
        author = "vercel-labs/agent-skills",
        installs = "28.4K installs",
        description = "Best practices for Next.js App Router, Server Components (RSC), Client Components, Server Actions, route handlers, and streaming metadata.",
        githubUrl = "https://github.com/vercel-labs/agent-skills",
        isInstalled = false,
        isEnabled = true,
        skillPrompt = """
            Skill: Next.js App Router Master
            - Default to React Server Components (RSC) unless interactivity requires 'use client'.
            - Use Server Actions for type-safe form submissions and mutations.
            - Leverage loading.tsx and Suspense boundaries for progressive streaming.
        """.trimIndent()
    ),
    AgentSkill(
        id = "vercel-ai-sdk",
        name = "Vercel AI SDK Integration",
        author = "vercel-labs/agent-skills",
        installs = "22.9K installs",
        description = "Patterns for streamText, generateText, tool calling, object generation, and streaming conversational UI with the Vercel AI SDK.",
        githubUrl = "https://github.com/vercel-labs/agent-skills",
        isInstalled = false,
        isEnabled = true,
        skillPrompt = """
            Skill: Vercel AI SDK Integration
            - Use streamText and useChat for real-time conversational streaming interfaces.
            - Implement type-safe Zod schema tool calling with tool().
        """.trimIndent()
    ),
    AgentSkill(
        id = "web-design-guidelines",
        name = "Web Design Guidelines",
        author = "vercel-labs/agent-skills",
        installs = "18.2K installs",
        description = "Review UI code for Web Interface Guidelines compliance. Visual hierarchy, contrast ratio, accessibility, and clean responsive layout styling.",
        githubUrl = "https://github.com/vercel-labs/agent-skills",
        isInstalled = false,
        isEnabled = true,
        skillPrompt = """
            Skill: Web Interface Design Guidelines
            - High contrast ratio, fluid typography, clean padding (8dp grid).
            - Dark canvas background (#0D0E15) with subtle borders (#1E2230).
            - Accessible touch targets (minimum 48dp) and keyboard navigation.
        """.trimIndent()
    ),
    AgentSkill(
        id = "vercel-serverless-edge",
        name = "Vercel Serverless & Edge Functions",
        author = "vercel-labs/agent-skills",
        installs = "15.6K installs",
        description = "Optimize serverless function execution, reduce cold starts, utilize Vercel Edge Runtime, KV storage, and Postgres connections.",
        githubUrl = "https://github.com/vercel-labs/agent-skills",
        isInstalled = false,
        isEnabled = true,
        skillPrompt = """
            Skill: Vercel Serverless & Edge Optimization
            - Use Edge Functions for low-latency globally distributed endpoints.
            - Minimize bundle size and avoid heavy sync imports inside serverless handlers.
        """.trimIndent()
    ),
    AgentSkill(
        id = "turbopack-monorepo",
        name = "Turbopack & Monorepo Optimization",
        author = "vercel-labs/agent-skills",
        installs = "12.3K installs",
        description = "Turborepo setup, workspace dependency management, build caching, and fast incremental builds with Turbopack.",
        githubUrl = "https://github.com/vercel-labs/agent-skills",
        isInstalled = false,
        isEnabled = true,
        skillPrompt = """
            Skill: Turborepo Monorepo Architecture
            - Define pipeline tasks with outputs in turbo.json.
            - Share internal packages using modern exports field in package.json.
        """.trimIndent()
    ),
    AgentSkill(
        id = "tailwind-v4-styling",
        name = "Tailwind CSS v4 & Styling Guidelines",
        author = "vercel-labs/agent-skills",
        installs = "21.0K installs",
        description = "Modern Tailwind CSS v4 patterns, CSS variables theme mapping, dark mode color palettes, and responsive utility layout design.",
        githubUrl = "https://github.com/vercel-labs/agent-skills",
        isInstalled = false,
        isEnabled = true,
        skillPrompt = """
            Skill: Tailwind CSS v4 Utility Design
            - Use CSS variables for color tokenization.
            - Leverage flexbox and grid utilities with gap spacing.
        """.trimIndent()
    ),

    // --- Anthropic (anthropic/agent-skills, anthropic/mcp) ---
    AgentSkill(
        id = "anthropic-claude-prompt-engineering",
        name = "Claude Prompt Engineering",
        author = "anthropic/agent-skills",
        installs = "31.3K installs",
        description = "Advanced prompt design for Anthropic Claude 3.5 Sonnet & Haiku: XML tags structuring, chain-of-thought reasoning, and system prompt optimization.",
        githubUrl = "https://github.com/anthropic/agent-skills",
        isInstalled = true,
        isEnabled = true,
        skillPrompt = """
            Skill: Anthropic Claude Prompt Engineering
            - Structure context using XML tags like <context>, <instructions>, and <examples>.
            - Request step-by-step thinking inside <thinking> tags before producing final output.
            - Keep role system prompts concise, direct, and explicit about constraints.
        """.trimIndent()
    ),
    AgentSkill(
        id = "anthropic-mcp-protocol",
        name = "Anthropic Model Context Protocol (MCP)",
        author = "anthropic/agent-skills",
        installs = "28.7K installs",
        description = "Connect AI models to external tools, databases, and APIs using Anthropic Model Context Protocol (MCP) servers and clients.",
        githubUrl = "https://github.com/anthropic/mcp",
        isInstalled = false,
        isEnabled = true,
        skillPrompt = """
            Skill: Anthropic Model Context Protocol (MCP)
            - Standardize tool and resource exposing via JSON-RPC 2.0 endpoints.
            - Handle dynamic tool calls, prompts, and context fetching safely.
        """.trimIndent()
    ),
    AgentSkill(
        id = "anthropic-tool-use-master",
        name = "Claude Tool Use & Computer Vision",
        author = "anthropic/agent-skills",
        installs = "25.1K installs",
        description = "Multi-modal vision analysis, image prompt inspection, tool use execution, and agentic loop control patterns for Claude.",
        githubUrl = "https://github.com/anthropic/agent-skills",
        isInstalled = false,
        isEnabled = true,
        skillPrompt = """
            Skill: Anthropic Vision & Tool Automation
            - Parse base64/URL image inputs for visual UI inspection and bug triage.
            - Execute structured JSON tool invocations with strict validation.
        """.trimIndent()
    ),
    AgentSkill(
        id = "anthropic-agentic-loop",
        name = "Claude Agentic Loop & Reasoning",
        author = "anthropic/agent-skills",
        installs = "20.4K installs",
        description = "Autonomous multi-turn agent loops with tool feedback handling, state management, and self-correction error recovery.",
        githubUrl = "https://github.com/anthropic/agent-skills",
        isInstalled = false,
        isEnabled = true,
        skillPrompt = """
            Skill: Anthropic Agentic Reasoning Loop
            - Evaluate tool call results objectively before formulating the next action.
            - If a tool call fails, analyze the error output and auto-correct parameter inputs.
        """.trimIndent()
    ),
    AgentSkill(
        id = "anthropic-context-optimization",
        name = "Claude Context Window Optimization",
        author = "anthropic/agent-skills",
        installs = "16.8K installs",
        description = "Optimize large 200k context windows, dynamic document indexing, key value memory retention, and token reduction.",
        githubUrl = "https://github.com/anthropic/agent-skills",
        isInstalled = false,
        isEnabled = true,
        skillPrompt = """
            Skill: Claude Context Management
            - Compress historical dialogue context while preserving key decision states.
            - Group related context under labeled XML tags.
        """.trimIndent()
    ),
    AgentSkill(
        id = "anthropic-guardrails-safety",
        name = "Claude Guardrails & Safety",
        author = "anthropic/agent-skills",
        installs = "14.2K installs",
        description = "Output format enforcement, strict schema validation, markdown sanitization, and safety response framing.",
        githubUrl = "https://github.com/anthropic/agent-skills",
        isInstalled = false,
        isEnabled = true,
        skillPrompt = """
            Skill: Anthropic Safety & Output Guardrails
            - Enforce structured JSON schemas without extraneous markdown text.
            - Validate inputs and gracefully report errors.
        """.trimIndent()
    ),

    // --- Expo (expo/skills, expo/expo) ---
    AgentSkill(
        id = "expo-router-navigation",
        name = "Expo Router & File Navigation",
        author = "expo/skills",
        installs = "29.8K installs",
        description = "File-based routing for React Native with Expo Router. Layouts, stacks, tabs, dynamic routes, and typed links.",
        githubUrl = "https://github.com/expo/expo",
        isInstalled = true,
        isEnabled = true,
        skillPrompt = """
            Skill: Expo Router File Navigation
            - Use app/ folder hierarchy with _layout.tsx for Stack/Tabs configuration.
            - Use useRouter() and <Link href="..."> for type-safe native transitions.
        """.trimIndent()
    ),
    AgentSkill(
        id = "expo-react-native-master",
        name = "Expo React Native Patterns",
        author = "expo/skills",
        installs = "32.5K installs",
        description = "Cross-platform mobile apps with Expo SDK 51+, Reanimated v3, Skia graphics, NativeWind, and EAS Build pipelines.",
        githubUrl = "https://github.com/expo/expo",
        isInstalled = false,
        isEnabled = true,
        skillPrompt = """
            Skill: Expo React Native Cross-Platform
            - Optimize list rendering with FlashList or FlatList.
            - Handle device safe areas with react-native-safe-area-context.
            - Animate native UI components using react-native-reanimated.
        """.trimIndent()
    ),
    AgentSkill(
        id = "expo-native-modules",
        name = "Expo Native Modules & Kotlin/Swift Adapters",
        author = "expo/skills",
        installs = "19.1K installs",
        description = "Custom Swift/Kotlin native plugins and native module bridge integration for Expo SDK 51+.",
        githubUrl = "https://github.com/expo/expo",
        isInstalled = false,
        isEnabled = true,
        skillPrompt = """
            Skill: Expo Native Module Integration
            - Write native Android Kotlin / iOS Swift module specs using Expo Modules API.
        """.trimIndent()
    ),
    AgentSkill(
        id = "expo-eas-build-ota",
        name = "Expo EAS Build & Updates",
        author = "expo/skills",
        installs = "24.6K installs",
        description = "EAS build configuration profiles, over-the-air (OTA) updates publishing, and runtime version management.",
        githubUrl = "https://github.com/expo/expo",
        isInstalled = false,
        isEnabled = true,
        skillPrompt = """
            Skill: Expo EAS & OTA Workflow
            - Configure eas.json with development, preview, and production profiles.
        """.trimIndent()
    ),
    AgentSkill(
        id = "expo-sensors-hardware",
        name = "Expo Camera, Location & Hardware",
        author = "expo/skills",
        installs = "21.4K installs",
        description = "Integrate device hardware APIs using Expo SDK: Camera, Gyroscope/Accelerometer, GPS Location, Biometrics, and Audio.",
        githubUrl = "https://github.com/expo/expo",
        isInstalled = false,
        isEnabled = true,
        skillPrompt = """
            Skill: Expo Hardware API Integration
            - Request device runtime permissions gracefully before accessing Camera/Location.
        """.trimIndent()
    ),

    // --- NextLevelBuilder (nextlevelbuilder/agent-skills) ---
    AgentSkill(
        id = "nextlevelbuilder-vibe-coding",
        name = "NextLevelBuilder Vibe Coding",
        author = "nextlevelbuilder/agent-skills",
        installs = "41.2K installs",
        description = "End-to-end full-stack app architecture: AI prompt analysis, incremental code generation, state persistence, and continuous app compilation.",
        githubUrl = "https://github.com/nextlevelbuilder/agent-skills",
        isInstalled = true,
        isEnabled = true,
        skillPrompt = """
            Skill: NextLevelBuilder Full-Stack Vibe Coding
            - Execute prompt intent with pristine UI layout, high contrast themes, and smooth animations.
            - Maintain clean modular architecture and synchronized local state storage.
            - Produce production-grade code without dead-end mock buttons.
        """.trimIndent()
    ),
    AgentSkill(
        id = "nextlevelbuilder-mobile-ui",
        name = "NextLevelBuilder Mobile Component Kit",
        author = "nextlevelbuilder/agent-skills",
        installs = "27.4K installs",
        description = "Polished Material 3 / Tailwind mobile UI components with gesture controls, glassmorphic card containers, and fluid touch interactions.",
        githubUrl = "https://github.com/nextlevelbuilder/agent-skills",
        isInstalled = true,
        isEnabled = true,
        skillPrompt = """
            Skill: NextLevelBuilder UI Design System
            - Use dark glassmorphic cards (background #131520, border #222533).
            - Accent key actions with cyan/blue gradient fills (#00F2FE -> #4FACFE).
            - Minimum touch target 48dp with haptic/ripple visual feedback.
        """.trimIndent()
    ),
    AgentSkill(
        id = "nextlevelbuilder-app-generator",
        name = "NextLevelBuilder Automated App Engine",
        author = "nextlevelbuilder/agent-skills",
        installs = "33.8K installs",
        description = "Automated boilerplate creation, database schema generator, Retrofit API client wiring, and ViewModels setup.",
        githubUrl = "https://github.com/nextlevelbuilder/agent-skills",
        isInstalled = false,
        isEnabled = true,
        skillPrompt = """
            Skill: NextLevelBuilder Automated App Generator
            - Generate complete reactive MVVM architectures with StateFlow UI bindings.
        """.trimIndent()
    ),
    AgentSkill(
        id = "nextlevelbuilder-sync-db",
        name = "NextLevelBuilder Local DB & Sync Engine",
        author = "nextlevelbuilder/agent-skills",
        installs = "23.5K installs",
        description = "SQLite Room database caching, background thread synchronization, and offline-first data resilience.",
        githubUrl = "https://github.com/nextlevelbuilder/agent-skills",
        isInstalled = false,
        isEnabled = true,
        skillPrompt = """
            Skill: NextLevelBuilder Local DB & Sync
            - Use Room DAOs with Flow reactive updates and Dispatchers.IO isolation.
        """.trimIndent()
    ),
    AgentSkill(
        id = "nextlevelbuilder-apk-pipeline",
        name = "NextLevelBuilder Android APK Build Pipeline",
        author = "nextlevelbuilder/agent-skills",
        installs = "38.1K installs",
        description = "Gradle compilation optimization, KSP symbol processing, keystore signing, and APK bundle generation.",
        githubUrl = "https://github.com/nextlevelbuilder/agent-skills",
        isInstalled = false,
        isEnabled = true,
        skillPrompt = """
            Skill: NextLevelBuilder APK Build Pipeline
            - Ensure clean gradle dependencies and zero compilation errors.
        """.trimIndent()
    ),

    // --- Standard Google & Android ---
    AgentSkill(
        id = "android-jetpack-compose",
        name = "Android Jetpack Compose Patterns",
        author = "google-ai/skills",
        installs = "38.5K installs",
        description = "Modern Material Design 3 patterns, state management with StateFlow and collectAsStateWithLifecycle, WindowInsets edge-to-edge layout, and performance optimization.",
        githubUrl = "https://github.com/androidx/androidx",
        isInstalled = true,
        isEnabled = true,
        skillPrompt = """
            Skill: Jetpack Compose & M3 Architecture
            - Use Material Theme tokens (MaterialTheme.colorScheme).
            - Implement edge-to-edge insets handling with Scaffold.
            - Optimize recompositions with remember, derivedStateOf, and key().
        """.trimIndent()
    ),
    AgentSkill(
        id = "room-db-guide",
        name = "Room Database Pattern",
        author = "google-ai/skills",
        installs = "22.3K installs",
        description = "Local data persistence with Room SQLite, DAOs, Flow reactive queries, TypeConverters, and KSP annotation processing.",
        githubUrl = "https://github.com/androidx/androidx",
        isInstalled = true,
        isEnabled = true,
        skillPrompt = """
            Skill: Room Database Persistence
            - Define clean @Entity data classes with autoGenerate PrimaryKeys.
            - Provide Flow<List<T>> reactive DAO methods.
        """.trimIndent()
    ),
    AgentSkill(
        id = "threejs-3d-master",
        name = "Three.js 3D Master",
        author = "threejs-dev/skills",
        installs = "15.8K installs",
        description = "3D Web Graphics, 3D Globe, perspective camera setup, dynamic lighting, custom shaders, mesh animation loops, and WebGL rendering with Three.js.",
        githubUrl = "https://github.com/mrdoob/three.js",
        isInstalled = true,
        isEnabled = true,
        skillPrompt = """
            Skill: Three.js 3D Web Engine
            - Use THREE.Scene, THREE.PerspectiveCamera, THREE.WebGLRenderer, and OrbitControls.
            - Build interactive 3D Globe, 3D Models, 3D Games, 3D Websites, and 3D Objects.
            - Handle window resize events dynamically.
        """.trimIndent()
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentSkillsDialog(
    skills: List<AgentSkill>,
    onToggleSkill: (String, Boolean) -> Unit,
    onInstallSkill: (String) -> Unit,
    onUninstallSkill: (String) -> Unit,
    onAddCustomSkill: (AgentSkill) -> Unit,
    onFetchOnlineSkills: () -> Unit = {},
    isFetchingSkills: Boolean = false,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }
    var showAddCustomDialog by remember { mutableStateOf(false) }

    val categories = listOf("All", "Vercel Labs", "Anthropic", "Expo", "NextLevelBuilder", "Installed")

    val filteredSkills = remember(skills, searchQuery, selectedCategory) {
        skills.filter { skill ->
            val matchesQuery = searchQuery.isBlank() ||
                skill.name.contains(searchQuery, ignoreCase = true) ||
                skill.author.contains(searchQuery, ignoreCase = true) ||
                skill.description.contains(searchQuery, ignoreCase = true)

            val matchesCategory = when (selectedCategory) {
                "Vercel Labs" -> skill.author.contains("vercel", ignoreCase = true)
                "Anthropic" -> skill.author.contains("anthropic", ignoreCase = true)
                "Expo" -> skill.author.contains("expo", ignoreCase = true)
                "NextLevelBuilder" -> skill.author.contains("nextlevelbuilder", ignoreCase = true)
                "Installed" -> skill.isInstalled
                else -> true
            }

            matchesQuery && matchesCategory
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0B0C10))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Top Navigation Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF161822))
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        // Pill Badge for Agent Skills
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(Color(0xFF1A1D2C))
                                .border(BorderStroke(1.dp, Color(0xFF2A2E44)), RoundedCornerShape(20.dp))
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Extension,
                                    contentDescription = null,
                                    tint = Color(0xFF00F2FE),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Agent Skills",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Top Action Buttons
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IconButton(
                            onClick = {
                                onFetchOnlineSkills()
                                android.widget.Toast.makeText(context, "Fetching live skills from Vercel-labs, Anthropic, Expo & NextLevelBuilder...", android.widget.Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF161822))
                                .border(BorderStroke(1.dp, Color(0xFF00F2FE).copy(alpha = 0.5f)), CircleShape)
                        ) {
                            if (isFetchingSkills) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = Color(0xFF00F2FE),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Fetch Skills",
                                    tint = Color(0xFF00F2FE)
                                )
                            }
                        }

                        IconButton(
                            onClick = { showAddCustomDialog = true },
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF00F2FE))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Add Custom Skill",
                                tint = Color(0xFF0D0E15)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Discover Skills Header
                Text(
                    text = "Discover Agent Skills",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Text(
                    text = "Extend your Agent with skills from Vercel-labs, Anthropic, Expo & NextLevelBuilder.",
                    fontSize = 13.sp,
                    color = Color(0xFF94A3B8),
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                )

                // Search Skills Field
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search skills (e.g., Anthropic, Expo, Vercel)...", color = Color(0xFF64748B), fontSize = 13.sp) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = Color(0xFF64748B)
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Clear",
                                    tint = Color(0xFF64748B)
                                )
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF161822)),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF00F2FE),
                        unfocusedBorderColor = Color(0xFF222533),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Category Filter Chips
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(categories) { category ->
                        val isSelected = selectedCategory == category
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(if (isSelected) Color(0xFF00F2FE) else Color(0xFF161822))
                                .border(
                                    BorderStroke(1.dp, if (isSelected) Color(0xFF00F2FE) else Color(0xFF222533)),
                                    RoundedCornerShape(20.dp)
                                )
                                .clickable { selectedCategory = category }
                                .padding(horizontal = 14.dp, vertical = 7.dp)
                        ) {
                            Text(
                                text = category,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) Color(0xFF0D0E15) else Color(0xFFCBD5E1)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Skills List
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredSkills, key = { it.id }) { skill ->
                        var isExpanded by remember { mutableStateOf(false) }

                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF131520)),
                            border = BorderStroke(1.dp, if (skill.isInstalled && skill.isEnabled) Color(0xFF00F2FE).copy(alpha = 0.4f) else Color(0xFF222533)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                            ) {
                                // Title & Install/Toggle
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = skill.name,
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )

                                        Text(
                                            text = "${skill.author} • ${skill.installs}",
                                            fontSize = 12.sp,
                                            color = Color(0xFF94A3B8),
                                            modifier = Modifier.padding(top = 2.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(8.dp))

                                    if (skill.isInstalled) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = if (skill.isEnabled) "ON" else "OFF",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (skill.isEnabled) Color(0xFF00F2FE) else Color(0xFF64748B),
                                                modifier = Modifier.padding(end = 6.dp)
                                            )
                                            Switch(
                                                checked = skill.isEnabled,
                                                onCheckedChange = { onToggleSkill(skill.id, it) },
                                                colors = SwitchDefaults.colors(
                                                    checkedThumbColor = Color(0xFF0D0E15),
                                                    checkedTrackColor = Color(0xFF00F2FE),
                                                    uncheckedThumbColor = Color(0xFF94A3B8),
                                                    uncheckedTrackColor = Color(0xFF222533)
                                                ),
                                                modifier = Modifier.scale(0.85f)
                                            )
                                        }
                                    } else {
                                        Button(
                                            onClick = { onInstallSkill(skill.id) },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF222638)),
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.FileDownload,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "Install",
                                                color = Color.White,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                // Description
                                Text(
                                    text = skill.description,
                                    fontSize = 13.sp,
                                    color = Color(0xFFCBD5E1),
                                    lineHeight = 18.sp
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                // Footer Row: View on GitHub & Expand details / Uninstall
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            try {
                                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(skill.githubUrl))
                                                context.startActivity(intent)
                                            } catch (_: Exception) {}
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        border = BorderStroke(1.dp, Color(0xFF2E344A)),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        modifier = Modifier.height(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Code,
                                            contentDescription = null,
                                            tint = Color(0xFF94A3B8),
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "View on GitHub",
                                            fontSize = 12.sp,
                                            color = Color(0xFFE2E8F0)
                                        )
                                    }

                                    if (skill.isInstalled) {
                                        IconButton(
                                            onClick = { onUninstallSkill(skill.id) },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.DeleteOutline,
                                                contentDescription = "Uninstall Skill",
                                                tint = Color(0xFFFF5252),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddCustomDialog) {
        AddCustomSkillDialog(
            onDismiss = { showAddCustomDialog = false },
            onAdd = { newSkill ->
                onAddCustomSkill(newSkill)
                showAddCustomDialog = false
            }
        )
    }
}

@Composable
fun AddCustomSkillDialog(
    onDismiss: () -> Unit,
    onAdd: (AgentSkill) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var author by remember { mutableStateOf("user/custom-skill") }
    var description by remember { mutableStateOf("") }
    var prompt by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF131520)),
            border = BorderStroke(1.dp, Color(0xFF222533)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Upload / Create Custom Skill",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Skill Name", color = Color(0xFF94A3B8)) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF00F2FE),
                        unfocusedBorderColor = Color(0xFF222533),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    singleLine = true
                )

                OutlinedTextField(
                    value = author,
                    onValueChange = { author = it },
                    label = { Text("Author / Repository", color = Color(0xFF94A3B8)) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF00F2FE),
                        unfocusedBorderColor = Color(0xFF222533),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    singleLine = true
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Short Description", color = Color(0xFF94A3B8)) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF00F2FE),
                        unfocusedBorderColor = Color(0xFF222533),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    minLines = 2,
                    maxLines = 3
                )

                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    label = { Text("Skill Prompt / SKILL.md Content", color = Color(0xFF94A3B8)) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF00F2FE),
                        unfocusedBorderColor = Color(0xFF222533),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    minLines = 4,
                    maxLines = 6
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = Color(0xFF94A3B8))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (name.isNotBlank()) {
                                val id = name.lowercase().replace(" ", "-").replace(Regex("[^a-z0-9-]"), "")
                                onAdd(
                                    AgentSkill(
                                        id = if (id.isEmpty()) "custom-skill-${System.currentTimeMillis()}" else id,
                                        name = name.trim(),
                                        author = author.ifBlank { "custom/user" },
                                        installs = "Custom Skill",
                                        description = description.ifBlank { "Custom user-defined skill." },
                                        githubUrl = "https://github.com",
                                        isInstalled = true,
                                        isEnabled = true,
                                        isCustom = true,
                                        skillPrompt = prompt.ifBlank { "Skill $name: Follow user rules." }
                                    )
                                )
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00F2FE)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Add Skill", color = Color(0xFF0D0E15), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
