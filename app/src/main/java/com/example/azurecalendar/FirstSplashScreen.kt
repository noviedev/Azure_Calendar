package com.example.azurecalendar

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.azurecalendar.ui.theme.AzureCalendarTheme
import kotlinx.coroutines.delay

@Composable
fun FirstSplashScreen(
    onSplashComplete: () -> Unit
) {
    val scale = remember { Animatable(0.85f) }

    val infiniteTransition = rememberInfiniteTransition(label = "starTwinkle")
    val starGlow by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "starGlow"
    )

    // Strictly 2.0 seconds total (400ms scale + 1600ms hold)
    LaunchedEffect(Unit) {
        scale.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing)
        )
        delay(1600L)
        onSplashComplete()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFEFE7FC)),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            drawCircle(Color.White.copy(alpha = 0.5f), w * 0.45f, Offset(w * 0.2f, h * 0.05f))
            drawCircle(Color.White.copy(alpha = 0.5f), w * 0.45f, Offset(w * 0.8f, h * 0.05f))

            drawFirstSplashStar(Offset(w * 0.13f, h * 0.09f), 7.dp.toPx(), Color(0xFF6B3AB7).copy(alpha = starGlow))
            drawFirstSplashStar(Offset(w * 0.88f, h * 0.24f), 7.dp.toPx(), Color(0xFF6B3AB7).copy(alpha = starGlow))
            drawFirstSplashStar(Offset(w * 0.12f, h * 0.85f), 7.dp.toPx(), Color(0xFF6B3AB7).copy(alpha = starGlow))
            drawFirstSplashStar(Offset(w * 0.84f, h * 0.88f), 5.dp.toPx(), Color.White.copy(alpha = starGlow))
        }

        Surface(
            shape = RoundedCornerShape(32.dp),
            color = Color(0xFF230D65),
            border = BorderStroke(3.5.dp, Color.White),
            shadowElevation = 18.dp,
            modifier = Modifier
                .fillMaxWidth(0.55f)
                .aspectRatio(1f)
                .scale(scale.value)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.padding(14.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.logo),
                    contentDescription = "Azure Calendar Logo",
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

private fun DrawScope.drawFirstSplashStar(center: Offset, size: Float, color: Color) {
    val path = Path().apply {
        moveTo(center.x, center.y - size)
        quadraticTo(center.x, center.y, center.x + size, center.y)
        quadraticTo(center.x, center.y, center.x + size, center.y)
        quadraticTo(center.x, center.y, center.x - size, center.y)
        quadraticTo(center.x, center.y, center.x - size, center.y)
        close()
    }
    drawPath(path = path, color = color)
}

@Preview(showBackground = true)
@Composable
fun FirstSplashScreenPreview() {
    AzureCalendarTheme {
        FirstSplashScreen(onSplashComplete = {})
    }
}