package com.example.azurecalendar

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Lock
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
import com.example.azurecalendar.ui.theme.AzureCalendarTheme

private val PurplePrimary get() = AzureCalendarColors.PurplePrimary
private val ScreenBg get() = AzureCalendarColors.ScreenBg
private val CardBg get() = AzureCalendarColors.CardBg
private val TextDark get() = AzureCalendarColors.TextDark
private val TextMuted get() = AzureCalendarColors.TextMuted

data class ShapeOption(
    val name: String,
    val statusLabel: String,
    val iconRes: Int,
    val color: Color,
    val bg: Color
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChooseCategoryScreen(
    selectedDateText: String = "Sept 1, 2026",
    initialUnitName: String = "Unit 12A",
    onBackClick: () -> Unit = {},
    onSaveClick: (shape: String, color: Color, unitName: String, dateText: String, checkInTimeText: String) -> Unit = { _, _, _, _, _ -> }
) {
    val context = LocalContext.current
    val unitOptions = listOf("Unit 12A", "Unit 15B", "Unit 08C", "Unit 06D")

    val initialUnitIdx = remember(initialUnitName) {
        val foundIdx = unitOptions.indexOfFirst {
            initialUnitName.contains(it, ignoreCase = true) || it.contains(initialUnitName, ignoreCase = true)
        }
        if (foundIdx != -1) foundIdx else 0
    }

    val realTimeCal = remember { java.util.Calendar.getInstance() }
    val monthNamesList = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sept", "Oct", "Nov", "Dec")
    val currentRealMonthShort = monthNamesList.getOrElse(realTimeCal.get(java.util.Calendar.MONTH)) { "Oct" }
    val currentRealYear = realTimeCal.get(java.util.Calendar.YEAR)
    val currentRealDay = realTimeCal.get(java.util.Calendar.DAY_OF_MONTH)

    val initialDateData = remember(selectedDateText) {
        val foundMonths = monthNamesList.filter { selectedDateText.contains(it, ignoreCase = true) }
        val foundYears = Regex("\\b20\\d{2}\\b").findAll(selectedDateText).mapNotNull { it.value.toIntOrNull() }.toList()
        val foundNumbers = Regex("\\b([1-9]|[12][0-9]|3[01])\\b").findAll(selectedDateText).mapNotNull { it.value.toIntOrNull() }.toList()

        val inM = foundMonths.getOrNull(0) ?: currentRealMonthShort
        val outM = if (foundMonths.size >= 2) foundMonths[1] else inM

        val inY = foundYears.getOrNull(0) ?: currentRealYear
        val outY = if (foundYears.size >= 2) foundYears[1] else inY

        val inD = foundNumbers.getOrNull(0) ?: currentRealDay
        val outD = if (foundNumbers.size >= 2) foundNumbers[1] else (inD + 2).coerceAtMost(30)

        listOf(inM, inD.toString(), inY.toString(), outM, outD.toString(), outY.toString())
    }

    var checkInMonth by remember(selectedDateText) { mutableStateOf(initialDateData[0]) }
    var checkInDay by remember(selectedDateText) { mutableIntStateOf(initialDateData[1].toInt()) }
    var checkInYear by remember(selectedDateText) { mutableIntStateOf(initialDateData[2].toInt()) }

    var checkOutMonth by remember(selectedDateText) { mutableStateOf(initialDateData[3]) }
    var checkOutDay by remember(selectedDateText) { mutableIntStateOf(initialDateData[4].toInt()) }
    var checkOutYear by remember(selectedDateText) { mutableIntStateOf(initialDateData[5].toInt()) }

    var checkInTime by remember { mutableStateOf("2:00 PM") }
    var checkOutTime by remember { mutableStateOf("12:00 PM") }
    var showCheckInTimeDropdown by remember { mutableStateOf(false) }
    var showCheckOutTimeDropdown by remember { mutableStateOf(false) }

    val standardTimeOptions = listOf(
        "8:00 AM", "9:00 AM", "10:00 AM", "11:00 AM", "12:00 PM",
        "1:00 PM", "2:00 PM", "3:00 PM", "4:00 PM", "5:00 PM", "6:00 PM", "7:00 PM"
    )

    var showCheckInDatePicker by remember { mutableStateOf(false) }
    var showCheckOutDatePicker by remember { mutableStateOf(false) }
    var showExtendStayDialog by remember { mutableStateOf(false) }

    var selectedShapeIndex by remember { mutableIntStateOf(0) }
    var selectedUnitIndex by remember { mutableIntStateOf(initialUnitIdx) }

    val shapes = listOf(
        ShapeOption("Four Leaf", "Available", R.drawable.four_leaf_clover, Color(0xFF00A86B), Color(0xFFE8F5E9)),
        ShapeOption("Heart", "Check-in", R.drawable.heart, Color(0xFFE91E63), Color(0xFFFFEBEE)),
        ShapeOption("Diamond", "Reserve", R.drawable.diamond, Color(0xFFF39C12), Color(0xFFFFF3E0)),
        ShapeOption("Circle", "Maintenance", R.drawable.circle, Color(0xFF2196F3), Color(0xFFE3F2FD)),
        ShapeOption("Star", "Rented", R.drawable.star, Color(0xFFE53935), Color(0xFFFFEBEE))
    )

    val currentFormattedDate = remember(checkInMonth, checkInDay, checkInYear, checkOutMonth, checkOutDay, checkOutYear) {
        if (checkInMonth == checkOutMonth && checkInYear == checkOutYear) {
            if (checkInDay == checkOutDay) "$checkInMonth $checkInDay, $checkInYear"
            else "$checkInMonth $checkInDay - $checkOutDay, $checkInYear"
        } else if (checkInYear == checkOutYear) {
            "$checkInMonth $checkInDay - $checkOutMonth $checkOutDay, $checkInYear"
        } else {
            "$checkInMonth $checkInDay, $checkInYear - $checkOutMonth $checkOutDay, $checkOutYear"
        }
    }

    val stayDurationNights = remember(checkInMonth, checkInDay, checkInYear, checkOutMonth, checkOutDay, checkOutYear) {
        val inCal = java.util.Calendar.getInstance().apply {
            clear()
            val mIdx = monthNamesList.indexOf(checkInMonth).let { if (it != -1) it else 9 }
            set(checkInYear, mIdx, checkInDay)
        }
        val outCal = java.util.Calendar.getInstance().apply {
            clear()
            val mIdx = monthNamesList.indexOf(checkOutMonth).let { if (it != -1) it else 9 }
            set(checkOutYear, mIdx, checkOutDay)
        }
        val diffMs = outCal.timeInMillis - inCal.timeInMillis
        val days = (diffMs / (1000 * 60 * 60 * 24)).toInt()
        maxOf(1, days)
    }

    fun applyNightsDuration(nightsToSet: Int) {
        val inCal = java.util.Calendar.getInstance().apply {
            clear()
            val mIdx = monthNamesList.indexOf(checkInMonth).let { if (it != -1) it else 9 }
            set(checkInYear, mIdx, checkInDay)
        }
        inCal.add(java.util.Calendar.DAY_OF_MONTH, maxOf(1, nightsToSet))
        checkOutDay = inCal.get(java.util.Calendar.DAY_OF_MONTH)
        checkOutMonth = monthNamesList[inCal.get(java.util.Calendar.MONTH)]
        checkOutYear = inCal.get(java.util.Calendar.YEAR)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ScreenBg)
            .statusBarsPadding()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
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
                Column(modifier = Modifier.padding(top = 15.dp)) {
                    Text(
                        text = "Choose Category — ${initialUnitName.ifBlank { "Unit 12A" }}",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextDark
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = currentFormattedDate,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = PurplePrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))



            // Check-in and Check-out Date Range Picker Section
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Select Date Range",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextDark
                )
                Text(
                    text = "🌙 $stayDurationNights ${if (stayDurationNights == 1) "Night" else "Nights"}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = PurplePrimary
                )
            }
            Spacer(modifier = Modifier.height(8.dp))

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFFF8F5FE),
                border = BorderStroke(1.dp, Color(0xFFE2D6F5)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Clickable Check-in Date Box with Calendar Icon
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFF3EDFD),
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { showCheckInDatePicker = true }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DateRange,
                                    contentDescription = "Pick Check-in Date",
                                    tint = PurplePrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Column {
                                    Text(text = "Check-in Date", fontSize = 10.sp, color = TextMuted)
                                    Text(
                                        text = "$checkInMonth $checkInDay, $checkInYear",
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

                        // Clickable Check-out Date Box with Calendar Icon
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFF3EDFD),
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { showCheckOutDatePicker = true }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DateRange,
                                    contentDescription = "Pick Check-out Date",
                                    tint = PurplePrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Column {
                                    Text(text = "Check-out Date", fontSize = 10.sp, color = TextMuted)
                                    Text(
                                        text = "$checkOutMonth $checkOutDay, $checkOutYear",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextDark
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = Color(0xFFE2D6F5))

                    // Stepper row to adjust duration directly
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Length of Stay:",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextMuted
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            IconButton(
                                onClick = { applyNightsDuration(stayDurationNights - 1) },
                                enabled = stayDurationNights > 1,
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(if (stayDurationNights > 1) Color(0xFFF3EDFD) else Color(0xFFE0E0E0))
                            ) {
                                Text("—", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (stayDurationNights > 1) PurplePrimary else Color.Gray)
                            }

                            Text(
                                text = "$stayDurationNights ${if (stayDurationNights == 1) "Night" else "Nights"}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextDark
                            )

                            IconButton(
                                onClick = { applyNightsDuration(stayDurationNights + 1) },
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFF3EDFD))
                            ) {
                                Text("+", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = PurplePrimary)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Quick Duration Preset Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(
                    1 to "1 Night",
                    2 to "2 Nights",
                    3 to "3 Nights",
                    5 to "5 Nights",
                    7 to "7 Nights (1 Wk)",
                    14 to "14 Nights",
                    30 to "30 Nights (1 Mo)"
                ).forEach { (nightsVal, labelStr) ->
                    val isSelected = stayDurationNights == nightsVal
                    FilterChip(
                        selected = isSelected,
                        onClick = { applyNightsDuration(nightsVal) },
                        label = { Text(labelStr, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = PurplePrimary,
                            selectedLabelColor = Color.White,
                            containerColor = Color(0xFFF8F5FE),
                            labelColor = TextDark
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Check-in & Check-out Time Selector Section
            Text(
                text = "Select Time Range",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = TextDark
            )
            Spacer(modifier = Modifier.height(8.dp))

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFFF8F5FE),
                border = BorderStroke(1.dp, Color(0xFFE2D6F5)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Check-in Time Selector
                    ExposedDropdownMenuBox(
                        expanded = showCheckInTimeDropdown,
                        onExpandedChange = { showCheckInTimeDropdown = !showCheckInTimeDropdown },
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFF3EDFD),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AccessTime,
                                    contentDescription = "Check-in Time",
                                    tint = PurplePrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Column {
                                    Text(text = "Check-in Time", fontSize = 10.sp, color = TextMuted)
                                    Text(
                                        text = checkInTime,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextDark
                                    )
                                }
                            }
                        }
                        ExposedDropdownMenu(
                            expanded = showCheckInTimeDropdown,
                            onDismissRequest = { showCheckInTimeDropdown = false }
                        ) {
                            standardTimeOptions.forEach { t ->
                                DropdownMenuItem(
                                    text = { Text(t, fontWeight = FontWeight.Bold) },
                                    onClick = {
                                        checkInTime = t
                                        showCheckInTimeDropdown = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "➞", fontSize = 16.sp, color = PurplePrimary, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.width(6.dp))

                    // Check-out Time Selector
                    ExposedDropdownMenuBox(
                        expanded = showCheckOutTimeDropdown,
                        onExpandedChange = { showCheckOutTimeDropdown = !showCheckOutTimeDropdown },
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFF3EDFD),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AccessTime,
                                    contentDescription = "Check-out Time",
                                    tint = PurplePrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Column {
                                    Text(text = "Check-out Time", fontSize = 10.sp, color = TextMuted)
                                    Text(
                                        text = checkOutTime,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextDark
                                    )
                                }
                            }
                        }
                        ExposedDropdownMenu(
                            expanded = showCheckOutTimeDropdown,
                            onDismissRequest = { showCheckOutTimeDropdown = false }
                        ) {
                            standardTimeOptions.forEach { t ->
                                DropdownMenuItem(
                                    text = { Text(t, fontWeight = FontWeight.Bold) },
                                    onClick = {
                                        checkOutTime = t
                                        showCheckOutTimeDropdown = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Select Category Tag",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = TextDark
            )
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.Top
            ) {
                shapes.forEachIndexed { globalIndex, shapeItem ->
                    val isSelected = selectedShapeIndex == globalIndex

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { selectedShapeIndex = globalIndex }
                            .padding(4.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = shapeItem.bg,
                            border = if (isSelected) BorderStroke(2.dp, PurplePrimary) else null,
                            modifier = Modifier.size(52.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Image(
                                    painter = painterResource(id = shapeItem.iconRes),
                                    contentDescription = shapeItem.statusLabel,
                                    modifier = Modifier.size(26.dp),
                                    contentScale = ContentScale.Fit
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = shapeItem.statusLabel,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) PurplePrimary else TextDark,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        }

        // Save Button
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 20.dp)
        ) {
            val selectedShape = shapes[selectedShapeIndex]
            val context = LocalContext.current

            val isCheckInDatePast = remember(checkInMonth, checkInDay, checkInYear) {
                val selectedCal = java.util.Calendar.getInstance().apply {
                    clear()
                    val mIdx = monthNamesList.indexOf(checkInMonth).let { if (it != -1) it else 9 }
                    set(checkInYear, mIdx, checkInDay)
                }
                val todayCal = java.util.Calendar.getInstance().apply {
                    set(java.util.Calendar.HOUR_OF_DAY, 0)
                    set(java.util.Calendar.MINUTE, 0)
                    set(java.util.Calendar.SECOND, 0)
                    set(java.util.Calendar.MILLISECOND, 0)
                }
                selectedCal.before(todayCal)
            }

            Button(
                onClick = {
                    if (isCheckInDatePast) {
                        Toast.makeText(context, "Booking is disabled for past dates! Please select today's date or a future date.", Toast.LENGTH_LONG).show()
                    } else {
                        onSaveClick(
                            selectedShape.statusLabel,
                            selectedShape.color,
                            initialUnitName.ifBlank { unitOptions.getOrElse(selectedUnitIndex) { "Unit 12A" } },
                            currentFormattedDate,
                            checkInTime
                        )
                    }
                },
                enabled = !isCheckInDatePast,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PurplePrimary,
                    disabledContainerColor = Color(0xFFE0E0E0),
                    disabledContentColor = Color(0xFF9E9E9E)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isCheckInDatePast) {
                        Icon(Icons.Default.Lock, contentDescription = "Past Date Blocked", modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Booking Blocked (Past Date)",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        Text(
                            text = "Save Tag ($currentFormattedDate)",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }

    if (showCheckInDatePicker || showCheckOutDatePicker) {
        val isCheckIn = showCheckInDatePicker
        val titleText = if (isCheckIn) "Select Check-in Date" else "Select Check-out Date"
        val startM = if (isCheckIn) checkInMonth else checkOutMonth
        val startY = if (isCheckIn) checkInYear else checkOutYear
        val startD = if (isCheckIn) checkInDay else checkOutDay

        var activePickerMonthShort by remember(isCheckIn) { mutableStateOf(startM) }
        var activePickerYear by remember(isCheckIn) { mutableIntStateOf(startY) }
        var tempSelectedDay by remember(isCheckIn) { mutableIntStateOf(startD) }

        val fullPickerMonthName = remember(activePickerMonthShort) {
            when (activePickerMonthShort.lowercase()) {
                "jan" -> "January"
                "feb" -> "February"
                "mar" -> "March"
                "apr" -> "April"
                "may" -> "May"
                "jun" -> "June"
                "jul" -> "July"
                "aug" -> "August"
                "sept", "sep" -> "September"
                "oct" -> "October"
                "nov" -> "November"
                "dec" -> "December"
                else -> activePickerMonthShort
            }
        }

        AlertDialog(
            onDismissRequest = {
                showCheckInDatePicker = false
                showCheckOutDatePicker = false
            },
            title = {
                Text(
                    text = titleText,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF2C1A36)
                )
            },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                val currentIdx = monthNamesList.indexOf(activePickerMonthShort)
                                if (currentIdx > 0) {
                                    activePickerMonthShort = monthNamesList[currentIdx - 1]
                                } else {
                                    activePickerMonthShort = monthNamesList[11]
                                    activePickerYear--
                                }
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Text("◀", fontSize = 12.sp, color = PurplePrimary)
                        }

                        Text(
                            text = "$fullPickerMonthName $activePickerYear",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = PurplePrimary
                        )

                        IconButton(
                            onClick = {
                                val currentIdx = monthNamesList.indexOf(activePickerMonthShort)
                                if (currentIdx < 11) {
                                    activePickerMonthShort = monthNamesList[currentIdx + 1]
                                } else {
                                    activePickerMonthShort = monthNamesList[0]
                                    activePickerYear++
                                }
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Text("▶", fontSize = 12.sp, color = PurplePrimary)
                        }
                    }

                    HorizontalDivider(color = Color(0xFFF3E5F5))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        listOf("M", "T", "W", "T", "F", "S", "S").forEach { h ->
                            Text(h, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF7B6287))
                        }
                    }

                    val monthIdx = remember(activePickerMonthShort) {
                        val idx = monthNamesList.indexOf(activePickerMonthShort)
                        if (idx != -1) idx else 9
                    }

                    val maxDaysInActiveMonth = remember(monthIdx, activePickerYear) {
                        val cal = java.util.Calendar.getInstance().apply {
                            clear()
                            set(java.util.Calendar.YEAR, activePickerYear)
                            set(java.util.Calendar.MONTH, monthIdx)
                            set(java.util.Calendar.DAY_OF_MONTH, 1)
                        }
                        cal.getActualMaximum(java.util.Calendar.DAY_OF_MONTH)
                    }

                    val leadingOffset = remember(monthIdx, activePickerYear) {
                        val cal = java.util.Calendar.getInstance().apply {
                            clear()
                            set(java.util.Calendar.YEAR, activePickerYear)
                            set(java.util.Calendar.MONTH, monthIdx)
                            set(java.util.Calendar.DAY_OF_MONTH, 1)
                        }
                        val dow = cal.get(java.util.Calendar.DAY_OF_WEEK)
                        (dow + 5) % 7
                    }

                    val totalGridCells = leadingOffset + maxDaysInActiveMonth

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(7),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 280.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        userScrollEnabled = false
                    ) {
                        items(count = totalGridCells) { index ->
                            if (index < leadingOffset) {
                                Box(modifier = Modifier.size(32.dp))
                            } else {
                                val dayNum = index - leadingOffset + 1
                                val isSelected = dayNum == tempSelectedDay

                                val isCellPast = remember(dayNum, monthIdx, activePickerYear) {
                                    val cellCal = java.util.Calendar.getInstance().apply {
                                        clear()
                                        set(activePickerYear, monthIdx, dayNum)
                                    }
                                    val todayCal = java.util.Calendar.getInstance().apply {
                                        set(java.util.Calendar.HOUR_OF_DAY, 0)
                                        set(java.util.Calendar.MINUTE, 0)
                                        set(java.util.Calendar.SECOND, 0)
                                        set(java.util.Calendar.MILLISECOND, 0)
                                    }
                                    cellCal.before(todayCal)
                                }

                                Surface(
                                    shape = CircleShape,
                                    color = if (isSelected) PurplePrimary else Color.Transparent,
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .clickable {
                                            if (isCellPast) {
                                                Toast.makeText(context, "Past dates cannot be selected for booking.", Toast.LENGTH_SHORT).show()
                                            } else {
                                                tempSelectedDay = dayNum
                                            }
                                        }
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = dayNum.toString(),
                                            fontSize = 13.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = when {
                                                isSelected -> Color.White
                                                isCellPast -> Color.LightGray
                                                else -> Color(0xFF2C1A36)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (isCheckIn) {
                            checkInMonth = activePickerMonthShort
                            checkInYear = activePickerYear
                            checkInDay = tempSelectedDay

                            val inCal = java.util.Calendar.getInstance().apply {
                                clear()
                                val mIdx = monthNamesList.indexOf(checkInMonth).let { if (it != -1) it else 9 }
                                set(checkInYear, mIdx, checkInDay)
                            }
                            val outCal = java.util.Calendar.getInstance().apply {
                                clear()
                                val mIdx = monthNamesList.indexOf(checkOutMonth).let { if (it != -1) it else 9 }
                                set(checkOutYear, mIdx, checkOutDay)
                            }

                            if (!outCal.after(inCal)) {
                                inCal.add(java.util.Calendar.DAY_OF_MONTH, 1)
                                checkOutDay = inCal.get(java.util.Calendar.DAY_OF_MONTH)
                                checkOutMonth = monthNamesList[inCal.get(java.util.Calendar.MONTH)]
                                checkOutYear = inCal.get(java.util.Calendar.YEAR)
                            }
                        } else {
                            checkOutMonth = activePickerMonthShort
                            checkOutYear = activePickerYear
                            checkOutDay = tempSelectedDay

                            val inCal = java.util.Calendar.getInstance().apply {
                                clear()
                                val mIdx = monthNamesList.indexOf(checkInMonth).let { if (it != -1) it else 9 }
                                set(checkInYear, mIdx, checkInDay)
                            }
                            val outCal = java.util.Calendar.getInstance().apply {
                                clear()
                                val mIdx = monthNamesList.indexOf(checkOutMonth).let { if (it != -1) it else 9 }
                                set(checkOutYear, mIdx, checkOutDay)
                            }

                            if (!outCal.after(inCal)) {
                                outCal.add(java.util.Calendar.DAY_OF_MONTH, -1)
                                checkInDay = outCal.get(java.util.Calendar.DAY_OF_MONTH)
                                checkInMonth = monthNamesList[outCal.get(java.util.Calendar.MONTH)]
                                checkInYear = outCal.get(java.util.Calendar.YEAR)
                            }
                        }
                        showCheckInDatePicker = false
                        showCheckOutDatePicker = false
                        if (selectedShapeIndex == 3) {
                            showExtendStayDialog = true
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Select Date", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showCheckInDatePicker = false
                    showCheckOutDatePicker = false
                }) {
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
fun ChooseCategoryScreenPreview() {
    AzureCalendarTheme {
        ChooseCategoryScreen()
    }
}
