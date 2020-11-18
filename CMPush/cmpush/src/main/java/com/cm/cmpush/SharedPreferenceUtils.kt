package com.cm.cmpush

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences

private const val SHARED_PREFERENCES_NAME = "com.cm.cmpush"

/**
 * Obtain a [SharedPreferenceUtils] for this [Context].
 */
internal fun Context.getSharedPreferenceUtils(): SharedPreferenceUtils {
    return getSharedPreferences(
        SHARED_PREFERENCES_NAME,
        Context.MODE_PRIVATE
    ).asUtils()
}

/**
 * Wrap this [SharedPreferences] instance in a [SharedPreferenceUtils] to work with the app-
 * specific values in a easy and reusable manner.
 */
internal fun SharedPreferences.asUtils() = SharedPreferenceUtils(this)

/**
 * App-specific utils for working with values stored in the shared preferences of the app.
 */
internal class SharedPreferenceUtils(private val sharedPreferences: SharedPreferences) {
    // <editor-fold desc="Account ID">
    fun hasAccountId() = contains(PreferenceKey.ACCOUNT_ID)

    fun getAccountId(): String? {
        return if (hasAccountId()) requireString(PreferenceKey.ACCOUNT_ID)
        else null
    }

    fun storeAccountId(accountId: String) = storeStringSynchronous(PreferenceKey.ACCOUNT_ID, accountId)
    // </editor-fold>

    // <editor-fold desc="Installation ID">
    fun hasInstallationId() = contains(PreferenceKey.INSTALLATION_ID)

    fun getInstallationId(): String? {
        return if (hasInstallationId()) requireString(PreferenceKey.INSTALLATION_ID)
        else return null
    }

    fun storeInstallationId(installationId: String) = storeStringSynchronous(PreferenceKey.INSTALLATION_ID, installationId)

    fun deleteInstallationId() = deleteString(PreferenceKey.INSTALLATION_ID)
    // </editor-fold>

    // <editor-fold desc="Data Hash">
    fun hasDataHash() = contains(PreferenceKey.DATA_HASH)

    fun getDataHash(): Int? {
        return if (hasDataHash()) requireInt(PreferenceKey.DATA_HASH)
        else null
    }

    fun storeDataHash(dataHash: Int) = storeIntSynchronous(PreferenceKey.DATA_HASH, dataHash)

    fun deleteDataHash() = deleteString(PreferenceKey.DATA_HASH)
    // </editor-fold>

    // <editor-fold desc="Channel ID">
    fun hasChannelId() = contains(PreferenceKey.CHANNEL_ID)

    fun getChannelId(): String? {
        return if (hasAccountId()) requireString(PreferenceKey.CHANNEL_ID)
        else null
    }

    fun storeChannelId(channelId: String) = storeStringSynchronous(PreferenceKey.CHANNEL_ID, channelId)
    // </editor-fold>

    // <editor-fold desc="Notification ID">
    fun hasNotificationId() = contains(PreferenceKey.NOTIFICATION_ID)

    fun getNotificationId(): Int? {
        return if (hasNotificationId()) requireInt(PreferenceKey.NOTIFICATION_ID)
        else null
    }

    fun storeNotificationId(notificationId: Int) = storeIntSynchronous(PreferenceKey.NOTIFICATION_ID, notificationId)

    fun deleteNotificationId() = deleteString(PreferenceKey.NOTIFICATION_ID)
    // </editor-fold>

    /**
     * Check whether given [key] is stored in the shared preferences.
     */
    private fun contains(key: PreferenceKey): Boolean {
        return sharedPreferences.contains(key.name)
    }

    /**
     * Store given [value] under given [key]
     *
     * @param key Key to associate with given value
     * @param value Value to store
     */
    private fun storeString(key: PreferenceKey, value: String) {
        sharedPreferences.edit().apply {
            putString(key.name, value)
            apply()
        }
    }

    /**
     * Store given [value] under given [key] synchronous
     *
     * @param key Key to associate with given value
     * @param value Value to store
     */
    @SuppressLint("ApplySharedPref")
    private fun storeStringSynchronous(key: PreferenceKey, value: String) {
        sharedPreferences.edit().apply {
            putString(key.name, value)
            commit() // This should NOT be apply()
        }
    }

    /**
     * Store given [value] under given [key] synchronous
     *
     * @param key Key to associate with given value
     * @param value Value to store
     */
    @SuppressLint("ApplySharedPref")
    private fun storeIntSynchronous(key: PreferenceKey, value: Int) {
        sharedPreferences.edit().apply {
            putInt(key.name, value)
            commit() // This should NOT be apply()
        }
    }

    /**
     * Require a string to be present and return it's value. Throw a [IllegalStateException] when
     * the string is nog present.
     *
     * @param key Key of the string to retrieve
     *
     * @throws IllegalStateException when the string is not present in the [sharedPreferences].
     */
    private fun requireString(key: PreferenceKey): String {
        return sharedPreferences.getString(key.name, null)
            ?: throw IllegalStateException("Cannot retrieve ${key.description} (${key.name}) from shared preferences")
    }

    private fun requireInt(key: PreferenceKey): Int {
        if (!sharedPreferences.contains(key.name)) throw IllegalStateException("Cannot retrieve ${key.description} (${key.name}) from shared preferences")
        return sharedPreferences.getInt(key.name, 0)
    }

    private fun deleteString(key: PreferenceKey) {
        sharedPreferences.edit().remove(key.name).apply()
    }

    /**
     * Unique keys for values in the shared preferences.
     */
    enum class PreferenceKey(
        /** Description of this specific key, will be included in error logging */
        val description: String
    ) {
        ACCOUNT_ID("accountId"),

        INSTALLATION_ID("installationId"),

        DATA_HASH("Data hash"),

        CHANNEL_ID("channelId"),

        NOTIFICATION_ID("notificationId")
    }
}