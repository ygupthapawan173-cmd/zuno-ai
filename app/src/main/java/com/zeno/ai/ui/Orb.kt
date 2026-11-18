package com.zeno.ai.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import com.zeno.ai.OrbState
import com.zeno.ai.ui.theme.ZCyan
import com.zeno.ai.ui.theme.ZMagenta
import com.zeno.ai.ui.theme.ZViolet
import kotlin.math.PI
import kotlin.math.sin

/** The glowing Jarvis orb. Colour and energy change with the assistant state; [level] is mic loudness 0..1. */
@Composable
fun Orb(state: OrbState, level: Float, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "orb")
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing)),
        label = "pulse",
    )
    val spin by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(7000, easing = LinearEasing)),
        label = "spin",
    )
    val energy by animateFloatAsState(
        targetValue = when (state) {
            OrbState.Idle -> 0.15f
            OrbState.Listening -> 0.45f + level * 0.55f
            OrbState.Thinking -> 0.7f
            OrbState.Speaking -> 0.9f
        },
        animationSpec = tween(350),
        label = "energy",
    )
    val accent by animateColorAsState(
        targetValue = when (state) {
            OrbState.Idle, OrbState.Listening -> ZCyan
            OrbState.Thinking -> ZViolet
            OrbState.Speaking -> ZMagenta
        },
        animationSpec = tween(500),
        label = "accent",
    )

    Canvas(modifier) {
        val c = center
        val r = size.minDimension / 2f
        val e = energy

        // outer glow
        drawCircle(
            brush = Brush.radialGradient(
                listOf(accent.copy(alpha = 0.30f + 0.2f * e), Color.Transparent),
                center = c,
                radius = r,
            ),
            radius = r,
            center = c,
        )

        // expanding pulse rings
        for (i in 0..2) {
            val p = (pulse + i / 3f) % 1f
            drawCircle(
                color = accent.copy(alpha = (1f - p) * (0.15f + 0.35f * e)),
                radius = r * (0.42f + 0.5f * p * (0.6f + e * 0.4f)),
                center = c,
                style = Stroke(width = 2.dp.toPx()),
            )
        }

        // breathing core
        val core = r * 0.40f * (1f + 0.06f * e * sin(pulse * 4f * PI.toFloat()))
        drawCircle(
            brush = Brush.radialGradient(
                listOf(
                    Color.White.copy(alpha = 0.95f),
                    accent,
                    ZViolet.copy(alpha = 0.85f),
                    Color.Transparent,
                ),
                center = c,
                radius = core * 1.35f,
            ),
            radius = core * 1.35f,
            center = c,
        )

        // rotating arc
        rotate(degrees = spin, pivot = c) {
            drawArc(
                brush = Brush.sweepGradient(
                    listOf(Color.Transparent, accent, ZMagenta, Color.Transparent),
                    center = c,
                ),
                startAngle = 0f,
                sweepAngle = 270f,
                useCenter = false,
                topLeft = Offset(c.x - r * 0.62f, c.y - r * 0.62f),
                size = Size(r * 1.24f, r * 1.24f),
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
            )
        }
    }
}
