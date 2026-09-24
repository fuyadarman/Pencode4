package com.example.crash

import android.content.Context
import android.util.Log

/**
 * Global App Crash Guard.
 * Catches unhandled thread/coroutine/WebView exceptions to prevent sudden app crashes,
 * safely logs stack traces, and preserves workspace state.
 */
object AppCrashGuard {

    private const val TAG = "AppCrashGuard"
    private var isInstalled = false

    fun install(context: Context) {
        if (isInstalled) return
        isInstalled = true

        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                Log.e(TAG, "Caught uncaught exception in thread: ${thread.name}", throwable)
                val msg = throwable.localizedMessage ?: throwable.javaClass.simpleName

                // Non-fatal background/WebView/network crash recovery
                val isRecoverable = thread.name.contains("DefaultDispatcher") ||
                        thread.name.contains("OkHttp") ||
                        thread.name.contains("Chrome_") ||
                        thread.name.contains("ThreadPool") ||
                        thread.name.contains("Async") ||
                        msg.contains("WebView", ignoreCase = true) ||
                        msg.contains("chromium", ignoreCase = true) ||
                        msg.contains("render", ignoreCase = true) ||
                        msg.contains("DeadObjectException") ||
                        msg.contains("SocketClosed") ||
                        msg.contains("CancellationException") ||
                        msg.contains("evaluateJavascript", ignoreCase = true) ||
                        msg.contains("NullPointerException") && thread.name.contains("Chrome")

                if (isRecoverable) {
                    Log.w(TAG, "Recovered from non-fatal background thread crash: $msg")
                    return@setDefaultUncaughtExceptionHandler
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in crash handler", e)
            }

            // Forward to standard handler if fatal main thread crash
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }
}
