package com.example.azurecalendar

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.azurecalendar.ui.theme.AzureCalendarTheme
import java.io.File

private val PurplePrimary get() = AzureCalendarColors.PurplePrimary
private val ScreenBg get() = AzureCalendarColors.ScreenBg
private val CardBg get() = AzureCalendarColors.CardBg
private val TextDark get() = AzureCalendarColors.TextDark
private val TextMuted get() = AzureCalendarColors.TextMuted
private val BorderPurple get() = AzureCalendarColors.BorderPurple

fun parseMonthIndex(monthStr: String): Int {
    if (monthStr.isBlank()) return java.util.Calendar.getInstance().get(java.util.Calendar.MONTH)
    val monthMap = mapOf(
        "jan" to 0, "feb" to 1, "mar" to 2, "apr" to 3, "may" to 4, "jun" to 5,
        "jul" to 6, "aug" to 7, "sep" to 8, "sept" to 8, "oct" to 9, "nov" to 10, "dec" to 11,
        "january" to 0, "february" to 1, "march" to 2, "april" to 3, "june" to 5,
        "july" to 6, "august" to 7, "september" to 8, "october" to 9, "november" to 10, "december" to 11
    )
    return monthMap.entries.find { monthStr.contains(it.key, ignoreCase = true) }?.value
        ?: java.util.Calendar.getInstance().get(java.util.Calendar.MONTH)
}

fun isDateCoveredByEvent(evtDate: String, dayNumber: Int, currentMonthName: String = "", currentYearNumber: Int = 0): Boolean {
    if (evtDate.isBlank()) return false

    val realTimeCal = java.util.Calendar.getInstance()
    val targetYear = if (currentYearNumber > 2000) currentYearNumber else realTimeCal.get(java.util.Calendar.YEAR)

    val monthMap = mapOf(
        "jan" to 1, "feb" to 2, "mar" to 3, "apr" to 4, "may" to 5, "jun" to 6,
        "jul" to 7, "aug" to 8, "sep" to 9, "sept" to 9, "oct" to 10, "nov" to 11, "dec" to 12,
        "january" to 1, "february" to 2, "march" to 3, "april" to 4, "june" to 6,
        "july" to 7, "august" to 8, "september" to 9, "october" to 10, "november" to 11, "december" to 12
    )

    val activeMonthIdx = if (currentMonthName.isNotBlank()) {
        monthMap.entries.find { currentMonthName.contains(it.key, ignoreCase = true) }?.value ?: (realTimeCal.get(java.util.Calendar.MONTH) + 1)
    } else (realTimeCal.get(java.util.Calendar.MONTH) + 1)

    val evtYear = Regex("\\b20\\d{2}\\b").find(evtDate)?.value?.toIntOrNull()
    if (evtYear != null && evtYear != targetYear) {
        return false
    }

    if (evtDate.contains("-") || evtDate.contains("to", ignoreCase = true)) {
        val parts = evtDate.split("-", "to", ignoreCase = true)
        if (parts.size >= 2) {
            val part1 = parts[0].trim()
            val part2 = parts[1].trim()

            val startNum = Regex("\\b([1-9]|[12][0-9]|3[01])\\b").find(part1)?.value?.toIntOrNull()
            val endNum = Regex("\\b([1-9]|[12][0-9]|3[01])\\b").find(part2)?.value?.toIntOrNull()

            val startMonth = monthMap.entries.find { part1.contains(it.key, ignoreCase = true) }?.value ?: activeMonthIdx
            val endMonth = monthMap.entries.find { part2.contains(it.key, ignoreCase = true) }?.value ?: startMonth

            if (startNum != null && endNum != null) {
                if (startMonth == endMonth) {
                    if (activeMonthIdx == startMonth) {
                        return dayNumber in minOf(startNum, endNum)..maxOf(startNum, endNum)
                    }
                } else {
                    return when (activeMonthIdx) {
                        startMonth -> dayNumber >= startNum
                        endMonth -> dayNumber <= endNum
                        in (startMonth + 1) until endMonth -> true
                        else -> false
                    }
                }
            }
        }
    }

    if (evtDate.contains("/")) {
        val slashParts = evtDate.split("/").mapNotNull { it.trim().toIntOrNull() }
        if (slashParts.size >= 2) {
            val m = slashParts[0]
            val d = slashParts[1]
            if (m == activeMonthIdx && d == dayNumber) return true
        }
    }

    val evtMonth = monthMap.entries.find { evtDate.contains(it.key, ignoreCase = true) }?.value ?: activeMonthIdx
    if (evtMonth != activeMonthIdx) {
        return false
    }

    val daysInEvt = Regex("\\b([1-9]|[12][0-9]|3[01])\\b").findAll(evtDate).mapNotNull { it.value.toIntOrNull() }.toList()
    return daysInEvt.contains(dayNumber)
}

fun isSameUnit(unit1: String, unit2: String): Boolean {
    if (unit1.isBlank() || unit2.isBlank()) return false
    val isGeneral1 = unit1.equals("Event", ignoreCase = true) || unit1.contains("Meeting", ignoreCase = true) || unit1.contains("Owner Event", ignoreCase = true)
    val isGeneral2 = unit2.equals("Event", ignoreCase = true) || unit2.contains("Meeting", ignoreCase = true) || unit2.contains("Owner Event", ignoreCase = true)
    if (isGeneral1 || isGeneral2) return false

    val clean1 = unit1.replace("Unit", "", ignoreCase = true).trim()
    val clean2 = unit2.replace("Unit", "", ignoreCase = true).trim()
    if (clean1.isBlank() || clean2.isBlank()) return false
    return clean1.equals(clean2, ignoreCase = true)
}

data class UnitCatalogItem(
    val id: String,
    val unitNumber: String,
    val tower: String,
    val description: String,
    val status: String,
    val imageResId: Int,
    val imageUri: String? = null,
    val rate: Double = 2500.0
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnitsDirectoryScreen(
    @Suppress("UNUSED_PARAMETER") onBackClick: () -> Unit = {},
    onViewScheduleClick: (unitNumber: String, tower: String, description: String, status: String, imageUri: String) -> Unit = { _, _, _, _, _ -> },
    onEditUnitClick: (unitNumber: String, tower: String, description: String, status: String, imageUri: String) -> Unit = { _, _, _, _, _ -> },
    isOwnerMode: Boolean = false,
    initialDayNumber: Int = 11,
    initialMonthIndex: Int = -1,
    initialYearNumber: Int = -1,
    onDateChange: (Int) -> Unit = {},
    onFullDateChange: (day: Int, monthIndex: Int, year: Int) -> Unit = { _, _, _ -> }
) {
    val context = LocalContext.current
    val dbHelper = remember { AzureDatabaseHelper(context) }

    val realTimeCal = remember { java.util.Calendar.getInstance() }
    val currentRealYear = realTimeCal.get(java.util.Calendar.YEAR)
    val currentRealDay = realTimeCal.get(java.util.Calendar.DAY_OF_MONTH)
    val currentRealMonthIdx = realTimeCal.get(java.util.Calendar.MONTH)

    var selectedDayNumber by remember(initialDayNumber) { mutableIntStateOf(if (initialDayNumber == 11) currentRealDay else initialDayNumber) }
    var selectedMonthIndex by remember(initialMonthIndex) { mutableIntStateOf(if (initialMonthIndex in 0..11) initialMonthIndex else currentRealMonthIdx) }
    var selectedYearNumber by remember(initialYearNumber) { mutableIntStateOf(if (initialYearNumber > 2020) initialYearNumber else currentRealYear) }

    val emitDateChange = { day: Int, month: Int, year: Int ->
        onDateChange(day)
        onFullDateChange(day, month, year)
    }

    val monthsList = remember {
        listOf(
            "January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December"
        )
    }
    val shortMonthName = monthsList[selectedMonthIndex].take(3)

    var showDatePickerDialog by remember { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedFilter by rememberSaveable { mutableStateOf("All") }
    val listState = rememberLazyListState()
    val filters = listOf("All", "Available", "Check-in", "Reserved", "Rented", "Under Maintenance")

    val dbEventsState = remember { mutableStateOf<List<Map<String, String>>>(emptyList()) }
    val dbUnitsState = remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }

    var timeTrigger by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(15000)
            timeTrigger++
        }
    }

    val isPreview = LocalInspectionMode.current
    DisposableEffect(isPreview) {
        if (isPreview) {
            onDispose { }
        } else {
            FirebaseCloudSyncManager.startRealtimeCloudListener(context, forceRestart = false)
            onDispose { }
        }
    }

    LaunchedEffect(Unit, selectedDayNumber, selectedMonthIndex, selectedYearNumber, timeTrigger, FirebaseCloudSyncManager.globalCloudSyncTrigger.intValue) {
        dbEventsState.value = dbHelper.getAllEvents()
        dbUnitsState.value = dbHelper.getAllUnits()
    }

    val deletedPrefs = remember { context.getSharedPreferences("azure_calendar_deleted_units", android.content.Context.MODE_PRIVATE) }
    val deletedSet = remember(dbUnitsState.value, FirebaseCloudSyncManager.globalCloudSyncTrigger.intValue) {
        deletedPrefs.getStringSet("deleted_unit_numbers", emptySet()).orEmpty()
    }

    val units = remember(dbUnitsState.value, dbEventsState.value, selectedDayNumber, selectedMonthIndex, selectedYearNumber, deletedSet) {
        val defaultUnitsList = listOf(
            UnitCatalogItem("default-1", "Unit 12A", "Tower 1", "1 Bedroom", "Available", R.drawable.img),
            UnitCatalogItem("default-2", "Unit 15B", "Azure Tower", "1 Bedroom", "Available", R.drawable.img),
            UnitCatalogItem("default-3", "Unit 08C", "Azure Tower", "1 Bedroom", "Available", R.drawable.img),
            UnitCatalogItem("default-4", "Unit 06D", "Azure Tower", "1 Bedroom", "Available", R.drawable.img)
        )

        val customUnitsFromDb = dbUnitsState.value
            .filter { dbMap ->
                val num = dbMap["unitNumber"]?.toString() ?: ""
                val clean = num.replace("Unit", "", ignoreCase = true).trim()
                clean.isNotBlank() && !clean.equals("Event", ignoreCase = true) && !clean.contains("Meeting", ignoreCase = true) && !clean.contains("Owner Event", ignoreCase = true)
            }
            .mapIndexed { idx, dbMap ->
                val num = dbMap["unitNumber"]?.toString() ?: "Unit ${idx + 1}"
                val tower = dbMap["tower"]?.toString() ?: "Azure Tower"
                val status = dbMap["status"]?.toString() ?: "Available"
                val imageUri = dbMap["image_uri"]?.toString()?.ifBlank { null } ?: dbMap["imageUri"]?.toString()?.ifBlank { null } ?: dbMap["image"]?.toString()?.ifBlank { null }
                val desc = dbMap["description"]?.toString() ?: "1 Bedroom"
                val rawRate = dbMap["rate"]
                val rateVal = when (rawRate) {
                    is Number -> rawRate.toDouble()
                    is String -> rawRate.toDoubleOrNull() ?: 2500.0
                    else -> 2500.0
                }
                val unitName = if (num.contains("Unit", ignoreCase = true)) num else "Unit $num"
                UnitCatalogItem(
                    id = "db-${idx + 1}",
                    unitNumber = unitName,
                    tower = tower,
                    description = desc,
                    status = status,
                    imageResId = R.drawable.img,
                    imageUri = imageUri,
                    rate = rateVal
                )
            }

        val allUnitsList = if (customUnitsFromDb.isNotEmpty()) {
            customUnitsFromDb.filter { unit ->
                deletedSet.none { isSameUnit(it, unit.unitNumber) }
            }
        } else {
            defaultUnitsList.filter { unit ->
                deletedSet.none { isSameUnit(it, unit.unitNumber) }
            }
        }

        allUnitsList.map { unitItem ->
            val matchingEvent = dbEventsState.value.filter { evt ->
                val evtDate = evt["date"] ?: ""
                val evtUnit = evt["unit"] ?: ""

                val unitMatches = isSameUnit(evtUnit, unitItem.unitNumber)
                val dateMatches = isDateCoveredByEvent(evtDate, selectedDayNumber, shortMonthName, selectedYearNumber)

                unitMatches && dateMatches
            }.firstOrNull { evt ->
                val shape = (evt["shape"] ?: "").lowercase()
                val title = (evt["title"] ?: "").lowercase()
                val combined = "$shape $title"
                !combined.contains("available") || combined.contains("not")
            } ?: dbEventsState.value.find { evt ->
                val evtDate = evt["date"] ?: ""
                val evtUnit = evt["unit"] ?: ""

                val unitMatches = isSameUnit(evtUnit, unitItem.unitNumber)
                val dateMatches = isDateCoveredByEvent(evtDate, selectedDayNumber, shortMonthName, selectedYearNumber)

                unitMatches && dateMatches
            }

            val dynamicStatus = if (matchingEvent != null) {
                val shape = matchingEvent["shape"] ?: ""
                val title = matchingEvent["title"] ?: ""
                val combined = "$shape $title"
                when {
                    combined.contains("Check", ignoreCase = true) || combined.contains("Heart", ignoreCase = true) -> "Check-in"
                    combined.contains("Reserve", ignoreCase = true) || combined.contains("Diamond", ignoreCase = true) -> "Reserved"
                    combined.contains("Maintenance", ignoreCase = true) || combined.contains("Circle", ignoreCase = true) -> "Under Maintenance"
                    combined.contains("Not", ignoreCase = true) || combined.contains("Star", ignoreCase = true) || combined.contains("Rent", ignoreCase = true) -> "Rented"
                    else -> shape.ifBlank { "Available" }
                }
            } else {
                "Available"
            }

            unitItem.copy(status = dynamicStatus)
        }
    }

    val filteredUnits = remember(searchQuery, selectedFilter, units) {
        units.filter { unit ->
            val matchesSearch = unit.unitNumber.contains(searchQuery, ignoreCase = true) ||
                    unit.tower.contains(searchQuery, ignoreCase = true)
            val matchesFilter = when (selectedFilter) {
                "All" -> true
                "Rented" -> unit.status.contains("Rented", ignoreCase = true) || unit.status.contains("Star", ignoreCase = true)
                else -> unit.status.contains(selectedFilter, ignoreCase = true)
            }
            matchesSearch && matchesFilter
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .background(ScreenBg)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Units",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextDark
                )

                Surface(
                    shape = RoundedCornerShape(30.dp),
                    color = Color(0xFFF3EDFD),
                    border = BorderStroke(1.dp, Color(0xFFE1BEE7))
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .clickable { showDatePickerDialog = true }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CalendarMonth,
                                contentDescription = "Select Date",
                                tint = Color(0xFFE53935),
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = "$shortMonthName $selectedDayNumber, $selectedYearNumber",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF2C1A36)
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = Color(0xFFE53935),
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .clickable {
                                    val realCal = java.util.Calendar.getInstance()
                                    selectedDayNumber = realCal.get(java.util.Calendar.DAY_OF_MONTH)
                                    selectedMonthIndex = realCal.get(java.util.Calendar.MONTH)
                                    selectedYearNumber = realCal.get(java.util.Calendar.YEAR)
                                    emitDateChange(selectedDayNumber, selectedMonthIndex, selectedYearNumber)
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Today,
                                    contentDescription = "Today",
                                    tint = Color.White,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = "Today",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search unit number...", color = TextMuted, fontSize = 14.sp) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = PurplePrimary
                    )
                },
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = CardBg,
                    unfocusedContainerColor = CardBg,
                    focusedBorderColor = BorderPurple,
                    unfocusedBorderColor = BorderPurple
                ),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                filters.forEach { filter ->
                    val isSelected = filter == selectedFilter
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = if (isSelected) PurplePrimary else Color(0xFFF3EDFD),
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .clickable { selectedFilter = filter }
                    ) {
                        Box(
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = filter,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) Color.White else TextMuted
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
        }

        items(filteredUnits) { unit ->
            val isAvailable = unit.status.equals("Available", ignoreCase = true)
            val isRented = unit.status.contains("Rented", ignoreCase = true)
            val displayStatusText = unit.status

            val (statusBg, statusTextCol, dotColor) = when {
                isAvailable -> Triple(Color(0xFFE8F5E9), Color(0xFF2E7D32), Color(0xFF00A86B))
                unit.status.contains("Check", ignoreCase = true) -> Triple(Color(0xFFFFEBEE), Color(0xFFC2185B), Color(0xFFE91E63))
                unit.status.contains("Reserve", ignoreCase = true) -> Triple(Color(0xFFFFF3E0), Color(0xFFEF6C00), Color(0xFFF39C12))
                unit.status.contains("Maintenance", ignoreCase = true) -> Triple(Color(0xFFE3F2FD), Color(0xFF1976D2), Color(0xFF2196F3))
                isRented -> Triple(Color(0xFFFFEBEE), Color(0xFFC62828), Color(0xFFE74C3C))
                else -> Triple(Color(0xFFFFEBEE), Color(0xFFC62828), Color(0xFFE74C3C))
            }

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isAvailable) CardBg else Color(0xFFF8F4FA)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = if (isAvailable) 1.dp else 0.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onViewScheduleClick(unit.unitNumber, unit.tower, unit.description, unit.status, unit.imageUri ?: "")
                    }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        UnitImageThumbnail(
                            imageUri = unit.imageUri,
                            unitNumber = unit.unitNumber,
                            modifier = Modifier
                                .size(75.dp)
                                .clip(RoundedCornerShape(12.dp))
                        )

                        Column {
                            Text(
                                text = unit.unitNumber,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = PurplePrimary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${unit.tower} • ₱${unit.rate.toInt()}",
                                fontSize = 13.sp,
                                color = TextMuted,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = unit.description,
                                fontSize = 12.sp,
                                color = TextMuted
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = statusBg
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
                                        .background(dotColor)
                                )
                                Text(
                                    text = displayStatusText,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = statusTextCol
                                )
                            }
                        }

                        if (isOwnerMode) {
                            IconButton(
                                onClick = { onEditUnitClick(unit.unitNumber, unit.tower, unit.description, unit.status, unit.imageUri ?: "") },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Edit Unit",
                                    tint = PurplePrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(20.dp))
        }
    }

    if (showDatePickerDialog) {
        var pickerMonthIndex by remember { mutableIntStateOf(selectedMonthIndex) }
        var pickerYear by remember { mutableIntStateOf(selectedYearNumber) }
        val pickerMonthsList = listOf(
            "January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December"
        )

        AlertDialog(
            onDismissRequest = { showDatePickerDialog = false },
            title = {
                Text(
                    text = "Select Date & Month",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF2C1A36)
                )
            },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = {
                            if (pickerMonthIndex > 0) {
                                pickerMonthIndex--
                            } else {
                                pickerMonthIndex = 11
                                pickerYear--
                            }
                        }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                contentDescription = "Prev Month",
                                tint = PurplePrimary
                            )
                        }

                        Text(
                            text = "${pickerMonthsList[pickerMonthIndex]} $pickerYear",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = PurplePrimary
                        )

                        IconButton(onClick = {
                            if (pickerMonthIndex < 11) {
                                pickerMonthIndex++
                            } else {
                                pickerMonthIndex = 0
                                pickerYear++
                            }
                        }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = "Next Month",
                                tint = PurplePrimary
                            )
                        }
                    }

                    HorizontalDivider(color = Color(0xFFF3E5F5))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        listOf("M", "T", "W", "T", "F", "S", "S").forEach { dayHeader ->
                            Text(
                                text = dayHeader,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF7B6287)
                            )
                        }
                    }

                    val pickerMaxDays = remember(pickerMonthIndex, pickerYear) {
                        val cal = java.util.Calendar.getInstance().apply {
                            clear()
                            set(java.util.Calendar.YEAR, pickerYear)
                            set(java.util.Calendar.MONTH, pickerMonthIndex)
                            set(java.util.Calendar.DAY_OF_MONTH, 1)
                        }
                        cal.getActualMaximum(java.util.Calendar.DAY_OF_MONTH)
                    }

                    val pickerOffset = remember(pickerMonthIndex, pickerYear) {
                        val cal = java.util.Calendar.getInstance().apply {
                            clear()
                            set(java.util.Calendar.YEAR, pickerYear)
                            set(java.util.Calendar.MONTH, pickerMonthIndex)
                            set(java.util.Calendar.DAY_OF_MONTH, 1)
                        }
                        val dow = cal.get(java.util.Calendar.DAY_OF_WEEK)
                        (dow + 5) % 7
                    }

                    val totalPickerCells = pickerOffset + pickerMaxDays
                    val pickerShortMonthName = pickerMonthsList[pickerMonthIndex].take(3)

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(7),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 280.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        userScrollEnabled = false
                    ) {
                        items(count = totalPickerCells) { index ->
                            if (index < pickerOffset) {
                                Box(modifier = Modifier.size(32.dp))
                            } else {
                                val dayNum = index - pickerOffset + 1
                                val isSelected = dayNum == selectedDayNumber && pickerMonthIndex == selectedMonthIndex && pickerYear == selectedYearNumber

                                val hasEvents = remember(dbEventsState.value, dayNum, pickerMonthIndex, pickerYear) {
                                    dbEventsState.value.any { evt ->
                                        val evtDate = evt["date"] ?: ""
                                        val monthMatches = evtDate.contains(pickerShortMonthName, ignoreCase = true) || evtDate.contains(pickerMonthsList[pickerMonthIndex], ignoreCase = true)
                                        val yearMatches = evtDate.contains(pickerYear.toString())
                                        monthMatches && yearMatches && isDateCoveredByEvent(evtDate, dayNum, pickerMonthsList[pickerMonthIndex])
                                    }
                                }

                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .clickable {
                                            selectedDayNumber = dayNum
                                            selectedMonthIndex = pickerMonthIndex
                                            selectedYearNumber = pickerYear
                                            emitDateChange(dayNum, pickerMonthIndex, pickerYear)
                                            showDatePickerDialog = false
                                        }
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = if (isSelected) PurplePrimary else Color.Transparent,
                                        modifier = Modifier.size(26.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = dayNum.toString(),
                                                fontSize = 12.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                color = if (isSelected) Color.White else Color(0xFF2C1A36)
                                            )
                                        }
                                    }
                                    if (hasEvents) {
                                        Box(
                                            modifier = Modifier
                                                .size(3.dp)
                                                .clip(CircleShape)
                                                .background(if (isSelected) Color.White else PurplePrimary)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDatePickerDialog = false }) {
                    Text("Close", color = PurplePrimary, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = Color.White,
            shape = RoundedCornerShape(20.dp)
        )
    }


}

@Composable
fun UnitImageThumbnail(
    imageUri: String?,
    unitNumber: String,
    modifier: Modifier = Modifier,
    placeholderRes: Int = R.drawable.img
) {
    val context = LocalContext.current
    val bitmap = remember(imageUri) {
        val raw = imageUri?.trim().orEmpty()
        if (raw.isBlank()) return@remember null

        try {
            val isBase64 = raw.contains("base64,", ignoreCase = true) ||
                    raw.startsWith("data:image", ignoreCase = true) ||
                    raw.startsWith("/9j/", ignoreCase = true) ||
                    raw.startsWith("iVBORw0KG", ignoreCase = true) ||
                    raw.length > 100

            if (isBase64) {
                val base64Part = if (raw.contains("base64,", ignoreCase = true)) {
                    raw.substringAfter("base64,").trim()
                } else if (raw.startsWith("data:image", ignoreCase = true)) {
                    raw.substringAfter(";").substringAfter(",").trim()
                } else {
                    raw.trim()
                }.replace("\\s+".toRegex(), "")

                val bytes = try {
                    Base64.decode(base64Part, Base64.NO_WRAP)
                } catch (_: Exception) {
                    try {
                        Base64.decode(base64Part, Base64.DEFAULT)
                    } catch (_: Exception) {
                        Base64.decode(base64Part, Base64.URL_SAFE)
                    }
                }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            } else if (raw.startsWith("file://", ignoreCase = true) || raw.startsWith("/data/", ignoreCase = true)) {
                val cleanPath = if (raw.startsWith("file://", ignoreCase = true)) Uri.parse(raw).path ?: raw.substringAfter("file://") else raw
                val file = java.io.File(cleanPath)
                if (file.exists() && file.length() > 0) {
                    BitmapFactory.decodeFile(file.absolutePath)
                } else null
            } else if (raw.startsWith("content://", ignoreCase = true)) {
                context.contentResolver.openInputStream(Uri.parse(raw))?.use { stream ->
                    BitmapFactory.decodeStream(stream)
                }
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = unitNumber,
            contentScale = ContentScale.Crop,
            modifier = modifier
        )
    } else {
        Image(
            painter = painterResource(id = placeholderRes),
            contentDescription = unitNumber,
            contentScale = ContentScale.Crop,
            modifier = modifier
        )
    }
}

@Preview(showBackground = true)
@Composable
fun UnitsDirectoryScreenPreview() {
    AzureCalendarTheme {
        UnitsDirectoryScreen()
    }
}