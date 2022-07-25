package com.cm.cmpush.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.cm.cmpush.CMPush
import com.cm.cmpush.objects.CMPushEvent
import com.cm.cmpush.objects.CMPushEventType
import com.cm.cmpush.objects.CMPushStatus

class NotificationDismissedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        context?.let { context ->
            intent?.extras?.let { extras ->
                extras.getInt(CMPush.KEY_NOTIFICATION_ID).let { notificationId ->
                    extras.getString(CMPush.KEY_MESSAGE_ID)?.let { messageId ->
                        CMPush.reportStatus(context, CMPushStatus(CMPushEvent(CMPushEventType.MessageDismissed, messageId)), null)
                    }
                }
            }
        }
    }

}