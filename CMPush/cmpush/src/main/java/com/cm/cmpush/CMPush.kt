package com.cm.cmpush

import android.app.PendingIntent
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.DrawableRes
import com.cm.cmpush.JSONHelper.formatJSONString
import com.cm.cmpush.JSONHelper.tryStringToJSONObject
import com.cm.cmpush.NetworkHelper.doNetworkRequest
import com.cm.cmpush.NotificationHelper.createNotification
import com.cm.cmpush.NotificationHelper.createNotificationChannel
import org.json.JSONObject


object CMPush {
    internal const val TAG = "CMPushLibrary"

    private fun getAccountId(context: Context): String? {
        val sharedPreferenceUtils = context.getSharedPreferenceUtils()
        return if (sharedPreferenceUtils.hasAccountId()) sharedPreferenceUtils.getAccountId()
        else null
    }

    private fun createBaseUrl(context: Context): String {
        return "https://api.cm.com/channelswebhook/push/v1/accounts/${getAccountId(context)}"
    }

    fun initialize(
        context: Context,
        accountId: String,
        channelId: String,
        channelName: String,
        channelDescription: String
    ) {
        context.getSharedPreferenceUtils().apply {
            this.storeAccountId(accountId)
            this.storeChannelId(channelId)
        }

        createNotificationChannel(
            context = context,
            channelId = channelId,
            channelName = channelName,
            channelDescription = channelDescription
        )
    }

    /**
     * Sends an OTP code to the user via SMS
     */
    fun preRegister(context: Context, msisdn: String, sender: String, callback: (success: Boolean, error: CMPushError?) -> Unit) {
        if (!context.getSharedPreferenceUtils().hasAccountId()) {
            Log.d(TAG, "CMPush isn't initialized yet!")
            return
        }

        doNetworkRequest(
            endpoint = "${createBaseUrl(context)}/preregister",
            method = "POST",
            body = JSONObject().apply {
                this.put("msisdn", msisdn)
                this.put("sender", sender)
            }.toString(),
            onSuccess = { statusCode, result ->
                if (statusCode == 204) {
                    callback.invoke(true, null)
                } else {
                    callback.invoke(false, CMPushError(statusCode, result))
                }
            },
            onError = { statusCode, result ->
                callback.invoke(false, CMPushError(statusCode, result))
            },
            onException = { exception ->
                callback.invoke(false, CMPushError(null, exception.localizedMessage ?: ""))
            }
        )
    }

    /**
     * Verify the OTP code the user received in a SMS
     */
    fun register(context: Context, pushToken: String, msisdn: String, otpCode: String, callback: (success: Boolean, error: CMPushError?) -> Unit) {
        val sharedPreferenceUtils = context.getSharedPreferenceUtils()

        if (!sharedPreferenceUtils.hasAccountId()) {
            Log.d(TAG, "CMPush isn't initialized yet!")
            return
        }

        val body = constructInformationObject(context, pushToken).apply {
            this.put("msisdn", msisdn)
            this.put("otpCode", otpCode)
        }.toString()

        doNetworkRequest(
            endpoint = "${createBaseUrl(context)}/register",
            method = "POST",
            body = body,
            onSuccess = { statusCode, result ->
                val response = tryStringToJSONObject(result)

                if (statusCode == 201 && response != null && response.has("installationId")) {
                    sharedPreferenceUtils.storeInstallationId(response.getString("installationId"))
                    callback(true, null)
                } else {
                    callback(false, CMPushError(statusCode, result))
                }
            },
            onError = { statusCode, result ->
                callback(false, CMPushError(statusCode, result))
            },
            onException = { exception ->
                callback.invoke(false, CMPushError(null, exception.localizedMessage ?: ""))
            }
        )
    }

    /**
     * Update the FCM Token together with other device info
     */
    fun updateToken(context: Context, pushToken: String, callback: (success: Boolean, error: CMPushError?) -> Unit) {
        val sharedPreferenceUtils = context.getSharedPreferenceUtils()

        if (!sharedPreferenceUtils.hasInstallationId()) {
            Log.d(TAG, "Not updating token since app doesn't have an installationId yet.")
            return
        }

        val installationId = sharedPreferenceUtils.getInstallationId()

        val body = constructInformationObject(context, pushToken)

        Log.d(TAG, "Has Data Hash: ${sharedPreferenceUtils.hasDataHash()}")

        if (sharedPreferenceUtils.hasDataHash()) {
            // If previous data hash is same, don't send an update to the backend

            Log.d(TAG, "Hashes: \nStored: ${sharedPreferenceUtils.getDataHash()} \nNew: ${body.hashCode()}")

            if (sharedPreferenceUtils.getDataHash() == body.hashCode()) {
                callback.invoke(true, null)
                return
            }
        }

        doNetworkRequest(
            endpoint = "${createBaseUrl(context)}/register/$installationId",
            method = "PUT",
            body = body.toString(),
            onSuccess = { statusCode, result ->
                if (statusCode == 204) {
                    // Store hash of data that was sent.
                    sharedPreferenceUtils.storeDataHash(
                        dataHash = body.hashCode()
                    )

                    callback(true, null)
                } else {
                    callback(false, CMPushError(statusCode, result))
                }
            },
            onError = { statusCode, result ->
                callback.invoke(false, CMPushError(statusCode, result))
            },
            onException = { exception ->
                callback.invoke(false, CMPushError(null, exception.localizedMessage ?: ""))
            }
        )
    }

    /**
     * Confirm a push message is received
     */
    fun pushReceived(context: Context, data: Map<String, String>, showNotification: Boolean = true, @DrawableRes notificationIcon: Int, notificationContentIntent: PendingIntent, callback: (success: Boolean, error: CMPushError?) -> Unit) {
        val sharedPreferenceUtils = context.getSharedPreferenceUtils()

        val notificationId = sharedPreferenceUtils.getNotificationId() ?: 0

        // Show notification
        if (showNotification && sharedPreferenceUtils.hasChannelId()) {
            safeLet(data["message"], sharedPreferenceUtils.getChannelId()) { message, channelId ->
                val title = data["title"] ?: getApplicationName(context)

                createNotification(
                    context = context,
                    notificationId = notificationId,
                    channelId = channelId,
                    title = title,
                    content = message,
                    intent = notificationContentIntent,
                    notificationIcon = notificationIcon
                )

                // Raise notificationId
                sharedPreferenceUtils.storeNotificationId(notificationId + 1)
            }
        }

        // Confirm push message

        if (!sharedPreferenceUtils.hasInstallationId()) {
            Log.d(TAG, "Can't confirm push message since app doesn't have an installationId yet.")
            return
        }

        val installationId = sharedPreferenceUtils.getInstallationId()

        val messageId = data["messageId"]

        // Confirm push received to backend
        doNetworkRequest(
            endpoint = "${createBaseUrl(context)}/sr/$installationId",
            method = "POST",
            body = JSONObject().apply {
                this.put("messageId", messageId)
            }.toString(),
            onSuccess = { statusCode, result ->
                Log.d(TAG, "Successfully confirmed push message ($statusCode): \n${formatJSONString(result)}")
                callback.invoke(true, CMPushError(statusCode, result))
            },
            onError = { statusCode, result ->
                Log.d(TAG, "Failed to confirm push message ($statusCode): \n${formatJSONString(result)}")
                callback.invoke(false, CMPushError(statusCode, result))
            },
            onException = { exception ->
                callback.invoke(false, CMPushError(null, exception.localizedMessage ?: ""))
            }
        )
    }

    /**
     * Check if the app is registered for push messages
     */
    fun isRegistered(context: Context): Boolean {
        val sharedPreferenceUtils = context.getSharedPreferenceUtils()

        return sharedPreferenceUtils.hasInstallationId()
    }

    /**
     * Remove the registration
     */
    fun deleteRegistration(context: Context, callback: (success: Boolean, error: CMPushError?) -> Unit) {
        val sharedPreferenceUtils = context.getSharedPreferenceUtils()

        if (!sharedPreferenceUtils.hasInstallationId()) {
            Log.d(TAG, "Already unregistered")
            callback.invoke(true, null)
            return
        }

        val installationId = sharedPreferenceUtils.getInstallationId()

        doNetworkRequest(
            endpoint = "${createBaseUrl(context)}/register/$installationId",
            method = "DELETE",
            onSuccess = { statusCode, result ->
                if (statusCode == 204) {
                    with(sharedPreferenceUtils) {
                        this.deleteInstallationId()
                        this.deleteDataHash()
                    }

                    callback(true, null)
                } else {
                    callback(false, CMPushError(statusCode, result))
                }
            },
            onError = { statusCode, result ->
                callback.invoke(false, CMPushError(statusCode, result))
            },
            onException = { exception ->
                callback.invoke(false, CMPushError(null, exception.localizedMessage ?: ""))
            }
        )
    }

    private fun constructInformationObject(context: Context, pushToken: String): JSONObject {
        val version = Build.VERSION.SDK_INT
        val versionName = Build.VERSION.RELEASE

        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        val appVersion = packageInfo.versionName

        return JSONObject().apply {
            this.put("pushToken", pushToken)
            this.put("deviceOs", "Android")
            this.put("deviceOsVersion", "$versionName ($version)")
            this.put("deviceModel", Build.MODEL)
            this.put("appId", context.packageName)
            this.put("appVersion", appVersion)
            this.put("libId", BuildConfig.LIBRARY_PACKAGE_NAME)
            this.put("libVersion", BuildConfig.LIBRARY_VERSION_NAME)
        }
    }

    private fun getApplicationName(context: Context): String {
        val applicationInfo = context.applicationInfo
        val stringId = applicationInfo.labelRes
        return if (stringId == 0) applicationInfo.nonLocalizedLabel.toString() else context.getString(stringId)
    }
}