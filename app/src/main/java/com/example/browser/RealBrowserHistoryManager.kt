package com.example.browser

import android.content.Context
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class BrowserBookmark(
    val title: String,
    val url: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class BrowserHistoryItem(
    val title: String,
    val url: String,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * RealBrowserHistoryManager
 * Manages full bookmarks, search history, and real browser configurations for Preview Tab.
 */
object RealBrowserHistoryManager {

    private const val PREFS_NAME = "pencode_browser_prefs"
    private const val KEY_BOOKMARKS = "bookmarks"
    private const val KEY_HISTORY = "history"

    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val bmType = Types.newParameterizedType(List::class.java, BrowserBookmark::class.java)
    private val bmAdapter = moshi.adapter<List<BrowserBookmark>>(bmType)

    private val histType = Types.newParameterizedType(List::class.java, BrowserHistoryItem::class.java)
    private val histAdapter = moshi.adapter<List<BrowserHistoryItem>>(histType)

    private val _bookmarks = MutableStateFlow<List<BrowserBookmark>>(emptyList())
    val bookmarks: StateFlow<List<BrowserBookmark>> = _bookmarks.asStateFlow()

    private val _history = MutableStateFlow<List<BrowserHistoryItem>>(emptyList())
    val history: StateFlow<List<BrowserHistoryItem>> = _history.asStateFlow()

    private var appContext: Context? = null

    val POPULAR_QUICK_LINKS = listOf(
        BrowserBookmark("Google", "https://www.google.com"),
        BrowserBookmark("Amazon", "https://www.amazon.com"),
        BrowserBookmark("LinkedIn", "https://www.linkedin.com"),
        BrowserBookmark("Indeed Jobs", "https://www.indeed.com"),
        BrowserBookmark("GitHub", "https://www.github.com"),
        BrowserBookmark("Twitter / X", "https://twitter.com")
    )

    fun initialize(context: Context) {
        appContext = context.applicationContext
        loadBookmarks()
        loadHistory()
    }

    private fun loadBookmarks() {
        val ctx = appContext ?: return
        val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_BOOKMARKS, null)
        if (!json.isNullOrBlank()) {
            try {
                _bookmarks.value = bmAdapter.fromJson(json) ?: POPULAR_QUICK_LINKS
                return
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        _bookmarks.value = POPULAR_QUICK_LINKS
    }

    private fun saveBookmarks() {
        val ctx = appContext ?: return
        try {
            val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val json = bmAdapter.toJson(_bookmarks.value)
            prefs.edit().putString(KEY_BOOKMARKS, json).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun addBookmark(title: String, url: String) {
        if (url.isBlank()) return
        val current = _bookmarks.value.filter { it.url != url }.toMutableList()
        current.add(0, BrowserBookmark(title.ifBlank { url }, url))
        _bookmarks.value = current
        saveBookmarks()
    }

    fun removeBookmark(url: String) {
        val current = _bookmarks.value.filter { it.url != url }
        _bookmarks.value = current
        saveBookmarks()
    }

    private fun loadHistory() {
        val ctx = appContext ?: return
        val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_HISTORY, null)
        if (!json.isNullOrBlank()) {
            try {
                _history.value = histAdapter.fromJson(json) ?: emptyList()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun saveHistory() {
        val ctx = appContext ?: return
        try {
            val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val json = histAdapter.toJson(_history.value)
            prefs.edit().putString(KEY_HISTORY, json).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun recordHistory(title: String, url: String) {
        if (url.isBlank() || url.startsWith("about:") || url.startsWith("data:")) return
        val current = _history.value.filter { it.url != url }.toMutableList()
        current.add(0, BrowserHistoryItem(title.ifBlank { url }, url))
        _history.value = current.take(150)
        saveHistory()
    }

    fun clearHistory() {
        _history.value = emptyList()
        saveHistory()
    }
}
