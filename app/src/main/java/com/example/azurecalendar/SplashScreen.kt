package com.example.azurecalendar

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.azurecalendar.ui.theme.AzureCalendarTheme
import kotlinx.coroutines.delay

private val TextDark = Color(0xFF2C1A36)
private val TextMuted = Color(0xFF7B6287)

@Composable
fun SplashScreen(
    onSplashComplete: () -> Unit = {}
) {
    val infiniteTransition = rememberInfiniteTransition(label = "starAnimation")
    val starGlow by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "starGlow"
    )

    val starOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 200f,
        animationSpec = infiniteRepeatable(
            animation = tween(5000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "starOffset"
    )

    LaunchedEffect(Unit) {
        delay(2000)
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

            val baseStars = listOf(
                Triple(0.13f, 0.09f, 7.dp.toPx()),
                Triple(0.88f, 0.24f, 7.dp.toPx()),
                Triple(0.12f, 0.85f, 7.dp.toPx()),
                Triple(0.84f, 0.88f, 5.dp.toPx()),
                Triple(0.50f, 0.15f, 6.dp.toPx()),
                Triple(0.30f, 0.60f, 5.dp.toPx()),
                Triple(0.70f, 0.70f, 6.dp.toPx())
            )

            baseStars.forEachIndexed { index, (relX, relY, starSize) ->
                val yShift = (starOffset + (index * 50f)) % h
                val adjustedY = (relY * h - yShift + h) % h
                val currentPos = Offset(relX * w, adjustedY)
                val color = if (index % 2 == 0) Color(0xFF6B3AB7).copy(alpha = starGlow) else Color.White.copy(alpha = starGlow)
                drawSplashStar(currentPos, starSize, color)
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(
                shape = CircleShape,
                color = Color(0xFF230D65),
                shadowElevation = 12.dp,
                modifier = Modifier.size(150.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.padding(12.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.logo),
                        contentDescription = "Azure Calendar Logo",
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Azure Calendar",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = TextDark
            )

            Text(
                text = "Property & Staycation Management",
                fontSize = 13.sp,
                color = TextMuted
            )
        }
    }
}

private fun DrawScope.drawSplashStar(center: Offset, size: Float, color: Color) {
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
fun SplashScreenPreview() {
    AzureCalendarTheme {
        SplashScreen()
    }
}
