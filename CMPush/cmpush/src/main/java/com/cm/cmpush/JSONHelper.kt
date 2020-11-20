package com.cm.cmpush

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

internal object JSONHelper {
    fun formatJSONString(json: String): String {
        return tryStringToJSONArray(json)?.toString(4) ?: tryStringToJSONObject(json)?.toString(4) ?: json
    }

    fun tryStringToJSONArray(json: String): JSONArray? {
        return try {
            JSONArray(json)
        } catch (e: JSONException) {
            null
        }
    }

    fun tryStringToJSONObject(json: String): JSONObject? {
        return try {
            JSONObject(json)
        } catch (e: JSONException) {
            null
        }
    }
}