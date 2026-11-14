package com.safeguard.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.safeguard.app.ui.theme.SgPalette
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

/**
 * The signature visualiser: a smooth teal sine when idle, and a jagged,
 * noise-injected waveform that grows in amplitude with [chaos] (0..1).
 */
@Composable
fun Waveform(
    chaos: Float,
    modifier: Modifier = Modifier,
    layers: Int = 3
) {
    val transition = rememberInfiniteTransition(label = "wave")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (chaos > 0.05f) 900 else 4200,
                easing = LinearEasing
            )
        ),
        label = "phase"
    )
    val jitter by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(700, easing = LinearEasing)),
        label = "jitter"
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(132.dp)
    ) {
        val midY = size.height / 2f
        val width = size.width
        val seed = (jitter * 1000).toInt()

        repeat(layers) { layer ->
            val layerChaos = (chaos * (1f - layer * 0.22f)).coerceIn(0f, 1f)
            val amplitude = 5f + layerChaos * (size.height * 0.36f)
            val path = Path()
            val steps = 140
            val layerPhase = phase + layer * 0.7f

            for (i in 0..steps) {
                val t = i / steps.toFloat()
                val x = t * width
                val envelope = sin(t * PI).toFloat().coerceIn(0f, 1f)
                var y = sin((t * 4 * PI) + layerPhase).toFloat() * amplitude * envelope
                if (layerChaos > 0.02f) {
                    val noise = Random(seed + i * 7 + layer * 31).nextFloat() - 0.5f
                    y += noise * amplitude * 1.15f * envelope * layerChaos
                    // burst spikes like a jammer
                    if (noise > 0.35f) y += (noise - 0.35f) * amplitude * 1.6f * envelope * layerChaos
                }
                val point = Offset(x, midY + y)
                if (i == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
            }

            val color = when (layer) {
                0 -> Color.White.copy(alpha = 0.92f)
                1 -> SgPalette.Teal
                else -> SgPalette.TealDim
            }
            drawPath(
                path = path,
                color = color.copy(alpha = 0.85f - layer * 0.22f),
                style = Stroke(width = (2.2f - layer * 0.5f).coerceAtLeast(0.8f))
            )
        }

        // baseline glow
        drawRect(
            brush = Brush.horizontalGradient(
                colors = listOf(
                    Color.Transparent,
                    SgPalette.Teal.copy(alpha = 0.10f + chaos * 0.22f),
                    Color.Transparent
                )
            ),
            topLeft = Offset(0f, midY - 2f),
            size = androidx.compose.ui.geometry.Size(width, 4f)
        )
    }
}

/**
 * Compact radial gauge used for latency / packet loss / fake signal.
 */
@Composable
fun RadialGauge(
    value: Float,
    label: String,
    display: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val radius = size.minDimension / 2f - 8f
        val stroke = 9f
        drawCircle(
            color = SgPalette.Grid,
            radius = radius,
            style = Stroke(width = stroke)
        )
        drawArc(
            color = SgPalette.Grid,
            startAngle = 140f,
            sweepAngle = 260f,
            useCenter = false,
            topLeft = Offset(center.x - radius, center.y - radius),
            size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
            style = Stroke(width = stroke)
        )
        drawArc(
            color = color,
            startAngle = 140f,
            sweepAngle = 260f * value.coerceIn(0f, 1f),
            useCenter = false,
            topLeft = Offset(center.x - radius, center.y - radius),
            size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
            style = Stroke(width = stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        )
    }
}

/** Small battery-style level bar for the modelled signal strength. */
fun signalBars(dbm: Int): Int {
    val normalized = when {
        dbm >= -60 -> 5
        dbm >= -70 -> 4
        dbm >= -80 -> 3
        dbm >= -95 -> 2
        dbm >= -105 -> 1
        else -> 0
    }
    return normalized.coerceIn(0, 5)
}

fun signalPercent(dbm: Int): Float = ((dbm + 115) / 60f).coerceIn(0f, 1f)

internal fun chaosLabel(chaos: Float): String = when {
    chaos < 0.15f -> "STABLE"
    chaos < 0.45f -> "STRESSED"
    chaos < 0.75f -> "DEGRADED" else -> "DISRUPTED"
}

internal fun absPercent(v: Float): Int = abs(v).toInt()
