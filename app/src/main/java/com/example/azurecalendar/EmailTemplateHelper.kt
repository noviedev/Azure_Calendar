package com.example.azurecalendar

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

object EmailTemplateHelper {

    fun getAllRegisteredEmails(context: Context): List<String> {
        val emails = mutableSetOf<String>()

        // 1. Owner email from Personal Info prefs
        try {
            val infoPrefs = context.getSharedPreferences("azure_calendar_personal_info", Context.MODE_PRIVATE)
            val adminEmail = infoPrefs.getString("email_Admin", "") ?: ""
            if (adminEmail.isNotBlank()) emails.add(adminEmail.trim().lowercase())

            val ownerEmail = infoPrefs.getString("email_Owner", "") ?: ""
            if (ownerEmail.isNotBlank()) emails.add(ownerEmail.trim().lowercase())

            infoPrefs.all.forEach { (key, value) ->
                if (key.startsWith("email_") && value is String && value.contains("@")) {
                    emails.add(value.trim().lowercase())
                }
            }
        } catch (_: Exception) {}

        // 2. Standard Owner emails
        emails.add("novie1413@gmail.com")
        emails.add("owner.azure@gmail.com")

        // 3. Registered Agents' emails from SQLite Database
        try {
            val dbHelper = AzureDatabaseHelper(context.applicationContext)
            val agents = dbHelper.getAllAgents()
            for (agent in agents) {
                val email = agent["email"] ?: ""
                if (email.isNotBlank() && email.contains("@")) {
                    emails.add(email.trim().lowercase())
                }
            }
        } catch (_: Exception) {}

        // 4. Firebase Auth Current User email
        try {
            val currentUserEmail = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.email ?: ""
            if (currentUserEmail.isNotBlank()) {
                emails.add(currentUserEmail.trim().lowercase())
            }
        } catch (_: Exception) {}

        // 5. Configured Sender emails from Email Settings
        try {
            val emailPrefs = context.getSharedPreferences("azure_calendar_email_prefs", Context.MODE_PRIVATE)
            emailPrefs.all.forEach { (key, value) ->
                if (key.startsWith("sender_email_") && value is String && value.contains("@")) {
                    emails.add(value.trim().lowercase())
                }
            }
        } catch (_: Exception) {}

        return emails.filter { it.isNotBlank() && it.contains("@") }.distinct()
    }

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
            else -> " ($weekdayCount Weekday Night${if (weekdayCount > 1) "s" else ""} @ ₱${String.format("%,.0f", weekdayRate)}/night)"
        }

        val formattedStr = "₱" + String.format("%,.0f", totalAmount) + breakdown
        return Pair(totalAmount, formattedStr)
    }

    fun getOwnerEmails(context: Context, fallbackOwner: String = "owner.azure@gmail.com"): List<String> {
        val emails = mutableSetOf<String>()
        try {
            val infoPrefs = context.getSharedPreferences("azure_calendar_personal_info", Context.MODE_PRIVATE)
            val adminEmail = infoPrefs.getString("email_Admin", "") ?: ""
            if (adminEmail.isNotBlank()) emails.add(adminEmail.trim().lowercase())

            val ownerPrefEmail = infoPrefs.getString("email_Owner", "") ?: ""
            if (ownerPrefEmail.isNotBlank()) emails.add(ownerPrefEmail.trim().lowercase())
        } catch (_: Exception) {}

        if (fallbackOwner.isNotBlank()) emails.add(fallbackOwner.trim().lowercase())
        emails.add("owner.azure@gmail.com")

        return emails.filter { it.isNotBlank() && it.contains("@") }.distinct()
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
        // Send email notifications to all registered agents and owners who booked/reserved units
        val allRecipientsList = (getAllRegisteredEmails(context) + listOf(ownerEmail, agentEmail)).filter { it.contains("@") }.distinct()
        val combinedRecipients = allRecipientsList.toTypedArray()

        // Reserved and Under Maintenance tags DO NOT include price in email templates (for both Agents and Owners)!
        val isNonPricedStatus = statusLabel.contains("Reserve", ignoreCase = true) ||
                                statusLabel.contains("Diamond", ignoreCase = true) ||
                                statusLabel.contains("Maintenance", ignoreCase = true) ||
                                statusLabel.contains("Circle", ignoreCase = true)

        // ONLY Check-in and Rented status tags have price written in the email template!
        val isRevenueBooking = !isNonPricedStatus && (
                                statusLabel.contains("Check", ignoreCase = true) ||
                                statusLabel.contains("Heart", ignoreCase = true) ||
                                statusLabel.contains("Rent", ignoreCase = true) ||
                                statusLabel.contains("Extend", ignoreCase = true)
                               )

        val formattedPrice = if (isNonPricedStatus) "" else if (amountText.isNotBlank()) amountText else {
            try {
                val dbHelper = AzureDatabaseHelper(context.applicationContext)
                val matchedUnit = dbHelper.getAllUnits().find { (it["unitNumber"]?.toString() ?: "").contains(unitName.replace("Unit", "", ignoreCase = true).trim(), ignoreCase = true) }
                val unitRate = (matchedUnit?.get("rate") as? Double) ?: 2500.0
                if (statusLabel.contains("Rent", ignoreCase = true)) {
                    val monthlyVal = if (unitRate >= 10000.0) unitRate else unitRate * 30.0
                    "₱" + String.format("%,.0f", monthlyVal) + " / month"
                } else {
                    val (_, calcStr) = calculateBookingPrice(dateText, unitRate)
                    calcStr
                }
            } catch (_: Exception) {
                ""
            }
        }

        val priceSection = if (isRevenueBooking && formattedPrice.isNotBlank()) "Total Rate / Price: $formattedPrice\n" else ""
        val recipientsFormatted = combinedRecipients.joinToString("\n • ")

        val subject = "Azure Calendar Realtime Unit Status Tag Update - $unitName ($statusLabel)"
        val bodyText = """
Hello Owner,

A real-time unit status tag update has been successfully processed in the Azure Calendar system.

--------------------------------------------------
REALTIME UNIT STATUS TAG DETAILS
--------------------------------------------------
Unit Number: $unitName
Realtime Status Tag: $statusLabel
Booking / Schedule Date: $dateText
Updated By / Agent: $agentName
${priceSection}--------------------------------------------------
RECIPIENT (OWNER)
 • $recipientsFormatted
--------------------------------------------------

This up-to-date status tag notification is automatically synchronized and routed in real time to the Owner.

Best regards,
Azure Calendar Management System
        """.trimIndent()

        try {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:")
                putExtra(Intent.EXTRA_EMAIL, combinedRecipients)
                putExtra(Intent.EXTRA_SUBJECT, subject)
                putExtra(Intent.EXTRA_TEXT, bodyText)
            }

            val chooserIntent = Intent.createChooser(intent, "Send Status Tag Update to All Registered Emails")
            chooserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooserIntent)
        } catch (e: Exception) {
            Toast.makeText(context, "Email client application not available", Toast.LENGTH_SHORT).show()
        }
    }
}
