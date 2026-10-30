package com.example.azurecalendar

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

private val DeepPurple = Color(0xFF2C103F)
private val MidnightPurple = Color(0xFF160824)

@Composable
fun NightSkyBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "starTwinkle")
    val starGlow by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "starGlow"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(DeepPurple, MidnightPurple)
                )
            )
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val starPositions = listOf(
                Offset(0.15f * size.width, 0.12f * size.height),
                Offset(0.35f * size.width, 0.08f * size.height),
                Offset(0.78f * size.width, 0.18f * size.height),
                Offset(0.85f * size.width, 0.07f * size.height),
                Offset(0.22f * size.width, 0.28f * size.height),
                Offset(0.62f * size.width, 0.32f * size.height),
                Offset(0.92f * size.width, 0.40f * size.height),
                Offset(0.12f * size.width, 0.55f * size.height),
                Offset(0.80f * size.width, 0.65f * size.height),
                Offset(0.40f * size.width, 0.72f * size.height)
            )

            starPositions.forEachIndexed { index, pos ->
                val alphaMultiplier = if (index % 2 == 0) 1f else 0.7f
                drawCircle(
                    color = Color(0xFFD8B4E2).copy(alpha = (starGlow * alphaMultiplier).coerceIn(0.1f, 1f)),
                    radius = if (index % 3 == 0) 4f else 3f,
                    center = pos
                )
            }
        }
        content()
    }
}
