package com.example.data

import java.io.File

enum class FrameworkType(val displayName: String, val defaultTestCommand: String) {
    ANDROID_KOTLIN("Android Kotlin", "gradle :app:testDebugUnitTest"),
    FLUTTER("Flutter", "flutter test"),
    REACT_VITE("React / Vite", "npm test -- --watchAll=false"),
    GENERIC("Generic Environment", "echo 'Generic workspace runner'")
}

/**
 * Manages GitHub Actions background workflow dispatch, command.yml updates,
 * framework auto-detection, and terminal execution logging.
 */
class GitHubCommandWorkflowManager {

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
