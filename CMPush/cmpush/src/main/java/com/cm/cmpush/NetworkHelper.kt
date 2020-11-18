package com.cm.cmpush

import android.util.Log
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.io.Reader
import java.lang.Exception
import java.net.HttpURLConnection
import java.net.URL

internal object NetworkHelper {
    fun doNetworkRequest(
        endpoint: String,
        method: String,
        body: String? = null,
        onSuccess: (statusCode: Int, result: String) -> Unit,
        onError: (statusCode: Int, result: String) -> Unit,
        onException: (exception: Exception) -> Unit
    ) {
        val url = URL(endpoint)

        Log.d(CMPush.TAG, "Starting network request.. \n$endpoint \n$body")

        Thread {
            val urlConnection = url.openConnection() as HttpURLConnection

            try {
                urlConnection.apply {
                    this.setChunkedStreamingMode(0)
                    this.requestMethod = method
                    this.setRequestProperty("Content-Type", "application/json; utf-8")
                    this.setRequestProperty("Accept", "application/json")
                }

                // If a body is given, send it with the network request
                body?.let { input ->
                    urlConnection.outputStream.write(input.toByteArray(Charsets.UTF_8))
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