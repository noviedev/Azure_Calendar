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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.HourglassTop
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

private val PurplePrimary = Color(0xFF6A1B9A)
private val ScreenBg = Color(0xFFFAF5FC)
private val TextDark = Color(0xFF2C1A36)
private val TextMuted = Color(0xFF7B6287)

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
    onSaveClick: (shape: String, color: Color, unitName: String, dateText: String) -> Unit = { _, _, _, _ -> }
) {
    val unitOptions = listOf("Unit 12A", "Unit 15B", "Unit 08C", "Unit 06D")

    val initialUnitIdx = remember(initialUnitName) {
        val foundIdx = unitOptions.indexOfFirst {
            initialUnitName.contains(it, ignoreCase = true) || it.contains(initialUnitName, ignoreCase = true)
        }
        if (foundIdx != -1) foundIdx else 0
    }

    val initialDays = remember(selectedDateText) {
        val numbers = Regex("\\b([1-9]|[12][0-9]|3[01])\\b").findAll(selectedDateText).mapNotNull { it.value.toIntOrNull() }.toList()
        if (numbers.size >= 2) {
            Pair(numbers[0], numbers[1])
        } else if (numbers.isNotEmpty()) {
            Pair(numbers[0], if (numbers[0] < 31) numbers[0] + 1 else 1)
        } else {
            Pair(1, 2)
        }
    }

    var checkInDay by remember { mutableIntStateOf(initialDays.first) }
    var checkOutDay by remember { mutableIntStateOf(initialDays.second) }

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
        ShapeOption("Star", "Not Available", R.drawable.star, Color(0xFFE53935), Color(0xFFFFEBEE))
    )

    val realTimeCal = remember { java.util.Calendar.getInstance() }
    val monthNamesList = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sept", "Oct", "Nov", "Dec")
    val currentRealMonthShort = monthNamesList.getOrElse(realTimeCal.get(java.util.Calendar.MONTH)) { "Oct" }
    val currentRealYear = realTimeCal.get(java.util.Calendar.YEAR)

    var activeMonthShort by remember(selectedDateText) {
        val foundM = monthNamesList.find { selectedDateText.contains(it, ignoreCase = true) }
        mutableStateOf(foundM ?: currentRealMonthShort)
    }

    var activeYear by remember(selectedDateText) {
        val foundY = Regex("\\b20\\d{2}\\b").find(selectedDateText)?.value?.toIntOrNull()
        mutableIntStateOf(foundY ?: currentRealYear)
    }

    val fullMonthName = remember(activeMonthShort) {
        when (activeMonthShort.lowercase()) {
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
            else -> activeMonthShort
        }
    }

    val currentFormattedDate = remember(checkInDay, checkOutDay, activeMonthShort, activeYear) {
        if (checkInDay == checkOutDay) "$activeMonthShort $checkInDay, $activeYear"
        else "$activeMonthShort $checkInDay - $activeMonthShort $checkOutDay, $activeYear"
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
                        text = "Choose Category",
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

            // Unit Selector Section
            Text(
                text = "Select Unit",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = TextDark
            )
            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                unitOptions.forEachIndexed { idx, unitName ->
                    val isSelected = selectedUnitIndex == idx
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = if (isSelected) PurplePrimary else Color(0xFFF3EDFD),
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .clickable { selectedUnitIndex = idx }
                    ) {
                        Box(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = unitName,
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) Color.White else TextMuted
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Check-in and Check-out Date Range Picker Section
            Text(
                text = "Select Date Range",
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
                                    text = "$activeMonthShort $checkInDay, $activeYear",
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
                                    text = "$activeMonthShort $checkOutDay, $activeYear",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextDark
                                )
                            }
                        }
                    }
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

            Button(
                onClick = {
                    onSaveClick(
                        selectedShape.statusLabel,
                        selectedShape.color,
                        unitOptions[selectedUnitIndex],
                        currentFormattedDate
                    )
                },
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Text(
                    text = "Save Tag ($currentFormattedDate)",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }

    if (showCheckInDatePicker || showCheckOutDatePicker) {
        val isCheckIn = showCheckInDatePicker
        val titleText = if (isCheckIn) "Select Check-in Date" else "Select Check-out Date"
        val targetInitialDay = if (isCheckIn) checkInDay else checkOutDay
        var tempSelectedDay by remember(isCheckIn, targetInitialDay) { mutableIntStateOf(targetInitialDay) }

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
                    color = TextDark
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
                                val currentIdx = monthNamesList.indexOf(activeMonthShort)
                                val prevIdx = if (currentIdx > 0) currentIdx - 1 else 11
                                activeMonthShort = monthNamesList[prevIdx]
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Text("◀", fontSize = 12.sp, color = PurplePrimary)
                        }

                        Text(
                            text = "$fullMonthName $activeYear",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = PurplePrimary
                        )

                        IconButton(
                            onClick = {
                                val currentIdx = monthNamesList.indexOf(activeMonthShort)
                                val nextIdx = if (currentIdx < 11) currentIdx + 1 else 0
                                activeMonthShort = monthNamesList[nextIdx]
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
                            Text(h, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextMuted)
                        }
                    }

                    val daysGrid = (1..31).toList().chunked(7)
                    Column(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        daysGrid.forEach { rowDays ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceAround
                            ) {
                                rowDays.forEach { dayNum ->
                                    val isSelected = dayNum == tempSelectedDay
                                    Surface(
                                        shape = CircleShape,
                                        color = if (isSelected) PurplePrimary else Color.Transparent,
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .clickable { tempSelectedDay = dayNum }
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
                Button(
                    onClick = {
                        if (isCheckIn) {
                            checkInDay = tempSelectedDay
                            if (checkOutDay <= checkInDay) {
                                checkOutDay = minOf(31, checkInDay + 1)
                            }
                        } else {
                            checkOutDay = tempSelectedDay
                            if (checkInDay >= checkOutDay) {
                                checkInDay = maxOf(1, checkOutDay - 1)
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
