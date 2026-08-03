package com.example.data

import java.io.File

enum class FrameworkType(val displayName: String, val defaultTestCommand: String) {
    ANDROID_KOTLIN("Android Kotlin", "gradle :app:testDebugUnitTest"),
    FLUTTER("Flutter", "flutter test"),
    REACT_VITE("React / Vite", "npm test -- --watchAll=false"),
    GENERIC("Generic Environment", "echo 'Generic workspace runner'")
}

data class CredentialsCheckResult(
    val isValid: Boolean,
    val promptMessage: String? = null
)

/**
 * Manages GitHub Actions background workflow dispatch, command.yml updates,
 * framework auto-detection, and terminal execution logging.
 */
class GitHubCommandWorkflowManager {

    /**
     * Checks if a command is considered heavy or unsupported on local device
     * and should be routed to GitHub Actions command.yml
     */
    fun isHeavyCommand(command: String): Boolean {
        val cmdLower = command.trim().lowercase()
        val heavyKeywords = listOf(
            "npm", "npx", "yarn", "pnpm", "node", "flutter", "dart",
            "docker", "cargo", "mvn", "python", "pip", "pytest", "go ",
            "gradlew", "gradle", "git push", "git commit", "integration-test",
            "heavy", "build-apk", "compile", "esbuild", "vite", "webpack", "rollup", "next"
        )
        return heavyKeywords.any { cmdLower.contains(it) }
    }

    /**
     * Checks if GitHub Repository Name and GHP Token are configured.
     * Asks user if credentials are missing.
     */
    fun checkGitHubCredentials(repoName: String?, ghpToken: String?): CredentialsCheckResult {
        val missingRepo = repoName.isNullOrBlank()
        val missingToken = ghpToken.isNullOrBlank()

        if (missingRepo || missingToken) {
            val missing = mutableListOf<String>()
            if (missingRepo) missing.add("GitHub Repository Name (e.g., user/repo)")
            if (missingToken) missing.add("GitHub Personal Access Token (GHP Token)")

            val msg = """
                [GitHub Credentials Required]
                To execute heavy commands ($missing) via GitHub Action command.yml:
                Please provide your:
                ${missing.joinToString("\n• ") { "• $it" }}
                
                Set them in settings or enter:
                repo: <username>/<repository>
                token: ghp_xxxxxxxxxxxxxxxxxxxx
            """.trimIndent()

            return CredentialsCheckResult(isValid = false, promptMessage = msg)
        }
        return CredentialsCheckResult(isValid = true)
    }

    /**
     * Automatically detects workspace framework based on project configuration files.
     */
    fun detectFramework(workspaceRoot: File): FrameworkType {
        val pubspec = File(workspaceRoot, "pubspec.yaml")
        val packageJson = File(workspaceRoot, "package.json")
        val gradleKts = File(workspaceRoot, "build.gradle.kts")
        val gradle = File(workspaceRoot, "build.gradle")

        return when {
            pubspec.exists() -> FrameworkType.FLUTTER
            gradleKts.exists() || gradle.exists() -> FrameworkType.ANDROID_KOTLIN
            packageJson.exists() -> FrameworkType.REACT_VITE
            else -> FrameworkType.GENERIC
        }
    }

    /**
     * Creates or updates the .github/workflows/command.yml file with updated command specifications.
     */
    fun updateCommandWorkflow(
        workspaceRoot: File,
        customCommand: String? = null
    ): File {
        val framework = detectFramework(workspaceRoot)
        val workflowDir = File(workspaceRoot, ".github/workflows")
        if (!workflowDir.exists()) {
            workflowDir.mkdirs()
        }

        val workflowFile = File(workflowDir, "command.yml")
        val activeCommand = customCommand ?: framework.defaultTestCommand

        val yamlContent = """
            name: Command & Test Execution Workflow

            on:
              push:
                branches: [ main, master, dev ]
              workflow_dispatch:
                inputs:
                  custom_command:
                    description: 'Custom command or test to execute'
                    required: false
                    default: '${activeCommand.replace("'", "''")}'
                  framework_target:
                    description: 'Detected framework'
                    required: false
                    default: '${framework.displayName}'

            jobs:
              execute-command:
                runs-on: ubuntu-latest
                steps:
                  - name: Checkout Codebase
                    uses: actions/checkout@v4
                    with:
                      fetch-depth: 0

                  - name: Framework Environment Setup
                    run: |
                      echo "Detected Framework: ${framework.displayName}"
                      echo "Executing Command: ${activeCommand.replace("'", "''")}"

                  - name: Run Background Command & Tests
                    run: |
                      echo "=== Starting Terminal Command Output Log ==="
                      ${activeCommand}
                      echo "=== Terminal Command Executed Successfully ==="

                  - name: Force Sync & Overwrite Push
                    if: always()
                    run: |
                      git config --global user.name "github-actions[bot]"
                      git config --global user.email "github-actions[bot]@users.noreply.github.com"
                      git add -A
                      git diff-index --quiet HEAD || (git commit -m "auto: sync codebase state & heavy task logs [ci skip]" && git push --force)
        """.trimIndent()

        workflowFile.writeText(yamlContent)
        return workflowFile
    }

    /**
     * Analyzes terminal command and formats log response for terminal display.
     */
    fun formatCommandExecutionLog(command: String, framework: FrameworkType, isSuccess: Boolean, output: String): String {
        val statusTag = if (isSuccess) "[SUCCESS LOG]" else "[ERROR / WARNING LOG]"
        return """
            ================================================================
            [GitHub Actions Background Runner - command.yml]
            Framework Detected : ${framework.displayName}
            Command Executed   : $command
            Execution Status   : $statusTag
            ----------------------------------------------------------------
            $output
            ================================================================
            [Sync Engine] All codebase files forcefully updated and synced to repository.
        """.trimIndent()
    }
}
