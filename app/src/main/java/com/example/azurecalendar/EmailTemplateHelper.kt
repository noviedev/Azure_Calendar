package com.example.azurecalendar

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

object EmailTemplateHelper {

    fun calculateBookingPrice(
        dateText: String,
        weekdayRate: Double = 2500.0,
        weekendRate: Double = 3500.0
    ): Pair<Double, String> {
        val monthNamesList = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")
        val realTimeCal = java.util.Calendar.getInstance()
        val defaultYear = realTimeCal.get(java.util.Calendar.YEAR)
        val defaultMonthIdx = realTimeCal.get(java.util.Calendar.MONTH)

        val numbers = Regex("\\d+").findAll(dateText).mapNotNull { it.value.toIntOrNull() }.filter { it in 1..31 }.toList()
        val startDay = if (numbers.isNotEmpty()) numbers[0] else realTimeCal.get(java.util.Calendar.DAY_OF_MONTH)
        val endDay = if (numbers.size >= 2) numbers[1] else startDay + 1

        val monthFoundIdx = monthNamesList.indexOfFirst { dateText.contains(it, ignoreCase = true) }
        val startMonthIdx = if (monthFoundIdx != -1) monthFoundIdx else defaultMonthIdx
        val startYear = Regex("\\b20\\d{2}\\b").find(dateText)?.value?.toIntOrNull() ?: defaultYear

        var totalAmount = 0.0
        var weekdayCount = 0
        var weekendCount = 0

        val cal = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.YEAR, startYear)
            set(java.util.Calendar.MONTH, startMonthIdx)
            set(java.util.Calendar.DAY_OF_MONTH, startDay)
        }

        val totalNights = maxOf(1, if (endDay > startDay) endDay - startDay else 1)

        for (i in 0 until totalNights) {
            val dayOfWeek = cal.get(java.util.Calendar.DAY_OF_WEEK)
            if (dayOfWeek == java.util.Calendar.FRIDAY || dayOfWeek == java.util.Calendar.SATURDAY || dayOfWeek == java.util.Calendar.SUNDAY) {
                weekendCount++
                totalAmount += weekendRate
            } else {
                weekdayCount++
                totalAmount += weekdayRate
            }
            cal.add(java.util.Calendar.DAY_OF_MONTH, 1)
        }

        val breakdown = when {
            weekdayCount > 0 && weekendCount > 0 -> " ($weekdayCount Weekday${if (weekdayCount > 1) "s" else ""} @ ₱${String.format("%,.0f", weekdayRate)} + $weekendCount Weekend${if (weekendCount > 1) "s" else ""} @ ₱${String.format("%,.0f", weekendRate)})"
            weekendCount > 0 -> " ($weekendCount Weekend Night${if (weekendCount > 1) "s" else ""} @ ₱${String.format("%,.0f", weekendRate)}/night)"
            else -> " ($weekdayCount Weekday Night${if (weekendCount > 1) "s" else ""} @ ₱${String.format("%,.0f", weekdayRate)}/night)"
        }

        val formattedStr = "₱" + String.format("%,.0f", totalAmount) + breakdown
        return Pair(totalAmount, formattedStr)
    }

    fun sendBookingEmail(
        context: Context,
        ownerEmail: String = "owner.azure@gmail.com",
        agentEmail: String = "agent@gmail.com",
        agentName: String = "Agent",
        unitName: String = "Unit 12A",
        statusLabel: String = "Check-in",
        dateText: String = "Sept 11, 2026",
        amountText: String = "₱2,500.00"
    ) {
        val isRevenueBooking = statusLabel.contains("Check", ignoreCase = true) ||
                               statusLabel.contains("Heart", ignoreCase = true) ||
                               statusLabel.contains("Reserve", ignoreCase = true) ||
                               statusLabel.contains("Diamond", ignoreCase = true) ||
                               statusLabel.contains("Extend", ignoreCase = true)

        val priceSection = if (isRevenueBooking) "Total Rate / Price: $amountText\n" else ""

        val subject = "Azure Calendar Status Update - $unitName ($statusLabel)"
        val bodyText = """
Hello $agentName & Owner,

A unit status update has been successfully processed in the Azure Calendar system.

--------------------------------------------------
BOOKING & STATUS DETAILS
--------------------------------------------------
Unit Number: $unitName
Booking Category / Status: $statusLabel
Booking Date: $dateText
Assigned Agent: $agentName
Agent Email: $agentEmail
Owner Email: $ownerEmail
${priceSection}--------------------------------------------------

This notification is automatically routed to both the Owner and the booking Agent.

Best regards,
Azure Calendar Management System
        """.trimIndent()

        val recipientEmails = if (agentEmail.isNotBlank() && !agentEmail.equals(ownerEmail, ignoreCase = true)) {
            arrayOf(ownerEmail, agentEmail)
        } else {
            arrayOf(ownerEmail)
        }

        try {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:")
                putExtra(Intent.EXTRA_EMAIL, recipientEmails)
                putExtra(Intent.EXTRA_SUBJECT, subject)
                putExtra(Intent.EXTRA_TEXT, bodyText)
            }

            val chooserIntent = Intent.createChooser(intent, "Send Email via Gmail")
            chooserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooserIntent)
        } catch (e: Exception) {
            Toast.makeText(context, "Gmail application not available", Toast.LENGTH_SHORT).show()
        }
    }
}
