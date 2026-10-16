package com.oshicue.tempo.ui.sounds

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oshicue.tempo.core.TempoError
import com.oshicue.tempo.core.TempoException
import com.oshicue.tempo.data.TempoAudio
import com.oshicue.tempo.data.TempoImportException
import com.oshicue.tempo.data.TempoNotifications
import com.oshicue.tempo.i18n.L10n
import com.oshicue.tempo.ui.common.TempoAlertDialog
import com.oshicue.tempo.ui.common.TempoRowCard
import com.oshicue.tempo.ui.common.TempoSectionHeader
import com.oshicue.tempo.ui.theme.tempoPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SoundSettingsSheet(
    audio: TempoAudio,
    notifications: TempoNotifications,
    onDismiss: () -> Unit
) {
    val palette = tempoPalette()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = palette.background
    ) {
        SoundSettingsContent(audio = audio, notifications = notifications, onDone = onDismiss)
    }
}

@Composable
private fun SoundSettingsContent(
    audio: TempoAudio,
    notifications: TempoNotifications,
    onDone: () -> Unit
) {
    val palette = tempoPalette()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var importing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var importedName by remember { mutableStateOf<String?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        notifications.onPermissionResult(granted)
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        importing = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    Result.success(audio.importSound(uri))
                } catch (error: Exception) {
                    Result.failure(error)
                }
            }
            importing = false
            result.fold(
                onSuccess = { id -> importedName = audio.title(id) },
                onFailure = { error ->
                    errorMessage = when (error) {
                        is TempoImportException -> L10n.text(error.messageKey)
                        is TempoException -> error.error.message
                        else -> L10n.text("audio.import.unreadable")
                    }
                }
            )
        }
    }

    DisposableEffect(Unit) {
        onDispose { audio.stopPreview() }
    }
    LaunchedEffect(Unit) { notifications.refreshAuthorization() }

    Column(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.96f)) {
        Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
            Text(
                L10n.text("sounds.title"),
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = palette.ink,
                modifier = Modifier.align(Alignment.Center)
            )
            TextButton(onClick = onDone, modifier = Modifier.align(Alignment.CenterEnd)) {
                Text(L10n.text("common.done"), color = palette.pink)
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                TempoRowCard {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(
                                Icons.Outlined.GraphicEq,
                                contentDescription = null,
                                tint = palette.ink,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                L10n.text("sounds.introTitle"),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = palette.ink
                            )
                        }
                        Text(L10n.text("sounds.intro"), fontSize = 14.sp, color = palette.muted)
                    }
                }
            }

            item { TempoSectionHeader(L10n.text("sounds.builtIn"), modifier = Modifier.padding(vertical = 4.dp)) }
            items(audio.builtInSounds, key = { it.id }) { sound ->
                SoundRow(
                    name = sound.name,
                    subtitle = sound.subtitle,
                    onPreview = { audio.preview(sound.id) },
                    previewLabel = L10n.format("sounds.previewAccessibility", sound.name)
                )
            }

            item {
                TempoSectionHeader(
                    if (audio.importedSounds.isEmpty()) {
                        L10n.text("sounds.myRecordings")
                    } else {
                        L10n.format("sounds.myRecordingsCount", audio.importedSounds.size)
                    },
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
            items(audio.importedSounds, key = { it.id }) { sound ->
                SoundRow(
                    name = sound.name,
                    subtitle = sound.subtitle,
                    onPreview = { audio.preview(sound.id) },
                    previewLabel = L10n.format("sounds.previewAccessibility", sound.name)
                )
            }
            item {
                TempoRowCard {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !importing) {
                                importLauncher.launch(arrayOf("audio/*"))
                            }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (importing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = palette.pink,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                Icons.Outlined.FileDownload,
                                contentDescription = null,
                                tint = palette.pink,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Text(
                            L10n.text(if (importing) "sounds.importing" else "sounds.import"),
                            color = palette.pink,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                Text(
                    L10n.text("sounds.importHelp"),
                    fontSize = 12.sp,
                    color = palette.muted,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
                )
            }

            item {
                TempoSectionHeader(
                    L10n.text("sounds.alertsSection"),
                    modifier = Modifier.padding(vertical = 4.dp)
                )
                TempoRowCard(modifier = Modifier.padding(top = 6.dp)) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                if (notifications.isAuthorized) Icons.Outlined.NotificationsActive else Icons.Outlined.Notifications,
                                contentDescription = null,
                                tint = if (notifications.isAuthorized) palette.pink else palette.muted,
                                modifier = Modifier.size(20.dp)
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(L10n.text("sounds.backgroundAlerts"), color = palette.ink, fontSize = 15.sp)
                                Text(
                                    notifications.authorizationDescription,
                                    fontSize = 12.sp,
                                    color = palette.muted
                                )
                            }
                        }
                        if (notifications.authorizationStatus == TempoNotifications.AuthorizationStatus.NotDetermined) {
                            Text(
                                L10n.text("sounds.allowNotifications"),
                                color = palette.pink,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier
                                    .clickable {
                                        notifications.markPromptRequested()
                                        permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                    .padding(vertical = 4.dp)
                            )
                        } else {
                            Text(
                                L10n.text("sounds.openNotificationSettings"),
                                color = palette.pink,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier
                                    .clickable {
                                        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        context.startActivity(intent)
                                    }
                                    .padding(vertical = 4.dp)
                            )
                        }
                        Text(L10n.text("sounds.alertsHelp"), fontSize = 12.sp, color = palette.muted)
                    }
                }
            }
        }
    }

    errorMessage?.let { text ->
        TempoAlertDialog(
            title = L10n.text("sounds.operationFailed"),
            message = text,
            onDismiss = { errorMessage = null }
        )
    }
    importedName?.let { name ->
        TempoAlertDialog(
            title = L10n.text("sounds.importedTitle"),
            message = L10n.format("sounds.importedMessage", name),
            onDismiss = { importedName = null }
        )
    }
}

@Composable
private fun SoundRow(
    name: String,
    subtitle: String,
    onPreview: () -> Unit,
    previewLabel: String
) {
    val palette = tempoPalette()
    TempoRowCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                Icons.Outlined.GraphicEq,
                contentDescription = null,
                tint = palette.pink,
                modifier = Modifier.size(20.dp)
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = palette.ink)
                Text(subtitle, fontSize = 12.sp, color = palette.muted)
            }
            IconButton(onClick = onPreview) {
                Icon(
                    Icons.Filled.PlayCircle,
                    contentDescription = previewLabel,
                    tint = palette.pink,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}
