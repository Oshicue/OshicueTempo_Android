package com.oshicue.tempo.ui.board

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oshicue.tempo.TempoCoordinator
import com.oshicue.tempo.core.TempoGroup
import com.oshicue.tempo.core.TempoMember
import com.oshicue.tempo.data.TempoAudio
import com.oshicue.tempo.data.TempoNotifications
import com.oshicue.tempo.data.TempoStore
import com.oshicue.tempo.i18n.L10n
import com.oshicue.tempo.ui.common.TempoIconButton
import com.oshicue.tempo.ui.common.TempoRowCard
import com.oshicue.tempo.ui.common.TempoWordmark
import com.oshicue.tempo.ui.groups.GroupManagementSheet
import com.oshicue.tempo.ui.sounds.SoundSettingsSheet
import com.oshicue.tempo.ui.theme.tempoDigits
import com.oshicue.tempo.ui.theme.tempoPalette
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class TicketSelection(val member: TempoMember, val group: TempoGroup)

@Composable
fun TimerBoardScreen(
    store: TempoStore,
    audio: TempoAudio,
    notifications: TempoNotifications,
    coordinator: TempoCoordinator,
    now: Long,
    modifier: Modifier = Modifier
) {
    val palette = tempoPalette()
    var showGroups by remember { mutableStateOf(false) }
    var showSounds by remember { mutableStateOf(false) }
    var selection by remember { mutableStateOf<TicketSelection?>(null) }
    var resettingMember by remember { mutableStateOf<TempoMember?>(null) }

    val group = store.groups.firstOrNull { it.id == store.selectedGroupID } ?: store.groups.firstOrNull()
    val pageCount = maxOf(1, ((group?.members?.size ?: 0) + 3) / 4)
    val pagerState = rememberPagerState(pageCount = { pageCount })
    val scope = rememberCoroutineScope()

    LaunchedEffect(store.selectedGroupID) { pagerState.scrollToPage(0) }
    LaunchedEffect(pageCount) {
        if (pagerState.currentPage >= pageCount) pagerState.scrollToPage(pageCount - 1)
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize().background(palette.background)) {
        val compact = maxHeight < 800.dp
        val smallScreen = maxHeight < 680.dp
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = if (maxWidth > 600.dp) 40.dp else 20.dp)
                .padding(top = 12.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(if (compact) 13.dp else 18.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = if (compact) 2.dp else 6.dp)) {
                TempoWordmark()
                Spacer(Modifier.weight(1f))
                TempoIconButton(
                    icon = Icons.AutoMirrored.Outlined.VolumeUp,
                    title = L10n.text("board.soundSettings"),
                    onClick = { showSounds = true }
                )
                Spacer(Modifier.width(10.dp))
                TempoIconButton(
                    icon = Icons.Outlined.Tune,
                    title = L10n.text("board.manageGroups"),
                    onClick = { showGroups = true }
                )
            }

            if (group != null) {
                GroupBar(
                    store = store,
                    group = group,
                    onManageGroups = { showGroups = true }
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = L10n.text("board.eventKind"),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp,
                        color = palette.muted
                    )
                    Spacer(Modifier.weight(1f))
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(if (store.runningCount > 0) palette.runningIndicator else palette.muted)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (store.runningCount > 0) {
                            L10n.format("board.runningCount", store.runningCount)
                        } else {
                            L10n.text("board.ready")
                        },
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = palette.muted
                    )
                }

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.weight(1f)
                ) { pageIndex ->
                    val members = group.members.drop(pageIndex * 4).take(4)
                    Column(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 1.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        for (row in 0 until 2) {
                            Row(
                                modifier = Modifier.weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                for (column in 0 until 2) {
                                    val localIndex = row * 2 + column
                                    val member = members.getOrNull(localIndex)
                                    if (member != null) {
                                        MemberTimerCard(
                                            member = member,
                                            group = group,
                                            round = store.round(forMember = member.id),
                                            now = now,
                                            index = pageIndex * 4 + localIndex,
                                            compact = compact,
                                            smallScreen = smallScreen,
                                            onStart = { selection = TicketSelection(member, group) },
                                            onPause = { store.pause(member.id) },
                                            onResume = { store.resume(member.id) },
                                            onReset = { resettingMember = member },
                                            modifier = Modifier.weight(1f)
                                        )
                                    } else {
                                        EmptyMemberSlot(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (pageCount > 1) {
                        Icon(
                            Icons.Outlined.ChevronLeft,
                            contentDescription = L10n.text("board.previousPage"),
                            tint = if (pagerState.currentPage == 0) palette.line else palette.muted,
                            modifier = Modifier
                                .size(32.dp)
                                .clickable(enabled = pagerState.currentPage > 0) {
                                    scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                                }
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        repeat(pageCount) { index ->
                            val active = pagerState.currentPage == index
                            Box(
                                modifier = Modifier
                                    .width(if (active) 18.dp else 5.dp)
                                    .height(5.dp)
                                    .clip(CircleShape)
                                    .background(if (active) palette.pink else palette.line)
                            )
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    if (pageCount > 1) {
                        Icon(
                            Icons.Outlined.ChevronRight,
                            contentDescription = L10n.text("board.nextPage"),
                            tint = if (pagerState.currentPage == pageCount - 1) palette.line else palette.muted,
                            modifier = Modifier
                                .size(32.dp)
                                .clickable(enabled = pagerState.currentPage < pageCount - 1) {
                                    scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                                }
                        )
                    }
                }

                if (!smallScreen) {
                    Row(modifier = Modifier.padding(top = 2.dp)) {
                        Text(
                            text = L10n.text("board.quickGuide"),
                            fontSize = 10.sp,
                            color = palette.muted
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = "%02d / %02d".format(pagerState.currentPage + 1, pageCount),
                            style = tempoDigits(10f, palette.muted)
                        )
                    }
                }
            } else {
                Spacer(Modifier.weight(1f))
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        Icons.Outlined.Timer,
                        contentDescription = null,
                        tint = palette.pink,
                        modifier = Modifier.size(58.dp)
                    )
                    Text(L10n.text("board.emptyTitle"), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = palette.ink)
                    Text(
                        L10n.text("board.emptyMessage"),
                        fontSize = 14.sp,
                        color = palette.muted,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(palette.primaryFill, CircleShape)
                            .clickable { showGroups = true }
                            .padding(horizontal = 28.dp, vertical = 16.dp)
                    ) {
                        Text(
                            L10n.text("board.addFirstGroup"),
                            fontWeight = FontWeight.SemiBold,
                            color = palette.onPrimary
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
            }
        }
    }

    selection?.let { current ->
        TicketEntrySheet(
            store = store,
            member = current.member,
            group = current.group,
            onDismiss = { selection = null }
        )
    }
    if (showGroups) {
        GroupManagementSheet(
            store = store,
            audio = audio,
            onDismiss = { showGroups = false }
        )
    }
    if (showSounds) {
        SoundSettingsSheet(
            audio = audio,
            notifications = notifications,
            onDismiss = { showSounds = false }
        )
    }

    resettingMember?.let { member ->
        com.oshicue.tempo.ui.common.TempoConfirmDialog(
            title = L10n.format("board.resetQuestion", member.name),
            message = null,
            confirmLabel = L10n.text("board.resetRound"),
            onConfirm = {
                store.reset(member.id)
                resettingMember = null
            },
            onDismiss = { resettingMember = null }
        )
    }
}

@Composable
private fun GroupBar(store: TempoStore, group: TempoGroup, onManageGroups: () -> Unit) {
    val palette = tempoPalette()
    var menuOpen by remember { mutableStateOf(false) }
    TempoRowCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { menuOpen = true }
                .padding(vertical = 14.dp, horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.weight(1f)) {
                Text(
                    L10n.text("board.currentGroup"),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Medium,
                    color = palette.muted
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        group.name,
                        fontSize = 21.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = palette.ink,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Icon(
                        Icons.Outlined.KeyboardArrowDown,
                        contentDescription = null,
                        tint = palette.ink,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("${group.secondsPerTicket}", style = tempoDigits(18f, palette.pink))
                    Text(
                        L10n.text("board.secondsPerTicket"),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = palette.pink
                    )
                }
                Text(
                    L10n.format("board.memberCount", group.members.size),
                    fontSize = 10.sp,
                    color = palette.muted
                )
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                store.groups.forEach { value ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                value.name,
                                fontWeight = if (value.id == group.id) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        onClick = {
                            store.selectedGroupID = value.id
                            menuOpen = false
                        },
                        leadingIcon = if (value.id == group.id) {
                            { Icon(Icons.Outlined.Check, contentDescription = null, tint = palette.pink) }
                        } else {
                            null
                        }
                    )
                }
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text(L10n.text("board.manageGroupsMenu")) },
                    onClick = {
                        menuOpen = false
                        onManageGroups()
                    },
                    leadingIcon = { Icon(Icons.Outlined.Tune, contentDescription = null) }
                )
            }
        }
    }
}
