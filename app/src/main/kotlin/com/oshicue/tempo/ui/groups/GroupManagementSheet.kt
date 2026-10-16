package com.oshicue.tempo.ui.groups

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oshicue.tempo.core.CountdownPhase
import com.oshicue.tempo.core.TempoError
import com.oshicue.tempo.core.TempoException
import com.oshicue.tempo.core.TempoGroup
import com.oshicue.tempo.core.TempoMember
import com.oshicue.tempo.core.newID
import com.oshicue.tempo.data.TempoAudio
import com.oshicue.tempo.data.TempoStore
import com.oshicue.tempo.i18n.L10n
import com.oshicue.tempo.ui.common.ColorSwatch
import com.oshicue.tempo.ui.common.TempoAlertDialog
import com.oshicue.tempo.ui.common.TempoConfirmDialog
import com.oshicue.tempo.ui.common.TempoRowCard
import com.oshicue.tempo.ui.common.TempoSectionHeader
import com.oshicue.tempo.ui.theme.hexColor
import com.oshicue.tempo.ui.theme.toHexString
import com.oshicue.tempo.ui.theme.tempoPalette

private sealed interface EditorRoute {
    data object New : EditorRoute
    data class Existing(val id: String) : EditorRoute
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupManagementSheet(
    store: TempoStore,
    audio: TempoAudio,
    onDismiss: () -> Unit
) {
    val palette = tempoPalette()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var editor by remember { mutableStateOf<EditorRoute?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = palette.background
    ) {
        // Back inside the editor returns to the list, like the iOS navigation stack.
        androidx.activity.compose.BackHandler(enabled = editor != null) { editor = null }
        val route = editor
        if (route == null) {
            GroupListContent(
                store = store,
                onDone = onDismiss,
                onEdit = { editor = it }
            )
        } else {
            GroupEditorContent(
                store = store,
                audio = audio,
                route = route,
                onClose = { editor = null },
                onSaved = { editor = null }
            )
        }
    }
}

@Composable
private fun GroupListContent(
    store: TempoStore,
    onDone: () -> Unit,
    onEdit: (EditorRoute) -> Unit
) {
    val palette = tempoPalette()
    var groupToDelete by remember { mutableStateOf<TempoGroup?>(null) }

    Column(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.96f)) {
        Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
            Text(
                L10n.text("groups.title"),
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = palette.ink,
                modifier = Modifier.align(Alignment.Center)
            )
            TextButton(
                onClick = onDone,
                modifier = Modifier.align(Alignment.CenterEnd)
            ) {
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
                    Text(
                        L10n.text("groups.intro"),
                        fontSize = 14.sp,
                        color = palette.muted,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
            item {
                TempoSectionHeader(
                    L10n.format("groups.count", store.groups.size),
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
                )
            }
            items(store.groups, key = { it.id }) { group ->
                GroupRow(
                    group = group,
                    selected = store.selectedGroupID == group.id,
                    onSelect = { store.selectedGroupID = group.id },
                    onEdit = { onEdit(EditorRoute.Existing(group.id)) },
                    onDelete = { groupToDelete = group }
                )
            }
            item {
                TempoRowCard {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onEdit(EditorRoute.New) }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Outlined.Add, contentDescription = null, tint = palette.pink, modifier = Modifier.size(20.dp))
                        Text(L10n.text("groups.add"), color = palette.pink, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }

    groupToDelete?.let { group ->
        val activeCount = group.members.count { member ->
            val round = store.round(forMember = member.id) ?: return@count false
            val phase = round.phase(System.currentTimeMillis())
            phase == CountdownPhase.Running || phase == CountdownPhase.Paused
        }
        TempoConfirmDialog(
            title = L10n.format("groups.deleteNamedTitle", group.name),
            message = if (activeCount > 0) {
                L10n.format("groups.deleteActiveMessage", activeCount)
            } else {
                L10n.text("groups.deleteMessage")
            },
            confirmLabel = L10n.text("groups.deleteAll"),
            onConfirm = {
                try {
                    store.deleteGroup(group.id)
                } catch (_: Exception) {
                    // The store surfaces the failure through message.
                }
                groupToDelete = null
            },
            onDismiss = { groupToDelete = null }
        )
    }
}

@Composable
private fun GroupRow(
    group: TempoGroup,
    selected: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val palette = tempoPalette()
    var menuOpen by remember { mutableStateOf(false) }
    TempoRowCard(onClick = onEdit) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    group.name,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    group.members.take(6).forEach { member ->
                        ColorSwatch(color = hexColor(member.colorHex), size = 8)
                    }
                    Spacer(Modifier.width(2.dp))
                    Text(
                        L10n.format("groups.memberSummary", group.members.size, group.secondsPerTicket),
                        fontSize = 12.sp,
                        color = palette.muted,
                        maxLines = 1
                    )
                }
            }
            IconButton(onClick = onSelect) {
                Icon(
                    if (selected) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                    contentDescription = if (selected) {
                        L10n.format("groups.currentAccessibility", group.name)
                    } else {
                        L10n.format("groups.selectAccessibility", group.name)
                    },
                    tint = if (selected) palette.pink else palette.muted,
                    modifier = Modifier.size(22.dp)
                )
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(
                        Icons.Outlined.MoreVert,
                        contentDescription = L10n.text("groups.edit"),
                        tint = palette.muted,
                        modifier = Modifier.size(20.dp)
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(L10n.text("groups.edit")) },
                        onClick = {
                            menuOpen = false
                            onEdit()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(L10n.text("groups.delete"), color = palette.pink) },
                        onClick = {
                            menuOpen = false
                            onDelete()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun GroupEditorContent(
    store: TempoStore,
    audio: TempoAudio,
    route: EditorRoute,
    onClose: () -> Unit,
    onSaved: () -> Unit
) {
    val palette = tempoPalette()
    val keyboard = LocalSoftwareKeyboardController.current
    val existing = (route as? EditorRoute.Existing)?.let { store.group(it.id) }
    val groupID = existing?.id ?: newID()
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var secondsText by remember { mutableStateOf((existing?.secondsPerTicket ?: 60).toString()) }
    val members = remember {
        (existing?.members?.map { MemberDraft(it.id, it.name, it.colorHex, it.soundID) }
            ?: listOf(MemberDraft(newID(), "", "FF3D6E", "chime"))).toMutableStateList()
    }
    var memberToRemove by remember { mutableStateOf<MemberDraft?>(null) }
    var pickerMember by remember { mutableStateOf<MemberDraft?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val seconds = secondsText.toIntOrNull()?.takeIf { it in 1..3600 }
    val canSave = name.trim().isNotEmpty() && seconds != null && members.isNotEmpty() &&
        members.all { it.name.trim().isNotEmpty() }

    DisposableEffect(Unit) {
        onDispose { audio.stopPreview() }
    }

    Column(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.96f).imePadding()) {
        Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
            TextButton(onClick = onClose, modifier = Modifier.align(Alignment.CenterStart)) {
                Text(L10n.text("common.cancel"), color = palette.muted)
            }
            Text(
                L10n.text(if (existing == null) "groupEditor.addTitle" else "groupEditor.editTitle"),
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = palette.ink,
                modifier = Modifier.align(Alignment.Center)
            )
            TextButton(
                onClick = {
                    keyboard?.hide()
                    val group = TempoGroup(
                        id = groupID,
                        name = name.trim(),
                        secondsPerTicket = seconds ?: return@TextButton,
                        members = members.map {
                            TempoMember(it.id, it.name.trim(), it.colorHex, it.soundID)
                        }
                    )
                    try {
                        store.saveGroup(group)
                        onSaved()
                    } catch (error: Exception) {
                        errorMessage = when (error) {
                            is TempoException -> error.error.message
                            else -> store.message?.text() ?: TempoError.CorruptSnapshot.message
                        }
                    }
                },
                enabled = canSave,
                modifier = Modifier.align(Alignment.CenterEnd)
            ) {
                Text(
                    L10n.text("common.save"),
                    color = if (canSave) palette.pink else palette.muted,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                TempoSectionHeader(L10n.text("groupEditor.groupSection"))
                TempoRowCard(modifier = Modifier.padding(top = 6.dp)) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        LabeledTextField(
                            label = L10n.text("groupEditor.namePlaceholder"),
                            value = name,
                            onValueChange = { name = it },
                            keyboardType = KeyboardType.Text
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(L10n.text("groupEditor.secondsPerTicket"), fontSize = 15.sp, color = palette.ink)
                            Spacer(Modifier.weight(1f))
                            BasicTextField(
                                value = secondsText,
                                onValueChange = { value ->
                                    secondsText = value.filter { it in '0'..'9' }.take(4)
                                },
                                textStyle = androidx.compose.ui.text.TextStyle(
                                    fontSize = 16.sp,
                                    color = palette.ink,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.End
                                ),
                                cursorBrush = SolidColor(palette.pink),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.width(64.dp)
                            )
                            Text("  " + L10n.text("groupEditor.secondsUnit"), fontSize = 15.sp, color = palette.muted)
                        }
                        if (seconds != null) {
                            Row {
                                Text(
                                    L10n.format("groupEditor.twoTickets", seconds * 2),
                                    fontSize = 12.sp,
                                    color = palette.muted
                                )
                                Spacer(Modifier.weight(1f))
                                Text(
                                    L10n.format("groupEditor.threeTickets", seconds * 3),
                                    fontSize = 12.sp,
                                    color = palette.muted
                                )
                            }
                        }
                        Text(
                            L10n.text("groupEditor.durationHelp"),
                            fontSize = 12.sp,
                            color = palette.muted
                        )
                    }
                }
            }

            itemsIndexed(members, key = { _, member -> member.id }) { index, member ->
                val active = hasActiveRound(store, member.id)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    TempoSectionHeader(L10n.format("groupEditor.memberNumber", "%02d".format(index + 1)))
                    TempoRowCard {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            LabeledTextField(
                                label = L10n.text("groupEditor.memberNamePlaceholder"),
                                value = member.name,
                                onValueChange = { member.name = it },
                                keyboardType = KeyboardType.Text
                            )
                            ColorField(
                                colorHex = member.colorHex,
                                onColorChange = { member.colorHex = it }
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(L10n.text("groupEditor.memberSound"), fontSize = 15.sp, color = palette.ink)
                                Spacer(Modifier.weight(1f))
                                Text(
                                    audio.title(member.soundID),
                                    fontSize = 15.sp,
                                    color = palette.pink,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.clickable { pickerMember = member }
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    audio.sounds.firstOrNull { it.id == member.soundID }?.subtitle
                                        ?: L10n.text("groupEditor.customSound"),
                                    fontSize = 12.sp,
                                    color = palette.muted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(Modifier.width(8.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.clickable { audio.preview(member.soundID) }
                                ) {
                                    Icon(
                                        Icons.Filled.PlayArrow,
                                        contentDescription = L10n.format(
                                            "groupEditor.previewAccessibility", audio.title(member.soundID)
                                        ),
                                        tint = palette.pink,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(L10n.text("common.preview"), fontSize = 13.sp, color = palette.pink)
                                }
                            }
                            if (active) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        Icons.Outlined.Timer,
                                        contentDescription = null,
                                        tint = palette.muted,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        L10n.text("groupEditor.activeRound"),
                                        fontSize = 12.sp,
                                        color = palette.muted
                                    )
                                }
                            }
                            if (members.size > 1) {
                                Text(
                                    L10n.text("groupEditor.deleteMember"),
                                    fontSize = 14.sp,
                                    color = palette.pink,
                                    modifier = Modifier.clickable {
                                        if (active) memberToRemove = member else members.remove(member)
                                    }
                                )
                            }
                        }
                    }
                }
            }

            item {
                TempoRowCard {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val paletteColors = listOf(
                                    "FF3D6E", "A78BFA", "59C9FF", "FFCB52", "70D6B1", "FF934F"
                                )
                                members.add(
                                    MemberDraft(
                                        id = newID(),
                                        name = "",
                                        colorHex = paletteColors[members.size % paletteColors.size],
                                        soundID = "chime"
                                    )
                                )
                            }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Outlined.PersonAdd, contentDescription = null, tint = palette.pink, modifier = Modifier.size(20.dp))
                        Text(L10n.text("groupEditor.addMember"), color = palette.pink, fontWeight = FontWeight.SemiBold)
                    }
                }
                Text(
                    L10n.text("groupEditor.membersHelp"),
                    fontSize = 12.sp,
                    color = palette.muted,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
                )
            }
        }
    }

    memberToRemove?.let { member ->
        TempoConfirmDialog(
            title = L10n.text("groupEditor.removeMemberTitle"),
            message = L10n.text("groupEditor.removeMemberMessage"),
            confirmLabel = L10n.text("groupEditor.removeMember"),
            onConfirm = {
                members.removeAll { it.id == member.id }
                memberToRemove = null
            },
            onDismiss = { memberToRemove = null }
        )
    }

    pickerMember?.let { member ->
        SoundPickerDialog(
            audio = audio,
            selected = member.soundID,
            onSelect = {
                member.soundID = it
                pickerMember = null
            },
            onDismiss = { pickerMember = null }
        )
    }

    errorMessage?.let { text ->
        TempoAlertDialog(
            title = L10n.text("groups.operationFailed"),
            message = text,
            onDismiss = { errorMessage = null }
        )
    }
}

private class MemberDraft(
    val id: String,
    name: String,
    colorHex: String,
    soundID: String
) {
    var name by mutableStateOf(name)
    var colorHex by mutableStateOf(colorHex)
    var soundID by mutableStateOf(soundID)
}

@Composable
private fun LabeledTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType
) {
    val palette = tempoPalette()
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, fontSize = 12.sp, color = palette.muted)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 16.sp, color = palette.ink),
            cursorBrush = SolidColor(palette.pink),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun ColorField(colorHex: String, onColorChange: (String) -> Unit) {
    val palette = tempoPalette()
    var hexText by remember { mutableStateOf(colorHex) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(L10n.text("groupEditor.memberColor"), fontSize = 15.sp, color = palette.ink)
            Spacer(Modifier.weight(1f))
            ColorSwatch(color = hexColor(colorHex), size = 22)
            BasicTextField(
                value = hexText,
                onValueChange = { value ->
                    val filtered = value.uppercase().filter { it in '0'..'9' || it in 'A'..'F' }.take(6)
                    hexText = filtered
                    if (filtered.length == 6) onColorChange(filtered)
                },
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontSize = 14.sp, color = palette.muted, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                ),
                cursorBrush = SolidColor(palette.pink),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                singleLine = true,
                modifier = Modifier.width(72.dp)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val presets = listOf("FF3D6E", "A78BFA", "59C9FF", "FFCB52", "70D6B1", "FF934F", "FF688D", "79B8FF")
            presets.forEach { preset ->
                val selected = preset.equals(colorHex, ignoreCase = true)
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(hexColor(preset), CircleShape)
                        .border(
                            width = if (selected) 2.dp else 0.75.dp,
                            color = if (selected) palette.pink else palette.swatchOutline,
                            shape = CircleShape
                        )
                        .clickable {
                            hexText = preset
                            onColorChange(preset)
                        }
                )
            }
        }
    }
}

@Composable
private fun SoundPickerDialog(
    audio: TempoAudio,
    selected: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val palette = tempoPalette()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(L10n.text("groupEditor.memberSound"), fontWeight = FontWeight.SemiBold) },
        text = {
            Column {
                audio.sounds.forEach { sound ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(sound.id) }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = sound.id == selected, onClick = { onSelect(sound.id) })
                        Column(modifier = Modifier.weight(1f)) {
                            Text(sound.name, color = palette.ink)
                            Text(sound.subtitle, fontSize = 12.sp, color = palette.muted)
                        }
                        IconButton(onClick = { audio.preview(sound.id) }) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = palette.pink)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(L10n.text("common.cancel")) }
        }
    )
}

private fun hasActiveRound(store: TempoStore, memberID: String): Boolean {
    val round = store.round(forMember = memberID) ?: return false
    return when (round.phase(System.currentTimeMillis())) {
        CountdownPhase.Running, CountdownPhase.Paused -> true
        CountdownPhase.Idle, CountdownPhase.Finished -> false
    }
}
