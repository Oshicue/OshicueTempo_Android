package com.oshicue.tempo

import android.app.Application
import com.oshicue.tempo.data.TempoAudio
import com.oshicue.tempo.data.TempoFiles
import com.oshicue.tempo.data.TempoNotifications
import com.oshicue.tempo.data.TempoStore
import com.oshicue.tempo.i18n.L10n
import java.io.File

class TempoApplication : Application() {
    lateinit var store: TempoStore
        private set
    lateinit var audio: TempoAudio
        private set
    lateinit var notifications: TempoNotifications
        private set

    override fun onCreate() {
        super.onCreate()
        L10n.attach(this)
        store = TempoStore(File(TempoFiles.directory(this), "state.json"))
        audio = TempoAudio(this)
        notifications = TempoNotifications(this)
    }
}
