package com.cm.cmpush.helper

import android.content.Context
import android.util.Log
import com.cm.cmpush.BuildConfig
import com.cm.cmpush.CMPush
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.io.Reader
import java.net.HttpURLConnection
import java.net.URL

internal object NetworkHelper {
    val productionUrl = "https://api.cm.com/channelswebhook/push/v2/accounts"
    var testUrl: String? = null

    internal fun getAccountId(context: Context): String? {
        val sharedPreferenceUtils = context.getSharedPreferenceUtils()
        return if (sharedPreferenceUtils.hasAccountId()) sharedPreferenceUtils.getAccountId()
        else null
    }

    internal fun createBaseUrl(context: Context): String {
        return "${testUrl ?: productionUrl}/${getAccountId(context)}"
    }

    fun doNetworkRequest(
        endpoint: String,
        method: String,
        body: String? = null,
        onSuccess: (statusCode: Int, result: String) -> Unit,
        onError: (statusCode: Int, result: String) -> Unit,
        onException: (exception: Exception) -> Unit
    ) {

        Thread {
            val url = URL(endpoint)

            Log.d(CMPush.TAG, "Starting network request ($method).. \n$endpoint \n$body")

            val urlConnection = url.openConnection() as HttpURLConnection

            try {
                urlConnection.apply {
                    this.requestMethod = method
                    this.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    this.setRequestProperty("Accept", "application/json")
                    this.connectTimeout = 10000
                    this.readTimeout = 15000
                }

                // If a body is given, send it with the network request
                body?.let { input ->
                    val bytes = input.toByteArray(Charsets.UTF_8)
                    urlConnection.outputStream.write(bytes, 0, bytes.size)
                    urlConnection.outputStream.flush()
                }

                val statusCode = urlConnection.responseCode

                if (statusCode >= 400) {
                    onError(statusCode, readStream(urlConnection.errorStream))
                } else {
                    onSuccess(statusCode, readStream(urlConnection.inputStream))
                }
            } catch (e: Exception) {
                Log.e(CMPush.TAG, "Failed to execute call", e)
                onException.invoke(e)
            } finally {
                urlConnection.disconnect()
            }
        }.start()
    }

    private fun readStream(inputStream: InputStream): String {
        val reader = BufferedReader(InputStreamReader(inputStream) as Reader?)

        val response = StringBuffer()
        try {
            var inputLine = reader.readLine()
            while (inputLine != null) {
                response.append(inputLine)
                inputLine = reader.readLine()
            }
        } catch (e: Exception) {
            Log.e(CMPush.TAG, "Failed to read response stream", e)
            e.printStackTrace()
        } finally {
            reader.close()
        }
        return response.toString()
    }

}