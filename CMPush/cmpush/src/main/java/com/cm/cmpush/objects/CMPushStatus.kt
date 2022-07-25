package com.cm.cmpush.objects

import org.json.JSONObject
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.collections.HashMap

val dateFormat by lazy {
    val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'")
    format.timeZone = TimeZone.getTimeZone("UTC")
    format
}

internal enum class CMPushEventType {
    MessageOpened,
    URLOpened,
    AppPageOpened,
    MessageDismissed
}

internal class CMPushEvent(
    val type : CMPushEventType,
    val reference : String,
    val custom : HashMap<String, String>? = null) {

    fun toJSONObject(): JSONObject = JSONObject().apply {
        put("type", type.name)
        put("reference", reference)
        custom?.let{
            val customObject = JSONObject().apply{
                it.forEach {
                    put(it.key, it.value)
                }
            }
            put("custom", customObject)
        }
    }
}

internal class CMPushMessage() {
    fun toJSONObject(): JSONObject = JSONObject().apply {
    }
}

internal enum class CMPushStatusType {
    Delivered
}

internal class CMPushStatusReport(
    val status : CMPushStatusType,
    val messageId : String) {

    fun toJSONObject(): JSONObject = JSONObject().apply {
        put("status", status.name)
        put("messageId", messageId)
        put("timestamp", dateFormat.format(Date()))
    }
}

internal class CMPushStatus(
    val event : CMPushEvent? = null,
    val message : CMPushMessage? = null,
    val statusReport : CMPushStatusReport? = null) {

    fun toJSONObject(): JSONObject = JSONObject().apply {
        event?.apply {
            put("event", toJSONObject())
        }
        message?.apply {
            put("message", toJSONObject())
        }
        statusReport?.apply {
            put("statusReport", toJSONObject())
        }
    }
}
