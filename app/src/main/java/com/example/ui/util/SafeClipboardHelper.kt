package com.example.ui.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext

/**
 * Crash-safe utility for clipboard copying and long-press copy interactions.
 * Prevents SecurityException, NullPointerException, or window focus crashes across all Android versions.
 */
object SafeClipboardHelper {

    fun copyToClipboard(
        context: Context,
        text: String,
        label: String = "Code / Text",
        showToast: Boolean = true,
        toastMessage: String? = null
    ): Boolean {
        if (text.isEmpty()) return false
        return try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            if (clipboard != null) {
                val clip = ClipData.newPlainText(label, text)
                clipboard.setPrimaryClip(clip)
                
                // Provide light tactile feedback if available
                vibrateLight(context)

                // Android 13+ (API 33+) provides its own system clipboard overlay confirmation,
                // but showing a short toast or for earlier versions gives clear assurance
                if (showToast && Build.VERSION.SDK_INT < 33) {
                    val msg = toastMessage ?: "Copied to clipboard"
                    Toast.makeText(context.applicationContext, msg, Toast.LENGTH_SHORT).show()
                } else if (showToast && toastMessage != null) {
                    Toast.makeText(context.applicationContext, toastMessage, Toast.LENGTH_SHORT).show()
                }
                true
            } else {
                false
            }
        } catch (e: Throwable) {
            // Guard against SecurityException, DeadSystemException, or window-token crashes
            try {
                if (showToast) {
                    Toast.makeText(context.applicationContext, "Could not copy: ${e.localizedMessage ?: "Permission denied"}", Toast.LENGTH_SHORT).show()
                }
            } catch (_: Throwable) {}
            false
        }
    }

    private fun vibrateLight(context: Context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createOneShot(20, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(20)
                }
            }
        } catch (_: Throwable) {
            // Non-critical, ignore if vibrator is unavailable
        }
    }
}

/**
 * Modifier extension to easily attach crash-safe long-press-to-copy behavior to any composable element.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.safeLongPressCopy(
    textToCopy: () -> String,
    label: String = "Text",
    feedbackMessage: String = "Copied to clipboard"
): Modifier {
    val context = LocalContext.current
    val interactionSource = remember { MutableInteractionSource() }

    return this.combinedClickable(
        interactionSource = interactionSource,
        indication = null,
        onClick = {},
        onLongClick = {
            val text = textToCopy()
            if (text.isNotBlank()) {
                SafeClipboardHelper.copyToClipboard(
                    context = context,
                    text = text,
                    label = label,
                    showToast = true,
                    toastMessage = feedbackMessage
                )
            }
        }
    )
}
