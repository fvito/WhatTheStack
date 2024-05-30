package com.haroldadmin.whatthestack

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.*
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.lang.ref.WeakReference


/**
 * A Bound Service which runs in a separate process than the host application.
 *
 * This service must be started with [Context.bindService]. A bound service lives only as long as
 * the calling context, so `bindService` must be called on an **APPLICATION CONTEXT**.
 *
 * [WhatTheStackInitializer] starts this service, and it dies when the host app terminates.
 * Therefore we don't need to explicitly handle [Service.onCreate], [Service.onDestroy] or call
 * [Service.stopSelf].
 *
 * [WhatTheStackExceptionHandler] sends messages to this service whenever an uncaught exception
 * is thrown in the host application. This service then starts an activity with the processed
 * exception data as an intent extra.
 */
@ServiceProcess
internal class WhatTheStackService : LifecycleService() {
    private var finishedWaitingForFile = false
    private var stopAfterWaitingForFile = false

    override fun onCreate() {
        super.onCreate()

        lifecycle.coroutineScope.launch {
            val earlyStartupCrashFile = getStartupCrashFile(this@WhatTheStackService)
            for (i in 0 until 10) {
                if (earlyStartupCrashFile.exists()) {
                    val crashDataRaw = earlyStartupCrashFile.readBytes()
                    val parcel = Parcel.obtain()
                    val crashBundle = try {
                        parcel.unmarshall(crashDataRaw, 0, crashDataRaw.size)
                        parcel.setDataPosition(0)
                        Bundle.CREATOR.createFromParcel(parcel)
                    } finally {
                        parcel.recycle()
                    }

                    getStartupCrashFile(applicationContext).delete()

                    // We cannot open the activity after app has already died because of
                    // background activity start restrictions. Show notification instead.
                    showCrashNotification(crashBundle)

                    stopSelf()

                    break
                }
                delay(500)
            }

            if (stopAfterWaitingForFile) {
                stopSelf()
            }
            finishedWaitingForFile = true
        }
    }

    private fun showCrashNotification(crashBundle: Bundle) {
        val notificationManager = getSystemService<NotificationManager>()!!

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
            !notificationManager.areNotificationsEnabled()) {
            Log.w(
                "WhatTheStack",
                "Cannot post crash notifications: App does not have a notification permission"
            )
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID_CRASHES,
                    "Crashes",
                    NotificationManager.IMPORTANCE_HIGH
                )
            )

            val channel = notificationManager.getNotificationChannel(CHANNEL_ID_CRASHES)
            if (channel.importance == NotificationManager.IMPORTANCE_NONE) {
                Log.w(
                    "WhatTheStack",
                    "Cannot post crash notifications: 'Crash' notification channel is disabled"
                )
                return
            }
        }

        val notification = NotificationCompat.Builder(this@WhatTheStackService, CHANNEL_ID_CRASHES)
            .setContentTitle("App crashed")
            .setContentText("Tap to see more info")
            .setSmallIcon(R.drawable.ic_baseline_bug_report_24)
            .setContentIntent(
                PendingIntent.getActivity(
                    this@WhatTheStackService,
                    1256,
                    createActivityIntent(crashBundle),
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        PendingIntent.FLAG_IMMUTABLE
                    } else {
                        0
                    }
                ),
            ).build()

        notificationManager.notify(2373489, notification)
    }
    /**
     * [Handler] that runs on the main thread to handle incoming processed uncaught
     * exceptions from [WhatTheStackExceptionHandler]
     *
     * We need to lazily initialize it because [getApplicationContext] returns null right
     * after the service is created.
     */
    private val handler by lazy { WhatTheStackHandler(applicationContext, WeakReference(this)) }

    /**
     * Runs when [WhatTheStackInitializer] calls [Context.bindService] to create a connection
     * to this service.
     *
     * It creates a [Messenger] that can be used to communicate with its [handler],
     * and returns its [IBinder].
     */
    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        val messenger = Messenger(handler)
        return messenger.binder
    }

    override fun onUnbind(intent: Intent?): Boolean {
        val value = super.onUnbind(intent)
        if (finishedWaitingForFile) {
            stopSelf()
        }
        stopAfterWaitingForFile = true
        return value
    }

    companion object {
        fun getStartupCrashFile(context: Context): File {
            return File(context.cacheDir, "lastCrash.bin")
        }
    }
}

/**
 * A [Handler] that runs on the main thread of the service process to process
 * incoming uncaught exception messages.
 */
@ServiceProcess
private class WhatTheStackHandler(
    private val applicationContext: Context,
    private val service: WeakReference<WhatTheStackService>
) : Handler(Looper.getMainLooper()) {

    override fun handleMessage(msg: Message) {
        WhatTheStackService.getStartupCrashFile(applicationContext).delete()

        val data = msg.data
        applicationContext.startActivity(applicationContext.createActivityIntent(data))

        service.get()?.stopSelf()
    }
}

private fun Context.createActivityIntent(data: Bundle): Intent {

    return Intent()
        .apply {
            setClass(this@createActivityIntent, WhatTheStackActivity::class.java)
            putExtras(data)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NEW_DOCUMENT
        }
}

private const val CHANNEL_ID_CRASHES = "CHANNEL_CRASHES"
