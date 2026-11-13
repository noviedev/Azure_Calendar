package com.example.azurecalendar

import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import com.example.azurecalendar.ui.theme.AzureCalendarTheme

// ============================================================================
// 1. MAIN AZURE CALENDAR VIEW COMPONENT
// ============================================================================

@Composable
fun AzureCalendarView(
    days: List<DayScheduleModel>,
    selectedDayNumber: Int,
    onDaySelected: (DayScheduleModel) -> Unit,
    onDateRangeSelected: (checkInDay: Int, checkOutDay: Int) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
    unitImageUrl: String? = null,
    monthTitle: String = "",
    onMonthYearSelected: (monthName: String, year: Int) -> Unit = { _, _ -> },
    @Suppress("UNUSED_PARAMETER") onPrevMonthClick: () -> Unit = {},
    @Suppress("UNUSED_PARAMETER") onNextMonthClick: () -> Unit = {}
) {
    val realTimeCal = remember { java.util.Calendar.getInstance() }
    val defaultCurrentYear = realTimeCal.get(java.util.Calendar.YEAR)
    val defaultCurrentMonthIdx = realTimeCal.get(java.util.Calendar.MONTH)

    val monthsList = listOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December"
    )

    val defaultMonthTitleText = if (monthTitle.isNotBlank()) monthTitle else "${monthsList[defaultCurrentMonthIdx]} $defaultCurrentYear"

    var selectedMode by remember { mutableStateOf("Month") }
    var showDateRangeDialog by remember { mutableStateOf(false) }
    var checkInDay by remember { mutableIntStateOf(realTimeCal.get(java.util.Calendar.DAY_OF_MONTH)) }
    var checkOutDay by remember { mutableIntStateOf((realTimeCal.get(java.util.Calendar.DAY_OF_MONTH) + 2).coerceAtMost(30)) }
    var activeClickedDay by remember { mutableStateOf<DayScheduleModel?>(null) }

    var currentMonthTitle by remember(defaultMonthTitleText) { mutableStateOf(defaultMonthTitleText) }
    var showMonthYearPicker by remember { mutableStateOf(false) }

    val parsedMonthIdx = remember(currentMonthTitle) {
        val foundIdx = monthsList.indexOfFirst { currentMonthTitle.contains(it, ignoreCase = true) }
        if (foundIdx != -1) foundIdx else defaultCurrentMonthIdx
    }

    val parsedYear = remember(currentMonthTitle) {
        val foundYear = Regex("\\b20\\d{2}\\b").find(currentMonthTitle)?.value?.toIntOrNull()
        foundYear ?: defaultCurrentYear
    }

    var selectedMonthIndex by remember(parsedMonthIdx) { mutableIntStateOf(parsedMonthIdx) }
    var selectedYear by remember(parsedYear) { mutableIntStateOf(parsedYear) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(AzureCalendarColors.ScreenBg)
    ) {
        // Top Title Bar with Realtime Today Jump Button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Calendar",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = AzureCalendarColors.TextDark
            )

            // Today Button to quickly jump back to real-time current date
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFFE53935),
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .clickable {
                        val realCal = java.util.Calendar.getInstance()
                        val todayMIdx = realCal.get(java.util.Calendar.MONTH)
                        val todayYr = realCal.get(java.util.Calendar.YEAR)
                        val todayDay = realCal.get(java.util.Calendar.DAY_OF_MONTH)
                        val todayMName = monthsList[todayMIdx]

                        currentMonthTitle = "$todayMName $todayYr"
                        selectedMonthIndex = todayMIdx
                        selectedYear = todayYr
                        onMonthYearSelected(todayMName, todayYr)
                        onDaySelected(
                            DayScheduleModel(
                                dayNumber = todayDay,
                                month = todayMIdx + 1,
                                year = todayYr,
                                dayType = CalendarDayType.WEEKDAY,
                                assignedShape = null,
                                events = emptyList(),
                                customColor = null
                            )
                        )
                    }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Today,
                        contentDescription = "Jump to Today",
                        tint = Color.White,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = "Today",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Month & Year Selector Bar with Continuous Prev (<) and Next (>) Arrow Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    val currentIdx = monthsList.indexOfFirst { currentMonthTitle.contains(it, ignoreCase = true) }.let { if (it != -1) it else defaultCurrentMonthIdx }
                    val currentYr = Regex("\\b20\\d{2}\\b").find(currentMonthTitle)?.value?.toIntOrNull() ?: defaultCurrentYear

                    val (newMonthIdx, newYr) = if (currentIdx > 0) {
                        Pair(currentIdx - 1, currentYr)
                    } else {
                        Pair(11, currentYr - 1)
                    }

                    val newMonthName = monthsList[newMonthIdx]
                    val newTitle = "$newMonthName $newYr"
                    currentMonthTitle = newTitle
                    selectedMonthIndex = newMonthIdx
                    selectedYear = newYr
                    onMonthYearSelected(newMonthName, newYr)
                },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = "Previous Month",
                    tint = AzureCalendarColors.PurplePrimary
                )
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFFF3EDFD),
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { showMonthYearPicker = true }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = currentMonthTitle,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2C1A36)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = "Select Month & Year",
                        tint = AzureCalendarColors.PurplePrimary
                    )
                }
            }

            IconButton(
                onClick = {
                    val currentIdx = monthsList.indexOfFirst { currentMonthTitle.contains(it, ignoreCase = true) }.let { if (it != -1) it else defaultCurrentMonthIdx }
                    val currentYr = Regex("\\b20\\d{2}\\b").find(currentMonthTitle)?.value?.toIntOrNull() ?: defaultCurrentYear

                    val (newMonthIdx, newYr) = if (currentIdx < 11) {
                        Pair(currentIdx + 1, currentYr)
                    } else {
                        Pair(0, currentYr + 1)
                    }

                    val newMonthName = monthsList[newMonthIdx]
                    val newTitle = "$newMonthName $newYr"
                    currentMonthTitle = newTitle
                    selectedMonthIndex = newMonthIdx
                    selectedYear = newYr
                    onMonthYearSelected(newMonthName, newYr)
                },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "Next Month",
                    tint = AzureCalendarColors.PurplePrimary
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Segmented Mode Toggle (Month / Week / Day)
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFFF3EDFD),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf("Month", "Week", "Day").forEach { mode ->
                    val isSelected = mode == selectedMode
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) AzureCalendarColors.PurplePrimary else Color(0xFFF3EDFD),
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { selectedMode = mode }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = mode,
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) Color.White else Color(0xFF7B6287)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        when (selectedMode) {
            "Week" -> {
                val weekStart = ((selectedDayNumber - 1) / 7) * 7 + 1
                val weekDaysList = days.filter { it.dayNumber in weekStart..(weekStart + 6) }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    weekDaysList.forEach { dayModel ->
                        val isSelected = dayModel.dayNumber == selectedDayNumber
                        val taggedShapeResList = dayModel.events.mapNotNull { evt ->
                            val shapeName = evt.description
                            when {
                                shapeName.contains("Not", ignoreCase = true) || shapeName.contains("Star", ignoreCase = true) -> R.drawable.star
                                shapeName.contains("Heart", ignoreCase = true) || shapeName.contains("Check", ignoreCase = true) -> R.drawable.heart
                                shapeName.contains("Diamond", ignoreCase = true) || shapeName.contains("Reserve", ignoreCase = true) -> R.drawable.diamond
                                shapeName.contains("Circle", ignoreCase = true) || shapeName.contains("Maintenance", ignoreCase = true) -> R.drawable.circle
                                shapeName.isBlank() || shapeName.contains("Available", ignoreCase = true) || shapeName.contains("Four", ignoreCase = true) -> null
                                else -> null
                            }
                        }.take(4)

                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) AzureCalendarColors.PurplePrimary else Color(0xFFF3EDFD)
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 2.dp)
                                .clickable { onDaySelected(dayModel) }
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "Day ${dayModel.dayNumber}",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) Color.White else AzureCalendarColors.TextDark
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                if (taggedShapeResList.isNotEmpty()) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        taggedShapeResList.forEach { resId ->
                                            Image(
                                                painter = painterResource(id = resId),
                                                contentDescription = null,
                                                modifier = Modifier.size(12.dp),
                                                contentScale = ContentScale.Fit
                                            )
                                        }
                                    }
                                } else {
                                    Text(
                                        text = "Available",
                                        fontSize = 9.sp,
                                        color = if (isSelected) Color.White.copy(alpha = 0.8f) else AzureCalendarColors.TextMuted
                                    )
                                }
                            }
                        }
                    }
                }
            }
            "Day" -> {
                val selectedDayModel = days.find { it.dayNumber == selectedDayNumber }
                val eventsForDay = selectedDayModel?.events.orEmpty()

                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Day Overview — ${currentMonthTitle.take(3)} $selectedDayNumber, $parsedYear",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = AzureCalendarColors.TextDark
                        )

                        HorizontalDivider(color = Color(0xFFF3E5F5))

                        val unitList = listOf("Unit 12A", "Unit 15B", "Unit 08C", "Unit 06D")
                        unitList.forEach { uName ->
                            val matchingEvt = eventsForDay.find { it.unitNumber.contains(uName, ignoreCase = true) }
                            val status = matchingEvt?.description ?: "Available"
                            val iconRes = when {
                                status.contains("Not", ignoreCase = true) || status.contains("Star", ignoreCase = true) -> R.drawable.star
                                status.contains("Check", ignoreCase = true) || status.contains("Heart", ignoreCase = true) -> R.drawable.heart
                                status.contains("Reserve", ignoreCase = true) || status.contains("Diamond", ignoreCase = true) -> R.drawable.diamond
                                status.contains("Maintenance", ignoreCase = true) || status.contains("Circle", ignoreCase = true) -> R.drawable.circle
                                else -> R.drawable.four_leaf_clover
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Image(
                                        painter = painterResource(id = iconRes),
                                        contentDescription = status,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Text(
                                        text = uName,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = AzureCalendarColors.TextDark
                                    )
                                }

                                Text(
                                    text = status,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = AzureCalendarColors.PurplePrimary
                                )
                            }
                        }
                    }
                }
            }
            else -> {
                // Month View (Default Grid)
                val weekDays = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    weekDays.forEach { dayName ->
                        Text(
                            text = dayName,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = AzureCalendarColors.TextMuted
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                val leadingOffset = remember(parsedMonthIdx, parsedYear) {
                    val cal = java.util.Calendar.getInstance().apply {
                        clear()
                        set(java.util.Calendar.YEAR, parsedYear)
                        set(java.util.Calendar.MONTH, parsedMonthIdx)
                        set(java.util.Calendar.DAY_OF_MONTH, 1)
                    }
                    val dow = cal.get(java.util.Calendar.DAY_OF_WEEK)
                    (dow + 5) % 7
                }

                val totalCells = leadingOffset + days.size

                LazyVerticalGrid(
                    columns = GridCells.Fixed(7),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    userScrollEnabled = false
                ) {
                    items(count = totalCells) { index ->
                        if (index < leadingOffset) {
                            Box(modifier = Modifier.aspectRatio(0.85f))
                        } else {
                            val dayModel = days[index - leadingOffset]
                            val isSelected = dayModel.dayNumber == selectedDayNumber

                            val taggedShapeResList = dayModel.events.mapNotNull { evt ->
                                val shapeName = evt.description
                                when {
                                    shapeName.contains("Not", ignoreCase = true) || shapeName.contains("Star", ignoreCase = true) -> R.drawable.star
                                    shapeName.contains("Heart", ignoreCase = true) || shapeName.contains("Check", ignoreCase = true) -> R.drawable.heart
                                    shapeName.contains("Diamond", ignoreCase = true) || shapeName.contains("Reserve", ignoreCase = true) || shapeName.contains("Extend", ignoreCase = true) -> R.drawable.diamond
                                    shapeName.contains("Circle", ignoreCase = true) || shapeName.contains("Maintenance", ignoreCase = true) -> R.drawable.circle
                                    shapeName.isBlank() || shapeName.contains("Available", ignoreCase = true) || shapeName.contains("Four", ignoreCase = true) -> null
                                    else -> null
                                }
                            }.distinct().take(4)

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .aspectRatio(0.85f)
                                    .clip(CircleShape)
                                    .clickable {
                                        activeClickedDay = dayModel
                                        onDaySelected(dayModel)
                                    },
                                verticalArrangement = Arrangement.Center
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = if (isSelected) AzureCalendarColors.PurplePrimary else Color.Transparent,
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = dayModel.dayNumber.toString(),
                                            fontSize = 13.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) Color.White else AzureCalendarColors.TextDark
                                        )
                                    }
                                }

                                if (taggedShapeResList.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        taggedShapeResList.forEach { resId ->
                                            Image(
                                                painter = painterResource(id = resId),
                                                contentDescription = null,
                                                modifier = Modifier.size(10.dp),
                                                contentScale = ContentScale.Fit
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Unit Preview Card Image Container
        Card(
            shape = RoundedCornerShape(20.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFFE8DEF8)),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.agent_dash),
                    contentDescription = "Unit Preview Image",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }

    // Interactive Check-in & Check-out Date Range Picker Dialog Popup
    if (showDateRangeDialog && activeClickedDay != null) {
        val context = LocalContext.current
        AlertDialog(
            onDismissRequest = { showDateRangeDialog = false },
            title = {
                Text(
                    text = "Select Check-in & Check-out Dates",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = AzureCalendarColors.TextDark
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Choose check-in and check-out range starting ${currentMonthTitle.take(3)} ${activeClickedDay!!.dayNumber}, $parsedYear",
                        fontSize = 13.sp,
                        color = AzureCalendarColors.TextMuted
                    )

                    // Check-in Date Selector
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Check-in Date:", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = AzureCalendarColors.TextDark)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { if (checkInDay > 1) checkInDay-- }) {
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Prev", tint = AzureCalendarColors.PurplePrimary)
                            }
                            Text("${currentMonthTitle.take(3)} $checkInDay, $parsedYear", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = AzureCalendarColors.PurplePrimary)
                            IconButton(onClick = { if (checkInDay < checkOutDay - 1) checkInDay++ }) {
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next", tint = AzureCalendarColors.PurplePrimary)
                            }
                        }
                    }

                    HorizontalDivider(color = Color(0xFFF3E5F5))

                    // Check-out Date Selector
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Check-out Date:", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = AzureCalendarColors.TextDark)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { if (checkOutDay > checkInDay + 1) checkOutDay-- }) {
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Prev", tint = AzureCalendarColors.PurplePrimary)
                            }
                            Text("${currentMonthTitle.take(3)} $checkOutDay, $parsedYear", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = AzureCalendarColors.PurplePrimary)
                            IconButton(onClick = { if (checkOutDay < 30) checkOutDay++ }) {
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next", tint = AzureCalendarColors.PurplePrimary)
                            }
                        }
                    }

                    HorizontalDivider(color = Color(0xFFF3E5F5))

                    // All Units Status Tags Overview for Clicked Day
                    Text(
                        text = "Unit Status Tags for ${currentMonthTitle.take(3)} ${activeClickedDay!!.dayNumber}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = AzureCalendarColors.TextDark
                    )

                    val unitList = listOf("Unit 12A", "Unit 15B", "Unit 08C", "Unit 06D")
                    val eventsForActiveDay = activeClickedDay?.events.orEmpty()

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        unitList.forEach { uName ->
                            val cleanU = uName.replace("Unit", "", ignoreCase = true).trim()
                            val matchingEvt = eventsForActiveDay.find {
                                val evtUnit = it.unitNumber.replace("Unit", "", ignoreCase = true).trim()
                                evtUnit.equals(cleanU, ignoreCase = true) || it.unitNumber.contains(uName, ignoreCase = true)
                            }
                            val rawStatus = matchingEvt?.description?.replace("Category: ", "")?.ifBlank { "Available" } ?: "Available"
                            val status = if (rawStatus.isBlank()) "Available" else rawStatus
                            val iconRes = when {
                                status.contains("Not", ignoreCase = true) || status.contains("Star", ignoreCase = true) -> R.drawable.star
                                status.contains("Check", ignoreCase = true) || status.contains("Heart", ignoreCase = true) -> R.drawable.heart
                                status.contains("Reserve", ignoreCase = true) || status.contains("Diamond", ignoreCase = true) -> R.drawable.diamond
                                status.contains("Maintenance", ignoreCase = true) || status.contains("Circle", ignoreCase = true) -> R.drawable.circle
                                else -> R.drawable.four_leaf_clover
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFFF8F5FE), RoundedCornerShape(10.dp))
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Image(
                                        painter = painterResource(id = iconRes),
                                        contentDescription = status,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = uName,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = AzureCalendarColors.TextDark
                                    )
                                }

                                Text(
                                    text = status,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AzureCalendarColors.PurplePrimary
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDateRangeDialog = false
                        Toast.makeText(context, "Dates set: Sept $checkInDay - Sept $checkOutDay, 2026", Toast.LENGTH_SHORT).show()
                        onDateRangeSelected(checkInDay, checkOutDay)
                        onDaySelected(activeClickedDay!!)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AzureCalendarColors.PurplePrimary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Confirm Dates & Continue", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDateRangeDialog = false }) {
                    Text("Cancel", color = AzureCalendarColors.TextMuted)
                }
            },
            containerColor = Color.White,
            shape = RoundedCornerShape(20.dp)
        )
    }

    // Interactive Month & Year Selection Dialog Popup
    if (showMonthYearPicker) {
        AlertDialog(
            onDismissRequest = { showMonthYearPicker = false },
            title = {
                Text(
                    text = "Select Month & Year",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF2C1A36)
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Year Selector Bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { selectedYear-- }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                contentDescription = "Prev Year",
                                tint = AzureCalendarColors.PurplePrimary
                            )
                        }

                        Text(
                            text = selectedYear.toString(),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = AzureCalendarColors.PurplePrimary
                        )

                        IconButton(onClick = { selectedYear++ }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = "Next Year",
                                tint = AzureCalendarColors.PurplePrimary
                            )
                        }
                    }

                    HorizontalDivider(color = Color(0xFFF3E5F5))

                    // Months 3-column Grid (Jan - Dec)
                    val monthGridRows = monthsList.chunked(3)
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        monthGridRows.forEachIndexed { rowIdx, rowMonths ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                rowMonths.forEachIndexed { colIdx, mName ->
                                    val mIdx = rowIdx * 3 + colIdx
                                    val isSelected = mIdx == selectedMonthIndex
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (isSelected) AzureCalendarColors.PurplePrimary else Color(0xFFF3EDFD),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(38.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable {
                                                selectedMonthIndex = mIdx
                                                val selectedMName = monthsList[selectedMonthIndex]
                                                currentMonthTitle = "$selectedMName $selectedYear"
                                                showMonthYearPicker = false
                                                onMonthYearSelected(selectedMName, selectedYear)
                                            }
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = mName.take(3),
                                                fontSize = 13.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                color = if (isSelected) Color.White else Color(0xFF2C1A36)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showMonthYearPicker = false }) {
                    Text("Done", color = AzureCalendarColors.PurplePrimary, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = Color.White,
            shape = RoundedCornerShape(20.dp)
        )
    }
}

// ============================================================================
// 2. SUPPORTING UI COMPONENTS
// ============================================================================

@Suppress("UnusedPrivateMember")
@Composable
private fun CalendarLegendIndicator(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = AzureCalendarColors.TextMuted
        )
    }
}

@Composable
fun CalendarBottomNavigationBar(
    selectedTab: Int = 1,
    unreadEventCount: Int = 0,
    onTabSelected: (Int) -> Unit = {}
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(AzureCalendarColors.CardBg)
            .border(1.dp, Color(0xFFF0E6FA))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(vertical = 6.dp, horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            BottomNavItem(
                icon = Icons.Default.Home,
                label = "Home",
                isSelected = selectedTab == 0,
                onClick = { onTabSelected(0) },
                modifier = Modifier.weight(1f)
            )
            BottomNavItem(
                icon = Icons.Default.DateRange,
                label = "Calendar",
                isSelected = selectedTab == 1,
                onClick = { onTabSelected(1) },
                modifier = Modifier.weight(1f)
            )
            BottomNavItem(
                icon = Icons.Default.Group,
                label = "Units",
                isSelected = selectedTab == 2,
                onClick = { onTabSelected(2) },
                modifier = Modifier.weight(1f)
            )
            BottomNavItem(
                icon = Icons.Default.Event,
                label = "Event",
                isSelected = selectedTab == 3,
                badgeCount = unreadEventCount,
                onClick = { onTabSelected(3) },
                modifier = Modifier.weight(1f)
            )
            BottomNavItem(
                icon = Icons.Default.Person,
                label = "Profile",
                isSelected = selectedTab == 4,
                onClick = { onTabSelected(4) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BottomNavItem(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    badgeCount: Int = 0,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        color = Color.Transparent,
        shape = RoundedCornerShape(14.dp),
        modifier = modifier
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(vertical = 6.dp)
        ) {
            BadgedBox(
                badge = {
                    if (badgeCount > 0) {
                        Badge(
                            containerColor = Color(0xFFE53935),
                            contentColor = Color.White
                        ) {
                            Text(
                                text = if (badgeCount > 99) "99+" else "$badgeCount",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) Color(0xFFF3EDFD) else Color.Transparent)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                        tint = if (isSelected) AzureCalendarColors.PurplePrimary else Color.Gray,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = if (isSelected) AzureCalendarColors.PurplePrimary else Color.Gray
            )
        }
    }
}

// ============================================================================
// 3. SAMPLE DATA GENERATOR & MODELS
// ============================================================================

fun createSampleCalendarMonth(
    monthName: String = "October",
    year: Int = 2026,
    currentAgentName: String = "Alex"
): List<DayScheduleModel> {
    val monthsList = listOf("january", "february", "march", "april", "may", "june", "july", "august", "september", "october", "november", "december")
    val monthIndex = monthsList.indexOfFirst { monthName.contains(it, ignoreCase = true) }.let { if (it != -1) it else 9 }

    val cal = java.util.Calendar.getInstance().apply {
        clear()
        set(java.util.Calendar.YEAR, year)
        set(java.util.Calendar.MONTH, monthIndex)
        set(java.util.Calendar.DAY_OF_MONTH, 1)
    }
    val daysInMonth = cal.getActualMaximum(java.util.Calendar.DAY_OF_MONTH)

    return (1..daysInMonth).map { day ->
        val dayCal = java.util.Calendar.getInstance().apply {
            clear()
            set(java.util.Calendar.YEAR, year)
            set(java.util.Calendar.MONTH, monthIndex)
            set(java.util.Calendar.DAY_OF_MONTH, day)
        }
        val dow = dayCal.get(java.util.Calendar.DAY_OF_WEEK)
        val dayType = when (dow) {
            java.util.Calendar.SUNDAY, java.util.Calendar.SATURDAY -> CalendarDayType.WEEKEND
            else -> CalendarDayType.WEEKDAY
        }

        DayScheduleModel(
            dayNumber = day,
            month = monthIndex + 1,
            year = year,
            dayType = dayType,
            assignedShape = null,
            events = emptyList(),
            customColor = null
        )
    }
}

enum class CalendarDayType {
    HOLIDAY,
    WEEKEND,
    WEEKDAY
}

enum class BlockingShape(val label: String, val color: Color) {
    HEART("Heart", Color(0xFFE91E63)),
    FLOWER("Four Leaf Flower", Color(0xFF4CAF50)),
    CIRCLE("Circle", Color(0xFF2196F3)),
    STAR("Star", Color(0xFFFFC107)),
    DIAMOND("Diamond", Color(0xFF78909C))
}

data class CalendarEventModel(
    val id: String,
    val title: String,
    val date: String,
    val time: String,
    val location: String,
    val description: String,
    val assignedAgent: String,
    val unitNumber: String,
    val guestName: String,
    val shapeBadge: BlockingShape?
)

data class DayScheduleModel(
    val dayNumber: Int,
    val month: Int,
    val year: Int,
    val dayType: CalendarDayType,
    val assignedShape: BlockingShape?,
    val events: List<CalendarEventModel>,
    val customColor: Color? = null
)

object AzureCalendarColors {
    var isDarkMode by mutableStateOf(false)
    var currentThemeMode by mutableStateOf("light")

    val CardBg: Color get() = if (isDarkMode) Color(0xFF1E1B26) else Color(0xFFFFFFFF)
    val BorderPurple: Color get() = if (isDarkMode) Color(0xFF3B2E4A) else Color(0xFFE0D0EC)
    val TextMuted: Color get() = if (isDarkMode) Color(0xFFAFA0C2) else Color(0xFF7B6287)
    val TextDark: Color get() = if (isDarkMode) Color(0xFFF3EDFD) else Color(0xFF2C1A36)
    val ScreenBg: Color get() = if (isDarkMode) Color(0xFF130F19) else Color(0xFFFAF5FC)

    var PurplePrimary by mutableStateOf(Color(0xFF6A1B9A))

    fun setAppThemeMode(context: android.content.Context, mode: String) {
        currentThemeMode = mode
        val prefs = context.getSharedPreferences("azure_calendar_theme_mode_prefs", android.content.Context.MODE_PRIVATE)
        val isDark = when (mode) {
            "dark" -> true
            "light" -> false
            else -> (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        }
        prefs.edit().putString("theme_mode", mode).putBoolean("is_dark_mode", isDark).apply()
        isDarkMode = isDark
    }

    fun initThemeMode(context: android.content.Context) {
        val prefs = context.getSharedPreferences("azure_calendar_theme_mode_prefs", android.content.Context.MODE_PRIVATE)
        val mode = prefs.getString("theme_mode", "light") ?: "light"
        currentThemeMode = mode
        val isDark = when (mode) {
            "dark" -> true
            "light" -> false
            else -> (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        }
        isDarkMode = isDark
    }

    fun updateThemeColor(hex: String) {
        PurplePrimary = parseHexColor(hex) ?: Color(0xFF6A1B9A)
    }

    fun parseHexColor(hex: String): Color? = try {
        val clean = hex.trim().removePrefix("#").removePrefix("0x").removePrefix("0X")
        val argb = when (clean.length) {
            6 -> 0xFF000000 or clean.toLong(16)
            8 -> clean.toLong(16)
            else -> return null
        }
        Color(argb.toInt())
    } catch (_: NumberFormatException) {
        null
    }
}

@Preview(showBackground = true)
@Composable
fun AzureCalendarViewPreview() {
    var showChooseCategory by remember { mutableStateOf(false) }
    var selectedDay by remember { mutableStateOf<DayScheduleModel?>(null) }
    var calendarDays by remember { mutableStateOf(createSampleCalendarMonth()) }

    AzureCalendarTheme {
        if (showChooseCategory) {
            ChooseCategoryScreen(
                selectedDateText = "Sept ${selectedDay?.dayNumber ?: 1}, 2026",
                onBackClick = { showChooseCategory = false },
                onSaveClick = { shape, color, unitName, dateText, checkInTimeText ->
                    if (selectedDay != null) {
                        calendarDays = calendarDays.map { day ->
                            if (day.dayNumber == selectedDay!!.dayNumber) {
                                day.copy(
                                    customColor = color,
                                    events = listOf(
                                        CalendarEventModel(
                                            id = "evt-${day.dayNumber}",
                                            title = shape,
                                            date = "Sept ${day.dayNumber}, 2026",
                                            time = "2:00 PM",
                                            location = "Azure Tower 1",
                                            description = "Category: $shape",
                                            assignedAgent = "Alex",
                                            unitNumber = "Unit 12A",
                                            guestName = "Guest",
                                            shapeBadge = null
                                        )
                                    )
                                )
                            } else day
                        }
                    }
                    showChooseCategory = false
                }
            )
        } else {
            AzureCalendarView(
                days = calendarDays,
                selectedDayNumber = selectedDay?.dayNumber ?: 12,
                onDaySelected = { dayModel ->
                    selectedDay = dayModel
                    showChooseCategory = true
                }
            )
        }
    }
}