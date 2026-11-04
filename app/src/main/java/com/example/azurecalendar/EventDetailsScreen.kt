package com.example.azurecalendar

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.azurecalendar.ui.theme.AzureCalendarTheme

private val PurplePrimary = Color(0xFF6A1B9A)
private val ScreenBg = Color(0xFFFAF5FC)
private val CardBg = Color(0xFFFFFFFF)
private val TextDark = Color(0xFF2C1A36)
private val TextMuted = Color(0xFF7B6287)

@Composable
fun BookingDetailsScreen(
    unitName: String = "Unit 12A",
    unitImageUri: String? = null,
    unitImageResId: Int = R.drawable.logo,
    isOwnerMode: Boolean = false,
    onBackClick: () -> Unit = {},
    @Suppress("UNUSED_PARAMETER") onViewUnitDetailsClick: () -> Unit = {},
    onEditTagClick: () -> Unit = {},
    onDeleteBookingClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val dbHelper = remember { AzureDatabaseHelper(context) }
    val sessionManager = remember { UserSessionManager(context) }

    val dbEvent = remember(unitName) {
        val allEvents = dbHelper.getAllEvents()
        allEvents.find { evt ->
            val evtUnit = evt["unit"] ?: ""
            evtUnit.contains(unitName, ignoreCase = true) || unitName.contains(evtUnit, ignoreCase = true)
        }
    }

    val bookingAgentName = remember(dbEvent, isOwnerMode) {
        val evtAgent = dbEvent?.get("agent")
        if (!evtAgent.isNullOrBlank()) {
            evtAgent
        } else {
            val sessionName = sessionManager.getUsername()
            if (sessionName.isNotBlank()) sessionName else if (isOwnerMode) "Admin Owner" else "Novelene Unabia"
        }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ScreenBg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        // Header Row with Glossy Circular Purple Back Button
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = Color(0xFF7B2CBF),
                border = BorderStroke(1.5.dp, Color(0xFFE0AAFF)),
                shadowElevation = 4.dp,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .clickable { onBackClick() }
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(Color(0xFF9D4EDD), Color(0xFF5A189A))
                            )
                        )
                        .border(1.dp, Color.White.copy(alpha = 0.6f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = "Back",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "Booking Details",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = TextDark
            )
        }

        // Large Banner Image Container (Displays Designated Unit's Uploaded Photo or logo.png)
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = CardBg),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFFF3EDFD)),
                contentAlignment = Alignment.Center
            ) {
                if (!unitImageUri.isNullOrEmpty()) {
                    AsyncImage(
                        model = unitImageUri,
                        contentDescription = unitName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Image(
                        painter = painterResource(id = unitImageResId),
                        contentDescription = unitName,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    )
                }
            }
        }

        // Unit Check-in Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = CardBg),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF00A86B),
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Business,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    Column {
                        Text(
                            text = unitName,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextDark
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Check-in",
                            fontSize = 13.sp,
                            color = TextMuted
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xFFE8F5E9)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF00A86B))
                        )
                        Text(
                            text = "Available",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF2E7D32)
                        )
                    }
                }
            }
        }

        // Event Details List Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = CardBg),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                EventDetailRow(
                    icon = Icons.Default.DateRange,
                    title = "September 11, 2026",
                    subtitle = "10:00 AM"
                )
                EventDetailRow(
                    icon = Icons.Default.LocationOn,
                    title = "Location",
                    subtitle = "Azure Urban Resort"
                )
                EventDetailRow(
                    icon = Icons.Default.Description,
                    title = "Description",
                    subtitle = "Guest check-in for weekend stay."
                )
                EventDetailRow(
                    icon = Icons.Default.Person,
                    title = "Agent",
                    subtitle = bookingAgentName
                )
                EventDetailRow(
                    icon = Icons.Default.Schedule,
                    title = "Reminder",
                    subtitle = "30 minutes before"
                )
            }
        }

        val currentLoggedUser = remember { sessionManager.getUsername() }
        val canUserEditBooking = remember(bookingAgentName, currentLoggedUser) {
            UserSessionManager.isSameCreator(bookingAgentName, currentLoggedUser)
        }

        // Action Buttons Row (Edit Category Tag & Delete Booking)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = {
                    if (canUserEditBooking) {
                        onEditTagClick()
                    } else {
                        Toast.makeText(context, "Permission Denied: Only $bookingAgentName can edit or modify this booking.", Toast.LENGTH_LONG).show()
                    }
                },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = if (canUserEditBooking) PurplePrimary else Color.Gray),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
            ) {
                Icon(
                    imageVector = if (canUserEditBooking) Icons.Default.Edit else Icons.Default.Lock,
                    contentDescription = "Edit Tag",
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (canUserEditBooking) "Edit Tag" else "Locked", fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }

            OutlinedButton(
                onClick = {
                    if (canUserEditBooking) {
                        onDeleteBookingClick()
                    } else {
                        Toast.makeText(context, "Permission Denied: Only $bookingAgentName can delete this booking.", Toast.LENGTH_LONG).show()
                    }
                },
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, if (canUserEditBooking) Color(0xFFE53935) else Color.Gray),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = if (canUserEditBooking) Color(0xFFE53935) else Color.Gray),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
            ) {
                Icon(
                    imageVector = if (canUserEditBooking) Icons.Default.Delete else Icons.Default.Lock,
                    contentDescription = "Delete",
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (canUserEditBooking) "Delete" else "Locked", fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }



        Spacer(modifier = Modifier.height(20.dp))
    }
}

@Composable
private fun EventDetailRow(icon: ImageVector, title: String, subtitle: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = CircleShape,
            color = Color(0xFFF3EDFD),
            modifier = Modifier.size(36.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = PurplePrimary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(text = title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextDark)
            Spacer(modifier = Modifier.height(1.dp))
            Text(text = subtitle, fontSize = 12.sp, color = TextMuted)
        }
    }
}

@Preview(showBackground = true)
@Composable
fun BookingDetailsScreenPreview() {
    AzureCalendarTheme {
        BookingDetailsScreen()
    }
}
