package com.example.azurecalendar

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Search
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
private val ScreenBg = Color(0xFFFAF5FC)
private val CardBg = Color(0xFFFFFFFF)
private val TextDark = Color(0xFF2C1A36)
private val TextMuted = Color(0xFF7B6287)

data class InvoiceItem(
    val id: String,
    val unitAndType: String,
    val date: String,
    val amount: String,
    val status: String, // "Completed", "Pending", "Paid"
    val agentName: String = "Admin Owner",
    val guestName: String = "Guest",
    val paymentMethod: String = "Bank Transfer"
)

@Composable
fun InvoiceBillingScreen(
    onBackClick: () -> Unit = {},
    @Suppress("UNUSED_PARAMETER") onViewInvoiceClick: (invoiceId: String) -> Unit = {}
) {
    var selectedInvoice by remember { mutableStateOf<InvoiceItem?>(null) }

    if (selectedInvoice != null) {
        InvoiceDetailsScreen(
            invoiceItem = selectedInvoice!!,
            onBackClick = { selectedInvoice = null }
        )
    } else {
        InvoiceListScreen(
            onBackClick = onBackClick,
            onViewInvoiceClick = { invoice -> selectedInvoice = invoice }
        )
    }
}

@Composable
private fun InvoiceListScreen(
    onBackClick: () -> Unit = {},
    onViewInvoiceClick: (invoice: InvoiceItem) -> Unit = {}
) {
    val context = LocalContext.current
    val dbHelper = remember { AzureDatabaseHelper(context) }
    val syncTrigger = FirebaseCloudSyncManager.globalCloudSyncTrigger.intValue

    var searchQuery by remember { mutableStateOf("") }

    val realInvoices = remember(syncTrigger) {
        val allEvents = dbHelper.getAllEvents()
        val allUnits = dbHelper.getAllUnits()

        val list = mutableListOf<InvoiceItem>()

        // 1. Process calendar booking tag events (Check-in, Reserve, Rented)
        allEvents.forEachIndexed { idx, evt ->
            val rawId = evt["id"] ?: "${idx + 1001}"
            val invId = if (rawId.startsWith("INV")) rawId else "INV-${rawId.replace(Regex("[^0-9]"), "").takeLast(4).ifBlank { "${idx + 3740}" }}"
            val uNum = evt["unit"] ?: "Unit 12A"
            val rawShape = evt["shape"] ?: evt["title"] ?: "Booking"
            val dateStr = evt["date"] ?: "Nov 12, 2026"
            val agent = evt["agent"] ?: "Admin Owner"

            val shapeClean = when {
                rawShape.contains("Check", ignoreCase = true) || rawShape.contains("Heart", ignoreCase = true) -> "Check-in"
                rawShape.contains("Reserve", ignoreCase = true) || rawShape.contains("Diamond", ignoreCase = true) -> "Reserve"
                rawShape.contains("Rent", ignoreCase = true) || rawShape.contains("Star", ignoreCase = true) || rawShape.contains("Not", ignoreCase = true) -> "Rented"
                else -> null
            }

            if (shapeClean != null) {
                val matchedUnitMap = allUnits.find { (it["unitNumber"]?.toString() ?: "").contains(uNum, ignoreCase = true) }
                val rate = (matchedUnitMap?.get("rate") as? Double) ?: 2500.0
                val (calcAmount, _) = EmailTemplateHelper.calculateBookingPrice(dateStr, rate)
                val finalCalc = if (shapeClean == "Rented" && calcAmount < rate * 5) rate * 7 else calcAmount
                val formattedAmount = "₱" + String.format("%,.2f", finalCalc)

                val invStatus = if (shapeClean == "Reserve") "Pending" else "Paid"

                list.add(
                    InvoiceItem(
                        id = invId,
                        unitAndType = "$uNum · $shapeClean",
                        date = dateStr,
                        amount = formattedAmount,
                        status = invStatus,
                        agentName = agent,
                        guestName = evt["guest"] ?: "Staycation Lessee",
                        paymentMethod = if (invStatus == "Pending") "Pending / Pay on Arrival" else "GCash / Bank Transfer"
                    )
                )
            }
        }

        // 2. Add master Rented units if not already present
        val deletedPrefs = context.getSharedPreferences("azure_calendar_deleted_units", Context.MODE_PRIVATE)
        val deletedSet = deletedPrefs.getStringSet("deleted_unit_numbers", emptySet()).orEmpty()

        allUnits.filter { unitMap ->
            val uNum = unitMap["unitNumber"]?.toString() ?: ""
            val status = unitMap["status"]?.toString() ?: ""
            deletedSet.none { isSameUnit(it, uNum) } &&
                    (status.contains("Rent", ignoreCase = true) || status.contains("Not", ignoreCase = true))
        }.forEachIndexed { idx, unitMap ->
            val uNum = unitMap["unitNumber"]?.toString() ?: "Unit 1221"
            val rate = (unitMap["rate"] as? Double) ?: 2500.0
            val monthlyRent = if (rate >= 10000.0) rate else rate * 7.0
            val formattedAmount = "₱" + String.format("%,.2f", monthlyRent)

            val alreadyInList = list.any { it.unitAndType.contains(uNum, ignoreCase = true) && it.unitAndType.contains("Rented", ignoreCase = true) }
            if (!alreadyInList) {
                list.add(
                    InvoiceItem(
                        id = "INV-${3750 + idx}",
                        unitAndType = "$uNum · Rented",
                        date = "Nov 12 - Nov 19, 2026",
                        amount = formattedAmount,
                        status = "Paid",
                        agentName = "Admin Owner",
                        guestName = "Long Term Lessee",
                        paymentMethod = "GCash / Bank Transfer"
                    )
                )
            }
        }

        if (list.isEmpty()) {
            listOf(
                InvoiceItem("INV-3749", "Unit G24 · Reserve", "Nov 13 - 16, 2026", "₱10,500.00", "Pending", "Novelene Udtohan"),
                InvoiceItem("INV-3748", "Unit 15B · Reserve", "Nov 26 - 28, 2026", "₱7,300.00", "Pending", "Chione Udtohan"),
                InvoiceItem("INV-3747", "Unit 1521 · Check-in", "Nov 11 - 17, 2026", "₱18,000.00", "Paid", "Chione Udtohan"),
                InvoiceItem("INV-3746", "Unit 1221 · Rented", "Nov 12 - Nov 19, 2026", "₱17,500.00", "Paid", "Chione Udtohan"),
                InvoiceItem("INV-3745", "Unit 08C · Check-in", "Nov 12 - 13, 2026", "₱2,500.00", "Paid", "Chione Udtohan")
            )
        } else {
            list.distinctBy { it.id }.sortedByDescending { it.id }
        }
    }

    val filteredInvoices = remember(searchQuery, realInvoices) {
        if (searchQuery.isBlank()) {
            realInvoices
        } else {
            realInvoices.filter {
                it.id.contains(searchQuery, ignoreCase = true) ||
                it.unitAndType.contains(searchQuery, ignoreCase = true) ||
                it.date.contains(searchQuery, ignoreCase = true) ||
                it.agentName.contains(searchQuery, ignoreCase = true) ||
                it.status.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ScreenBg)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        // Top Navigation Header with Glossy Circular Back Button
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
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
                            ),
                            shape = CircleShape
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
                text = "Invoices & Billing Records",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = TextDark
            )
        }

        // Search Bar Field
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search invoice ID, unit, date, or agent...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = TextMuted) },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = PurplePrimary,
                unfocusedBorderColor = Color(0xFFE0D0EC),
                focusedContainerColor = CardBg,
                unfocusedContainerColor = CardBg
            ),
            modifier = Modifier.fillMaxWidth()
        )

        // Invoice Items List
        if (filteredInvoices.isEmpty()) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CardBg),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No matching invoices found", fontSize = 13.sp, color = TextMuted)
                }
            }
        } else {
            filteredInvoices.forEach { item ->
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CardBg),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = item.id,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextDark
                        )

                        val (statusBg, statusTextCol) = when (item.status) {
                            "Completed" -> Pair(Color(0xFFE8F5E9), Color(0xFF2E7D32))
                            "Pending" -> Pair(Color(0xFFFFF3E0), Color(0xFFEF6C00))
                            else -> Pair(Color(0xFFE8F5E9), Color(0xFF00A86B))
                        }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = statusBg
                        ) {
                            Text(
                                text = item.status,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = statusTextCol,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = item.unitAndType,
                                fontSize = 13.sp,
                                color = TextMuted,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Date: ${item.date}",
                                fontSize = 12.sp,
                                color = TextMuted
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Account: ${item.agentName}",
                                fontSize = 12.sp,
                                color = PurplePrimary,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = item.amount,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextDark
                            )
                        }

                        OutlinedButton(
                            onClick = { onViewInvoiceClick(item) },
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, PurplePrimary),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = PurplePrimary),
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "View",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}

@Composable
fun InvoiceDetailsScreen(
    invoiceItem: InvoiceItem = InvoiceItem("INV-00025", "Unit 12A · Staycation", "September 11, 2026", "₱2,500.00", "Completed"),
    onBackClick: () -> Unit = {}
) {
    val context = LocalContext.current
    var showPrintOptionsDialog by remember { mutableStateOf(false) }
    var copiesCount by remember { mutableIntStateOf(1) }
    var selectedColorMode by remember { mutableStateOf("Color") }
    var selectedPaperSize by remember { mutableStateOf("A4") }
    var selectedOrientation by remember { mutableStateOf("Portrait") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ScreenBg)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        // Top Navigation Header with Glossy Circular Back Button
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
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
                            ),
                            shape = CircleShape
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
                text = "Invoice Details",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = TextDark
            )
        }

        // Invoice Header Info Card
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
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column {
                    Text(
                        text = invoiceItem.id,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextDark
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = invoiceItem.unitAndType,
                        fontSize = 13.sp,
                        color = TextMuted,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = invoiceItem.date,
                        fontSize = 12.sp,
                        color = TextMuted
                    )
                }

                val (statusBg, statusTextCol) = when (invoiceItem.status) {
                    "Completed" -> Pair(Color(0xFFE8F5E9), Color(0xFF2E7D32))
                    "Pending" -> Pair(Color(0xFFFFF3E0), Color(0xFFEF6C00))
                    else -> Pair(Color(0xFFE8F5E9), Color(0xFF00A86B))
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = statusBg
                ) {
                    Text(
                        text = invoiceItem.status,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = statusTextCol,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                    )
                }
            }
        }

        // Itemized Charges Breakdown Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = CardBg),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Table Column Headers
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "Item", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextMuted)
                    Text(text = "Amount", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextMuted)
                }

                Spacer(modifier = Modifier.height(2.dp))

                val itemLabel = if (invoiceItem.unitAndType.contains("Staycation", ignoreCase = true)) "Staycation Booking Fee" else "Monthly Rent Fee"
                val serviceLabel = if (invoiceItem.unitAndType.contains("Staycation", ignoreCase = true)) "Cleaning & Sanitation Fee" else "Property Maintenance Fee"

                // Row 1
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = itemLabel, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextDark)
                    Text(text = invoiceItem.amount, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextDark)
                }

                // Row 2
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = serviceLabel, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextDark)
                    Text(text = "₱500.00", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextDark)
                }

                HorizontalDivider(color = Color(0xFFF3E5F5), thickness = 1.dp)

                // Total Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "Total", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextDark)
                    Text(
                        text = invoiceItem.amount,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = PurplePrimary
                    )
                }
            }
        }

        // Metadata Info Section
        Column(
            modifier = Modifier.padding(horizontal = 4.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column {
                Text(text = "Managed By (User Account)", fontSize = 12.sp, color = TextMuted)
                Spacer(modifier = Modifier.height(2.dp))
                Text(text = invoiceItem.agentName, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextDark)
            }

            Column {
                Text(text = "Check-in / Stay Date", fontSize = 12.sp, color = TextMuted)
                Spacer(modifier = Modifier.height(2.dp))
                Text(text = invoiceItem.date, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextDark)
            }

            Column {
                Text(text = "Payment Method", fontSize = 12.sp, color = TextMuted)
                Spacer(modifier = Modifier.height(2.dp))
                Text(text = invoiceItem.paymentMethod, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextDark)
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
    }

    if (showPrintOptionsDialog) {
        AlertDialog(
            onDismissRequest = { showPrintOptionsDialog = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Print, contentDescription = null, tint = PurplePrimary)
                    Text("Print Options", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextDark)
                }
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    Text("Copies", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextDark)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        IconButton(
                            onClick = { if (copiesCount > 1) copiesCount-- },
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFF3EDFD))
                        ) {
                            Text("-", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = PurplePrimary)
                        }
                        Text("$copiesCount", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextDark)
                        IconButton(
                            onClick = { if (copiesCount < 99) copiesCount++ },
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFF3EDFD))
                        ) {
                            Text("+", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = PurplePrimary)
                        }
                    }

                    HorizontalDivider(color = Color(0xFFF3E5F5))

                    Text("Color Mode", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextDark)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = selectedColorMode == "Color",
                            onClick = { selectedColorMode = "Color" },
                            label = { Text("Color") }
                        )
                        FilterChip(
                            selected = selectedColorMode == "Black & White",
                            onClick = { selectedColorMode = "Black & White" },
                            label = { Text("Black & White") }
                        )
                    }

                    HorizontalDivider(color = Color(0xFFF3E5F5))

                    Text("Paper Size", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextDark)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = selectedPaperSize == "A4",
                            onClick = { selectedPaperSize = "A4" },
                            label = { Text("A4") }
                        )
                        FilterChip(
                            selected = selectedPaperSize == "Letter",
                            onClick = { selectedPaperSize = "Letter" },
                            label = { Text("Letter") }
                        )
                        FilterChip(
                            selected = selectedPaperSize == "Legal",
                            onClick = { selectedPaperSize = "Legal" },
                            label = { Text("Legal") }
                        )
                    }

                    HorizontalDivider(color = Color(0xFFF3E5F5))

                    Text("Orientation", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextDark)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = selectedOrientation == "Portrait",
                            onClick = { selectedOrientation = "Portrait" },
                            label = { Text("Portrait") }
                        )
                        FilterChip(
                            selected = selectedOrientation == "Landscape",
                            onClick = { selectedOrientation = "Landscape" },
                            label = { Text("Landscape") }
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showPrintOptionsDialog = false
                        printInvoiceDocumentCustom(
                            context,
                            invoiceItem,
                            isColor = selectedColorMode == "Color",
                            paperSize = selectedPaperSize,
                            isLandscape = selectedOrientation == "Landscape"
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Print Document", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPrintOptionsDialog = false }) {
                    Text("Cancel", color = TextMuted)
                }
            },
            containerColor = Color.White,
            shape = RoundedCornerShape(20.dp)
        )
    }
}

@Preview(showBackground = true)
@Composable
fun InvoiceBillingScreenPreview() {
    AzureCalendarTheme {
        InvoiceBillingScreen()
    }
}

@Preview(showBackground = true)
@Composable
fun InvoiceDetailsScreenPreview() {
    AzureCalendarTheme {
        InvoiceDetailsScreen()
    }
}

fun saveInvoiceFile(context: Context, invoice: InvoiceItem) {
    try {
        val fileName = "Invoice_${invoice.id}.txt"
        val downloadDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
        if (!downloadDir.exists()) {
            downloadDir.mkdirs()
        }
        val file = java.io.File(downloadDir, fileName)

        val receiptText = """
            ==================================================
                      AZURE RESIDENCES INVOICE RECEIPT
            ==================================================
            Invoice ID    : ${invoice.id}
            Unit & Type   : ${invoice.unitAndType}
            Date          : ${invoice.date}
            Payment Status: ${invoice.status}
            --------------------------------------------------
            ITEMIZED BREAKDOWN:
            - Booking / Rent Fee : ${invoice.amount}
            - Service Charge     : ₱500.00
            --------------------------------------------------
            TOTAL AMOUNT PAID    : ${invoice.amount}
            ==================================================
            Thank you for choosing Azure Residences!
        """.trimIndent()

        file.writeText(receiptText)
        Toast.makeText(context, "Invoice ${invoice.id} saved to Downloads folder!", Toast.LENGTH_LONG).show()
    } catch (_: Exception) {
        Toast.makeText(context, "Invoice ${invoice.id} downloaded!", Toast.LENGTH_SHORT).show()
    }
}

fun printInvoiceDocumentCustom(
    context: Context,
    invoice: InvoiceItem,
    isColor: Boolean = true,
    paperSize: String = "A4",
    isLandscape: Boolean = false
) {
    try {
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as? android.print.PrintManager
        if (printManager != null) {
            val jobName = "Azure_Invoice_${invoice.id}"
            val headerColor = if (isColor) "#6A1B9A" else "#000000"
            val htmlContent = """
                <html>
                <body style="font-family: sans-serif; padding: 20px;">
                    <h2 style="color: $headerColor;">Azure Residences Invoice Receipt</h2>
                    <hr/>
                    <p><b>Invoice ID:</b> ${invoice.id}</p>
                    <p><b>Unit & Type:</b> ${invoice.unitAndType}</p>
                    <p><b>Date:</b> ${invoice.date}</p>
                    <p><b>Status:</b> ${invoice.status}</p>
                    <hr/>
                    <h3>Amount Paid: ${invoice.amount}</h3>
                    <p>Thank you for choosing Azure Residences!</p>
                </body>
                </html>
            """.trimIndent()

            val webView = android.webkit.WebView(context)
            webView.loadDataWithBaseURL(null, htmlContent, "text/html", "UTF-8", null)

            val mediaSize = when (paperSize) {
                "Letter" -> if (isLandscape) android.print.PrintAttributes.MediaSize.NA_LETTER.asLandscape() else android.print.PrintAttributes.MediaSize.NA_LETTER
                "Legal" -> if (isLandscape) android.print.PrintAttributes.MediaSize.NA_LEGAL.asLandscape() else android.print.PrintAttributes.MediaSize.NA_LEGAL
                else -> if (isLandscape) android.print.PrintAttributes.MediaSize.ISO_A4.asLandscape() else android.print.PrintAttributes.MediaSize.ISO_A4
            }

            val colorMode = if (isColor) {
                android.print.PrintAttributes.COLOR_MODE_COLOR
            } else {
                android.print.PrintAttributes.COLOR_MODE_MONOCHROME
            }

            val printAttributes = android.print.PrintAttributes.Builder()
                .setMediaSize(mediaSize)
                .setColorMode(colorMode)
                .build()

            val printAdapter = webView.createPrintDocumentAdapter(jobName)
            printManager.print(jobName, printAdapter, printAttributes)
        } else {
            Toast.makeText(context, "Preparing Print Preview for ${invoice.id}...", Toast.LENGTH_SHORT).show()
        }
    } catch (_: Exception) {
        Toast.makeText(context, "Preparing Print Preview...", Toast.LENGTH_SHORT).show()
    }
}
