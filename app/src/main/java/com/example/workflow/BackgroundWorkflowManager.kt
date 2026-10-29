package com.example.workflow

import android.content.Context
import com.example.data.ProjectFileEntity
import com.example.data.VibeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * BackgroundWorkflowManager
 * 
 * Manages the background 'android.yml' GitHub Actions workflow file:
 * 1. Hidden from the user interface (File Explorer tree, Global Search, tabs) so the user cannot see it.
 * 2. Fully accessible to AI models for reading, writing, editing, and listing like a normal file.
 * 3. Automatically included and pushed to GitHub whenever the user clicks the Build button in the
 *    Build Tab OR when the AI executes 'trigger_build'.
 */
object BackgroundWorkflowManager {

    const val WORKFLOW_FILE_PATH = ".github/workflows/android.yml"
    const val WORKFLOW_ALIAS = "android.yml"

    /**
     * Determines if a file path is the background workflow file that should be hidden ONLY from the user.
     * The AI agent is not restricted by this and can view/edit it normally.
     */
    fun isUserHiddenFile(path: String?): Boolean {
        if (path.isNullOrBlank()) return false
        val clean = path.replace('\\', '/').trim().trimStart('/')
        val lower = clean.lowercase()
        return lower == "android.yml" ||
                lower == ".github/workflows/android.yml" ||
                lower.endsWith("/android.yml")
    }

    /**
     * Filters a list of project files for USER-facing views (Explorer tree, search, tabs).
     * Excludes background workflow files so the user does not see them.
     */
    fun filterUserVisibleFiles(files: List<ProjectFileEntity>): List<ProjectFileEntity> {
        return files.filter { !isUserHiddenFile(it.path) }
    }

    /**
     * Returns default workflow content for android.yml tailored to the template.
     */
    fun getDefaultWorkflowContent(templateKey: String?): String {
        return when (templateKey) {
            "flutter" -> """name: Flutter Build
on:
  push:
    branches: [ "main", "master" ]
  workflow_dispatch:

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: subosito/flutter-action@v2
        with:
          channel: 'stable'
      - run: flutter pub get
      - run: flutter build apk --release
      - uses: actions/upload-artifact@v4
        with:
          name: app-release
          path: build/app/outputs/flutter-apk/app-release.apk
"""
            "react_vite" -> """name: React Vite Web Build
on:
  push:
    branches: [ "main", "master" ]
  workflow_dispatch:

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-node@v4
        with:
          node-version: 20
      - run: npm install
      - run: npm run build
      - uses: actions/upload-artifact@v4
        with:
          name: web-dist
          path: dist/
"""
            else -> """name: Android Build
on:
  push:
    branches: [ "main", "master" ]
  workflow_dispatch:

env:
  ACTIONS_AUDIT_NODE_VERSION: 'false'

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'
          cache: gradle
      - name: Grant execute permission for gradlew
        run: chmod +x gradlew || true
      - name: Build with Gradle
        run: ./gradlew assembleDebug || gradle assembleDebug
      - name: Upload APK
        uses: actions/upload-artifact@v4
        with:
          name: app-debug
          path: app/build/outputs/apk/debug/app-debug.apk
"""
        }
    }

    /**
     * Ensures the background android.yml workflow exists both in the database and on disk.
     */
    suspend fun ensureBackgroundWorkflow(
        projectName: String,
        repository: VibeRepository,
        templateKey: String? = "android_kotlin"
    ): ProjectFileEntity = withContext(Dispatchers.IO) {
        val existingFiles = repository.getFilesForProject(projectName)
        val existingWorkflow = existingFiles.find { 
            val p = it.path.replace('\\', '/').trim().trimStart('/').lowercase()
            p == ".github/workflows/android.yml" || p == "android.yml" || p.endsWith("/android.yml")
        }

        if (existingWorkflow != null && existingWorkflow.content.isNotBlank()) {
            existingWorkflow
        } else {
            val content = getDefaultWorkflowContent(templateKey)
            repository.saveFile(projectName, WORKFLOW_FILE_PATH, content)
            ProjectFileEntity(
                projectName = projectName,
                path = WORKFLOW_FILE_PATH,
                content = content
            )
        }
    }

    /**
     * Prepares files for Git Push when a build is triggered.
     * Guarantees that .github/workflows/android.yml is included in the pushed files even if
     * hidden from the user interface.
     */
    suspend fun prepareFilesForBuildPush(
        projectName: String,
        repository: VibeRepository,
        currentFiles: List<ProjectFileEntity>,
        templateKey: String?,
        includeAndroidYml: Boolean = true
    ): List<ProjectFileEntity> = withContext(Dispatchers.IO) {
        val mutableFiles = currentFiles.toMutableList()
        if (includeAndroidYml) {
            val hasWorkflow = mutableFiles.any { 
                val p = it.path.replace('\\', '/').trim().trimStart('/').lowercase()
                p == ".github/workflows/android.yml" || p.endsWith("/android.yml")
            }

            if (!hasWorkflow) {
                val workflowFile = ensureBackgroundWorkflow(projectName, repository, templateKey)
                mutableFiles.add(workflowFile)
            }
        }
        mutableFiles
    }
}
