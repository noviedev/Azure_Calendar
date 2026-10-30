package com.example.azurecalendar

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.azurecalendar.ui.theme.AzureCalendarTheme

private val PurplePrimary = Color(0xFF6A1B9A)
private val BorderPurple = Color(0xFFE1BEE7)

@Composable
fun WelcomeScreen(
    onLoginClick: () -> Unit = {},
    onCreateAccountClick: () -> Unit = {},
    onOwnerPreviewClick: () -> Unit = {}
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Header Section with background image and positioned logo covering the calendar graphic
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.40f),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.background),
                    contentDescription = "Header Background",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFF230D65),
                    shadowElevation = 8.dp,
                    modifier = Modifier
                        .size(195.dp)
                        .padding(bottom = 45.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.padding(8.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.logo),
                            contentDescription = "Azure Calendar Logo",
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }

            // Bottom White Content Section with Illustration, Side Clouds, Plus Signs, and Buttons
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Spacer(modifier = Modifier.height(2.dp))

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(210.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val w = size.width
                            val h = size.height

                            // Side Cloud Outlines / Shapes
                            drawCircle(
                                color = Color(0xFFEBE1F5).copy(alpha = 0.7f),
                                radius = 42.dp.toPx(),
                                center = Offset(-15.dp.toPx(), h * 0.5f)
                            )
                            drawCircle(
                                color = Color(0xFFEBE1F5).copy(alpha = 0.7f),
                                radius = 48.dp.toPx(),
                                center = Offset(w + 15.dp.toPx(), h * 0.55f)
                            )

                            // Plus Signs (+)
                            val plusColor = Color(0xFF7B6287).copy(alpha = 0.45f)
                            drawPlus(Offset(w * 0.18f, 16.dp.toPx()), 12.dp.toPx(), plusColor)
                            drawPlus(Offset(w * 0.68f, 8.dp.toPx()), 14.dp.toPx(), plusColor)
                            drawPlus(Offset(0.88f * w, h - 18.dp.toPx()), 13.dp.toPx(), plusColor)
                            drawPlus(Offset(0.08f * w, h - 25.dp.toPx()), 12.dp.toPx(), plusColor)
                        }

                        Image(
                            painter = painterResource(id = R.drawable.team_calendar_illustration),
                            contentDescription = "Team Calendar Illustration",
                            modifier = Modifier
                                .fillMaxWidth(0.88f)
                                .height(190.dp)
                        )
                    }
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onLoginClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary)
                    ) {
                        Text(
                            text = "LOG IN",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            letterSpacing = 1.sp
                        )
                    }

                    OutlinedButton(
                        onClick = onCreateAccountClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.5.dp, BorderPurple),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = PurplePrimary)
                    ) {
                        Text(
                            text = "CREATE ACCOUNT",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }
                }
            }
        }
    }
}

private fun DrawScope.drawPlus(center: Offset, size: Float, color: Color) {
    val stroke = 3.dp.toPx()
    drawLine(
        color = color,
        start = Offset(center.x - size / 2, center.y),
        end = Offset(center.x + size / 2, center.y),
        strokeWidth = stroke
    )
    drawLine(
        color = color,
        start = Offset(center.x, center.y - size / 2),
        end = Offset(center.x, center.y + size / 2),
        strokeWidth = stroke
    )
}

@Preview(showBackground = true)
@Composable
fun WelcomeScreenPreview() {
    AzureCalendarTheme(content = {
        WelcomeScreen()
    })
}
