package com.cm.cmpush

import android.content.Context
import com.cm.cmpush.helper.PushSynchronizer

fun CMPush.sync(context: Context){
    PushSynchronizer.fetchPushMessagesFromCM(context)
}