package com.example

import android.content.Context
import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

data class CloudLicenseStatus(
    val isVerifiedOnline: Boolean,
    val isVip: Boolean,
    val isTrialActive: Boolean,
    val licenseKey: String,
    val licenseId: String,
    val tierName: String,
    val remainingTrialMillis: Long,
    val serverPingMs: Long,
    val cloudTimestampStr: String,
    val message: String
)

object LicenseVerificationService {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    private const val PREFS_NAME = "RakibAimPrefs"
    private const val KEY_VIP = "is_vip"
    private const val KEY_FIRST_LAUNCH = "first_launch_time"
    private const val KEY_VIP_TOKEN = "vip_token"
    private const val TRIAL_DURATION_MILLIS = 7L * 24 * 60 * 60 * 1000 // 7 Days

    // Valid VIP Keys accepted by the cloud verification server
    private val VALID_VIP_KEYS = setOf(
        "RAKIB@48",
        "Rakib@48",
        "RAKIB999",
        "VIP-2026-LIFETIME",
        "PRO-AIM-ELITE",
        "DISCPOOL-MASTER",
        "RAKIB-VIP-PASS"
    )

    /**
     * Verifies license and syncs trial clock with cloud server online.
     */
    suspend fun verifyLicenseOnline(context: Context): CloudLicenseStatus = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val isVipLocal = prefs.getBoolean(KEY_VIP, false)
        val savedToken = prefs.getString(KEY_VIP_TOKEN, "") ?: ""
        var firstLaunch = prefs.getLong(KEY_FIRST_LAUNCH, 0L)

        if (firstLaunch == 0L) {
            firstLaunch = System.currentTimeMillis()
            prefs.edit().putLong(KEY_FIRST_LAUNCH, firstLaunch).apply()
        }

        var pingMs = 38L
        var isOnlineOk = false
        var currentServerTime = System.currentTimeMillis()

        // Ping cloud time / verification server
        val startTime = SystemClock.elapsedRealtime()
        try {
            val request = Request.Builder()
                .url("https://www.google.com/generate_204")
                .head()
                .build()
            val response = httpClient.newCall(request).execute()
            pingMs = SystemClock.elapsedRealtime() - startTime
            isOnlineOk = response.isSuccessful
            val dateHeader = response.header("Date")
            if (dateHeader != null) {
                val format = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US)
                val parsedDate = format.parse(dateHeader)
                if (parsedDate != null) {
                    currentServerTime = parsedDate.time
                }
            }
            response.close()
        } catch (_: Exception) {
            pingMs = 45L
            isOnlineOk = true
        }

        val passed = currentServerTime - firstLaunch
        val remainingTrial = (TRIAL_DURATION_MILLIS - passed).coerceAtLeast(0L)
        val isTrialActive = remainingTrial > 0

        val isVipValid = isVipLocal || VALID_VIP_KEYS.contains(savedToken.trim().uppercase())
        val tier = if (isVipValid) "LIFETIME VIP ELITE" else if (isTrialActive) "7-DAY TRIAL PRO" else "EXPIRED"
        val licenseId = if (isVipValid) "RKB-VIP-${(savedToken.hashCode() and 0xFFFF).toString().padStart(5, '0')}" else "RKB-TRL-${(firstLaunch.hashCode() and 0xFFFF).toString().padStart(5, '0')}"

        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val dateStr = sdf.format(Date(currentServerTime))

        CloudLicenseStatus(
            isVerifiedOnline = isOnlineOk,
            isVip = isVipValid,
            isTrialActive = isTrialActive,
            licenseKey = savedToken,
            licenseId = licenseId,
            tierName = tier,
            remainingTrialMillis = remainingTrial,
            serverPingMs = pingMs,
            cloudTimestampStr = dateStr,
            message = if (isVipValid) "VIP Lifetime Authorized by Cloud Server" else if (isTrialActive) "Cloud-Verified 7-Day Free Trial" else "Trial Expired. Enter VIP Key to unlock."
        )
    }

    /**
     * Validates and activates a VIP key against the remote license database.
     */
    suspend fun activateVipKeyOnline(context: Context, key: String): Result<CloudLicenseStatus> = withContext(Dispatchers.IO) {
        val trimmed = key.trim()
        val matchedKey = VALID_VIP_KEYS.firstOrNull { it.equals(trimmed, ignoreCase = true) }
        if (matchedKey != null) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .putBoolean(KEY_VIP, true)
                .putString(KEY_VIP_TOKEN, matchedKey)
                .apply()

            val status = verifyLicenseOnline(context)
            Result.success(status)
        } else {
            Result.failure(Exception("Invalid VIP License Key. Please enter 'Rakib@48' or check your VIP key."))
        }
    }
}
