package com.controlqr.acceso

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import com.controlqr.acceso.ui.ControlQrRoot
import com.controlqr.acceso.ui.theme.ControlQrTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as ControlQrApp).container

        setContent {
            CompositionLocalProvider(LocalAppContainer provides container) {
                ControlQrTheme {
                    ControlQrRoot()
                }
            }
        }
    }
}
