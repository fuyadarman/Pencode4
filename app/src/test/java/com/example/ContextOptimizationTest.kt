package com.example

import com.example.api.Content
import com.example.api.Part
import com.example.context.CodeChunkerAndPruner
import com.example.context.ContextOptimizationManager
import com.example.context.ContextSummarizer
import com.example.context.SymbolCodeIndexer
import org.junit.Assert.*
import org.junit.Test

class ContextOptimizationTest {

    @Test
    fun testCodeChunkerAndPruner_fileReadsAndDuplicates() {
        val longContent = "A".repeat(6000)
        val readPart1 = Part(text = "System/Tool Output for 'read_file' (File: app/src/main/Test.kt):\n--- File: app/src/main/Test.kt ---\n$longContent")
        val readPart2 = Part(text = "System/Tool Output for 'read_file' (File: app/src/main/Test.kt):\n--- File: app/src/main/Test.kt ---\n$longContent")

        val history = listOf(
            Content(role = "user", parts = listOf(readPart1)),
            Content(role = "user", parts = listOf(readPart2))
        )

        val result = CodeChunkerAndPruner.pruneAndChunkContents(history)

        // Part 1 (older) should be omitted because part 2 (newer) was read after
        val text1 = result[0].parts.first().text ?: ""
        val text2 = result[1].parts.first().text ?: ""

        assertTrue("Older duplicate read should be omitted", text1.contains("Older duplicate content omitted"))
        assertTrue("Newer read should be chunked", text2.contains("Smart Chunking: Omitted"))
    }

    @Test
    fun testContextSummarizer_compressHistory() {
        val history = mutableListOf<Content>()
        history.add(Content(role = "user", parts = listOf(Part(text = "Create a new app feature"))))

        // Add 12 operational turns
        for (i in 1..12) {
            history.add(Content(role = "user", parts = listOf(Part(text = "System/Tool Output for 'edit_file':\n--- File: File$i.kt ---\nModified code"))))
        }

        val compressed = ContextSummarizer.compressHistory(history, maxTokenThreshold = 100)

        assertTrue("History size should be compressed", compressed.size < history.size)
        val summaryContent = compressed[1].parts.first().text ?: ""
        assertTrue("Context Memory State should be created", summaryContent.contains("[Context Memory State"))
    }

    @Test
    fun testSymbolCodeIndexer_indexingAndRAG() {
        val indexer = SymbolCodeIndexer()
        val codeSample = """
            package com.example
            
            class VibeRepository {
                fun fetchUserSessions() {}
            }
            
            @Composable
            fun UserSessionCard() {}
            
            val activeState = mutableStateOf(true)
        """.trimIndent()

        indexer.indexFileContent("app/src/main/VibeRepository.kt", codeSample)

        val symbols = indexer.findRelevantSymbols("fetchUserSessions")
        assertEquals(1, symbols.size)
        assertEquals("fetchUserSessions", symbols.first().name)
        assertEquals("function", symbols.first().kind)

        val block = indexer.buildSymbolContextBlock("UserSessionCard")
        assertTrue(block.contains("UserSessionCard"))
    }

    @Test
    fun testContextOptimizationManager_fullPipeline() {
        val manager = ContextOptimizationManager()
        val history = listOf(
            Content(role = "user", parts = listOf(Part(text = "Build feature"))),
            Content(role = "user", parts = listOf(Part(text = "System/Tool Output for 'run_command':\nBuild succeeded")))
        )

        val optimized = manager.optimizeHistory(history)
        assertNotNull(optimized)
        assertEquals(2, optimized.size)
    }
}
