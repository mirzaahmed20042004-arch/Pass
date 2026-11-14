package com.safeguard.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.safeguard.app.ui.theme.Mono
import com.safeguard.app.ui.theme.SgPalette

/**
 * One metric card: radial arc, big number, unit, label.
 * [progress] is 0..1, [value] is already formatted for display.
 */
@Composable
fun GaugeCard(
    progress: Float,
    value: String,
    unit: String,
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
    footer: String? = null
) {
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(550),
        label = "gauge"
    )
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(SgPalette.Card)
            .padding(vertical = 12.dp, horizontal = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.size(74.dp)) {
                val radius = size.minDimension / 2f - 6f
                val topLeft = Offset(center.x - radius, center.y - radius)
                val arcSize = Size(radius * 2, radius * 2)
                drawArc(
                    color = SgPalette.Grid,
                    startAngle = 140f,
                    sweepAngle = 260f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = 7f, cap = StrokeCap.Round)
                )
                drawArc(
                    color = color.copy(alpha = 0.9f),
                    startAngle = 140f,
                    sweepAngle = 260f * animated,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = 7f, cap = StrokeCap.Round)
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = value,
                    color = SgPalette.TextPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = Mono
                )
                Text(
                    text = unit,
                    color = SgPalette.TextSecondary,
                    fontSize = 8.sp,
                    letterSpacing = 1.sp
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = label.uppercase(),
            color = SgPalette.TextSecondary,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 9.sp
        )
        if (footer != null) {
            Text(
                text = footer,
                color = color.copy(alpha = 0.85f),
                fontSize = 9.sp,
                fontFamily = Mono
            )
        }
    }
}

/** Horizontal bar used inside capability rows. */
@Composable
fun MiniBar(progress: Float, color: Color, modifier: Modifier = Modifier) {
    val animated by animateFloatAsState(progress.coerceIn(0f, 1f), tween(500), label = "bar")
    Canvas(
        modifier = modifier
            .height(4.dp)
            .fillMaxWidth()
    ) {
        drawRoundRect(
            color = SgPalette.Grid,
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f)
        )
        drawRoundRect(
            color = color,
            size = Size(size.width * animated, size.height),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f)
        )
    }
}

@Composable
fun SignalBars(bars: Int, active: Boolean, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(5) { index ->
            val on = index < bars
            Box(
                modifier = Modifier
                    .width(5.dp)
                    .height((7 + index * 4).dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        if (on) {
                            if (active) SgPalette.Teal else SgPalette.TextSecondary
                        } else {
                            SgPalette.Grid
                        }
                    )
            )
        }
    }
}
