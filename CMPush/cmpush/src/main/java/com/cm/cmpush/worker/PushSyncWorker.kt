package com.cm.cmpush.worker

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.cm.cmpush.helper.PushSynchronizer

class PushSyncWorker(val context: Context, workerParams: WorkerParameters) : Worker(context, workerParams) {
    override fun doWork(): Result {
        //On background thread
        //Just trigger sync and report OK
        Handler(Looper.getMainLooper()).post {
            PushSynchronizer.fetchPushMessagesFromCM(context)
        }

        return Result.success()
    }
}
