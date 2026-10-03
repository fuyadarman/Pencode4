package com.example.preview.universal

import com.example.data.ProjectFileEntity

/**
 * FrameworkDetector
 * 
 * Automatically identifies the project framework (Native Android Kotlin Compose, 
 * Kotlin XML Layout, Flutter Dart, React Web, or Vanilla Web) from the workspace file tree.
 */
object FrameworkDetector {

    enum class FrameworkType(val displayName: String, val badgeColor: String, val iconEmoji: String) {
        KOTLIN_COMPOSE("Android Jetpack Compose", "#3DDC84", "🤖"),
        KOTLIN_XML("Android Native (XML Layout)", "#3DDC84", "📱"),
        FLUTTER_DART("Flutter (Dart)", "#02569B", "💙"),
        REACT_WEB("React Web", "#61DAFB", "⚛️"),
        VANILLA_WEB("HTML / JS Web", "#E44D26", "🌐"),
        UNKNOWN("Generic Code", "#8B949E", "📁")
    }

    data class DetectionResult(
        val frameworkType: FrameworkType,
        val primaryFile: String?,
        val entryPoints: List<String>,
        val isMobileFramework: Boolean
    )

    fun detect(files: List<ProjectFileEntity>): DetectionResult {
        if (files.isEmpty()) {
            return DetectionResult(FrameworkType.UNKNOWN, null, emptyList(), false)
        }

        val paths = files.map { it.path.lowercase() }

        // 1. Check Flutter (pubspec.yaml or lib/main.dart or *.dart files)
        val hasPubspec = paths.any { it.endsWith("pubspec.yaml") || it.endsWith("pubspec.yml") }
        val hasDartFiles = paths.any { it.endsWith(".dart") }
        val mainDart = files.find { it.path.endsWith("lib/main.dart", ignoreCase = true) || it.path.equals("main.dart", ignoreCase = true) }

        if (hasPubspec || (hasDartFiles && mainDart != null)) {
            val dartFiles = files.filter { it.path.endsWith(".dart", ignoreCase = true) }.map { it.path }
            return DetectionResult(
                frameworkType = FrameworkType.FLUTTER_DART,
                primaryFile = mainDart?.path ?: dartFiles.firstOrNull(),
                entryPoints = dartFiles,
                isMobileFramework = true
            )
        }

        // 2. Check Kotlin Android (build.gradle.kts, MainActivity.kt, @Composable or AndroidManifest.xml)
        val hasGradle = paths.any { it.endsWith("build.gradle") || it.endsWith("build.gradle.kts") || it.endsWith("settings.gradle.kts") }
        val kotlinFiles = files.filter { it.path.endsWith(".kt", ignoreCase = true) }
        val hasCompose = kotlinFiles.any { file ->
            file.content.contains("@Composable") || 
            file.content.contains("androidx.compose") || 
            file.content.contains("setContent {")
        }

        val mainActivity = kotlinFiles.find { it.path.endsWith("MainActivity.kt", ignoreCase = true) }
            ?: kotlinFiles.firstOrNull()

        if (hasCompose || (hasGradle && kotlinFiles.isNotEmpty())) {
            return DetectionResult(
                frameworkType = FrameworkType.KOTLIN_COMPOSE,
                primaryFile = mainActivity?.path,
                entryPoints = kotlinFiles.map { it.path },
                isMobileFramework = true
            )
        }

        val xmlLayouts = files.filter { it.path.contains("res/layout") && it.path.endsWith(".xml", ignoreCase = true) }
        if (xmlLayouts.isNotEmpty() && kotlinFiles.isNotEmpty()) {
            return DetectionResult(
                frameworkType = FrameworkType.KOTLIN_XML,
                primaryFile = mainActivity?.path ?: xmlLayouts.first().path,
                entryPoints = kotlinFiles.map { it.path } + xmlLayouts.map { it.path },
                isMobileFramework = true
            )
        }

        // 3. Check Web (index.html, React, JSX, JS)
        val hasIndexHtml = paths.any { it.endsWith("index.html") || it.equals("index.html") }
        val hasPackageJson = paths.any { it.endsWith("package.json") }
        val isReact = paths.any { it.endsWith(".jsx") || it.endsWith(".tsx") } || files.any { it.content.contains("React.") || it.content.contains("from 'react'") }

        if (isReact) {
            return DetectionResult(
                frameworkType = FrameworkType.REACT_WEB,
                primaryFile = files.find { it.path.endsWith("App.jsx", true) || it.path.endsWith("App.tsx", true) || it.path.endsWith("index.html", true) }?.path,
                entryPoints = files.filter { it.path.endsWith(".jsx", true) || it.path.endsWith(".tsx", true) || it.path.endsWith(".js", true) }.map { it.path },
                isMobileFramework = false
            )
        }

        if (hasIndexHtml) {
            return DetectionResult(
                frameworkType = FrameworkType.VANILLA_WEB,
                primaryFile = files.find { it.path.endsWith("index.html", true) }?.path,
                entryPoints = listOf("index.html"),
                isMobileFramework = false
            )
        }

        // Fallback check if any Kotlin files exist
        if (kotlinFiles.isNotEmpty()) {
            return DetectionResult(
                frameworkType = FrameworkType.KOTLIN_COMPOSE,
                primaryFile = kotlinFiles.first().path,
                entryPoints = kotlinFiles.map { it.path },
                isMobileFramework = true
            )
        }

        // Fallback check if any Dart files exist
        if (hasDartFiles) {
            return DetectionResult(
                frameworkType = FrameworkType.FLUTTER_DART,
                primaryFile = files.first { it.path.endsWith(".dart", true) }.path,
                entryPoints = files.filter { it.path.endsWith(".dart", true) }.map { it.path },
                isMobileFramework = true
            )
        }

        return DetectionResult(FrameworkType.UNKNOWN, null, emptyList(), false)
    }
}
