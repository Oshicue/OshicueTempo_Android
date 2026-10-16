package com.oshicue.tempo

import android.Manifest
import android.os.Build
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.oshicue.tempo.core.CountdownPhase
import com.oshicue.tempo.data.TempoAudio
import com.oshicue.tempo.data.TempoNotifications
import com.oshicue.tempo.data.TempoStore
import com.oshicue.tempo.data.TempoVisibility
import com.oshicue.tempo.i18n.L10n
import com.oshicue.tempo.ui.board.TimerBoardScreen
import com.oshicue.tempo.ui.common.TempoAlertDialog
import com.oshicue.tempo.ui.theme.tempoPalette
import kotlinx.coroutines.delay

/** Cross-surface coordination state, mirroring the iOS app-level scene handling. */
class TempoCoordinator {
    var suppressCatchUpAudio = false
    val deferredAlerts = LinkedHashMap<String, String>()
}

@Composable
fun TempoRoot(store: TempoStore, audio: TempoAudio, notifications: TempoNotifications) {
    val palette = tempoPalette()
    val coordinator = remember { TempoCoordinator() }
    val haptics = LocalHapticFeedback.current
    val context = LocalContext.current
    val activity = context as? android.app.Activity
    val lifecycleOwner = LocalLifecycleOwner.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var permissionRequest by remember { mutableStateOf(false) }
    var scheduleTick by remember { mutableIntStateOf(0) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        notifications.onPermissionResult(granted)
        scheduleTick += 1
    }

    fun scheduleRunningRounds() {
        val currentTime = System.currentTimeMillis()
        for (round in store.rounds.values) {
            if (round.phase(currentTime) != CountdownPhase.Running) continue
            val member = store.member(round.memberID) ?: continue
            val deadline = round.deadline ?: continue
            if (store.round(forMember = round.memberID)?.deadline != deadline) continue
            notifications.schedule(round, member.name, round.soundID ?: member.soundID)
        }
    }

    fun catchUpAndSchedule() {
        coordinator.suppressCatchUpAudio = true
        store.tick()
        coordinator.suppressCatchUpAudio = false
        val resolved = mutableListOf<String>()
        coordinator.deferredAlerts.forEach { (roundID, soundID) ->
            val round = store.rounds.values.firstOrNull { it.id == roundID }
            if (round != null && round.phase(System.currentTimeMillis()) == CountdownPhase.Finished) {
                notifications.cancel(roundID)
                audio.play(soundID)
                resolved += roundID
            }
        }
        resolved.forEach { coordinator.deferredAlerts.remove(it) }
        notifications.refreshAuthorization()
        notifications.createChannels(audio)
        if (notifications.isAuthorized) scheduleRunningRounds()
    }

    DisposableEffect(store) {
        store.onRoundStarted = { round, member ->
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            val deadline = round.deadline
            if (deadline != null) {
                notifications.refreshAuthorization()
                if (notifications.authorizationStatus == TempoNotifications.AuthorizationStatus.NotDetermined) {
                    permissionRequest = true
                } else if (notifications.isAuthorized) {
                    val current = store.round(forMember = member.id)
                    if (current?.id == round.id && current.phase(System.currentTimeMillis()) == CountdownPhase.Running &&
                        current.deadline == deadline
                    ) {
                        notifications.schedule(round, member.name, round.soundID ?: member.soundID)
                    }
                }
            }
        }
        store.onRoundCancelled = { roundID -> notifications.cancel(roundID) }
        store.onRoundFinished = { round, member ->
            when (TempoVisibility.mode) {
                TempoVisibility.Mode.Visible -> {
                    notifications.cancel(round.id)
                    if (!coordinator.suppressCatchUpAudio) {
                        audio.play(round.soundID ?: member.soundID)
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                }
                TempoVisibility.Mode.Paused -> {
                    // A permission sheet temporarily pauses the activity. Deliver its
                    // expired cue when resumed, while retaining any scheduled alert.
                    if (!coordinator.suppressCatchUpAudio) {
                        coordinator.deferredAlerts[round.id] = round.soundID ?: member.soundID
                    }
                }
                TempoVisibility.Mode.Stopped -> Unit
            }
        }
        onDispose { }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { TempoVisibility.mode = TempoVisibility.Mode.Visible }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { TempoVisibility.mode = TempoVisibility.Mode.Paused }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        TempoVisibility.mode = TempoVisibility.Mode.Stopped
        coordinator.deferredAlerts.clear()
    }
    LifecycleEventEffect(Lifecycle.Event.ON_START) { catchUpAndSchedule() }

    LaunchedEffect(Unit) { catchUpAndSchedule() }
    LaunchedEffect(scheduleTick) { if (scheduleTick > 0) scheduleRunningRounds() }
    LaunchedEffect(permissionRequest) {
        if (permissionRequest) {
            permissionRequest = false
            notifications.markPromptRequested()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                notifications.onPermissionResult(true)
            }
        }
    }
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                delay(100)
                val tick = System.currentTimeMillis()
                now = tick
                store.tick(tick)
            }
        }
    }
    val runningCount = store.runningCount
    LaunchedEffect(runningCount) {
        if (activity != null) {
            if (runningCount > 0) {
                activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(palette.background)) {
        TimerBoardScreen(
            store = store,
            audio = audio,
            notifications = notifications,
            coordinator = coordinator,
            now = now
        )
    }

    val activeError = store.message ?: audio.error ?: notifications.error
    activeError?.let { message ->
        TempoAlertDialog(
            title = L10n.text("board.operationFailed"),
            message = message.text(),
            onDismiss = {
                store.message = null
                audio.error = null
                notifications.error = null
            }
        )
    }
}
