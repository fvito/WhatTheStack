package com.haroldadmin.whatthestack

import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.ui.graphics.toArgb
import androidx.core.view.WindowCompat
import com.haroldadmin.whatthestack.ui.pages.ExceptionPage
import com.haroldadmin.whatthestack.ui.theme.SystemBarsColor
import com.haroldadmin.whatthestack.ui.theme.WhatTheStackTheme

/**
 * An Activity which displays various pieces of information regarding the exception which
 * occurred.
 */
internal class WhatTheStackActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val type = intent.getStringExtra(KEY_EXCEPTION_TYPE) ?: ""
        val message = intent.getStringExtra(KEY_EXCEPTION_MESSAGE) ?: ""
        val stackTrace = intent.getStringExtra(KEY_EXCEPTION_STACKTRACE) ?: ""

        val statusBarColor = SystemBarsColor.toArgb()

        setContent {

            enableEdgeToEdge(
                statusBarStyle = SystemBarStyle.auto(statusBarColor, statusBarColor)
            )

            WhatTheStackTheme {
                ExceptionPage(
                    type = type,
                    message = message,
                    stackTrace = stackTrace,
                )
            }
        }
    }
}
