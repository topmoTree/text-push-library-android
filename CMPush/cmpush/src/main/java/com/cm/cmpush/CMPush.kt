package com.cm.cmpush

import android.app.Application
import android.app.NotificationChannel
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.LocaleList
import android.util.Base64
import android.util.Log
import androidx.annotation.DrawableRes
import com.cm.cmpush.helper.JSONHelper.formatJSONString
import com.cm.cmpush.helper.JSONHelper.tryStringToJSONObject
import com.cm.cmpush.helper.NetworkHelper
import com.cm.cmpush.helper.NetworkHelper.createBaseUrl
import com.cm.cmpush.helper.NetworkHelper.doNetworkRequest
import com.cm.cmpush.helper.NotificationHelper.createNotification
import com.cm.cmpush.helper.NotificationHelper.createNotificationChannel
import com.cm.cmpush.helper.calculateHashCode
import com.cm.cmpush.helper.getSharedPreferenceUtils
import com.cm.cmpush.objects.*
import com.cm.cmpush.objects.CMData
import com.cm.cmpush.objects.PushAuthorization
import com.cm.cmpush.worker.PushSyncWorkerHelper.disableSync
import com.cm.cmpush.worker.PushSyncWorkerHelper.updateSyncSettings
import org.json.JSONArray
import org.json.JSONObject
import java.lang.Exception
import java.util.*
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

object CMPush {
    internal const val TAG = "CMPushLibrary"
    internal const val KEY_NOTIFICATION_ID = "notificationId"
    internal const val KEY_MESSAGE_ID = "messageId"
    internal const val KEY_SUGGESTION = "suggestion"
    const val OPEN_APP_PAGE = "openPage"

    // User configuration
    internal lateinit var appIntent: Intent
        private set

    @DrawableRes
    private var notificationIcon: Int? = null

    fun setTestURL(url: String) {
        NetworkHelper.testUrl = url
    }

    /**
     * Initialize the CMPush SDK
     * Should be called from your [Application] class so the [notificationIcon] and [notificationContentIntent] are accessible when the app is not fully started
     *
     * @param context                   the [Context] of the Application
     * @param applicationKey            the applicationKey received from CM
     * @param channelId                 the id to use for the [NotificationChannel]
     * @param channelName               the id to use for the [NotificationChannel]
     * @param channelDescription        the id to use for the [NotificationChannel]
     * @param notificationIcon          the icon to use in the notifications
     * @param notificationIntent        [Intent] that should be called when the user opens a notification.
     */
    fun initialize(
        context: Context,
        applicationKey: String,
        channelId: String,
        channelName: String,
        channelDescription: String,
        @DrawableRes notificationIcon: Int,
        notificationIntent: Intent
    ) {
        context.getSharedPreferenceUtils().apply {
            this.storeAccountId(applicationKey)
            this.storeChannelId(channelId)
        }

        createNotificationChannel(
            context = context,
            channelId = channelId,
            channelName = channelName,
            channelDescription = channelDescription
        )

        this.notificationIcon = notificationIcon
        this.appIntent = notificationIntent
    }

    @Deprecated("This function is deprecated", ReplaceWith("updateMSISDN(context, msisdn, sender, callback)"), level = DeprecationLevel.ERROR)
    fun preRegister(context: Context, msisdn: String, sender: String, callback: (success: Boolean, error: CMPushError?) -> Unit) {
        // Deprecated
    }

    @Deprecated("This function is deprecated", ReplaceWith("updateOTP(context, msisdn, otpCode, callback)"), level = DeprecationLevel.ERROR)
    fun register(context: Context, pushToken: String, msisdn: String, otpCode: String, callback: (success: Boolean, error: CMPushError?) -> Unit) {
        // Deprecated
    }

    /**
     * Update the FCM Token together with other device info
     */
    data class PendingUpdate(val pushToken: String, val callback: (success: Boolean, error: CMPushError?, installationId: String?) -> Unit)

    var isUpdating = false
    var pendingUpdate: PendingUpdate? = null
    val pendingUpdateLock = ReentrantLock()
    fun updateToken(context: Context, pushToken: String, callback: (success: Boolean, error: CMPushError?, installationId: String?) -> Unit) {
        pendingUpdateLock.withLock {
            if (!isUpdating) {
                Log.d(TAG, "UpdateToken: No pending update, continue")
                isUpdating = true
                doUpdateToken(context, pushToken, callback)
            } else {
                //More than one at same time. Remember last one. Retry when ready.
                Log.d(TAG, "UpdateToken: Pending update, wait")
                pendingUpdate = PendingUpdate(pushToken, callback = callback)
            }
        }
    }

    private fun performPendingUpdate(context: Context) {
        pendingUpdateLock.withLock {
            pendingUpdate?.let { pending ->
                this.pendingUpdate = null
                doUpdateToken(context, pending.pushToken, pending.callback)
            } ?: run {
                isUpdating = false
            }
        }
    }

    private fun doUpdateToken(context: Context, pushToken: String, callback: (success: Boolean, error: CMPushError?, installationId: String?) -> Unit) {
        Log.d(TAG, "UpdateToken: Perform update, token = " + pushToken)

        val sharedPreferenceUtils = context.getSharedPreferenceUtils()

        // Store push token for future use in MSISDN register
        sharedPreferenceUtils.storePushToken(pushToken)

        val body = constructInformationObject(context, pushToken)

        // Register the device if there is no installationId yet.
        if (!sharedPreferenceUtils.hasInstallationId()) {
            Log.d(TAG, "UpdateToken: Registering device because we don't have an installationId yet.")
            doNetworkRequest(
                endpoint = "${createBaseUrl(context)}/register",
                method = "POST",
                body = body.toString(),
                onSuccess = { statusCode, result ->
                    val response = tryStringToJSONObject(result)

                    if (statusCode == 201 && response != null && response.has("installationId")) {
                        val installationId = response.getString("installationId")

                        // Store installationId
                        sharedPreferenceUtils.storeInstallationId(installationId)

                        // Store hash of data that was sent.
                        sharedPreferenceUtils.storeDataHash(
                            dataHash = body.calculateHashCode()
                        )

                        // Schedule-settings for push amplification
                        updateSyncSettings(context, response)

                        Log.d(TAG, "UpdateToken: POST ok, installationId = " + installationId)

                        callback(true, null, installationId)
                    } else {
                        Log.e(TAG, "UpdateToken: POST failed. StatusCode = " + statusCode + " / response = " + response)
                        callback(false, CMPushError.ServerError(statusCode, result), null)
                    }
                    performPendingUpdate(context)
                },
                onError = { statusCode, result ->
                    Log.e(TAG, "UpdateToken: POST failed. StatusCode = " + statusCode + " / result = " + result)
                    callback.invoke(false, CMPushError.ServerError(statusCode, result), null)
                    performPendingUpdate(context)
                },
                onException = { exception ->
                    Log.e(TAG, "UpdateToken: POST failed.", exception)
                    callback.invoke(false, CMPushError.ServerError(null, exception.localizedMessage ?: ""), null)
                    performPendingUpdate(context)
                }
            )
        } else {
            val installationId = sharedPreferenceUtils.getInstallationId()

            if (sharedPreferenceUtils.hasDataHash()) {
                // If previous data hash is same, don't send an update to the backend
                if (sharedPreferenceUtils.getDataHash() == body.calculateHashCode()) {
                    Log.d(TAG, "UpdateToken: No data has changed, don't send request to CM")
                    callback.invoke(true, null, installationId)
                    performPendingUpdate(context)
                    return
                }
            }

            doNetworkRequest(
                endpoint = "${createBaseUrl(context)}/register/$installationId",
                method = "PUT",
                body = body.toString(),
                onSuccess = { statusCode, result ->
                    if (statusCode == 200) {
                        // Store hash of data that was sent.
                        Log.d(TAG, "UpdateToken: Storing data hash: ${body.calculateHashCode()}")

                        sharedPreferenceUtils.storeDataHash(
                            dataHash = body.calculateHashCode()
                        )

                        // Schedule-settings for push amplification
                        tryStringToJSONObject(result)?.let {
                            updateSyncSettings(context, it)
                        } ?: run {
                            disableSync(context)
                        }
                        Log.d(TAG, "UpdateToken: PUT ok, installationId = " + installationId)
                        callback(true, null, installationId)
                    } else {
                        Log.e(TAG, "UpdateToken: PUT failed. StatusCode = " + statusCode)
                        callback(false, CMPushError.ServerError(statusCode, result), null)
                    }
                    performPendingUpdate(context)
                },
                onError = { statusCode, result ->
                    Log.e(TAG, "UpdateToken: PUT failed. StatusCode = " + statusCode + " / result = " + result)
                    callback.invoke(false, CMPushError.ServerError(statusCode, result), null)
                    performPendingUpdate(context)
                },
                onException = { exception ->
                    Log.e(TAG, "UpdateToken: PUT failed.", exception)
                    callback.invoke(false, CMPushError.ServerError(null, exception.localizedMessage ?: ""), null)
                    performPendingUpdate(context)
                }
            )
        }
    }

    /**
     * Sends an OTP code to the user via SMS
     */
    fun updateMSISDN(context: Context, msisdn: String, callback: (success: Boolean, error: CMPushError?) -> Unit) {
        val sharedPreferenceUtils = context.getSharedPreferenceUtils()

        if (!sharedPreferenceUtils.hasAccountId()) {
            Log.e(TAG, "CMPush isn't initialized yet!")
            callback.invoke(false, CMPushError.NotInitialized)
            return
        }

        if (!sharedPreferenceUtils.hasInstallationId()) {
            Log.e(TAG, "Device needs to be registered before MSISDN can be set!")
            callback.invoke(false, CMPushError.NotRegistered)
            return
        }

        if (!sharedPreferenceUtils.hasPushToken()) {
            Log.e(TAG, "Device doesn't have a push token yet!")
            callback.invoke(false, CMPushError.NotRegistered)
            return
        }

        val pushToken = sharedPreferenceUtils.requirePushToken()

        val installationId = sharedPreferenceUtils.getInstallationId()

        val body = constructInformationObjectWithMSISDN(context, pushToken, msisdn)

        doNetworkRequest(
            endpoint = "${createBaseUrl(context)}/register/$installationId",
            method = "PUT",
            body = body.toString(),
            onSuccess = { statusCode, result ->
                if (statusCode == 200) {
                    callback.invoke(true, null)
                } else {
                    callback.invoke(false, CMPushError.ServerError(statusCode, result))
                }
            },
            onError = { statusCode, result ->
                callback.invoke(false, CMPushError.ServerError(statusCode, result))
            },
            onException = { exception ->
                callback.invoke(false, CMPushError.ServerError(null, exception.localizedMessage ?: ""))
            }
        )
    }

    /**
     * Verify the OTP code the user received in a SMS
     */
    fun updateOTP(context: Context, msisdn: String, otpCode: String, callback: (success: Boolean, error: CMPushError?) -> Unit) {
        val sharedPreferenceUtils = context.getSharedPreferenceUtils()

        if (!sharedPreferenceUtils.hasAccountId()) {
            Log.e(TAG, "CMPush isn't initialized yet!")
            callback.invoke(false, CMPushError.NotInitialized)
            return
        }

        if (!sharedPreferenceUtils.hasInstallationId()) {
            Log.e(TAG, "Device needs to be registered before MSISDN can be set!")
            callback.invoke(false, CMPushError.NotRegistered)
            return
        }

        if (!sharedPreferenceUtils.hasPushToken()) {
            Log.e(TAG, "Device doesn't have a push token yet!")
            callback.invoke(false, CMPushError.NotRegistered)
            return
        }

        val pushToken = sharedPreferenceUtils.requirePushToken()

        val installationId = sharedPreferenceUtils.getInstallationId()

        val body = constructInformationObjectWithMSISDNAndOTP(context, pushToken, msisdn, otpCode)

        doNetworkRequest(
            endpoint = "${createBaseUrl(context)}/register/$installationId",
            method = "PUT",
            body = body.toString(),
            onSuccess = { statusCode, result ->
                val response = tryStringToJSONObject(result)

                if (statusCode == 200 && response != null) {
                    // Store MSISDN so we know a MSISDN is linked to the installation
                    sharedPreferenceUtils.storeMISDN(msisdn)

                    callback(true, null)
                } else {
                    callback(false, CMPushError.ServerError(statusCode, result))
                }
            },
            onError = { statusCode, result ->
                callback(false, CMPushError.ServerError(statusCode, result))
            },
            onException = { exception ->
                callback.invoke(false, CMPushError.ServerError(null, exception.localizedMessage ?: ""))
            }
        )
    }

    @Deprecated("This function is deprecated, notificationIcon & notificationContentIntent should be passed in the CMPush.initialize() function.", ReplaceWith("pushReceived(context, data, callback)"), level = DeprecationLevel.ERROR)
    fun pushReceived(context: Context, data: Map<String, String>, showNotification: Boolean = true, @DrawableRes notificationIcon: Int, notificationContentIntent: PendingIntent, callback: (success: Boolean, error: CMPushError?) -> Unit) {
        // Deprecated
    }

    /**
     * Notify the CMPush library that a push has been received.
     *
     * @param context  the [Context] of the Application
     * @param data     the notification data received from Firebase
     * @param callback (optional) a callback to check if CM received the push confirmation
     */
    fun pushReceived(context: Context, data: Map<String, String>, callback: ((success: Boolean, error: CMPushError?) -> Unit)? = null) {
        //cm object can be BASE64 encoded Json-string or plain json object
        val cmData = CMData.fromJSONObject(tryStringToJSONObject(data["cm"]) ?: tryStringToJSONObject(decodeBase64(data["cm"]))) ?: kotlin.run {
            Log.e(TAG, "Missing CM data in push message! Aborting..")
            return
        }

        showAndConfirmPushNotification(
            context = context,
            cmData = cmData,
            callback = callback
        )
    }

    private fun decodeBase64(input: String?) : String? {
        if (input==null){
            return null
        }
        try {
            return String(Base64.decode(input, Base64.DEFAULT), Charsets.UTF_8)
        }catch (error: Exception){
            Log.e(TAG, "Invalid Base64")
            return null
        }
    }

    /**
     * Internal function to show and confirm the push notification
     *
     * Used by
     * - [pushReceived] (external function that receives incoming FCM messages)
     * - Push Amplification (future plans)
     */
    internal fun showAndConfirmPushNotification(context: Context, cmData: CMData, callback: ((success: Boolean, error: CMPushError?) -> Unit)? = null) {
        // Notification icon is required
        val notificationIcon = this.notificationIcon ?: kotlin.run {
            Log.e(TAG, "Notification icon not initialized yet!")
            callback?.invoke(false, CMPushError.NotInitialized)
            return
        }

        val sharedPreferenceUtils = context.getSharedPreferenceUtils()

        // Show notification
        if (sharedPreferenceUtils.hasChannelId()) {
            // Check if message has been shown in the past (push messages can come from Firebase or PushSynchronizer
            if (setMessageShown(context, cmData.messageId)) {
                sharedPreferenceUtils.getChannelId()?.let { channelId ->
                    createNotification(
                        context = context,
                        channelId = channelId,
                        cmData = cmData,
                        notificationIcon = notificationIcon
                    )

                    reportStatus(context, CMPushStatus(null, null, CMPushStatusReport(CMPushStatusType.Delivered, cmData.messageId)), callback)
                }
            } else {
                Log.d(TAG, "Not showing notification with messageId ${cmData.messageId} because it was already shown in the past!")
                callback?.invoke(false, CMPushError.ServerError(null, "Duplicate messageId: ${cmData.messageId}"))
                return
            }
        }
    }

    internal fun reportStatus(context: Context, status: CMPushStatus, callback: ((success: Boolean, error: CMPushError?) -> Unit)?) {
        val sharedPreferenceUtils = context.getSharedPreferenceUtils()

        // Confirm push message
        if (!sharedPreferenceUtils.hasInstallationId()) {
            Log.d(TAG, "Can't confirm push message since app doesn't have an installationId yet.")
            return
        }

        val installationId = sharedPreferenceUtils.getInstallationId()

        // Confirm push received to backend
        doNetworkRequest(
            endpoint = "${createBaseUrl(context)}/profiles/$installationId/events",
            method = "POST",
            body = status.toJSONObject().toString(),
            onSuccess = { statusCode, result ->
                Log.d(TAG, "Successfully sent status for push message ($statusCode): \n${formatJSONString(result)}")
                if (statusCode == 200) {
                    callback?.invoke(true, null)
                } else {
                    callback?.invoke(false, CMPushError.ServerError(statusCode, result))
                }
            },
            onError = { statusCode, result ->
                Log.d(TAG, "Failed to send status for push message ($statusCode): \n${formatJSONString(result)}")
                callback?.invoke(false, CMPushError.ServerError(statusCode, result))
            },
            onException = { exception ->
                callback?.invoke(false, CMPushError.ServerError(null, exception.localizedMessage ?: ""))
            }
        )
    }

    /**
     * Check if the app is registered for push messages
     */
    fun isRegistered(context: Context): Boolean {
        return context.getSharedPreferenceUtils().hasInstallationId()
    }

    /**
     * Return the installationID or null if not registered yet.
     *
     */
    fun installationID(context: Context): String? {
        return context.getSharedPreferenceUtils().getInstallationId()
    }

    /**
     * Check if a MSISDN is linked
     */
    fun hasRegisteredMSISDN(context: Context): Boolean {
        return context.getSharedPreferenceUtils().hasMSISDN()
    }

    /**
     * Retrieve the linked MSISDN
     */
    fun getRegisteredMSISDN(context: Context): String? {
        return context.getSharedPreferenceUtils().getMSISDN()
    }

    /**
     * Unlink MSISDN from the device registration
     */
    fun unregisterMSISDN(context: Context, callback: (success: Boolean, error: CMPushError?) -> Unit) {
        val sharedPreferenceUtils = context.getSharedPreferenceUtils()

        if (!sharedPreferenceUtils.hasMSISDN()) {
            Log.d(TAG, "No MSISDN linked")
            callback.invoke(true, CMPushError.NoMSISDN)
            return
        }

        if (!sharedPreferenceUtils.hasPushToken()) {
            Log.e(TAG, "Device doesn't have a push token yet!")
            callback.invoke(false, CMPushError.NotRegistered)
            return
        }

        val pushToken = sharedPreferenceUtils.requirePushToken()

        val installationId = sharedPreferenceUtils.getInstallationId()

        val body = constructInformationObject(context, pushToken)

        // Remove MSISDN from body
        body.remove("msisdn")

        doNetworkRequest(
            endpoint = "${createBaseUrl(context)}/register/$installationId",
            method = "PUT",
            body = body.toString(),
            onSuccess = { statusCode, result ->
                if (statusCode == 200) {
                    sharedPreferenceUtils.deleteMSISDN()
                    callback.invoke(true, null)
                } else {
                    callback.invoke(false, CMPushError.ServerError(statusCode, result))
                }
            },
            onError = { statusCode, result ->
                callback.invoke(false, CMPushError.ServerError(statusCode, result))
            },
            onException = { exception ->
                callback.invoke(false, CMPushError.ServerError(null, exception.localizedMessage ?: ""))
            }
        )
    }

    /**
     * Remove the entire device registration
     * Note that you should register the device again using [updateToken()]
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
                        this.deletePushToken()
                        this.deleteMessageHistory()
                        this.deleteMSISDN()
                    }

                    callback(true, null)
                } else {
                    callback(false, CMPushError.ServerError(statusCode, result))
                }
            },
            onError = { statusCode, result ->
                callback.invoke(false, CMPushError.ServerError(statusCode, result))
            },
            onException = { exception ->
                callback.invoke(false, CMPushError.ServerError(null, exception.localizedMessage ?: ""))
            }
        )
    }

    private fun constructInformationObject(context: Context, pushToken: String): JSONObject {
        val version = Build.VERSION.SDK_INT
        val versionName = Build.VERSION.RELEASE

        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        val appVersion = packageInfo.versionName

        val languages = arrayListOf<Locale>()
        val locales = LocaleList.getDefault()
        for (i in 0 until locales.size()) {
            languages.add(locales[i])
        }

        val sharedPreferenceUtils = context.getSharedPreferenceUtils()

        return JSONObject().apply {
            this.put("pushToken", pushToken)
            this.put("deviceOs", "Android")
            this.put("deviceOsVersion", "$versionName ($version)")
            this.put("deviceModel", Build.MODEL)
            this.put("appId", context.packageName)
            this.put("appVersion", appVersion)
            this.put("libId", BuildConfig.LIBRARY_PACKAGE_NAME)
            this.put("libVersion", BuildConfig.LIBRARY_VERSION_NAME)
            this.put("pushAuthorization", PushAuthorization.Authorized.name)
            this.put("preferredLanguages", JSONArray(languages.map { it.toLanguageTag() }))

            if (sharedPreferenceUtils.hasMSISDN()) {
                this.put("msisdn", sharedPreferenceUtils.getMSISDN())
            }
        }
    }

    private fun constructInformationObjectWithMSISDN(context: Context, pushToken: String, msisdn: String): JSONObject {
        return constructInformationObject(context, pushToken).apply {
            this.put("msisdn", msisdn)
        }
    }

    private fun constructInformationObjectWithMSISDNAndOTP(context: Context, pushToken: String, msisdn: String, otpCode: String): JSONObject {
        return constructInformationObjectWithMSISDN(context, pushToken, msisdn).apply {
            this.put("otpCode", otpCode)
        }
    }

    //Message history
    private val maxMessageAge = when (BuildConfig.DEBUG) {
        true -> 5 * 60 * 1000L   //5 minutes
        false -> 60 * 24 * 60 * 60 * 1000L   //60 days
    }
    private val messageHistoryLock = ReentrantLock()

    //Return true if message should be shown
    private fun setMessageShown(context: Context, messageId: String): Boolean {
        val sharedPreferenceUtils = context.getSharedPreferenceUtils()

        messageHistoryLock.withLock {
            //Purge old history if needed
            var dirty = false
            val now = Date().time
            val history = sharedPreferenceUtils.getMessageHistory()
            val purgedHistory = JSONArray()
            (0 until history.length()).forEach {
                (history.get(it) as? JSONObject)?.let { oldMessage ->
                    (oldMessage.get("date") as? Long)?.let { date ->
                        if (now - date < maxMessageAge) {
                            //keep it
                            purgedHistory.put(oldMessage)
                        } else {
                            //discard it
                            dirty = true
                        }
                    }
                } ?: run {
                    dirty = true
                }
            }

            for (i in 0 until purgedHistory.length()) {
                if ((purgedHistory.get(i) as JSONObject).get("id") == messageId) {
                    Log.d(TAG, "Ignore message, already shown")
                    if (dirty) {
                        Log.d(TAG, "Purged history: ${purgedHistory.toString(4)}")
                        sharedPreferenceUtils.storeMessageHistory(purgedHistory)
                    }
                    return false
                }
            }

            val message = JSONObject()
            message.put("id", messageId)
            message.put("date", Date().time)
            purgedHistory.put(message)

            Log.d(TAG, "History: ${purgedHistory.toString(4)}")

            sharedPreferenceUtils.storeMessageHistory(purgedHistory)

            return true
        }
    }

    internal fun getApplicationName(context: Context): String {
        val applicationInfo = context.applicationInfo
        val stringId = applicationInfo.labelRes
        return if (stringId == 0) applicationInfo.nonLocalizedLabel.toString() else context.getString(stringId)
    }

}