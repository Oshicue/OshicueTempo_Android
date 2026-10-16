package com.oshicue.tempo.ui.board

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ConfirmationNumber
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oshicue.tempo.core.CountdownPhase
import com.oshicue.tempo.core.CountdownRound
import com.oshicue.tempo.core.TempoGroup
import com.oshicue.tempo.core.TempoMember
import com.oshicue.tempo.i18n.L10n
import com.oshicue.tempo.ui.common.ColorSwatch
import com.oshicue.tempo.ui.theme.hexColor
import com.oshicue.tempo.ui.theme.memberAccent
import com.oshicue.tempo.ui.theme.tempoDigits
import com.oshicue.tempo.ui.theme.tempoPalette
import com.oshicue.tempo.ui.theme.tempoTime

@Composable
fun MemberTimerCard(
    member: TempoMember,
    group: TempoGroup,
    round: CountdownRound?,
    now: Long,
    index: Int,
    compact: Boolean,
    smallScreen: Boolean,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = tempoPalette()
    val color = memberAccent(member.colorHex)
    val memberText = if (palette.dark) color else palette.ink

    val phase = round?.phase(now) ?: CountdownPhase.Idle
    val running = phase == CountdownPhase.Running
    val paused = phase == CountdownPhase.Paused
    val finished = phase == CountdownPhase.Finished
    val remaining = round?.remaining(now) ?: group.secondsPerTicket.toDouble()
    val fraction = round?.let { maxOf(0.0, minOf(1.0, remaining / maxOf(1.0, it.duration))) } ?: 1.0

    val status = when {
        finished -> L10n.text("timer.finished")
        paused -> L10n.text("timer.paused")
        running -> L10n.text("timer.running")
        else -> L10n.text("timer.tapToStart")
    }

    val shape = RoundedCornerShape(24.dp)
    val borderColor = if (palette.dark) {
        color.copy(alpha = if (finished) 0.65f else if (running) 0.28f else 0.12f)
    } else {
        color.copy(alpha = if (finished) 0.45f else if (running) 0.3f else 0.2f)
    }
    val tintTop = if (palette.dark) {
        color.copy(alpha = if (finished) 0.12f else 0.07f)
    } else {
        color.copy(alpha = if (finished) 0.04f else 0.02f)
    }
    val buttonTint = color.copy(alpha = if (palette.dark) 0.1f else 0.05f)

    Box(
        modifier = modifier
            .fillMaxSize()
            .shadow(
                8.dp, shape, clip = false,
                ambientColor = palette.cardShadow, spotColor = palette.cardShadow
            )
            .clip(shape)
            .background(palette.surface)
            .background(Brush.linearGradient(listOf(tintTop, Color.Transparent)))
            .border(1.dp, borderColor, shape)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (smallScreen) 12.dp else if (compact) 14.dp else 17.dp),
            verticalArrangement = Arrangement.spacedBy(
                if (smallScreen) 5.dp else if (compact) 8.dp else 12.dp
            ),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ColorSwatch(color = hexColor(member.colorHex), size = 7)
                    Text(text = "%02d".format(index + 1), style = tempoDigits(9f, palette.muted))
                }
                Spacer(Modifier.weight(1f))
                if (running) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(4.dp)
                                .clip(CircleShape)
                                .background(color)
                        )
                        Text(
                            text = L10n.text("timer.live"),
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp,
                            color = memberText
                        )
                    }
                } else {
                    Icon(
                        imageVector = when {
                            finished -> Icons.Outlined.Check
                            paused -> Icons.Filled.Pause
                            else -> Icons.Outlined.GraphicEq
                        },
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }

            Text(
                text = member.name,
                fontSize = if (smallScreen) 17.sp else if (compact) 19.sp else 21.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = palette.ink
            )

            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                val side = minOf(maxWidth, maxHeight)
                val digitsSize = (side.value * 0.185f).coerceIn(13f, 25f)
                Box(
                    modifier = Modifier
                        .size(side)
                        .semantics {
                            contentDescription = L10n.format(
                                "timer.accessibility", member.name, status, tempoTime(remaining)
                            )
                        }
                        .clickable(enabled = !running && !paused) { onStart() },
                    contentAlignment = Alignment.Center
                ) {
                    val trackColor = color.copy(alpha = 0.1f)
                    val progressColor = color.copy(alpha = if (finished) 0.35f else 0.9f)
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val backing = 4.dp.toPx()
                        val stroke = 3.dp.toPx()
                        if (!palette.dark) {
                            // A fine neutral backing keeps white representative colors visible.
                            val inset = backing / 2
                            drawArc(
                                color = palette.line,
                                startAngle = 0f,
                                sweepAngle = 360f,
                                useCenter = false,
                                topLeft = Offset(inset, inset),
                                size = Size(size.width - backing, size.height - backing),
                                style = Stroke(width = backing)
                            )
                        }
                        val inset = backing / 2
                        drawArc(
                            color = trackColor,
                            startAngle = 0f,
                            sweepAngle = 360f,
                            useCenter = false,
                            topLeft = Offset(inset, inset),
                            size = Size(size.width - inset * 2, size.height - inset * 2),
                            style = Stroke(width = stroke)
                        )
                        drawArc(
                            color = progressColor,
                            startAngle = -90f,
                            sweepAngle = (fraction * 360).toFloat(),
                            useCenter = false,
                            topLeft = Offset(inset, inset),
                            size = Size(size.width - inset * 2, size.height - inset * 2),
                            style = Stroke(width = stroke, cap = StrokeCap.Round)
                        )
                    }
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        Text(
                            text = tempoTime(remaining),
                            style = tempoDigits(
                                digitsSize,
                                if (finished) memberText else palette.ink
                            ),
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(horizontal = 7.dp)
                        )
                        Text(
                            text = status,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            color = if (finished) memberText else palette.muted
                        )
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    Icons.Outlined.ConfirmationNumber,
                    contentDescription = null,
                    tint = palette.muted,
                    modifier = Modifier.size(12.dp)
                )
                Text(
                    text = round?.let {
                        L10n.format("timer.roundSummary", it.tickets, it.duration.toLong())
                    } ?: L10n.format("timer.ticketDuration", group.secondsPerTicket),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = palette.muted,
                    maxLines = 1
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (running || paused) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp)
                            .clip(CircleShape)
                            .background(buttonTint)
                            .clickable { if (running) onPause() else onResume() },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                if (running) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = null,
                                tint = memberText,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = if (running) L10n.text("timer.pause") else L10n.text("timer.resume"),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = memberText,
                                maxLines = 1
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(palette.controlSurface)
                            .clickable { onReset() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Outlined.Replay,
                            contentDescription = L10n.format("timer.resetMember", member.name),
                            tint = palette.muted,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp)
                            .clip(CircleShape)
                            .background(buttonTint)
                            .clickable { onStart() },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                if (finished) Icons.Outlined.Refresh else Icons.Outlined.Add,
                                contentDescription = null,
                                tint = memberText,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = if (finished) {
                                    L10n.text("timer.nextRound")
                                } else {
                                    L10n.text("timer.enterTickets")
                                },
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = memberText,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Empty slot in a 2x2 page. */
@Composable
fun EmptyMemberSlot(modifier: Modifier = Modifier) {
    val palette = tempoPalette()
    val shape = RoundedCornerShape(24.dp)
    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(shape)
            .background(palette.surface.copy(alpha = 0.35f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Outlined.AutoAwesome,
            contentDescription = null,
            tint = palette.line,
            modifier = Modifier.size(22.dp)
        )
    }
}
