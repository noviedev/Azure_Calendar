package com.example.azurecalendar

import androidx.activity.compose.BackHandler
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import coil.compose.AsyncImage
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
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

private val PurplePrimary get() = AzureCalendarColors.PurplePrimary
private val ScreenBg = Color(0xFFFAF5FC)
private val CardBg = Color(0xFFFFFFFF)
private val TextDark = Color(0xFF2C1A36)
private val TextMuted = Color(0xFF7B6287)
private val BorderPurple = Color(0xFFE0D0EC)

fun isDateCoveredByEvent(evtDate: String, dayNumber: Int, currentMonthName: String = ""): Boolean {
    if (evtDate.isBlank()) return false

    val monthNamesList = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")

    // 1. If evtDate represents a range e.g. "Oct 30 - Nov 2, 2026"
    if (evtDate.contains("-") || evtDate.contains("to", ignoreCase = true)) {
        val parts = evtDate.split("-", "to", ignoreCase = true)
        if (parts.size >= 2) {
            val part1 = parts[0].trim()
            val part2 = parts[1].trim()

            val startNum = Regex("\\b([1-9]|[12][0-9]|3[01])\\b").find(part1)?.value?.toIntOrNull()
            val endNum = Regex("\\b([1-9]|[12][0-9]|3[01])\\b").find(part2)?.value?.toIntOrNull()

            val startMonth = monthNamesList.find { part1.contains(it, ignoreCase = true) }
            val endMonth = monthNamesList.find { part2.contains(it, ignoreCase = true) }

            if (startNum != null && endNum != null) {
                if (startMonth != null && endMonth != null && startMonth != endMonth && currentMonthName.isNotBlank()) {
                    val activeM = currentMonthName.take(3).lowercase()
                    if (activeM == startMonth) {
                        return dayNumber >= startNum
                    } else if (activeM == endMonth) {
                        return dayNumber <= endNum
                    }
                }

                val startDay = minOf(startNum, endNum)
                val endDay = maxOf(startNum, endNum)
                return dayNumber in startDay..endDay
            }
        }
    }

    // 2. Exact single date match
    val dayNums = Regex("\\b([1-9]|[12][0-9]|3[01])\\b")
        .findAll(evtDate)
        .mapNotNull { it.value.toIntOrNull() }
        .toList()

    return dayNums.contains(dayNumber)
}

fun isSameUnit(unit1: String, unit2: String): Boolean {
    if (unit1.isEmpty() || unit2.isEmpty()) return false
    val clean1 = unit1.replace("Unit", "", ignoreCase = true).trim()
    val clean2 = unit2.replace("Unit", "", ignoreCase = true).trim()
    return clean1.equals(clean2, ignoreCase = true)
}

data class UnitCatalogItem(
    val id: String,
    val unitNumber: String,
    val tower: String,
    val description: String,
    val status: String, // "Available", "Check-in", "Reserved", "Under Maintenance", "Not Available"
    val imageResId: Int,
    val imageUri: String? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnitsDirectoryScreen(
    @Suppress("UNUSED_PARAMETER") onBackClick: () -> Unit = {},
    onViewScheduleClick: (unitNumber: String, tower: String, description: String, status: String, imageUri: String) -> Unit = { _, _, _, _, _ -> },
    onEditUnitClick: (unitNumber: String, tower: String, description: String, status: String, imageUri: String) -> Unit = { _, _, _, _, _ -> },
    isOwnerMode: Boolean = false,
    initialDayNumber: Int = 11,
    onDateChange: (Int) -> Unit = {}
) {
    val context = LocalContext.current
    val dbHelper = remember { AzureDatabaseHelper(context) }

    val realTimeCal = remember { java.util.Calendar.getInstance() }
    val currentRealYear = realTimeCal.get(java.util.Calendar.YEAR)
    val currentRealDay = realTimeCal.get(java.util.Calendar.DAY_OF_MONTH)
    val currentRealMonthIdx = realTimeCal.get(java.util.Calendar.MONTH)

    var selectedDayNumber by remember(initialDayNumber) { mutableIntStateOf(if (initialDayNumber == 11) currentRealDay else initialDayNumber) }
    var selectedMonthIndex by remember { mutableIntStateOf(currentRealMonthIdx) }
    var selectedYearNumber by remember { mutableIntStateOf(currentRealYear) }

    val monthsList = remember {
        listOf(
            "January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December"
        )
    }
    val shortMonthName = monthsList[selectedMonthIndex].take(3)

    var showDatePickerDialog by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("All") }
    val filters = listOf("All", "Available", "Check-in", "Reserved", "Rented", "Under Maintenance", "Not Available")

    val dbEventsState = remember { mutableStateOf<List<Map<String, String>>>(emptyList()) }
    val dbUnitsState = remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }

    LaunchedEffect(Unit, selectedDayNumber, selectedMonthIndex, selectedYearNumber, FirebaseCloudSyncManager.globalCloudSyncTrigger.intValue) {
        dbEventsState.value = dbHelper.getAllEvents()
        dbUnitsState.value = dbHelper.getAllUnits()
    }

    val deletedPrefs = remember { context.getSharedPreferences("azure_calendar_deleted_units", android.content.Context.MODE_PRIVATE) }
    val deletedSet = remember(dbUnitsState.value) {
        deletedPrefs.getStringSet("deleted_unit_numbers", emptySet()).orEmpty()
    }

    val units = remember(dbUnitsState.value, dbEventsState.value, selectedDayNumber, selectedMonthIndex, selectedYearNumber, deletedSet) {
        val defaultUnitsList = listOf(
            UnitCatalogItem("default-1", "Unit 12A", "Tower 1", "1 Bedroom", "Available", R.drawable.img),
            UnitCatalogItem("default-2", "Unit 15B", "Azure Tower", "1 Bedroom", "Available", R.drawable.img),
            UnitCatalogItem("default-3", "Unit 08C", "Azure Tower", "1 Bedroom", "Available", R.drawable.img),
            UnitCatalogItem("default-4", "Unit 06D", "Azure Tower", "1 Bedroom", "Available", R.drawable.img)
        )

        val customUnitsFromDb = dbUnitsState.value.mapIndexed { idx, dbMap ->
            val num = dbMap["unitNumber"]?.toString() ?: "Unit ${idx + 1}"
            val tower = dbMap["tower"]?.toString() ?: "Azure Tower"
            val status = dbMap["status"]?.toString() ?: "Available"
            val imageUri = dbMap["image_uri"]?.toString()
            val desc = dbMap["description"]?.toString() ?: "1 Bedroom"
            val unitName = if (num.contains("Unit", ignoreCase = true)) num else "Unit $num"
            UnitCatalogItem(
                id = "db-${idx + 1}",
                unitNumber = unitName,
                tower = tower,
                description = desc,
                status = status,
                imageResId = R.drawable.img,
                imageUri = imageUri
            )
        }

        val allUnitsList = (customUnitsFromDb + defaultUnitsList)
            .distinctBy { it.unitNumber.replace("Unit", "", ignoreCase = true).trim() }
            .filter { unit ->
                deletedSet.none { isSameUnit(it, unit.unitNumber) }
            }

        // Update each unit's status depending on the selected DATE in SQLite!
        allUnitsList.map { unitItem ->
            val fullMonthName = monthsList[selectedMonthIndex]
            val monthNum = selectedMonthIndex + 1
            val monthNumStr = String.format("%02d", monthNum)

            val matchingEvent = dbEventsState.value.find { evt ->
                val evtDate = evt["date"] ?: ""
                val evtUnit = evt["unit"] ?: ""

                val monthMatches = evtDate.isBlank() ||
                        evtDate.contains(shortMonthName, ignoreCase = true) ||
                        evtDate.contains(fullMonthName, ignoreCase = true) ||
                        evtDate.contains("/$monthNum/") ||
                        evtDate.contains("/$monthNumStr/") ||
                        evtDate.contains("-$monthNum-") ||
                        evtDate.contains("-$monthNumStr-")

                val yearMatches = evtDate.isBlank() ||
                        evtDate.contains(selectedYearNumber.toString()) ||
                        evtDate.contains(selectedYearNumber.toString().takeLast(2))

                val dateMatches = isDateCoveredByEvent(evtDate, selectedDayNumber)
                val unitMatches = isSameUnit(evtUnit, unitItem.unitNumber)
                monthMatches && yearMatches && dateMatches && unitMatches
            }

            val dynamicStatus = when {
                matchingEvent != null -> {
                    val shape = matchingEvent["shape"] ?: ""
                    val title = matchingEvent["title"] ?: ""
                    val combined = "$shape $title"
                    when {
                        combined.contains("Check", ignoreCase = true) || combined.contains("Heart", ignoreCase = true) -> "Check-in"
                        combined.contains("Reserve", ignoreCase = true) || combined.contains("Diamond", ignoreCase = true) -> "Reserved"
                        combined.contains("Maintenance", ignoreCase = true) || combined.contains("Circle", ignoreCase = true) -> "Under Maintenance"
                        combined.contains("Not", ignoreCase = true) || combined.contains("Star", ignoreCase = true) -> "Not Available"
                        else -> "Available"
                    }
                }
                else -> unitItem.status
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
                "Not Available" -> !unit.status.equals("Available", ignoreCase = true)
                "Rented" -> unit.status.contains("Rented", ignoreCase = true)
                else -> unit.status.contains(selectedFilter, ignoreCase = true)
            }
            matchesSearch && matchesFilter
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(ScreenBg)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(16.dp))
            // Title & Date Bar
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

                // Date Navigation Bar
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFFF3EDFD)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        IconButton(
                            onClick = {
                                if (selectedDayNumber > 1) {
                                    selectedDayNumber--
                                    onDateChange(selectedDayNumber)
                                }
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                contentDescription = "Prev",
                                tint = PurplePrimary
                            )
                        }

                        Text(
                            text = "$shortMonthName $selectedDayNumber, $selectedYearNumber",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextDark,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { showDatePickerDialog = true }
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )

                        IconButton(
                            onClick = {
                                if (selectedDayNumber < 30) {
                                    selectedDayNumber++
                                    onDateChange(selectedDayNumber)
                                }
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = "Next",
                                tint = PurplePrimary
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Search Bar
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

            // Filter Chips
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

        // Unit Cards
        items(filteredUnits) { unit ->
            val isAvailable = unit.status.equals("Available", ignoreCase = true)
            val isRented = unit.status.contains("Rented", ignoreCase = true)
            val displayStatusText = if (isRented && !unit.status.contains("Not Available", ignoreCase = true)) "Not Available (Rented)" else unit.status

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
                        val isValidUri = !unit.imageUri.isNullOrBlank() && (unit.imageUri.startsWith("content://") || unit.imageUri.startsWith("file://") || java.io.File(unit.imageUri).exists())

                        if (isValidUri) {
                            val uriStr = unit.imageUri ?: ""
                            val fileObj = java.io.File(uriStr)
                            val imageModel: Any = if (fileObj.exists()) fileObj else uriStr

                            AsyncImage(
                                model = imageModel,
                                contentDescription = unit.unitNumber,
                                contentScale = ContentScale.Crop,
                                error = painterResource(id = R.drawable.img),
                                placeholder = painterResource(id = R.drawable.img),
                                modifier = Modifier
                                    .size(75.dp)
                                    .clip(RoundedCornerShape(12.dp))
                            )
                        } else {
                            Image(
                                painter = painterResource(id = R.drawable.img),
                                contentDescription = unit.unitNumber,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(75.dp)
                                    .clip(RoundedCornerShape(12.dp))
                            )
                        }

                        Column {
                            Text(
                                text = unit.unitNumber,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = PurplePrimary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = unit.tower,
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

    // Mini Calendar Date Picker Dialog Popup
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
                    color = TextDark
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
                                color = TextMuted
                            )
                        }
                    }

                    val daysList = (1..30).toList()
                    val rows = daysList.chunked(7)

                    Column(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        rows.forEach { rowDays ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceAround
                            ) {
                                rowDays.forEach { dayNum ->
                                    val isSelected = dayNum == selectedDayNumber
                                    Surface(
                                        shape = CircleShape,
                                        color = if (isSelected) PurplePrimary else Color.Transparent,
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .clickable {
                                                selectedDayNumber = dayNum
                                                selectedMonthIndex = pickerMonthIndex
                                                selectedYearNumber = pickerYear
                                                onDateChange(dayNum)
                                                showDatePickerDialog = false
                                            }
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = dayNum.toString(),
                                                fontSize = 13.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                color = if (isSelected) Color.White else TextDark
                                            )
                                        }
                                    }
                                }
                                if (rowDays.size < 7) {
                                    repeat(7 - rowDays.size) {
                                        Spacer(modifier = Modifier.size(32.dp))
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

@Preview(showBackground = true)
@Composable
fun UnitsDirectoryScreenPreview() {
    AzureCalendarTheme {
        UnitsDirectoryScreen()
    }
}