package com.cm.cmpush.worker

import android.content.Context
import android.util.Log
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.cm.cmpush.BuildConfig
import com.cm.cmpush.CMPush
import com.cm.cmpush.helper.getSharedPreferenceUtils
import com.cm.cmpush.objects.CMPollSettings
import org.json.JSONObject
import java.util.*
import java.util.concurrent.TimeUnit

internal object PushSyncWorkerHelper {

    fun updateSyncSettings(context: Context, settings: JSONObject) {
        val prefs = context.getSharedPreferenceUtils()

        //New settings received. Update schedule.
        //First disable any existing poll
        disableSync(context)

        CMPollSettings.fromJSONObject(settings, Date().time)?.let { pollSettings ->
            prefs.storePollSettings(pollSettings)

            //Schedule background refresh
            scheduleNextSync(context, pollSettings)
        }
    }

    fun disableSync(context: Context) {
        val prefs = context.getSharedPreferenceUtils()

        //Remove from preferences
        prefs.deletePollSettings()

        //Remove currently scheduled fetch
        WorkManager.getInstance(context).cancelAllWork()
    }

    fun scheduleNextSync(context: Context, settings: CMPollSettings) {
        //Check if endDate is not reached.
        val now = Date()
        val diff = now.time - settings.startTime
        val timeunit = when (BuildConfig.DEBUG) {
            true -> TimeUnit.MINUTES
            false -> TimeUnit.DAYS
        }
        if (timeunit.convert(diff, TimeUnit.MILLISECONDS) <= settings.days) {
            //Allowed
            val work = OneTimeWorkRequestBuilder<PushSyncWorker>()
                .setInitialDelay(settings.interval, TimeUnit.SECONDS)
                .build()
            Log.d(CMPush.TAG, "Schedule next sync: $work")
            WorkManager.getInstance(context).enqueue(work)
        } else {
            //Expired, remove stored hash. This causes an updateToken on the next app launch and
            //we can get new poll settings
            Log.d(CMPush.TAG, "No more polling, expired")
            val prefs = context.getSharedPreferenceUtils()
            prefs.deleteDataHash()
        }
    }

}