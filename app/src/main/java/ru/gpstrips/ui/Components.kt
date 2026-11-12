package ru.gpstrips.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun Panel(
    modifier: Modifier = Modifier,
    color: Color = P.surface,
    pad: Dp = 16.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val base = modifier
        .clip(RoundedCornerShape(20.dp))
        .background(color)
        .border(1.dp, P.stroke, RoundedCornerShape(20.dp))
        .let { if (onClick != null) it.clickable { onClick() } else it }
    Column(base.padding(pad), content = content)
}

@Composable
fun StatTile(
    label: String,
    value: String,
    sub: String? = null,
    modifier: Modifier = Modifier,
    accent: Color = P.text
) {
    Column(modifier) {
        Text(
            text = label.uppercase(),
            color = P.dim,
            fontSize = 11.sp,
            letterSpacing = 0.8.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.height(4.dp))
        Text(text = value, color = accent, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        if (sub != null) {
            Text(text = sub, color = P.dim, fontSize = 12.sp)
        }
    }
}

@Composable
fun ActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    enabled: Boolean = true
) {
    val bg = if (primary) P.accent else P.surface2
    Box(
        modifier = modifier
            .height(46.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (enabled) bg else P.surface2.copy(alpha = 0.45f))
            .let { if (enabled) it.clickable { onClick() } else it }
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (!enabled) P.dim else if (primary) Color(0xFF04231B) else P.text,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        color = P.dim,
        fontSize = 11.sp,
        letterSpacing = 1.sp,
        fontWeight = FontWeight.Medium,
        modifier = modifier.padding(start = 4.dp, bottom = 8.dp)
    )
}
