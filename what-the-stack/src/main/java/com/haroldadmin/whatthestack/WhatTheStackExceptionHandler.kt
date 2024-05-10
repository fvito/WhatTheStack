package com.haroldadmin.whatthestack

import android.content.Context
import android.os.Message
import android.os.Parcel
import androidx.core.os.bundleOf


/**
 * A [Thread.UncaughtExceptionHandler] which is meant to be used as a default exception handler on
 * the application.
 *
 * It runs in the host app's process to:
 * 1. Process any exception it catches and forward the result in a [Message] to [WhatTheStackService]
 * 2. Call the default exception handler it replaced, if any
 * 3. Kill the app process if there was no previous default exception handler
 */
@HostAppProcess
internal class WhatTheStackExceptionHandler(
    private val context: Context,
    private val messengerHolder: MessengerHolder,
    private val defaultHandler: Thread.UncaughtExceptionHandler?,
) : Thread.UncaughtExceptionHandler {
    override fun uncaughtException(t: Thread, e: Throwable) {
        e.printStackTrace()
        val serviceMessenger = messengerHolder.serviceMessenger

        val exceptionData = e.process().let {
            bundleOf(
                KEY_EXCEPTION_TYPE to it.type,
                KEY_EXCEPTION_CAUSE to it.cause,
                KEY_EXCEPTION_MESSAGE to it.message,
                KEY_EXCEPTION_STACKTRACE to it.stacktrace
            )
        }

        if (serviceMessenger != null) {
            serviceMessenger.send(
                Message().apply {
                    data = exceptionData
                }
            )
        } else {
            // Service has not started yet. Wait a second
            Thread.sleep(1000)

            val parcel = Parcel.obtain()
            val bundleBytes = try {
                exceptionData.writeToParcel(parcel, 0)
                parcel.marshall()
            } finally {
                parcel.recycle()
            }

            WhatTheStackService.getStartupCrashFile(context).writeBytes(bundleBytes)
        }

        defaultHandler?.uncaughtException(t, e)
    }
}
