package com.cm.cmpush.helper

import android.content.Context
import android.util.Log
import android.widget.Toast
import com.cm.cmpush.CMPush
import com.cm.cmpush.helper.NetworkHelper.createBaseUrl
import com.cm.cmpush.objects.CMData
import com.cm.cmpush.worker.PushSyncWorkerHelper

internal object PushSynchronizer {
    fun fetchPushMessagesFromCM(context: Context) {
        val sharedPreferenceUtils = context.getSharedPreferenceUtils()

        sharedPreferenceUtils.getPollSettings()?.let { pollSettings ->
            //Toast.makeText(context, "Fetching notifications..", Toast.LENGTH_LONG).show()

            if (!sharedPreferenceUtils.hasInstallationId()) {
                Log.d(CMPush.TAG, "Can't confirm push message since app doesn't have an installationId yet.")
                sharedPreferenceUtils.deletePollSettings()
                return
            }

            val installationId = sharedPreferenceUtils.getInstallationId()

            NetworkHelper.doNetworkRequest(
                endpoint = "${createBaseUrl(context)}/pendingmessages/$installationId",
                method = "GET",
                onSuccess = { statusCode, result ->
                    val response = JSONHelper.tryStringToJSONObject(result)

                    when (statusCode) {
                        200 -> {
                            try {
                                response?.getJSONArray("messages")?.let { messages ->
                                    Array(messages.length()) { i ->
                                        messages.getJSONObject(i).let { pushMessage ->
                                            CMData.fromJSONObject(pushMessage)
                                        }
                                    }.filterNotNull().forEach { cmData ->
                                        CMPush.showAndConfirmPushNotification(
                                            context = context,
                                            cmData = cmData
                                        )
                                    }
                                    PushSyncWorkerHelper.scheduleNextSync(context, pollSettings)
                                } ?: run {
                                    Log.d(CMPush.TAG, "Failed to retrieve pending notifications from CM")
                                    PushSyncWorkerHelper.scheduleNextSync(context, pollSettings)
                                }
                            } catch (ex: Exception) {
                                Log.e(CMPush.TAG, "Failed to retrieve pending notifications from CM:", ex)
                                PushSyncWorkerHelper.scheduleNextSync(context, pollSettings)
                            }
                        }
                        else -> {
                            Log.d(CMPush.TAG, "Polling status: " + statusCode)
                            PushSyncWorkerHelper.scheduleNextSync(context, pollSettings)
                        }
                    }
                },
                onError = { statusCode, result ->
                    if (statusCode == 403) {
                        //No more polling
                        Log.d(CMPush.TAG, "403 - No more polling")
                        PushSyncWorkerHelper.disableSync(context)
                    } else {
                        Log.e(CMPush.TAG, "Failed to retrieve pending notifications from CM ($statusCode): $result")
                        PushSyncWorkerHelper.scheduleNextSync(context, pollSettings)
                    }
                },
                onException = { exception ->
                    Log.e(CMPush.TAG, "Failed to retrieve pending notifications from CM", exception)
                    PushSyncWorkerHelper.scheduleNextSync(context, pollSettings)
                }
            )
        } ?: run {
            Log.d(CMPush.TAG, "No poll settings, no poll")
        }
    }
}