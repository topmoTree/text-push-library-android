package com.cm.cmpush.receiver

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import com.cm.cmpush.CMPush
import com.cm.cmpush.objects.CMData
import com.cm.cmpush.objects.CMPushEvent
import com.cm.cmpush.objects.CMPushEventType
import com.cm.cmpush.objects.CMPushStatus
import org.json.JSONObject

class NotificationInteractionReceiver : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(TAG, "NotificationInteractionReceiver onCreate()")

        super.onCreate(savedInstanceState)
        handleNotificationIntent(this, intent)
        finish()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent != null) {
            handleNotificationIntent(this, intent)
        }
        finish()
    }

    private fun handleNotificationIntent(context: Context, intent: Intent) {
        val notificationId = intent.getIntExtra(CMPush.KEY_NOTIFICATION_ID, -1)

        if (notificationId == -1) {
            Log.e(CMPush.TAG, "Not a valid intent for statistics")
            return
        }

        val messageId = intent.getStringExtra(CMPush.KEY_MESSAGE_ID) ?: kotlin.run {
            Log.e(CMPush.TAG, "Missing messageId in intent!")
            return
        }

        Log.d(CMPush.TAG, "Handling intent with notificationId: $notificationId")

        //FIXME remove later
        Log.d(CMPush.TAG, "Handling intent with notificationId: $notificationId")

        // Remove notification
        NotificationManagerCompat.from(context).cancel(notificationId)

        // Handle suggestion action
        intent.getStringExtra(CMPush.KEY_SUGGESTION)?.let { suggestionJSON ->

            Log.d(CMPush.TAG, "intent.getStringExtra")

            val suggestion = CMData.Suggestion.fromJSONObject(JSONObject(suggestionJSON))

            var eventType : CMPushEventType? = null
            var reference : String? = null
            when (suggestion.action) {
                CMData.Suggestion.Action.OpenUrl -> {
                    eventType = CMPushEventType.URLOpened
                    reference = suggestion.url
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(suggestion.url)).apply {
                            this.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                    )
                }
                CMData.Suggestion.Action.Reply -> {
                    Log.e(CMPush.TAG, "Reply suggestion not implemented yet")
                }
                CMData.Suggestion.Action.OpenAppPage -> {
                    eventType = CMPushEventType.AppPageOpened
                    reference = suggestion.page
                    context.startActivity(
                        CMPush.appIntent.apply {
                            putExtra(CMPush.OPEN_APP_PAGE, suggestion.page)
                        }
                    )
                }
                CMData.Suggestion.Action.Unknown -> {
                    Log.e(CMPush.TAG, "Unknown action for suggestion..")
                }
            }

            eventType?.let{ type ->
                reference?.let{ reference ->
                    CMPush.reportStatus(context, CMPushStatus(CMPushEvent(type, reference, hashMapOf("label" to suggestion.label, "postbackdata" to suggestion.postbackData))), null)
                }
            }
        } ?: run {
            //Just an Open intent

            Log.d(CMPush.TAG, "Just an Open intent")

            context.startActivity(
                CMPush.appIntent
            )
            CMPush.reportStatus(context, CMPushStatus(CMPushEvent(CMPushEventType.MessageOpened, messageId)), null)
        }
    }

    companion object {
        const val TAG = "CMPushLibrary-NotificationInteractionReceiver"
    }
}