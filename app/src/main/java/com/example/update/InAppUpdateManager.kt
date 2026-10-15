package com.example.update

import android.app.Activity
import android.util.Log
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * InAppUpdateManager
 *
 * Implements silent and flexible in-app updates using the official Google Play In-App Updates API.
 * Automatically checks, downloads update files in the background without interrupting the user,
 * and completes installation seamlessly.
 */
class InAppUpdateManager(private val activity: Activity) {

    private val appUpdateManager: AppUpdateManager = AppUpdateManagerFactory.create(activity)
    
    private val _updateStatus = MutableStateFlow<String>("idle")
    val updateStatus: StateFlow<String> = _updateStatus.asStateFlow()

    private val installStateUpdatedListener = InstallStateUpdatedListener { state ->
        when (state.installStatus()) {
            InstallStatus.DOWNLOADING -> {
                val bytesDownloaded = state.bytesDownloaded()
                val totalBytes = state.totalBytesToDownload()
                val pct = if (totalBytes > 0) (bytesDownloaded * 100 / totalBytes).toInt() else 0
                _updateStatus.value = "Downloading update ($pct%)..."
                Log.d("InAppUpdateManager", "Silent download progress: $pct%")
            }
            InstallStatus.DOWNLOADED -> {
                _updateStatus.value = "Update downloaded. Ready to apply."
                Log.d("InAppUpdateManager", "Update downloaded! Completing update silently.")
                // Silently trigger install completion
                appUpdateManager.completeUpdate()
            }
            InstallStatus.INSTALLED -> {
                _updateStatus.value = "Application updated successfully."
            }
            InstallStatus.FAILED -> {
                _updateStatus.value = "Update failed"
            }
            else -> {}
        }
    }

    fun initAndCheckForUpdates() {
        try {
            appUpdateManager.registerListener(installStateUpdatedListener)
            val appUpdateInfoTask = appUpdateManager.appUpdateInfo

            appUpdateInfoTask.addOnSuccessListener { appUpdateInfo: AppUpdateInfo ->
                if (appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE &&
                    appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)
                ) {
                    _updateStatus.value = "Update available. Starting silent flexible update..."
                    val options = AppUpdateOptions.newBuilder(AppUpdateType.FLEXIBLE).build()
                    appUpdateManager.startUpdateFlow(appUpdateInfo, activity, options)
                } else if (appUpdateInfo.installStatus() == InstallStatus.DOWNLOADED) {
                    // In case it was already downloaded in previous run
                    appUpdateManager.completeUpdate()
                } else {
                    _updateStatus.value = "App is up to date"
                }
            }.addOnFailureListener { e ->
                Log.d("InAppUpdateManager", "Update check skipped/failed (normal in debug or non-play environment): ${e.message}")
            }
        } catch (e: Exception) {
            Log.e("InAppUpdateManager", "Failed to initialize in-app update", e)
        }
    }

    fun onDestroy() {
        try {
            appUpdateManager.unregisterListener(installStateUpdatedListener)
        } catch (_: Exception) {}
    }
}
