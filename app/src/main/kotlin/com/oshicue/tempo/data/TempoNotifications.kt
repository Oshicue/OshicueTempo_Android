package com.oshicue.tempo.data

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.oshicue.tempo.MainActivity
import com.oshicue.tempo.R
import com.oshicue.tempo.core.CountdownRound
import com.oshicue.tempo.i18n.L10n
import com.oshicue.tempo.i18n.LocalizedMessage

/** Tracks whether a main activity is visible so alarms fire silently while the app is open. */
object TempoVisibility {
    enum class Mode { Visible, Paused, Stopped }

    @Volatile
    var mode: Mode = Mode.Stopped

    val isVisible: Boolean get() = mode == Mode.Visible
}

/**
 * Schedules exact alarms that post a per-member notification when a round ends,
 * mirroring the iOS local notification behavior. Alarm times are based on the
 * elapsed realtime clock, so wall-clock changes never shift a running timer.
 */
class TempoNotifications(private val context: Context) {
    enum class AuthorizationStatus { NotDetermined, Authorized, Denied }

    var authorizationStatus by mutableStateOf(AuthorizationStatus.NotDetermined)
        private set
    var error by mutableStateOf<LocalizedMessage?>(null)

    val isAuthorized: Boolean get() = authorizationStatus == AuthorizationStatus.Authorized

    val authorizationDescription: String
        get() = when (authorizationStatus) {
            AuthorizationStatus.Authorized -> L10n.text("notifications.authorized")
            AuthorizationStatus.Denied -> L10n.text("notifications.denied")
            AuthorizationStatus.NotDetermined -> L10n.text("notifications.notDetermined")
        }

    private val preferences =
        context.getSharedPreferences("oshicue-tempo-notifications", Context.MODE_PRIVATE)

    fun refreshAuthorization() {
        val manager = NotificationManagerCompat.from(context)
        val enabled = manager.areNotificationsEnabled()
        val permissionMissing = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        authorizationStatus = when {
            enabled && !permissionMissing -> AuthorizationStatus.Authorized
            !preferences.getBoolean(KEY_PROMPTED, false) -> AuthorizationStatus.NotDetermined
            else -> AuthorizationStatus.Denied
        }
    }

    /** Called right before the system permission dialog is shown. */
    fun markPromptRequested() {
        preferences.edit { putBoolean(KEY_PROMPTED, true) }
    }

    fun onPermissionResult(granted: Boolean) {
        markPromptRequested()
        refreshAuthorization()
        if (!granted) authorizationStatus = AuthorizationStatus.Denied
    }

    fun schedule(round: CountdownRound, memberName: String, soundID: String?) {
        val deadline = round.deadline ?: return
        val delay = deadline - System.currentTimeMillis()
        if (delay <= 0) return
        val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendingIntent = alarmIntent(round.id, memberName, soundID)
        manager.cancel(pendingIntent)
        val triggerAt = SystemClock.elapsedRealtime() + delay
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || manager.canScheduleExactAlarms()) {
                manager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pendingIntent)
            } else {
                // Exact alarms were revoked; the alert still fires, just less precisely.
                manager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pendingIntent)
            }
        } catch (_: SecurityException) {
            try {
                manager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pendingIntent)
            } catch (_: Exception) {
                error = LocalizedMessage.of("notifications.scheduleFailed")
            }
        }
    }

    fun cancel(roundID: String) {
        val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        manager.cancel(alarmIntent(roundID, "", null))
        NotificationManagerCompat.from(context).cancel(notificationId(roundID))
    }

    fun cancelAll() {
        NotificationManagerCompat.from(context).cancelAll()
    }

    /** Posts the finished-round notification; shared by the receiver and catch-up paths. */
    fun post(roundID: String, memberName: String, soundID: String?, silent: Boolean) {
        val audio = TempoAudio(context)
        val manager = NotificationManagerCompat.from(context)
        createChannels(audio)
        val channelId = channelId(soundID)
        val launch = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val contentIntent = PendingIntent.getActivity(
            context, notificationId(roundID), launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(L10n.format("notifications.roundFinishedTitle", memberName))
            .setContentText(L10n.text("notifications.roundFinishedBody"))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setSilent(silent)
            .build()
        try {
            manager.notify(notificationId(roundID), notification)
        } catch (_: SecurityException) {
            error = LocalizedMessage.of("notifications.scheduleFailed")
        }
    }

    /** Registers a channel per alert sound; channels are immutable once created. */
    fun createChannels(audio: TempoAudio) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val group = NotificationChannelGroup(CHANNEL_GROUP, L10n.text("notifications.channel.group"))
        manager.createNotificationChannelGroup(group)
        val soundIDs = (audio.sounds.map { it.id } + listOf("chime", "pulse", "bell", "arcade")).distinct()
        for (soundID in soundIDs) {
            val id = channelId(soundID)
            if (manager.getNotificationChannel(id) != null) continue
            val channel = NotificationChannel(
                id,
                audio.sounds.firstOrNull { it.id == soundID }?.name ?: soundID,
                NotificationManager.IMPORTANCE_HIGH
            )
            channel.group = CHANNEL_GROUP
            channel.description = L10n.text("notifications.channel.description")
            val uri = audio.notificationSoundUri(soundID)
            if (uri != null) {
                val attributes = android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION_EVENT)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                channel.setSound(uri, attributes)
            }
            manager.createNotificationChannel(channel)
        }
        val defaultId = channelId(null)
        if (manager.getNotificationChannel(defaultId) == null) {
            val channel = NotificationChannel(
                defaultId,
                L10n.text("notifications.channel.defaultName"),
                NotificationManager.IMPORTANCE_HIGH
            )
            channel.group = CHANNEL_GROUP
            channel.description = L10n.text("notifications.channel.description")
            manager.createNotificationChannel(channel)
        }
    }

    fun channelId(soundID: String?): String = "tempo-${soundID ?: "default"}"

    fun notificationId(roundID: String): Int = roundID.hashCode() and 0x7FFFFFFF

    private fun alarmIntent(roundID: String, memberName: String, soundID: String?): PendingIntent {
        val intent = Intent(context, TempoAlarmReceiver::class.java).apply {
            action = TempoAlarmReceiver.ACTION_ROUND_FINISHED
            putExtra(TempoAlarmReceiver.EXTRA_ROUND_ID, roundID)
            putExtra(TempoAlarmReceiver.EXTRA_MEMBER_NAME, memberName)
            putExtra(TempoAlarmReceiver.EXTRA_SOUND_ID, soundID)
        }
        return PendingIntent.getBroadcast(
            context, notificationId(roundID), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        const val KEY_PROMPTED = "notificationPrompted"
        const val CHANNEL_GROUP = "oshicue-tempo"
    }
}
