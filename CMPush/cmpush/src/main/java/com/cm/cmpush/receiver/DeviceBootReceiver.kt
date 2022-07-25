package com.cm.cmpush.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.cm.cmpush.CMPush.TAG
import com.cm.cmpush.helper.PushSynchronizer

internal class DeviceBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Device booted up!")

        if (intent.action != Intent.ACTION_BOOT_COMPLETED) {
            Log.e(TAG, "Action that started ${DeviceBootReceiver::class.simpleName} is not ACTION_BOOT_COMPLETED!")
            return
        }

        PushSynchronizer.fetchPushMessagesFromCM(context)
    }
}