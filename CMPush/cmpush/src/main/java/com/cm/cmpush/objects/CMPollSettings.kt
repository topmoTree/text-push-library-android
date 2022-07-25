package com.cm.cmpush.objects

import org.json.JSONObject
import java.lang.Exception
import java.util.*

internal data class CMPollSettings(
    val interval: Long,     //interval in seconds
    val days: Int,          //days
    val startTime: Long     //only needed for duration
) {
    fun toJSONObject(): JSONObject = JSONObject().apply {
        put(polling_interval_key, interval)
        put(polling_days_key, days)
        put(polling_starttime_key, startTime)
    }

    companion object {
        val polling_interval_key = "poll_interval"
        val polling_days_key = "poll_days"
        val polling_starttime_key = "poll_start_time"

        fun fromJSONObject(jsonObject: JSONObject?, startTime: Long? = null): CMPollSettings? {
            if (jsonObject == null) return null

            try {
                return CMPollSettings(
                    jsonObject.getLong(polling_interval_key),
                    jsonObject.getInt(polling_days_key),
                    startTime ?: jsonObject.getLong(polling_starttime_key)
                )
            } catch (ex: Exception) {
                return null
            }
        }
    }
}