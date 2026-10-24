package me.pj.jqb.ui.components

import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalView
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Immediate, zero-lag tactile feedback that bypasses view hierarchy delays.
 */
fun View.tickHaptic(enabled: Boolean = true, confirm: Boolean = false) {
    if (!enabled) return
    val constant = if (confirm) {
        HapticFeedbackConstants.CONFIRM
    } else {
        HapticFeedbackConstants.KEYBOARD_TAP
    }
    performHapticFeedback(constant, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING)
}

/**
 * Multi-pulse party-popper haptic rumble sequence.
 */
suspend fun View.rumblePartyPopperHaptic(enabled: Boolean = true) {
    if (!enabled) return
    val flag = HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING
    performHapticFeedback(HapticFeedbackConstants.LONG_PRESS, flag)
    delay(45)
    performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, flag)
    delay(40)
    performHapticFeedback(HapticFeedbackConstants.CONFIRM, flag)
    delay(50)
    performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, flag)
    delay(55)
    performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK, flag)
    delay(65)
    performHapticFeedback(HapticFeedbackConstants.CONFIRM, flag)
}

private data class ConfettiPiece(
    val startXRatio: Float,
    val startYRatio: Float,
    val vx: Float,
    val vy: Float,
    val color: Color,
    val width: Float,
    val height: Float,
    val shapeType: Int, // 0 = ribbon streamer, 1 = square confetti, 2 = circle dot
    val rotationSpeed: Float,
    val swayPhase: Float,
    val swayAmp: Float
)

private val CONFETTI_PALETTE = listOf(
    Color(0xFFFFB300), // Gold Trophy
    Color(0xFFE53935), // Ferrari Red
    Color(0xFF00E676), // Bright Mint
    Color(0xFF2979FF), // Electric Blue
    Color(0xFFD500F9), // Neon Purple
    Color(0xFFFF6D00), // Vibrant Orange
    Color(0xFF00E5FF), // Cyan
    Color(0xFFFF4081)  // Hot Pink
)

/**
 * High-energy Material 3 celebratory confetti cannon & streamer shower overlay.
 * Increment [burstTrigger] (> 0) to launch a 165-particle cannon burst with rhythmic haptic rumble.
 */
@Composable
fun ConfettiCelebrationOverlay(
    burstTrigger: Int,
    hapticEnabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    if (burstTrigger <= 0) return

    val view = LocalView.current
    var progress by remember(burstTrigger) { mutableFloatStateOf(0f) }

    val particles = remember(burstTrigger) {
        val rng = Random(burstTrigger * 13337 + System.currentTimeMillis().toInt())
        List(165) { idx ->
            // 3 cannon origins: left cannon (0), right cannon (1), top-center shower (2)
            val mode = idx % 3
            val startX = when (mode) {
                0 -> 0.04f + rng.nextFloat() * 0.18f
                1 -> 0.78f + rng.nextFloat() * 0.18f
                else -> 0.12f + rng.nextFloat() * 0.76f
            }
            val startY = when (mode) {
                0, 1 -> 0.32f + rng.nextFloat() * 0.18f
                else -> -0.04f - rng.nextFloat() * 0.10f
            }
            val vx = when (mode) {
                0 -> 180f + rng.nextFloat() * 580f   // Shoot forcefully right-upward
                1 -> -180f - rng.nextFloat() * 580f  // Shoot forcefully left-upward
                else -> (rng.nextFloat() - 0.5f) * 520f
            }
            val vy = when (mode) {
                0, 1 -> -680f - rng.nextFloat() * 620f // High-power upward cannon pop!
                else -> 120f + rng.nextFloat() * 480f
            }
            val shapeType = when {
                rng.nextFloat() < 0.38f -> 0 // Long ribbon streamer
                rng.nextFloat() < 0.78f -> 1 // Confetti rectangle
                else -> 2                    // Circle dot
            }
            val w = if (shapeType == 0) 9f + rng.nextFloat() * 8f else 13f + rng.nextFloat() * 14f
            val h = if (shapeType == 0) 24f + rng.nextFloat() * 24f else 10f + rng.nextFloat() * 14f

            ConfettiPiece(
                startXRatio = startX,
                startYRatio = startY,
                vx = vx,
                vy = vy,
                color = CONFETTI_PALETTE[rng.nextInt(CONFETTI_PALETTE.size)],
                width = w,
                height = h,
                shapeType = shapeType,
                rotationSpeed = (rng.nextFloat() - 0.5f) * 960f,
                swayPhase = rng.nextFloat() * 6.28f,
                swayAmp = 22f + rng.nextFloat() * 42f
            )
        }
    }

    LaunchedEffect(burstTrigger) {
        view.rumblePartyPopperHaptic(enabled = hapticEnabled)
    }

    LaunchedEffect(burstTrigger) {
        val startNanos = withFrameNanos { it }
        val durationNanos = 2_400_000_000L // 2.4 seconds
        while (true) {
            val now = withFrameNanos { it }
            val elapsed = now - startNanos
            val t = (elapsed.toFloat() / durationNanos.toFloat()).coerceIn(0f, 1f)
            progress = t
            if (t >= 1f) break
        }
    }

    if (progress in 0.001f..0.999f) {
        val tSec = progress * 2.4f
        val alpha = ((1f - progress) * 2.6f).coerceIn(0f, 1f)

        Canvas(modifier = modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val gravity = 980f
            // Slight air drag on horizontal velocity so cannon shots arc gracefully
            val dragFactor = 1f / (1f + tSec * 0.65f)

            particles.forEach { p ->
                val sway = sin(tSec * 6.2f + p.swayPhase) * p.swayAmp
                val x = p.startXRatio * w + (p.vx * dragFactor) * tSec + sway
                val y = p.startYRatio * h + p.vy * tSec + 0.5f * gravity * tSec * tSec
                if (y in -120f..(h + 120f) && x in -120f..(w + 120f)) {
                    val pieceColor = p.color.copy(alpha = alpha)
                    if (p.shapeType == 2) {
                        drawCircle(
                            color = pieceColor,
                            radius = p.width * 0.45f,
                            center = Offset(x, y)
                        )
                    } else {
                        val angle = p.rotationSpeed * tSec
                        val flutterScale = (0.3f + 0.7f * abs(cos(tSec * 7.5f + p.swayPhase)))
                        rotate(degrees = angle, pivot = Offset(x, y)) {
                            drawRoundRect(
                                color = pieceColor,
                                topLeft = Offset(x - p.width / 2f, y - (p.height * flutterScale) / 2f),
                                size = Size(p.width, p.height * flutterScale),
                                cornerRadius = CornerRadius(3f, 3f)
                            )
                        }
                    }
                }
            }
        }
    }
}
