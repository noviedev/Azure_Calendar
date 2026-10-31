package com.example.azurecalendar

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
fun UnitDetailsScreen(
    unitName: String = "Unit 12A",
    towerName: String = "Azure Tower",
    unitStatus: String = "Available",
    imageUri: String? = null,
    imageResId: Int = R.drawable.background,
    initialCheckInDay: Int = 11,
    initialMonthName: String = "Sept",
    initialYearNumber: Int = 2026,
    isOwnerMode: Boolean = false,
    onBackClick: () -> Unit = {},
    onEditUnitClick: () -> Unit = {},
    onEditTagClick: (dateRangeText: String) -> Unit = {},
    onBookNowClick: (dateRangeText: String) -> Unit = {},
    @Suppress("UNUSED_PARAMETER") onTabSelected: (Int) -> Unit = {}
) {
    val context = LocalContext.current
    val syncTrigger = FirebaseCloudSyncManager.globalCloudSyncTrigger.intValue
    var currentStatus by remember(unitStatus, syncTrigger) { mutableStateOf(unitStatus) }
    var currentEventDate by remember { mutableStateOf("") }

    var showBreachContractDialog by remember { mutableStateOf(false) }
    var showDateRangePicker by remember { mutableStateOf(false) }
    var showExtendStayDialog by remember { mutableStateOf(false) }
    val realTimeCal = remember { java.util.Calendar.getInstance() }
    val monthNamesList = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sept", "Oct", "Nov", "Dec")
    val currentRealMonth = monthNamesList.getOrElse(realTimeCal.get(java.util.Calendar.MONTH)) { "Oct" }
    val currentRealYear = realTimeCal.get(java.util.Calendar.YEAR)
    val currentRealDay = realTimeCal.get(java.util.Calendar.DAY_OF_MONTH)

    val activeMonth = remember(initialMonthName) {
        if (initialMonthName == "Sept" || initialMonthName.isBlank()) currentRealMonth else initialMonthName
    }
    val activeYear = remember(initialYearNumber) {
        if (initialYearNumber == 2026 && initialMonthName == "Sept") currentRealYear else initialYearNumber
    }
    val activeCheckIn = remember(initialCheckInDay) {
        if (initialCheckInDay == 11) currentRealDay else initialCheckInDay
    }

    // Keep status and date coverage updated automatically when tags or cloud sync changes
    LaunchedEffect(syncTrigger, unitName, activeCheckIn, activeMonth, activeYear) {
        val dbHelper = AzureDatabaseHelper(context)

        val allEvents = dbHelper.getAllEvents()
        val matchingEvent = allEvents.find { evt ->
            val evtDate = evt["date"] ?: ""
            val evtUnit = evt["unit"] ?: ""
            val unitMatches = isSameUnit(evtUnit, unitName)
            val dateMatches = isDateCoveredByEvent(evtDate, activeCheckIn, activeMonth)
            unitMatches && dateMatches
        }

        if (matchingEvent != null) {
            currentEventDate = matchingEvent["date"] ?: ""
            val shape = matchingEvent["shape"] ?: ""
            val title = matchingEvent["title"] ?: ""
            val combined = "$shape $title"
            currentStatus = when {
                combined.contains("Check", ignoreCase = true) || combined.contains("Heart", ignoreCase = true) -> "Check-in"
                combined.contains("Reserve", ignoreCase = true) || combined.contains("Diamond", ignoreCase = true) -> "Reserved"
                combined.contains("Maintenance", ignoreCase = true) || combined.contains("Circle", ignoreCase = true) -> "Under Maintenance"
                combined.contains("Not", ignoreCase = true) || combined.contains("Star", ignoreCase = true) -> "Not Available"
                else -> shape.ifBlank { "Available" }
            }
        } else {
            currentEventDate = ""
            val match = dbHelper.getAllUnits().find {
                val uNum = it["unitNumber"]?.toString() ?: ""
                isSameUnit(uNum, unitName)
            }
            if (match != null) {
                val raw = match["status"]?.toString() ?: "Available"
                currentStatus = raw.ifBlank { "Available" }
            }
        }
    }

    val maxDaysInActiveMonth = remember(activeMonth, activeYear) {
        val monthsList = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")
        val monthIdx = monthsList.indexOfFirst { activeMonth.contains(it, ignoreCase = true) }.let { if (it != -1) it else 9 }
        val cal = java.util.Calendar.getInstance().apply {
            clear()
            set(java.util.Calendar.YEAR, activeYear)
            set(java.util.Calendar.MONTH, monthIdx)
            set(java.util.Calendar.DAY_OF_MONTH, 1)
        }
        cal.getActualMaximum(java.util.Calendar.DAY_OF_MONTH)
    }

    var checkInDay by remember(activeCheckIn) { mutableIntStateOf(activeCheckIn) }
    var checkOutDay by remember(activeCheckIn, maxDaysInActiveMonth) { mutableIntStateOf((activeCheckIn + 2).coerceAtMost(maxDaysInActiveMonth)) }
    var monthName by remember(activeMonth) { mutableStateOf(activeMonth) }
    var yearNumber by remember(activeYear) { mutableIntStateOf(activeYear) }

    val shortM = monthName.take(3)
    @Suppress("UNUSED_VARIABLE")
    val scheduleText = if (checkInDay == checkOutDay) {
        "$shortM $checkInDay, $yearNumber"
    } else {
        "$shortM $checkInDay - $shortM $checkOutDay, $yearNumber"
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

        // Top Header Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
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
                    text = "Unit Details",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextDark
                )
            }

            // Edit Unit Info Button (VISIBLE ONLY IN OWNER ACCOUNT)
            if (isOwnerMode) {
                Button(
                    onClick = onEditUnitClick,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit Unit", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Edit Unit", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Large Unit Image Card (Displays Owner Uploaded Photo)
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = CardBg),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
        ) {
            val isValidUri = !imageUri.isNullOrBlank() && (imageUri.startsWith("content://") || imageUri.startsWith("file://") || java.io.File(imageUri).exists())

            if (isValidUri) {
                val fileObj = java.io.File(imageUri)
                val imageModel: Any = if (fileObj.exists()) fileObj else imageUri

                AsyncImage(
                    model = imageModel,
                    contentDescription = unitName,
                    contentScale = ContentScale.Crop,
                    error = painterResource(id = imageResId),
                    placeholder = painterResource(id = imageResId),
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Image(
                    painter = painterResource(id = imageResId),
                    contentDescription = unitName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // Unit Title & Status
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = unitName,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = PurplePrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "$towerName  |  1 Bedroom",
                    fontSize = 14.sp,
                    color = TextMuted,
                    fontWeight = FontWeight.Medium
                )
            }

            val (statusBg, statusTextCol, dotColor) = when (currentStatus) {
                "Available" -> Triple(Color(0xFFE8F5E9), Color(0xFF2E7D32), Color(0xFF00A86B))
                "Reserved" -> Triple(Color(0xFFFFF3E0), Color(0xFFEF6C00), Color(0xFFF39C12))
                else -> Triple(Color(0xFFFFEBEE), Color(0xFFC62828), Color(0xFFE74C3C))
            }

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = statusBg
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(dotColor)
                    )
                    Text(
                        text = currentStatus,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = statusTextCol
                    )
                }
            }
        }

        // Details Info Card
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = CardBg),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Start
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Bed,
                            contentDescription = null,
                            tint = PurplePrimary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(text = "Bed", fontSize = 12.sp, color = TextMuted)
                            Text(text = "1 Bed", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextDark)
                        }
                    }
                }
            }
        }

        // Tag Date Coverage Info Card (Displays explicit Check-in Date and Check-out Date)
        val (inDateStr, outDateStr) = remember(currentEventDate, activeCheckIn, activeMonth, activeYear) {
            if (currentEventDate.isNotBlank() && (currentEventDate.contains("-") || currentEventDate.contains("to", ignoreCase = true))) {
                val parts = currentEventDate.split("-", "to", ignoreCase = true)
                val p1 = parts[0].trim()
                val p2 = parts.getOrNull(1)?.trim() ?: p1
                val yr = Regex("\\b20\\d{2}\\b").find(currentEventDate)?.value ?: activeYear.toString()
                val p1Full = if (p1.contains(yr)) p1 else "$p1, $yr"
                val p2Full = if (p2.contains(yr)) p2 else "$p2, $yr"
                Pair(p1Full, p2Full)
            } else if (currentEventDate.isNotBlank()) {
                Pair(currentEventDate, currentEventDate)
            } else {
                val shortM = activeMonth.take(3)
                val cIn = "$shortM $activeCheckIn, $activeYear"
                val nextDayCal = java.util.Calendar.getInstance().apply {
                    clear()
                    val monthNamesList = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sept", "Oct", "Nov", "Dec")
                    val mIdx = monthNamesList.indexOf(shortM).let { if (it != -1) it else 9 }
                    set(activeYear, mIdx, activeCheckIn)
                    add(java.util.Calendar.DAY_OF_MONTH, 1)
                }
                val monthNamesList = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sept", "Oct", "Nov", "Dec")
                val outM = monthNamesList[nextDayCal.get(java.util.Calendar.MONTH)]
                val outD = nextDayCal.get(java.util.Calendar.DAY_OF_MONTH)
                val outY = nextDayCal.get(java.util.Calendar.YEAR)
                Pair(cIn, "$outM $outD, $outY")
            }
        }

        val fullCoverageRange = if (inDateStr == outDateStr) inDateStr else "$inDateStr - $outDateStr"

        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F5FE)),
            border = BorderStroke(1.dp, Color(0xFFE2D6F5)),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .clickable { onEditTagClick(fullCoverageRange) }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Tag Date Coverage",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextMuted
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Check-in Date Box
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFF3EDFD),
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DateRange,
                                contentDescription = "Check-in",
                                tint = PurplePrimary,
                                modifier = Modifier.size(18.dp)
                            )
                            Column {
                                Text(text = "Check-in Date", fontSize = 10.sp, color = TextMuted)
                                Text(
                                    text = inDateStr,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextDark
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "➞", fontSize = 16.sp, color = PurplePrimary, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.width(6.dp))

                    // Check-out Date Box
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFF3EDFD),
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DateRange,
                                contentDescription = "Check-out",
                                tint = PurplePrimary,
                                modifier = Modifier.size(18.dp)
                            )
                            Column {
                                Text(text = "Check-out Date", fontSize = 10.sp, color = TextMuted)
                                Text(
                                    text = outDateStr,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextDark
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        val isRentedUnit = currentStatus.contains("Rented", ignoreCase = true) || currentStatus.contains("Lease", ignoreCase = true)

        if (isRentedUnit) {
            OutlinedButton(
                onClick = { showBreachContractDialog = true },
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.5.dp, Color(0xFFE53935)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFE53935)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Icon(Icons.Default.Gavel, contentDescription = "Terminate Lease", modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Breach / Terminate Lease Contract", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }

        val isCheckedInUnit = currentStatus.contains("Check-in", ignoreCase = true) || currentStatus.contains("Check in", ignoreCase = true) || currentStatus.contains("Heart", ignoreCase = true)

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (isCheckedInUnit) {
                OutlinedButton(
                    onClick = { showExtendStayDialog = true },
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.5.dp, PurplePrimary),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = PurplePrimary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Icon(Icons.Default.MoreTime, contentDescription = "Extend Stay", modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Extend Stay", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { onEditTagClick(fullCoverageRange) },
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.5.dp, PurplePrimary),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = PurplePrimary),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit Tag", modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Edit Tag", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = { onBookNowClick(fullCoverageRange) },
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary),
                    modifier = Modifier
                        .weight(1.2f)
                        .height(48.dp)
                ) {
                    Text(
                        text = "Book Now",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
    }

    if (showBreachContractDialog) {
        val dbHelper = remember { AzureDatabaseHelper(context) }
        AlertDialog(
            onDismissRequest = { showBreachContractDialog = false },
            title = { Text("Terminate Lease for $unitName", fontWeight = FontWeight.Bold, color = TextDark) },
            text = { Text("Lessee breached or terminated contract early. Change status of $unitName back to Available?", color = TextMuted) },
            confirmButton = {
                Button(
                    onClick = {
                        dbHelper.updateUnitStatus(unitName, "Available")
                        dbHelper.deleteEventsForUnit(unitName)
                        FirebaseCloudSyncManager.deleteEventFromCloud("", unitName)
                        FirebaseCloudSyncManager.pushUnitStatusToCloud(unitName, "Available")
                        currentStatus = "Available"
                        Toast.makeText(context, "Contract terminated! $unitName is now Available.", Toast.LENGTH_SHORT).show()
                        showBreachContractDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935))
                ) {
                    Text("Terminate & Make Available", fontWeight = FontWeight.Bold, color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBreachContractDialog = false }) {
                    Text("Cancel", color = TextMuted)
                }
            },
            containerColor = Color.White,
            shape = RoundedCornerShape(20.dp)
        )
    }

    if (showExtendStayDialog) {
        val dbHelper = remember { AzureDatabaseHelper(context) }
        var extendPreset by remember { mutableStateOf("+1 Day") }
        var calcResult by remember(checkOutDay, monthName, yearNumber, extendPreset) {
            val (hAdd, dAdd) = when (extendPreset) {
                "+3 Hours" -> Pair(3, 0)
                "+6 Hours" -> Pair(6, 0)
                "+12 Hours" -> Pair(12, 0)
                "+22 Hours" -> Pair(22, 0)
                "+1 Day" -> Pair(0, 1)
                "+2 Days" -> Pair(0, 2)
                else -> Pair(0, 1)
            }
            mutableStateOf(calculateExtendedDateTime(checkOutDay, monthName, yearNumber, hAdd, dAdd))
        }

        AlertDialog(
            onDismissRequest = { showExtendStayDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.HourglassTop, contentDescription = null, tint = PurplePrimary, modifier = Modifier.size(22.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "Extend Stay - $unitName", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextDark)
                }
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Select extension duration for $unitName. The new check-out date and time will calculate automatically:",
                        fontSize = 12.sp,
                        color = TextMuted
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("+3 Hours", "+6 Hours", "+12 Hours", "+22 Hours", "+1 Day", "+2 Days").forEach { preset ->
                            val isSel = preset == extendPreset
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSel) PurplePrimary else Color(0xFFF3EDFD),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        extendPreset = preset
                                        val (hAdd, dAdd) = when (preset) {
                                            "+3 Hours" -> Pair(3, 0)
                                            "+6 Hours" -> Pair(6, 0)
                                            "+12 Hours" -> Pair(12, 0)
                                            "+22 Hours" -> Pair(22, 0)
                                            "+1 Day" -> Pair(0, 1)
                                            "+2 Days" -> Pair(0, 2)
                                            else -> Pair(0, 1)
                                        }
                                        calcResult = calculateExtendedDateTime(checkOutDay, monthName, yearNumber, hAdd, dAdd)
                                    }
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        text = preset,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSel) Color.White else TextDark
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = Color(0xFFF3E5F5))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFF3EDFD),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("Calculated Check-out Date", fontSize = 10.sp, color = TextMuted)
                                Text("${calcResult.first} 📅", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextDark)
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFF3EDFD),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("Calculated Check-out Hour", fontSize = 10.sp, color = TextMuted)
                                Text("${calcResult.second} 🕒", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextDark)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val newTargetDate = "$shortM $checkInDay - ${calcResult.first}"
                        val newTime = calcResult.second
                        val agentRole = if (isOwnerMode) "Admin" else "Agent"

                        dbHelper.deleteEventByDateAndUnit(newTargetDate, unitName)
                        dbHelper.insertEvent(
                            title = "Staycation - Extend Stay",
                            date = newTargetDate,
                            time = newTime,
                            agent = agentRole,
                            unit = unitName,
                            shape = "Extend Stay",
                            color = 0xFF9C27B0.toLong()
                        )
                        dbHelper.updateUnitStatus(unitName, "Extend Stay")

                        // Push both the event and status update to Cloud Firestore!
                        FirebaseCloudSyncManager.pushEventToCloud(
                            mapOf(
                                "title" to "Staycation - Extend Stay",
                                "date" to newTargetDate,
                                "time" to newTime,
                                "agent" to agentRole,
                                "unit" to unitName,
                                "shape" to "Extend Stay",
                                "color" to 0xFF9C27B0.toLong().toString()
                            )
                        )
                        FirebaseCloudSyncManager.pushUnitStatusToCloud(unitName, "Extend Stay")
                        currentStatus = "Extend Stay"

                        Toast.makeText(context, "Stay extended ($extendPreset)! New Check-out: ${calcResult.first} at ${calcResult.second}", Toast.LENGTH_LONG).show()
                        showExtendStayDialog = false

                        EmailTemplateHelper.sendBookingEmail(
                            context = context,
                            ownerEmail = "owner.azure@gmail.com",
                            agentEmail = if (isOwnerMode) "admin@azure.com" else "agent@azure.com",
                            agentName = if (isOwnerMode) "Admin Owner" else "Agent",
                            unitName = unitName,
                            statusLabel = "Extend Stay ($extendPreset)",
                            dateText = newTargetDate
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Confirm Extension", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showExtendStayDialog = false }) {
                    Text("Cancel", color = TextMuted)
                }
            },
            containerColor = Color.White,
            shape = RoundedCornerShape(20.dp)
        )
    }

    if (showDateRangePicker) {
        AlertDialog(
            onDismissRequest = { showDateRangePicker = false },
            title = {
                Text(
                    text = "Select Check-in & Check-out",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextDark
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Choose booking dates for $unitName",
                        fontSize = 13.sp,
                        color = TextMuted
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Check-in Date:", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextDark)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { if (checkInDay > 1) checkInDay-- }) {
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Prev", tint = PurplePrimary)
                            }
                            Text("$shortM $checkInDay, $yearNumber", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = PurplePrimary)
                            IconButton(onClick = { if (checkInDay < checkOutDay - 1) checkInDay++ }) {
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next", tint = PurplePrimary)
                            }
                        }
                    }

                    HorizontalDivider(color = Color(0xFFF3E5F5))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Check-out Date:", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextDark)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { if (checkOutDay > checkInDay + 1) checkOutDay-- }) {
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Prev", tint = PurplePrimary)
                            }
                            Text("$shortM $checkOutDay, $yearNumber", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = PurplePrimary)
                            IconButton(onClick = { if (checkOutDay < maxDaysInActiveMonth) checkOutDay++ }) {
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next", tint = PurplePrimary)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDateRangePicker = false
                        Toast.makeText(context, "Schedule set to $shortM $checkInDay - $shortM $checkOutDay!", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Confirm Schedule", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDateRangePicker = false }) {
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
fun UnitDetailsScreenPreview() {
    AzureCalendarTheme {
        UnitDetailsScreen()
    }
}

fun calculateExtendedDateTime(baseDay: Int, baseMonthStr: String, baseYear: Int, hoursToAdd: Int, daysToAdd: Int): Pair<String, String> {
    return try {
        val monthNamesList = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sept", "Oct", "Nov", "Dec")
        val mIdx = monthNamesList.indexOfFirst { baseMonthStr.contains(it, ignoreCase = true) }.coerceAtLeast(0)

        val cal = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.YEAR, baseYear)
            set(java.util.Calendar.MONTH, mIdx)
            set(java.util.Calendar.DAY_OF_MONTH, baseDay)
            set(java.util.Calendar.HOUR_OF_DAY, 12)
            set(java.util.Calendar.MINUTE, 0)
        }

        if (hoursToAdd > 0) cal.add(java.util.Calendar.HOUR_OF_DAY, hoursToAdd)
        if (daysToAdd > 0) cal.add(java.util.Calendar.DAY_OF_MONTH, daysToAdd)

        val newDay = cal.get(java.util.Calendar.DAY_OF_MONTH)
        val newMIdx = cal.get(java.util.Calendar.MONTH)
        val newMonthStr = monthNamesList.getOrElse(newMIdx) { baseMonthStr }
        val newYear = cal.get(java.util.Calendar.YEAR)

        val hour24 = cal.get(java.util.Calendar.HOUR_OF_DAY)
        val isPM = hour24 >= 12
        val hour12 = when {
            hour24 == 0 || hour24 == 12 -> 12
            hour24 > 12 -> hour24 - 12
            else -> hour24
        }
        val formattedTime = "$hour12:00 ${if (isPM) "PM" else "AM"}"
        val formattedDate = "$newMonthStr $newDay, $newYear"

        Pair(formattedDate, formattedTime)
    } catch (_: Throwable) {
        Pair("$baseMonthStr ${baseDay + daysToAdd}, $baseYear", "12:00 PM")
    }
}