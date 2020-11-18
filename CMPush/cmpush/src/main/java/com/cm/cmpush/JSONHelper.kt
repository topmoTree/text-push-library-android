package com.cm.cmpush

import android.util.Log
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.io.Reader
import java.lang.Exception
import java.net.HttpURLConnection
import java.net.URL

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