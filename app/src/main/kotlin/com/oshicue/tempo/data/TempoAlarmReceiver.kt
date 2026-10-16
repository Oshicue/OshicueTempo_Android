package com.oshicue.tempo.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.oshicue.tempo.core.TempoJson
import java.io.File

/**
 * Fires when a scheduled round reaches its deadline. The stored snapshot is the
 * source of truth: stale alarms whose round was cancelled, paused or finished are
 * ignored, exactly like the iOS app reconciles its pending notifications.
 */
class TempoAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_ROUND_FINISHED) return
        val roundID = intent.getStringExtra(EXTRA_ROUND_ID) ?: return
        val memberName = intent.getStringExtra(EXTRA_MEMBER_NAME) ?: return
        val soundID = intent.getStringExtra(EXTRA_SOUND_ID)

        val snapshotFile = File(File(context.filesDir, "OshicueTempo"), "state.json")
        val snapshot = try {
            TempoJson.decode(snapshotFile.readText())
        } catch (_: Exception) {
            null
        } ?: return
        val round = snapshot.rounds.firstOrNull { it.id == roundID } ?: return
        if (round.completedAt != null || round.pausedRemaining != null || round.deadline == null) return

        // While the app is open, the in-app ticker cancels this alarm and plays the
        // cue itself; posting silently here would double the alert.
        if (TempoVisibility.isVisible) return

        val notifications = TempoNotifications(context)
        notifications.post(roundID, memberName, soundID, silent = false)
    }

    companion object {
        const val ACTION_ROUND_FINISHED = "com.oshicue.tempo.action.ROUND_FINISHED"
        const val EXTRA_ROUND_ID = "roundID"
        const val EXTRA_MEMBER_NAME = "memberName"
        const val EXTRA_SOUND_ID = "soundID"
    }
}

/** Restores alarms after a reboot or an app update, which clears pending alarms. */
class TempoBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }
        val snapshotFile = File(File(context.filesDir, "OshicueTempo"), "state.json")
        val snapshot = try {
            TempoJson.decode(snapshotFile.readText())
        } catch (_: Exception) {
            null
        } ?: return
        val notifications = TempoNotifications(context)
        for (round in snapshot.rounds) {
            if (round.completedAt != null || round.pausedRemaining != null || round.deadline == null) continue
            val member = snapshot.groups.firstOrNull { it.id == round.groupID }
                ?.members?.firstOrNull { it.id == round.memberID } ?: continue
            notifications.schedule(round, member.name, round.soundID ?: member.soundID)
        }
    }
}
