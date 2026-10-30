package com.example.azurecalendar

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.azurecalendar.ui.theme.AzureCalendarTheme

private val PurplePrimary = Color(0xFF6A1B9A)
private val PurpleDark = Color(0xFF4A148C)
private val LavenderLight = Color(0xFFF3E5F5)
private val ScreenBg = Color(0xFFFAF5FC)
private val CardBg = Color(0xFFFFFFFF)
private val TextDark = Color(0xFF2C1A36)
private val TextMuted = Color(0xFF7B6287)
private val BorderPurple = Color(0xFFE1BEE7)

data class EmailTemplate(
    val triggerName: String,
    var subject: String,
    var body: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmailTemplateSettingsScreen(
    onBackClick: () -> Unit = {}
) {
    val context = LocalContext.current

    val triggerOptions = listOf(
        "Booking Created",
        "Schedule Changed",
        "Schedule Created",
        "Invoice Generated"
    )
    var selectedTrigger by remember { mutableStateOf(triggerOptions[0]) }

    var createdSubject by remember { mutableStateOf("Confirmed: New Booking for {Unit_Number}") }
    var createdBody by remember {
        mutableStateOf(
            "Hello {Guest_Name},\n\nYour staycation reservation for {Unit_Number} ({BR_Type}) on {Booking_Date} has been confirmed.\n\nAssigned Agent: {Agent_Name}\n\nThank you for choosing Azure Residences!"
        )
    }

    var changedSubject by remember { mutableStateOf("Notice: Schedule Updated for {Unit_Number}") }
    var changedBody by remember {
        mutableStateOf(
            "Hello {Guest_Name},\n\nPlease note that your staycation schedule for {Unit_Number} has been updated to: {Booking_Date}.\n\nIf you have any questions, please contact your agent."
        )
    }

    var invoiceSubject by remember { mutableStateOf("Invoice Receipt: Azure Booking #{Invoice_ID}") }
    var invoiceBody by remember {
        mutableStateOf(
            "Dear {Guest_Name},\n\nYour invoice for {Unit_Number} has been generated.\n\nTotal Balance: {Total_Amount}\nStatus: {Payment_Status}\n\nPlease retain this receipt for your records."
        )
    }

    val activeSubject = when (selectedTrigger) {
        "Schedule Changed" -> changedSubject
        "Schedule Created" -> createdSubject
        "Invoice Generated" -> invoiceSubject
        else -> createdSubject
    }

    val activeBody = when (selectedTrigger) {
        "Schedule Changed" -> changedBody
        "Schedule Created" -> createdBody
        "Invoice Generated" -> invoiceBody
        else -> createdBody
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ScreenBg)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        // Top Navigation Header with Glossy Circular Back Button
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
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "Email Templates",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextDark
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Admin Automated Notification Settings",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextMuted
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Trigger Selector Chips
            Text(
                text = "Select Event Trigger",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = TextDark
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                triggerOptions.forEach { trigger ->
                    val isSelected = (trigger == selectedTrigger)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (isSelected) PurplePrimary else CardBg)
                            .border(
                                width = 1.dp,
                                color = if (isSelected) PurplePrimary else BorderPurple,
                                shape = RoundedCornerShape(20.dp)
                            )
                            .clickable { selectedTrigger = trigger }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = trigger,
                            color = if (isSelected) Color.White else TextMuted,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Template Editor Card
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = CardBg,
                border = BorderStroke(1.dp, BorderPurple),
                shadowElevation = 2.dp
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Customize Email Content",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextDark
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Supported tokens: {Guest_Name}, {Unit_Number}, {BR_Type}, {Booking_Date}, {Total_Amount}",
                        fontSize = 11.sp,
                        color = TextMuted,
                        lineHeight = 15.sp
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Subject Line Input
                    OutlinedTextField(
                        value = activeSubject,
                        onValueChange = { updated ->
                            when (selectedTrigger) {
                                "Schedule Changed" -> changedSubject = updated
                                "Schedule Created" -> createdSubject = updated
                                "Invoice Generated" -> invoiceSubject = updated
                                else -> createdSubject = updated
                            }
                        },
                        label = { Text("Subject Line") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Message Body Input
                    OutlinedTextField(
                        value = activeBody,
                        onValueChange = { updated ->
                            when (selectedTrigger) {
                                "Schedule Changed" -> changedBody = updated
                                "Schedule Created" -> createdBody = updated
                                "Invoice Generated" -> invoiceBody = updated
                                else -> createdBody = updated
                            }
                        },
                        label = { Text("Message Body") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 140.dp),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Live Preview Card
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = LavenderLight,
                border = BorderStroke(1.dp, BorderPurple)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Recipient Preview",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = PurpleDark
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Subject: $activeSubject",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextDark
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = activeBody,
                        fontSize = 12.sp,
                        color = TextDark,
                        lineHeight = 16.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Action Buttons (Save & Test Email)
            Button(
                onClick = {
                    Toast.makeText(context, "Template for '$selectedTrigger' Saved!", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary)
            ) {
                Text(
                    text = "Save Template Changes",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedButton(
                onClick = {
                    Toast.makeText(context, "Test email dispatched to admin address", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, PurplePrimary)
            ) {
                Icon(Icons.Default.Send, contentDescription = null, tint = PurplePrimary, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Send Test Email",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = PurplePrimary
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }

@Preview(showBackground = true)
@Composable
fun EmailTemplateSettingsScreenPreview() {
    AzureCalendarTheme {
        EmailTemplateSettingsScreen()
    }
}
