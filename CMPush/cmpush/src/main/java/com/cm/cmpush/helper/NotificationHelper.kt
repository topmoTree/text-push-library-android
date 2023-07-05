package com.cm.cmpush.helper

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.DrawableRes
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.cm.cmpush.CMPush
import com.cm.cmpush.CMPush.TAG
import com.cm.cmpush.objects.CMData
import com.cm.cmpush.receiver.NotificationDismissedReceiver
import com.cm.cmpush.receiver.NotificationInteractionReceiver
import java.net.URL
import java.util.concurrent.Executors


internal object NotificationHelper {
    fun createNotificationChannel(
        context: Context,
        channelId: String,
        channelName: String,
        channelDescription: String
    ) {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(channelId, channelName, importance).apply {
                description = channelDescription
            }
            // Register the channel with the system
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun createNotification(
        context: Context,
        cmData: CMData,
        channelId: String,
        @DrawableRes notificationIcon: Int
    ) {
        val notificationId = getNotificationId(context)

        val openAppPageSuggestion = cmData.suggestions.firstOrNull{ it.action == CMData.Suggestion.Action.OpenAppPage }

        val openIntent = PendingIntent.getActivity(
            context,
            notificationId,
            Intent(context, NotificationInteractionReceiver::class.java).apply {
                putExtra(CMPush.KEY_NOTIFICATION_ID, notificationId)
                putExtra(CMPush.KEY_MESSAGE_ID, cmData.messageId)
                if (openAppPageSuggestion != null){
                    putExtra(CMPush.OPEN_APP_PAGE, openAppPageSuggestion.page)
                }

                //flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val deleteIntent = PendingIntent.getBroadcast(
            context,
            notificationId,
            Intent(context, NotificationDismissedReceiver::class.java).apply {
                putExtra(CMPush.KEY_NOTIFICATION_ID, notificationId)
                putExtra(CMPush.KEY_MESSAGE_ID, cmData.messageId)
                //flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(notificationIcon)
            .setContentTitle(cmData.title ?: CMPush.getApplicationName(context))
            .setContentText(cmData.body)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(openIntent)
            .setDeleteIntent(deleteIntent)
            .setAutoCancel(true)

        // If body is big, make it an expandable notification
        if (cmData.media == null && cmData.body.length > 100) {
            builder.setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(cmData.body)
            )
        }

        val executor = Executors.newSingleThreadExecutor()
        val handler = Handler(Looper.getMainLooper())

        // Handle the rest async for if images needs to be downloaded
        executor.execute {
            cmData.media?.let { media ->
                // Only allow jpeg / png images
                if (media.mimeType != "image/jpeg" && media.mimeType != "image/png") {
                    Log.e(TAG, "Media with mimeType '${media.mimeType}' not supported.")
                    return@let null
                }

                return@let media.mediaUri
            }?.let { image ->
                try {
                    val stream = URL(image).openStream()
                    val bitmap = BitmapFactory.decodeStream(stream)

                    builder
                        .setLargeIcon(bitmap)
                        .setStyle(
                            NotificationCompat.BigPictureStyle()
                                .bigPicture(bitmap)
                                .bigLargeIcon(null)
                        )
                } catch (e: Exception) {
                    Log.e(TAG, "Failed decoding image url: ${e.message}", e)
                }
            }

            // Add action buttons
            cmData.suggestions.forEach { suggestion ->
                builder.addAction(
                    0,
                    suggestion.label,
                    PendingIntent.getActivity(
                        context,
                        getSuggestionId(context),
                        Intent(context, NotificationInteractionReceiver::class.java).apply {
                            action = suggestion.action.name
                            putExtra(CMPush.KEY_NOTIFICATION_ID, notificationId)
                            putExtra(CMPush.KEY_MESSAGE_ID, cmData.messageId)
                            putExtra(CMPush.KEY_SUGGESTION, suggestion.toJSONObject().toString())
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        },
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                    )
                )
            }

            // Show notification
            handler.post {
                NotificationManagerCompat.from(context).notify(notificationId, builder.build())
            }
        }
    }

    private fun getSuggestionId(context: Context): Int {
        val sharedPreferenceUtils = context.getSharedPreferenceUtils()

        val nextId = (sharedPreferenceUtils.getSuggestionId() ?: 0) + 1
        sharedPreferenceUtils.storeSuggestionId(nextId)

        return nextId
    }

    private fun getNotificationId(context: Context): Int {
        val sharedPreferenceUtils = context.getSharedPreferenceUtils()

        val nextId = (sharedPreferenceUtils.getNotificationId() ?: 0) + 1
        sharedPreferenceUtils.storeNotificationId(nextId)

        return nextId
    }
}