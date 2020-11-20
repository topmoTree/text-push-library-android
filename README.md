# CMPush

CMPush is a solution for customers that want to send push notifications to their apps by using phone numbers. 
The CM platform will look up the corresponding push token for the telephone number and send a push message. When a push message can't be delivered for some reason, CM will send the message by SMS (or another channel , if configured)



Please see [https://www.cm.com/app/docs/en/api/business-messaging-api/1.0/index#push](https://www.cm.com/app/docs/en/api/business-messaging-api/1.0/index#push) for details.

---

# ONBOARDING

Developers need to onboard at CM by providing a Firebase admin.json

The onboarding process at CM will return a unique accountid, that needs to be passed into the library. 

# Adding CMPush to project

To enable CMPush you need to add Firebase to your app and you have to add the CMPush library to your project. Next you have to add a PushReceiver that extends the FirebaseMessagingService. The CMPush library confirms and shows push messages and this functionality needs to be called from FirebaseMessagingService.

## Adding Firebase Cloud Messaging to your project

The first step of enabling push notifications is adding Firebase Cloud Messaging to your Android app and creating a custom `FirebaseMessagingService` as described in the following tutorial:
[Set up Firebase Cloud-Messaging](https://firebase.google.com/docs/cloud-messaging/android/client)

## Add CMPush Library

Add the JitPack repository to your root `build.gradle`.

```
allprojects {
    repositories {
        google()
        jcenter()
        maven {
            url 'https://jitpack.io'
        }
    }
}
```

Next, add the dependency to your app-level `build.gradle`.

```
dependencies {
    // CMPush Library
    implementation 'com.github.CMDotCom:text-push-library-android:1.0.0.0'
}
```

Now perform a `project sync` and the library is added to your Android project.

## Configure CMPush

CMPush needs to be configured at application startup. Add CMPush.initialize to the `onCreate` method of your MainActivity. 
The initialize method requires a few parameters. 

`context`: So the library can use SharedPreferences to store configuration options.

`accountId`: The accountId received from CM.

Since Android 8 notifications are linked to a `notification channel`. You can read more about notification channels here:
[Notification channel info](https://developer.android.com/training/notify-user/channels)

Because the library can also handle the showing of the push notifications, it needs a `channelId`, `channelName` and `channelDescription`.

```kotlin
CMPush.initialize(
    context = this,
    accountId = "abcdefgh12345678",
    channelId = "CMPushAnnouncements",
    channelName = "Announcements",
    channelDescription = "Announcements of our latest products!"
)
```

## Add CMPush to your FirebaseMessagingReceiver

To confirm / show the Firebase push messages in your app you need to implement some methods in your own `FirebaseMessagingService`.

Incoming push messages will arrive in the `onMessageReceived(remoteMessage: RemoteMessage)`. 
To confirm that a push message has been delivered the `CMPush.pushReceived()` method has to be implemented in the `onMessageReceived()`.

The CMPush library can create the visible notification by passing `showNotification = true`. If you want to take control over the push notifications yourself you can simply pass `showNotification = false` and handle the notifications yourself. 

To open a specific Activity when the notification is clicked. You should pass a `PendingIntent` that contains an Intent to your Activity.

```kotlin
class PushReceiver : FirebaseMessagingService() {
    companion object {
        const val TAG = "PushReceiver"
    }

    override fun onNewToken(token: String) {
        CMPush.updateToken(
            context = applicationContext,
            pushToken = token,
            callback = { success, error ->
                if (success) {
                    Log.d(TAG, "Successfully updated token!")
                } else {
                    Log.e(TAG, "Failed to update token: $error")
                }
            }
        )
    }

    /**
     * Incoming push messages will arrive here. They aren't being shown to the user yet.
     * The CMPush library can show the notifications for you by setting "showNotification" to true.
     * If you want to handle the showing of the notifications yourself you can pass false to the "showNotification".
     */
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        // Create an Intent that is called when the notification is clicked
        val intent = Intent(applicationContext, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            applicationContext, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Notify CMPush library that a push message has been received
        CMPush.pushReceived(
            context = applicationContext,
            data = remoteMessage.data,
            showNotification = true,
            notificationIcon = R.drawable.ic_launcher_foreground,
            notificationContentIntent = pendingIntent,
            callback = { success, error ->
                Log.d(TAG, "Confirmed push: $success, $error")
            }
        )

        super.onMessageReceived(remoteMessage)
    }
}
```

## Registering phone number

### PreRegister

Use `CMPush.preregister()`  to trigger an OTP request for a phone number that was entered by end-user, used to link a phone number to this device.

```kotlin
CMPush.preRegister(
    context = requireContext(),
    msisdn = view.input_phone_number.text.toString(),
    sender = "CMPush Demo",
    callback = { success, error ->
        if (success) {
            // Store the phone number and navigate to a OTP input screen.
        } else {
            // Show an error to the user
        }
    }
)
```

### Register

Checks an OTP that user has received by SMS, link a phone number to this device.
This call also requires you to pass the `Firebase Pushtoken`.

```kotlin
FirebaseMessaging.getInstance().token.addOnCompleteListener(OnCompleteListener { task ->
    if (!task.isSuccessful) {
        Log.w(TAG, "Fetching FCM registration token failed", task.exception)
        return@OnCompleteListener
    }
    // Get new FCM registration token
    val token = task.result ?: ""
    CMPush.register(
        context = requireContext(),
        pushToken = token,
        msisdn = phoneNumber,
        otpCode = view.input_otp_code.text.toString(),
        callback = { success, error ->
            if (success) {
                // Finish the setup
            } else {
                // Show an error to the user
            }
        }
    )
})
```

## Keeping the PushToken / Device info up-to-date

To keep the pushToken / device info up-to-date you need to add the `CMPush.updateToken()` to the `onCreate()` of your `MainActivity`.
The library keeps track of the previously send pushToken / device info and checks if there are differences. If so it sends an update to CM. Thus there is no need to keep track of the pushToken yourself!

```kotlin
FirebaseMessaging.getInstance().token.addOnCompleteListener(OnCompleteListener { task ->
    if (!task.isSuccessful) {
        Log.w(TAG, "Fetching FCM registration token failed", task.exception)
        return@OnCompleteListener
    }

    // Get new FCM registration token
    val token = task.result ?: ""

    CMPush.updateToken(
        context = this@MainActivity,
        pushToken = token,
        callback = { success, error ->
            Log.d("CMPush", "Updated token: $success, Error: $error")
        }
    )
})
```

## Check if the app is registered for push notifications

To check if the app is registered for push notifications the `CMPush.isRegistered()` method can be called. This will return a `boolean true` if the app is registered.

```kotlin
CMPush.isRegistered(
    context = requireContext()
)
```

## Delete a registration

To delete a registration the `CMPush.deleteRegistration()` can be called.

```kotlin
CMPush.deleteRegistration(
    context = requireContext(),
    callback = { success, error ->
        Log.d("CMPush", "Unregistered: $success, Error: $error")
    }
)
```
