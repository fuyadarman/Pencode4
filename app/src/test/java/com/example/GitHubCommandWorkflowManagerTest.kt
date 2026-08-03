package com.example

import com.example.data.FrameworkType
import com.example.data.GitHubCommandWorkflowManager
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class GitHubCommandWorkflowManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testDetectFramework_AndroidKotlin() {
        val manager = GitHubCommandWorkflowManager()
        val root = tempFolder.newFolder()
        File(root, "build.gradle.kts").createNewFile()

        val framework = manager.detectFramework(root)
        assertEquals(FrameworkType.ANDROID_KOTLIN, framework)
    }

    @Test
    fun testDetectFramework_Flutter() {
        val manager = GitHubCommandWorkflowManager()
        val root = tempFolder.newFolder()
        File(root, "pubspec.yaml").createNewFile()

        val framework = manager.detectFramework(root)
        assertEquals(FrameworkType.FLUTTER, framework)
    }

    @Test
    fun testDetectFramework_ReactVite() {
        val manager = GitHubCommandWorkflowManager()
        val root = tempFolder.newFolder()
        File(root, "package.json").createNewFile()

        val framework = manager.detectFramework(root)
        assertEquals(FrameworkType.REACT_VITE, framework)
    }

    @Test
    fun testUpdateCommandWorkflow() {
        val manager = GitHubCommandWorkflowManager()
        val root = tempFolder.newFolder()
        File(root, "build.gradle.kts").createNewFile()

        val workflowFile = manager.updateCommandWorkflow(root, "gradle test")
        assertTrue(workflowFile.exists())
        val content = workflowFile.readText()
        assertTrue(content.contains("Command & Test Execution Workflow"))
        assertTrue(content.contains("gradle test"))
        assertTrue(content.contains("Android Kotlin"))
    }

    @Test
    fun testFormatCommandExecutionLog() {
        val manager = GitHubCommandWorkflowManager()
        val log = manager.formatCommandExecutionLog("gradle test", FrameworkType.ANDROID_KOTLIN, true, "BUILD SUCCESSFUL")
        assertTrue(log.contains("[GitHub Actions Background Runner - command.yml]"))
        assertTrue(log.contains("BUILD SUCCESSFUL"))
        assertTrue(log.contains("[SUCCESS LOG]"))
    }
}
