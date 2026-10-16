package com.oshicue.tempo.ui.board

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oshicue.tempo.core.TempoError
import com.oshicue.tempo.core.TempoException
import com.oshicue.tempo.core.TempoGroup
import com.oshicue.tempo.core.TempoMember
import com.oshicue.tempo.data.TempoStorageException
import com.oshicue.tempo.data.TempoStore
import com.oshicue.tempo.i18n.L10n
import com.oshicue.tempo.ui.common.ColorSwatch
import com.oshicue.tempo.ui.common.TempoAlertDialog
import com.oshicue.tempo.ui.theme.hexColor
import com.oshicue.tempo.ui.theme.memberAccent
import com.oshicue.tempo.ui.theme.tempoDigits
import com.oshicue.tempo.ui.theme.tempoPalette
import com.oshicue.tempo.ui.theme.tempoTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TicketEntrySheet(
    store: TempoStore,
    member: TempoMember,
    group: TempoGroup,
    onDismiss: () -> Unit
) {
    val palette = tempoPalette()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = palette.background
    ) {
        TicketEntryContent(
            store = store,
            member = member,
            group = group,
            onDismiss = onDismiss
        )
    }
}

@Composable
private fun TicketEntryContent(
    store: TempoStore,
    member: TempoMember,
    group: TempoGroup,
    onDismiss: () -> Unit
) {
    val palette = tempoPalette()
    var tickets by rememberSaveable { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    val focusRequester = remember { FocusRequester() }
    val count = tickets.toIntOrNull()
    val valid = count != null && count in 1..999 && count * group.secondsPerTicket <= 86400

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.96f)
            .imePadding()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.CenterStart)
            ) {
                Text(L10n.text("common.cancel"), color = palette.pink)
            }
            Text(
                L10n.text("tickets.newRound"),
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = palette.ink,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ColorSwatch(color = hexColor(member.colorHex), size = 12)
                Text(member.name, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = palette.ink)
                Spacer(Modifier.weight(1f))
                Text(L10n.text("tickets.roundTickets"), fontSize = 14.sp, color = palette.muted)
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(palette.surface)
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    BasicTextField(
                        value = tickets,
                        onValueChange = { value ->
                            tickets = value.filter { it in '0'..'9' }.take(3)
                        },
                        textStyle = tempoDigits(60f, palette.ink),
                        cursorBrush = SolidColor(palette.pink),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().focusRequester(focusRequester)
                    )
                    if (tickets.isEmpty()) {
                        Text(
                            "0",
                            style = tempoDigits(60f, palette.muted.copy(alpha = 0.35f))
                        )
                    }
                }
                Text(
                    L10n.text("tickets.unit"),
                    fontSize = 22.sp,
                    color = palette.muted
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    L10n.format("tickets.formula", group.secondsPerTicket, count ?: 0),
                    fontSize = 14.sp,
                    color = palette.muted
                )
                Spacer(Modifier.weight(1f))
                Text(
                    tempoTime(((count ?: 0) * group.secondsPerTicket).toDouble()),
                    style = tempoDigits(
                        20f,
                        if (palette.dark) memberAccent(member.colorHex) else palette.ink
                    )
                )
            }

            Text(
                text = if ((count ?: 0) * group.secondsPerTicket > 86400) {
                    L10n.text("tickets.maximumDuration")
                } else {
                    L10n.text("tickets.hint")
                },
                fontSize = 12.sp,
                color = palette.muted
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (valid) palette.primaryFill else palette.controlSurface)
                    .clickable(enabled = valid) {
                        val value = count ?: return@clickable
                        try {
                            store.start(memberID = member.id, tickets = value)
                            onDismiss()
                        } catch (error: Exception) {
                            message = when (error) {
                                is TempoException -> error.error.message
                                is TempoStorageException -> store.message?.text()
                                else -> error.message ?: TempoError.CorruptSnapshot.message
                            }
                        }
                    },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = if (valid) palette.onPrimary else palette.muted,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    L10n.text("tickets.confirmStart"),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (valid) palette.onPrimary else palette.muted
                )
            }
        }
    }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    message?.let { text ->
        TempoAlertDialog(
            title = L10n.text("tickets.cannotStart"),
            message = text,
            onDismiss = { message = null }
        )
    }
}
