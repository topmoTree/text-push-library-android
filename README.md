
![CM](./Images/logo.svg)

# CMPush

CMPush is a solution for customers that want to send push notifications to their apps by using phone numbers. 
The CM platform will look up the corresponding push token for the telephone number and send a push message. When a push message can't be delivered for some reason, CM will send the message by SMS (or another channel, if configured)

---

# RELEASE NOTES

## 2.0.0

##### Breaking

* `CMPush.updateToken(updateToken(context: Context, pushToken: String, callback: (success: Boolean, error: CMPushError?, installationId: String?) -> Unit))` now returns the installationID or a `CMPushError` in the callback. Use the installationID to send push messages to your customers if the MSISDN OTP flow is not needed. The installationID can be added to the customer account for example. Call `CMPush.unregisterMSISDN(context: Context, callback: (success: Boolean, error: CMPushError?) -> Unit)` when the user logs out. Don't perform `CMPush.updateMSISDN(context: Context, msisdn: String, callback: (success: Boolean, error: CMPushError?) -> Unit)` before the installationID was received.

* `CMPush.preRegister(context: Context, msisdn: String, sender: String, callback: (success: Boolean, error: CMPushError?) -> Unit)` is deprecated and replaced with `CMPush.updateMSISDN(context: Context, msisdn: String, callback: (success: Boolean, error: CMPushError?) -> Unit)`. Use this call after `CMPush.updateToken(context: Context, pushToken: String, callback: (success: Boolean, error: CMPushError?, installationId: String?) -> Unit)` completed successfully to start OTP flow.

* `CMPush.register(context: Context, pushToken: String, msisdn: String, otpCode: String, callback: (success: Boolean, error: CMPushError?) -> Unit)` is deprecated and replaced with `CMPush.updateOTP(context: Context, msisdn: String, otpCode: String, callback: (success: Boolean, error: CMPushError?) -> Unit)`. Use this call to send the OTP code to the server.

* `CMPush.pushReceived(context: Context, data: Map<String, String>, showNotification: Boolean = true, @DrawableRes notificationIcon: Int, notificationContentIntent: PendingIntent, callback: (success: Boolean, error: CMPushError?) -> Unit)` is deprecated and replaced with `CMPush.pushReceived(context: Context, data: Map<String, String>, callback: ((success: Boolean, error: CMPushError?) -> Unit)? = null)`. 

* Use `CMPush.unregisterMSISDN(context: Context, callback: (success: Boolean, error: CMPushError?) -> Unit)` to unregister an MSISDN instead of `CMPush.deleteRegistration()` 

* `CMPushError` is now a sealed class that contains more details. `CMPushError.errorMessage` is now `CMPushError.message`. 

* `CMPush.isRegistered()` now checks if the SDK is registered with the CM server. Use `CMPush.hasRegisteredMSISDN()` to check wether a MSISDN was registered to this account.

* Use `CMPush.installationID()` to retrieve the installationID (or null if not yet registered to CM server). Use the installationID to send push messages to your customers if the MSISDN OTP flow is not needed.

* Added support for action buttons. These buttons can also link to pages in the app. More info about this in de documentation below.

##### Upgrade guide
* If you haven't already, move the `CMPush.initialize()` to the `onCreate()` method of your `Application` class. This is for showing push notifications that come through Push Amplification.
* In your FirebaseMessagingService, replace `CMPush.pushReceived(context: Context, data: Map<String, String>, showNotification: Boolean = true, @DrawableRes notificationIcon: Int, notificationContentIntent: PendingIntent, callback: (success: Boolean, error: CMPushError?) -> Unit)` with `CMPush.pushReceived(context: Context, data: Map<String, String>, callback: ((success: Boolean, error: CMPushError?) -> Unit)? = null)`. `notificationIcon` should be passed in the `CMPush.initialize()` method. The `notificationContentIntent` has also been moved to the `CMPush.initialize()`.
* Wait for ``CMPush.updateToken(context: Context, pushToken: String, callback: (success: Boolean, error: CMPushError?, installationId: String?) -> Unit)`` to return the installationID before starting `CMPush.updateMSISDN(context: Context, msisdn: String, callback: (success: Boolean, error: CMPushError?) -> Unit)`. If no MSISDN verification is needed, store the installationID in the customer account.
* Replace your call to `CMPush.preRegister(context: Context, msisdn: String, sender: String, callback: (success: Boolean, error: CMPushError?) -> Unit)` with `CMPush.updateMSISDN(context: Context, msisdn: String, callback: (success: Boolean, error: CMPushError?) -> Unit)`
* Replace your call to `CMPush.register(context: Context, pushToken: String, msisdn: String, otpCode: String, callback: (success: Boolean, error: CMPushError?) -> Unit)` with `CMPush.updateOTP(context: Context, msisdn: String, otpCode: String, callback: (success: Boolean, error: CMPushError?) -> Unit)`
* Replace your call to `CMPush.deleteRegistration()` with `CMPush.unregisterMSISDN(context: Context, callback: (success: Boolean, error: CMPushError?) -> Unit)`
* Replace your call to `CMPush.isRegistered()` with `CMPush.hasRegisteredMSISDN()`
##### Enhancements

* Support for rich media in push notifications.
  Supported media are:
    - "image/jpeg": JPG image (max 10MB)
    - "image/png": PNG image (max 10MB)

* Support for suggestions (actions) in push notifications.
  Supported actions are:
    - OpenUrl: website url. The url will be opened in the browser of the user
    - OpenAppPage: open a specific page in the app.
      In the `Intent` of the launch activity, the page is stored in the extra's with key `CMPush.OPEN_APP_PAGE`.

* Report preferred language to the CM server to be able to localize messages
* Now also supports Push Notifications without SMS verification (OTP) flow.

## 1.0.0
* Initial release

# ONBOARDING

Developers need to onboard at CM by providing a Firebase admin.json

The onboarding process at CM will return a unique applicationKey, that needs to be passed into the library. 


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
    implementation 'com.github.CMDotCom:text-push-library-android:2.0.0'
}
```

Now perform a `project sync` and the library is added to your Android project.

## Configure CMPush

CMPush needs to be configured at application startup. Add `CMPush.initialize()` to the `onCreate` method of your `Application`. It's important that this is done in the `Application` and not in an Activity! 
The initialize method requires a few parameters. 

`context`: So the library can use SharedPreferences to store configuration options.

`applicationKey`: The applicationKey received from CM.

`notificationIcon`: The icon (drawable res) that should be used for the notifications.

`notificationIntent`: `Intent` to specify which `Activity` should be opened when a user interacts with a notification. Make sure your flags are correct. Usually FLAG_ACTIVITY_NEW_TASK and FLAG_ACTIVITY_CLEAR_TOP.

Since Android 8 notifications are linked to a `notification channel`. You can read more about notification channels here:
[Notification channel info](https://developer.android.com/training/notify-user/channels)

Because the library can also handle the showing of the push notifications, it needs a `channelId`, `channelName` and `channelDescription`.

```kotlin
val startIntent = Intent(applicationContext, MainActivity::class.java)
startIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
startIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)

CMPush.initialize(
    context = this,
    applicationKey = "<key>",
    channelId = "CMPushAnnouncements",
    channelName = "Announcements",
    channelDescription = "Announcements of our latest products!",
    notificationIcon = R.drawable.ic_launcher_foreground,
    notificationIntent = startIntent
)
```

## CMPushError ##
CMPushError is a sealed class that is returned by several API calls. It can be used to determine the cause of the error:
```kotlin
when (error){
    CMPushError.NoMSISDN -> TODO()
    CMPushError.NotInitialized -> TODO()
    CMPushError.NotRegistered -> TODO()
    CMPushError.Offline -> TODO()
    is CMPushError.ServerError -> TODO()
}
```

## Add CMPush to your FirebaseMessagingReceiver

To confirm / show the Firebase push messages in your app you need to implement some methods in your own `FirebaseMessagingService`.

Incoming push messages will arrive in the `onMessageReceived(remoteMessage: RemoteMessage)`. 
To show the notification and confirm that a push message has been delivered the `CMPush.pushReceived()` method has to be implemented in the `onMessageReceived()`.

The CMPush library will create the notification for you.

```kotlin
class PushReceiver : FirebaseMessagingService() {
    companion object {
        const val TAG = "PushReceiver"
    }

    override fun onNewToken(token: String) {
        CMPush.updateToken(
            context = applicationContext,
            pushToken = token,
            callback = { success, error, _ ->
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
     * The CMPush library will notify CM the message has been received and create the Notification
     */
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        // Notify CMPush library that a push message has been received 
        CMPush.pushReceived(
            context = applicationContext,
            data = remoteMessage.data,
            callback = { success, error ->
                Log.d(TAG, "Confirmed push: $success, $error")
            }
        )

        super.onMessageReceived(remoteMessage)
    }
}
```

## Register the device

Everytime the app starts, call `CMPush.updateToken()`.
The library keeps track of the previously send pushToken / device info and checks if there are differences. If so it sends an update to CM. Thus, there is no need to keep track of the pushToken yourself!

If successful, an `installationId` will be returned which you can use to send push messages to the app.

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
        callback = { success, error, installationId ->
            if (success) {
                // You can store the installationId together with your customer info
            } else {
                // Handle error
            }
        }
    )
})
```

## Linking phone number

### UpdateMSISDN

Use `CMPush.updateMSISDN()` to trigger an OTP request for a phone number that was entered by end-user, used to link a phone number to this device.

```kotlin
CMPush.updateMSISDN(
    context = requireContext(),
    msisdn = view.input_phone_number.text.toString(),
    callback = { success, error ->
        if (success) {
          // Store the phone number and navigate to a OTP input screen.
        } else {
          // Show an error to the user
        }
    }
)
```

### UpdateOTP

Checks an OTP that user has received by SMS to link a phone number to this device.

```kotlin
CMPush.updateOTP(
    context = requireContext(),
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
```

## Check if the app is registered for push notifications

To check if the app is registered for push notifications the `CMPush.isRegistered()` method can be called. This will return a `boolean true` if the app is registered.

```kotlin
CMPush.isRegistered(
    context = requireContext()
)
```

## Check if the user has a MSISDN linked

To check if the user has a MSISDN linked the `CMPush.hasMSISDNLinked()` method can be called. This will return a `boolean true` if the user has linked a phone number.

```kotlin
CMPush.hasRegisteredMSISDN(
    context = requireContext()
)
```

## Unlink MSISDN

To unlink the MSISDN of a user, the `CMPush.unregisterMSISDN()` can be called. 

```kotlin
CMPush.unregisterMSISDN(
    context = requireContext(),
    callback = { success, error ->
        Log.d("CMPush", "Unregistered MSISDN: $success, Error: $error")
    }
)
```

## Delete the registration

To delete the entire device registration, the `CMPush.deleteRegistration()` can be called. 

Note that you should register the device again using `CMPush.updateToken()`

```kotlin
CMPush.deleteRegistration(
    context = requireContext(),
    callback = { success, error ->
        Log.d("CMPush", "Unregistered: $success, Error: $error")
    }
)
```


