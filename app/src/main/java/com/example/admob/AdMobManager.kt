package com.example.admob

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.appopen.AppOpenAd
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean

/**
 * AdMobManager
 *
 * Centralized Google AdMob Ad Management using official Google Test Ad Units:
 * - App Open Ad on launch
 * - Rewarded Ad triggered after every 8 prompt submissions
 * - Interstitial Ad triggered after every 5 switches to Build or Preview tab
 * - Banner Ads for Terminal, Build, and Preview tabs
 */
object AdMobManager {

    private const val TAG = "AdMobManager"

    // Official Google Test Ad Unit IDs
    const val TEST_APP_OPEN_AD_UNIT_ID = "ca-app-pub-3940256099942544/9257395921"
    const val TEST_REWARDED_AD_UNIT_ID = "ca-app-pub-3940256099942544/5224354917"
    const val TEST_INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-3940256099942544/1033173712"
    const val TEST_BANNER_AD_UNIT_ID = "ca-app-pub-3940256099942544/6300978111"

    const val PROMPTS_PER_REWARDED_AD = 8
    const val TAB_SWITCHES_PER_INTERSTITIAL_AD = 5

    /**
     * Master switch to enable/disable all AdMob ads across the app.
     * Temporarily set to false as requested by the user.
     * When requested to re-enable, toggle this back to true.
     */
    var isAdsEnabled: Boolean = false

    private val isInitialized = AtomicBoolean(false)
    private var isAppOpenAdShowing = false
    private var appOpenAd: AppOpenAd? = null
    private var rewardedAd: RewardedAd? = null
    private var interstitialAd: InterstitialAd? = null
    private var isLoadingRewarded = false
    private var isLoadingAppOpen = false
    private var isLoadingInterstitial = false

    private val _promptCounter = MutableStateFlow(0)
    val promptCounter: StateFlow<Int> = _promptCounter.asStateFlow()

    private val _buildPreviewSwitchCounter = MutableStateFlow(0)
    val buildPreviewSwitchCounter: StateFlow<Int> = _buildPreviewSwitchCounter.asStateFlow()

    private var appOpenShownOnce = false

    /**
     * Initializes Google Mobile Ads SDK and preloads ads.
     */
    fun initialize(context: Context) {
        if (!isAdsEnabled) return
        if (isInitialized.compareAndSet(false, true)) {
            try {
                val reqConfig = com.google.android.gms.ads.RequestConfiguration.Builder()
                    .setTestDeviceIds(listOf(AdRequest.DEVICE_ID_EMULATOR))
                    .build()
                MobileAds.setRequestConfiguration(reqConfig)
                MobileAds.initialize(context) { status ->
                    Log.d(TAG, "MobileAds initialization complete: $status")
                    loadRewardedAd(context.applicationContext)
                    loadInterstitialAd(context.applicationContext)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed initializing MobileAds", e)
            }
        }
    }

    /**
     * Loads and displays App Open Ad once per app session launch.
     */
    fun showAppOpenAdIfAvailable(activity: Activity) {
        if (!isAdsEnabled || appOpenShownOnce) return

        if (appOpenAd != null) {
            showAppOpenAd(activity)
            return
        }

        if (isLoadingAppOpen) return
        isLoadingAppOpen = true

        val request = AdRequest.Builder().build()
        AppOpenAd.load(
            activity,
            TEST_APP_OPEN_AD_UNIT_ID,
            request,
            object : AppOpenAd.AppOpenAdLoadCallback() {
                override fun onAdLoaded(ad: AppOpenAd) {
                    isLoadingAppOpen = false
                    appOpenAd = ad
                    showAppOpenAd(activity)
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    isLoadingAppOpen = false
                    appOpenAd = null
                    Log.w(TAG, "App Open Ad failed to load: ${error.message}")
                }
            }
        )
    }

    private fun showAppOpenAd(activity: Activity) {
        val ad = appOpenAd ?: return
        if (isAppOpenAdShowing) return

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                appOpenAd = null
                isAppOpenAdShowing = false
                appOpenShownOnce = true
                Log.d(TAG, "App Open Ad dismissed")
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                appOpenAd = null
                isAppOpenAdShowing = false
                Log.w(TAG, "App Open Ad failed to show: ${adError.message}")
            }

            override fun onAdShowedFullScreenContent() {
                isAppOpenAdShowing = true
                appOpenShownOnce = true
                Log.d(TAG, "App Open Ad showing")
            }
        }

        ad.show(activity)
    }

    /**
     * Preloads Rewarded Ad in background.
     */
    fun loadRewardedAd(context: Context) {
        if (!isAdsEnabled || isLoadingRewarded || rewardedAd != null) return
        isLoadingRewarded = true

        val request = AdRequest.Builder().build()
        RewardedAd.load(
            context,
            TEST_REWARDED_AD_UNIT_ID,
            request,
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    isLoadingRewarded = false
                    rewardedAd = ad
                    Log.d(TAG, "Rewarded Ad loaded successfully")
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    isLoadingRewarded = false
                    rewardedAd = null
                    Log.w(TAG, "Rewarded Ad failed to load: ${error.message}")
                }
            }
        )
    }

    /**
     * Called whenever a user sends a prompt.
     * Tracks prompt count and triggers Rewarded Ad every 8 prompts.
     */
    fun onPromptSent(activity: Activity?, onRewardEarned: (() -> Unit)? = null) {
        if (!isAdsEnabled) return
        val currentCount = _promptCounter.value + 1
        _promptCounter.value = currentCount
        Log.d(TAG, "Prompt count updated: $currentCount")

        if (activity != null) {
            loadRewardedAd(activity.applicationContext)
        }

        if (currentCount > 0 && currentCount % PROMPTS_PER_REWARDED_AD == 0) {
            if (activity != null) {
                showRewardedAd(activity, onRewardEarned)
            }
        }
    }

    /**
     * Preloads Interstitial Ad in background.
     */
    fun loadInterstitialAd(context: Context) {
        if (!isAdsEnabled || isLoadingInterstitial || interstitialAd != null) return
        isLoadingInterstitial = true

        val request = AdRequest.Builder().build()
        InterstitialAd.load(
            context,
            TEST_INTERSTITIAL_AD_UNIT_ID,
            request,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    isLoadingInterstitial = false
                    interstitialAd = ad
                    Log.d(TAG, "Interstitial Ad loaded successfully")
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    isLoadingInterstitial = false
                    interstitialAd = null
                    Log.w(TAG, "Interstitial Ad failed to load: ${error.message}")
                }
            }
        )
    }

    /**
     * Called whenever a user switches tabs in workspace.
     * Counts switches to Build or Preview tabs and triggers Interstitial Ad every 5 times.
     */
    fun onTabSwitched(activity: Activity?, tabName: String) {
        if (!isAdsEnabled) return
        val isBuildOrPreview = tabName.contains("PREVIEW", ignoreCase = true) ||
                tabName.contains("BUILD", ignoreCase = true)

        if (isBuildOrPreview) {
            val current = _buildPreviewSwitchCounter.value + 1
            _buildPreviewSwitchCounter.value = current
            Log.d(TAG, "Build/Preview tab switch count: $current")

            if (activity != null) {
                loadInterstitialAd(activity.applicationContext)
            }

            if (current > 0 && current % TAB_SWITCHES_PER_INTERSTITIAL_AD == 0) {
                if (activity != null) {
                    showInterstitialAd(activity)
                }
            }
        }
    }

    /**
     * Shows the Interstitial Ad to the user.
     */
    fun showInterstitialAd(activity: Activity) {
        val ad = interstitialAd
        if (ad != null) {
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    interstitialAd = null
                    loadInterstitialAd(activity.applicationContext)
                    Log.d(TAG, "Interstitial Ad dismissed")
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    interstitialAd = null
                    loadInterstitialAd(activity.applicationContext)
                    Log.w(TAG, "Interstitial Ad failed to show: ${adError.message}")
                }

                override fun onAdShowedFullScreenContent() {
                    Log.d(TAG, "Interstitial Ad showed full screen")
                }
            }

            ad.show(activity)
        } else {
            Log.d(TAG, "Interstitial ad was not ready, loading for next time")
            loadInterstitialAd(activity.applicationContext)
        }
    }

    /**
     * Shows the Rewarded Ad to the user.
     */
    fun showRewardedAd(activity: Activity, onRewardEarned: (() -> Unit)? = null) {
        val ad = rewardedAd
        if (ad != null) {
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    rewardedAd = null
                    loadRewardedAd(activity.applicationContext)
                    Log.d(TAG, "Rewarded Ad dismissed")
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    rewardedAd = null
                    loadRewardedAd(activity.applicationContext)
                    Log.w(TAG, "Rewarded Ad failed to show: ${adError.message}")
                }

                override fun onAdShowedFullScreenContent() {
                    Log.d(TAG, "Rewarded Ad showed full screen")
                }
            }

            ad.show(activity) { rewardItem ->
                Log.d(TAG, "User earned reward: ${rewardItem.amount} ${rewardItem.type}")
                onRewardEarned?.invoke()
            }
        } else {
            Log.d(TAG, "Rewarded ad was not ready, loading for next time")
            loadRewardedAd(activity.applicationContext)
        }
    }
}
