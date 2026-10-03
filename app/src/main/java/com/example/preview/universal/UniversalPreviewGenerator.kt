package com.example.preview.universal

import com.example.data.ProjectFileEntity

/**
 * UniversalPreviewGenerator
 * 
 * Master coordinator for Pencode Universal Preview Engine.
 * Automatically detects Kotlin Jetpack Compose or Flutter Dart projects,
 * converts them to PreviewIR, and generates an interactive, zero-emulator mobile preview.
 */
object UniversalPreviewGenerator {

    /**
     * Determines whether the given project files can be rendered by the Universal Preview Engine.
     */
    fun isUniversalPreviewApplicable(files: List<ProjectFileEntity>): Boolean {
        val detection = FrameworkDetector.detect(files)
        return detection.isMobileFramework
    }

    /**
     * Generates the complete interactive preview HTML for the project.
     */
    fun generateInteractivePreviewHtml(files: List<ProjectFileEntity>): String {
        val detection = FrameworkDetector.detect(files)

        val document: PreviewDocument = when (detection.frameworkType) {
            FrameworkDetector.FrameworkType.KOTLIN_COMPOSE,
            FrameworkDetector.FrameworkType.KOTLIN_XML -> {
                KotlinComposeAdapter.parse(files)
            }
            FrameworkDetector.FrameworkType.FLUTTER_DART -> {
                FlutterAdapter.parse(files)
            }
            else -> {
                // Fallback document
                PreviewDocument(
                    framework = detection.frameworkType,
                    appTitle = "Mobile Preview",
                    screens = emptyList(),
                    globalState = emptyMap()
                )
            }
        }

        return UniversalPreviewRuntime.generateHtml(document)
    }
}
