package com.example.logging

import android.content.Context
import android.os.Build
import android.util.Log
import com.example.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.crashlytics.FirebaseCrashlytics

/**
 * Real integration with Firebase Crashlytics to monitor application stability,
 * capture breadcrumbs, record uncaught exceptions, and track non-fatal freezes.
 */
object CrashlyticsManager {

    private const val TAG = "CrashlyticsManager"
    private var isInitialized = false

    fun init(context: Context) {
        try {
            // Check if Firebase is initialized via google-services.json or manual setup
            val apps = FirebaseApp.getApps(context)
            if (apps.isNotEmpty()) {
                val crashlytics = FirebaseCrashlytics.getInstance()
                crashlytics.setCrashlyticsCollectionEnabled(true)
                crashlytics.setCustomKey("app_id", BuildConfig.APPLICATION_ID)
                crashlytics.setCustomKey("version_name", BuildConfig.VERSION_NAME)
                crashlytics.setCustomKey("is_debug", BuildConfig.DEBUG)
                crashlytics.setCustomKey("android_sdk", Build.VERSION.SDK_INT)
                crashlytics.setCustomKey("device_model", "${Build.MANUFACTURER} ${Build.MODEL}")
                isInitialized = true
                Log.i(TAG, "Firebase Crashlytics inicializado com sucesso.")
            } else {
                Log.w(TAG, "FirebaseApp não foi inicializado. Adicione o google-services.json em /app para habilitar relatórios remotos na nuvem.")
                isInitialized = false
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Não foi possível inicializar Firebase Crashlytics: ${e.message}")
            isInitialized = false
        }
    }

    fun isAvailable(): Boolean = isInitialized

    /**
     * Record an exception (fatal or non-fatal) to Firebase Crashlytics.
     */
    fun recordException(throwable: Throwable, reason: String? = null) {
        if (!isInitialized) return
        try {
            val crashlytics = FirebaseCrashlytics.getInstance()
            if (reason != null) {
                crashlytics.setCustomKey("failure_reason", LogSanitizer.sanitize(reason))
            }
            crashlytics.recordException(throwable)
        } catch (e: Throwable) {
            Log.e(TAG, "Falha ao registrar exceção no Crashlytics: ${e.message}")
        }
    }

    /**
     * Log a breadcrumb message to Firebase Crashlytics.
     */
    fun log(message: String) {
        if (!isInitialized) return
        try {
            val sanitized = LogSanitizer.sanitize(message)
            FirebaseCrashlytics.getInstance().log(sanitized)
        } catch (_: Throwable) {}
    }

    /**
     * Set a custom key-value pair in Firebase Crashlytics.
     */
    fun setCustomKey(key: String, value: String) {
        if (!isInitialized) return
        try {
            val sanitized = LogSanitizer.sanitize(value)
            FirebaseCrashlytics.getInstance().setCustomKey(key, sanitized)
        } catch (_: Throwable) {}
    }
}
