package com.oshicue.tempo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.oshicue.tempo.i18n.L10n
import com.oshicue.tempo.ui.theme.OshicueTempoTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        L10n.attach(this)
        val app = application as TempoApplication
        setContent {
            OshicueTempoTheme {
                TempoRoot(
                    store = app.store,
                    audio = app.audio,
                    notifications = app.notifications
                )
            }
        }
    }
}
