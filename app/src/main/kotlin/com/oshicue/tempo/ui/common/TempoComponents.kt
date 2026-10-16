package com.oshicue.tempo.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oshicue.tempo.i18n.L10n
import com.oshicue.tempo.ui.theme.UnboundedBlack
import com.oshicue.tempo.ui.theme.UnboundedBold
import com.oshicue.tempo.ui.theme.tempoPalette

/** The OSHICUE / TEMPO wordmark in the bundled Unbounded font. */
@Composable
fun TempoWordmark(modifier: Modifier = Modifier) {
    val palette = tempoPalette()
    Column(modifier = modifier) {
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(color = palette.ink)) { append("OSHI") }
                withStyle(SpanStyle(color = palette.brandPink)) { append("CUE") }
            },
            fontFamily = UnboundedBlack,
            fontWeight = FontWeight.Black,
            fontSize = 22.sp,
            letterSpacing = 0.45.sp
        )
        Text(
            text = "TEMPO",
            fontFamily = UnboundedBold,
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp,
            letterSpacing = 4.6.sp,
            color = palette.ink
        )
    }
}

/** Circular surface button used in the board header. */
@Composable
fun TempoIconButton(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = tempoPalette()
    IconButton(
        onClick = onClick,
        modifier = modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(palette.surface, CircleShape)
            .border(BorderStroke(1.dp, palette.line), CircleShape)
    ) {
        Icon(icon, contentDescription = title, tint = palette.ink, modifier = Modifier.size(20.dp))
    }
}

@Composable
fun TempoAlertDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.SemiBold) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(L10n.text("common.ok")) }
        }
    )
}

@Composable
fun TempoConfirmDialog(
    title: String,
    message: String?,
    confirmLabel: String,
    destructive: Boolean = true,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val palette = tempoPalette()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.SemiBold) },
        text = message?.let { { Text(it) } },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmLabel, color = if (destructive) palette.pink else palette.ink, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(L10n.text("common.cancel")) }
        }
    )
}

/** A small rounded surface card matching the iOS list rows. */
@Composable
fun TempoRowCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val palette = tempoPalette()
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(palette.surface, shape)
            .border(1.dp, palette.line, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    ) {
        content()
    }
}

/** Small color swatch with the adaptive outline used for member colors. */
@Composable
fun ColorSwatch(color: Color, size: Int, modifier: Modifier = Modifier) {
    val palette = tempoPalette()
    Box(
        modifier = modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(color, CircleShape)
            .border(0.75.dp, palette.swatchOutline, CircleShape)
    )
}

@Composable
fun SectionFooterText(text: String, modifier: Modifier = Modifier) {
    val palette = tempoPalette()
    Text(
        text = text,
        fontSize = 12.sp,
        color = palette.muted,
        modifier = modifier.padding(horizontal = 4.dp)
    )
}

/** Uppercase muted section label used above list groups. */
@Composable
fun TempoSectionHeader(text: String, modifier: Modifier = Modifier) {
    val palette = tempoPalette()
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = text.uppercase(),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp,
            color = palette.muted
        )
    }
}
