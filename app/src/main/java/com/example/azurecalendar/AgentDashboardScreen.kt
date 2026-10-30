package com.example.azurecalendar

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import coil.compose.AsyncImage
import com.example.azurecalendar.ui.theme.AzureCalendarTheme

private val PurplePrimary get() = AzureCalendarColors.PurplePrimary
private val ScreenBg = Color(0xFFFAF7FF)
private val CardBg = Color(0xFFFFFFFF)
private val TextDark = Color(0xFF2C1A36)
private val TextMuted = Color(0xFF7B6287)

@Composable
fun AgentDashboardScreen(
    agentName: String = "Alex Reyes",
    @Suppress("UNUSED_PARAMETER") onNavigateToUnits: () -> Unit = {},
    onLogoutClick: () -> Unit = {}
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) } // 0: Home, 1: Calendar, 2: Units, 3: Event, 4: Profile
    var showFullScheduleViewAll by remember { mutableStateOf(false) }

    if (showFullScheduleViewAll) {
        FullScheduleViewAllScreen(
            onBackClick = { showFullScheduleViewAll = false }
        )
    } else {
        Scaffold(
            bottomBar = {
                AgentBottomNavigationBar(
                    selectedTab = selectedTab,
                    onTabSelected = { selectedTab = it }
                )
            },
            containerColor = ScreenBg
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                when (selectedTab) {
                    0 -> HomeDashboardContent(
                        agentName = agentName,
                        onLogoutClick = onLogoutClick,
                        onNavigateToCalendar = { showFullScheduleViewAll = true }
                    )
                    1 -> CalendarTabContent(agentName = agentName, onNavigateToUnits = { selectedTab = 2 })
                    2 -> UnitsTabContent(agentName = agentName, onTabSelected = { selectedTab = it })
                    3 -> NotificationsCenterScreen(isOwnerMode = false, agentName = agentName)
                    4 -> ProfileTabContent(agentName = agentName, isOwnerMode = false, onLogoutClick = onLogoutClick)
                    else -> HomeDashboardContent(agentName = agentName, onLogoutClick = onLogoutClick)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullScheduleViewAllScreen(
    onBackClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val isPreview = LocalInspectionMode.current
    val dbHelper = remember { AzureDatabaseHelper(context) }
    val syncTick = FirebaseCloudSyncManager.globalCloudSyncTrigger.intValue

    val realTimeCal = remember { java.util.Calendar.getInstance() }
    val monthNamesList = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sept", "Oct", "Nov", "Dec")
    val currentShortM = monthNamesList.getOrElse(realTimeCal.get(java.util.Calendar.MONTH)) { "Oct" }
    val currentDay = realTimeCal.get(java.util.Calendar.DAY_OF_MONTH)
    val currentYear = realTimeCal.get(java.util.Calendar.YEAR)
    val todayDateStr = "$currentShortM $currentDay, $currentYear"

    val dbEvents = remember(isPreview, syncTick) {
        if (isPreview) emptyList() else dbHelper.getAllEvents()
    }
    val unitList = remember(isPreview, syncTick) {
        if (isPreview) {
            listOf("Unit 12A", "Unit 15B", "Unit 08C", "Unit 06D")
        } else {
            val defaultUnits = listOf("Unit 12A", "Unit 15B", "Unit 08C", "Unit 06D")
            val dbUnits = dbHelper.getAllUnits().mapNotNull { it["unitNumber"]?.toString() }
            (dbUnits + defaultUnits)
                .distinctBy { it.replace("Unit", "", ignoreCase = true).trim() }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ScreenBg)
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = PurplePrimary
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = "Today's Units Schedule",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextDark
                )
                Text(
                    text = todayDateStr,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = PurplePrimary
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(unitList) { unitName ->
                val cleanU = unitName.replace("Unit", "", ignoreCase = true).trim()
                val matchingEvt = dbEvents.find { evt ->
                    val u = (evt["unit"] ?: "").replace("Unit", "", ignoreCase = true).trim()
                    u.equals(cleanU, ignoreCase = true)
                }

                val matchedUnitRow = dbHelper.getAllUnits().find {
                    (it["unitNumber"]?.toString() ?: "").replace("Unit", "", ignoreCase = true).trim().equals(cleanU, ignoreCase = true)
                }

                val status = matchingEvt?.get("shape") ?: matchedUnitRow?.get("status")?.toString() ?: "Available"
                val time = matchingEvt?.get("time") ?: "10:00 AM"
                val agent = matchingEvt?.get("agent") ?: "Admin"

                val (statusBg, statusTextCol, dotColor) = when {
                    status.contains("Check", ignoreCase = true) || status.contains("Heart", ignoreCase = true) -> Triple(Color(0xFFFFEBEE), Color(0xFFC2185B), Color(0xFFE91E63))
                    status.contains("Reserve", ignoreCase = true) || status.contains("Diamond", ignoreCase = true) -> Triple(Color(0xFFFFF3E0), Color(0xFFEF6C00), Color(0xFFF39C12))
                    status.contains("Maintenance", ignoreCase = true) || status.contains("Circle", ignoreCase = true) -> Triple(Color(0xFFE3F2FD), Color(0xFF1976D2), Color(0xFF2196F3))
                    status.contains("Not", ignoreCase = true) || status.contains("Star", ignoreCase = true) -> Triple(Color(0xFFFFEBEE), Color(0xFFC62828), Color(0xFFE53935))
                    else -> Triple(Color(0xFFE8F5E9), Color(0xFF2E7D32), Color(0xFF00A86B))
                }

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
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = unitName,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextDark
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "$time • Agent: $agent",
                                fontSize = 12.sp,
                                color = TextMuted
                            )
                        }

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
                                    text = status,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = statusTextCol
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeDashboardContent(
    agentName: String,
    @Suppress("UNUSED_PARAMETER") onLogoutClick: () -> Unit,
    @Suppress("UNUSED_PARAMETER") onNavigateToCalendar: () -> Unit = {}
) {
    val context = LocalContext.current
    val isPreview = LocalInspectionMode.current

    val dbEventsState = remember { mutableStateOf<List<Map<String, String>>>(emptyList()) }
    val dbUnitsState = remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    val dbHelper = remember { AzureDatabaseHelper(context) }

    LaunchedEffect(Unit, FirebaseCloudSyncManager.globalCloudSyncTrigger.intValue) {
        dbEventsState.value = dbHelper.getAllEvents()
        dbUnitsState.value = dbHelper.getAllUnits()
    }

    val (availableCount, reservedCount, occupiedCount) = remember(dbEventsState.value, dbUnitsState.value, isPreview) {
        if (isPreview) {
            Triple("12", "5", "3")
        } else {
            try {
                val deletedPrefs = context.getSharedPreferences("azure_calendar_deleted_units", Context.MODE_PRIVATE)
                val deletedSet = deletedPrefs.getStringSet("deleted_unit_numbers", emptySet()).orEmpty()
                val defaultUnitsMap = listOf(
                    mapOf("unitNumber" to "Unit 12A", "status" to "Available"),
                    mapOf("unitNumber" to "Unit 15B", "status" to "Available"),
                    mapOf("unitNumber" to "Unit 08C", "status" to "Available"),
                    mapOf("unitNumber" to "Unit 06D", "status" to "Available")
                )
                val dbUnits = (dbUnitsState.value + defaultUnitsMap).distinctBy {
                    (it["unitNumber"]?.toString() ?: "").replace("Unit", "", ignoreCase = true).trim()
                }.filter { unitMap ->
                    val num = unitMap["unitNumber"]?.toString() ?: ""
                    deletedSet.none { isSameUnit(it, num) }
                }

                var occCount = 0
                var resCount = 0
                var availCount = 0

                dbUnits.forEach { matchedUnitMap ->
                    val status = (matchedUnitMap["status"]?.toString() ?: "Available").lowercase()
                    when {
                        status.contains("check") || status.contains("heart") || status.contains("occup") || status.contains("rent") -> occCount++
                        status.contains("reserve") || status.contains("diamond") -> resCount++
                        else -> availCount++
                    }
                }

                Triple(availCount.toString(), resCount.toString(), occCount.toString())
            } catch (_: Exception) {
                Triple("0", "0", "0")
            }
        }
    }

    val prefs = remember { context.getSharedPreferences("azure_calendar_profile_prefs", Context.MODE_PRIVATE) }
    var profilePhotoUri by remember {
        mutableStateOf(if (isPreview) "" else prefs.getString("profile_photo_$agentName", "") ?: "")
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val savedPath = saveSampledProfilePhoto(context, uri, "profile_photo_$agentName.jpg")
            if (savedPath != null) {
                profilePhotoUri = savedPath
                if (!isPreview) {
                    prefs.edit().putString("profile_photo_$agentName", savedPath).apply()
                }
                Toast.makeText(context, "Profile picture updated from Gallery!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val infoPrefs = remember { context.getSharedPreferences("azure_calendar_personal_info", Context.MODE_PRIVATE) }
    val displayHeaderName = remember(agentName, isPreview) {
        if (isPreview) agentName
        else infoPrefs.getString("name_$agentName", infoPrefs.getString("name_Admin", agentName)) ?: agentName
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    contentAlignment = Alignment.BottomEnd,
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .clickable { galleryLauncher.launch("image/*") }
                ) {
                    Surface(
                        shape = CircleShape,
                        color = PurplePrimary,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (profilePhotoUri.isNotBlank()) {
                                val fileObj = java.io.File(profilePhotoUri)
                                val imageModel: Any = if (fileObj.exists()) fileObj else profilePhotoUri

                                AsyncImage(
                                    model = imageModel,
                                    contentDescription = "Agent Profile Photo",
                                    contentScale = ContentScale.Crop,
                                    onError = {
                                        profilePhotoUri = ""
                                        if (!isPreview) {
                                            prefs.edit().remove("profile_photo_$agentName").apply()
                                        }
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Text(
                                    text = agentName.take(2).uppercase(),
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }

                    Surface(
                        shape = CircleShape,
                        color = Color.White,
                        border = BorderStroke(1.dp, PurplePrimary),
                        modifier = Modifier.size(18.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.CameraAlt,
                                contentDescription = "Upload Photo",
                                tint = PurplePrimary,
                                modifier = Modifier.size(10.dp)
                            )
                        }
                    }
                }

                val realTimeGreeting = remember {
                    val hour24 = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
                    when (hour24) {
                        in 5..11 -> "Good morning,"
                        in 12..16 -> "Good afternoon,"
                        else -> "Good evening,"
                    }
                }

                Column {
                    Text(
                        text = realTimeGreeting,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextMuted
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = displayHeaderName,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextDark
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "👋", fontSize = 18.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            StatusSummaryCard(
                title = "Available",
                count = availableCount,
                bgColor = Color(0xFFE8F8F5),
                textColor = Color(0xFF00A86B),
                modifier = Modifier.weight(1f)
            )
            StatusSummaryCard(
                title = "Reserved",
                count = reservedCount,
                bgColor = Color(0xFFFFF8E7),
                textColor = Color(0xFFF39C12),
                modifier = Modifier.weight(1f)
            )
            StatusSummaryCard(
                title = "Occupied",
                count = occupiedCount,
                bgColor = Color(0xFFFFEAEA),
                textColor = Color(0xFFE74C3C),
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Today's Schedule",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = TextDark
            )
            Text(
                text = "View All",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = PurplePrimary,
                modifier = Modifier.clickable { onNavigateToCalendar() }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        val syncedUnits = dbUnitsState.value.distinctBy {
            (it["unitNumber"]?.toString() ?: "").replace("Unit", "", ignoreCase = true).trim()
        }
        syncedUnits.take(4).forEachIndexed { idx, unitMap ->
            val uName = unitMap["unitNumber"]?.toString() ?: "Unit"
            val uStatus = unitMap["status"]?.toString() ?: "Available"
            val indColor = when {
                uStatus.contains("Reserve", ignoreCase = true) -> Color(0xFFF39C12)
                uStatus.contains("Available", ignoreCase = true) -> Color(0xFF00A86B)
                else -> Color(0xFFE74C3C)
            }
            val timeSlot = when (idx) {
                0 -> "10:00 AM"
                1 -> "2:00 PM"
                else -> "5:00 PM"
            }
            ScheduleItemCard(
                time = timeSlot,
                unit = uName,
                type = uStatus,
                indicatorColor = indColor
            )
            Spacer(modifier = Modifier.height(10.dp))
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}

@Composable
private fun CalendarTabContent(
    agentName: String,
    @Suppress("UNUSED_PARAMETER") onNavigateToUnits: () -> Unit
) {
    val context = LocalContext.current
    val isPreview = LocalInspectionMode.current
    val dbHelper = remember { AzureDatabaseHelper(context) }

    val realTimeCal = remember { java.util.Calendar.getInstance() }
    val monthNamesList = listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")
    val currentRealMonth = monthNamesList.getOrElse(realTimeCal.get(java.util.Calendar.MONTH)) { "October" }
    val currentRealYear = realTimeCal.get(java.util.Calendar.YEAR)
    val currentRealDay = realTimeCal.get(java.util.Calendar.DAY_OF_MONTH)

    var selectedDayNumber by remember { mutableIntStateOf(currentRealDay) }
    var selectedCheckInDay by remember { mutableIntStateOf(currentRealDay) }
    var selectedCheckOutDay by remember { mutableIntStateOf((currentRealDay + 2).coerceAtMost(30)) }
    var currentSubScreen by remember { mutableStateOf("calendar") }
    var selectedUnitName by remember { mutableStateOf("Unit 12A") }
    var refreshTrigger by remember { mutableIntStateOf(0) }
    var activeMonthName by remember { mutableStateOf(currentRealMonth) }
    var activeYearNumber by remember { mutableIntStateOf(currentRealYear) }

    val currentMonthText = remember(activeMonthName, activeYearNumber) {
        "$activeMonthName $activeYearNumber"
    }

    val dbEventsState = remember { mutableStateOf<List<Map<String, String>>>(emptyList()) }
    val dbUnitsState = remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }

    LaunchedEffect(currentSubScreen, refreshTrigger, activeMonthName, activeYearNumber, FirebaseCloudSyncManager.globalCloudSyncTrigger.intValue) {
        dbEventsState.value = dbHelper.getAllEvents()
        dbUnitsState.value = dbHelper.getAllUnits()
    }

    val calendarDays = remember(activeMonthName, activeYearNumber, currentSubScreen, isPreview, refreshTrigger, dbEventsState.value) {
        if (isPreview) {
            createSampleCalendarMonth(agentName)
        } else {
            createSampleCalendarMonth(agentName).map { day ->
                val dbEvents = dbEventsState.value.filter { evt ->
                    val evtDate = evt["date"] ?: ""
                    val shortM = activeMonthName.take(3)
                    val monthMatches = evtDate.contains(shortM, ignoreCase = true) || evtDate.contains(activeMonthName, ignoreCase = true)
                    val yearMatches = evtDate.contains(activeYearNumber.toString())
                    monthMatches && yearMatches && isDateCoveredByEvent(evtDate, day.dayNumber, activeMonthName)
                }
                if (dbEvents.isNotEmpty()) {
                    val first = dbEvents[0]
                    val colorLong = first["color"]?.toLongOrNull() ?: 0L
                    val customCol = if (colorLong != 0L) Color(colorLong.toULong()) else null
                    day.copy(
                        customColor = customCol,
                        events = dbEvents.map { evt ->
                            CalendarEventModel(
                                id = evt["id"] ?: "0",
                                title = evt["title"] ?: "Booking",
                                date = evt["date"] ?: "${activeMonthName.take(3)} ${day.dayNumber}, $activeYearNumber",
                                time = evt["time"] ?: "2:00 PM",
                                location = "Azure Tower",
                                description = evt["shape"] ?: "Booking",
                                assignedAgent = evt["agent"] ?: agentName,
                                unitNumber = evt["unit"] ?: "12A",
                                guestName = "Guest",
                                shapeBadge = null
                            )
                        }
                    )
                } else {
                    day.copy(customColor = null, events = emptyList(), assignedShape = null)
                }
            }
        }
    }

    when (currentSubScreen) {
        "booking_details" -> {
            val selectedDayModel = calendarDays.find { it.dayNumber == selectedDayNumber }
            val currentEvt = selectedDayModel?.events?.firstOrNull()
            val unitForDisplay = currentEvt?.unitNumber ?: selectedUnitName
            BookingDetailsScreen(
                unitName = unitForDisplay,
                isOwnerMode = false,
                onBackClick = { currentSubScreen = "calendar" },
                onViewUnitDetailsClick = { currentSubScreen = "units_view" },
                onEditTagClick = { currentSubScreen = "choose_category" },
                onDeleteBookingClick = {
                    val matchingEvts = dbHelper.getAllEvents().filter { it["date"]?.contains("$selectedDayNumber") == true }
                    matchingEvts.forEach { e ->
                        e["id"]?.toLongOrNull()?.let { dbHelper.deleteEvent(it) }
                    }
                    FirebaseCloudSyncManager.deleteEventFromCloud("", unitForDisplay)
                    FirebaseCloudSyncManager.pushUnitStatusToCloud(unitForDisplay, "Available")
                    refreshTrigger++
                    currentSubScreen = "calendar"
                    Toast.makeText(context, "Tag & booking deleted for ${activeMonthName.take(3)} $selectedDayNumber!", Toast.LENGTH_SHORT).show()
                }
            )
        }
        "units_view" -> {
            UnitsDirectoryScreen(
                initialDayNumber = selectedDayNumber,
                onDateChange = { selectedDayNumber = it },
                onViewScheduleClick = { unitNumber, _, _, _, _ ->
                    selectedUnitName = unitNumber
                    currentSubScreen = "choose_category"
                }
            )
        }
        "choose_category" -> {
            val shortM = activeMonthName.take(3)
            val defaultTargetDate = if (selectedCheckInDay == selectedCheckOutDay) {
                "$shortM $selectedCheckInDay, $activeYearNumber"
            } else {
                "$shortM $selectedCheckInDay - $shortM $selectedCheckOutDay, $activeYearNumber"
            }

            ChooseCategoryScreen(
                selectedDateText = defaultTargetDate,
                initialUnitName = selectedUnitName,
                onBackClick = { currentSubScreen = "units_view" },
                onSaveClick = { shape, color, unitName, dateText ->
                    val finalTargetDate = dateText.ifBlank { defaultTargetDate }
                    val colorLong = color.value.toLong()
                    dbHelper.deleteEventByDateAndUnit(finalTargetDate, unitName)
                    dbHelper.deleteEventsForUnit(unitName)
                    dbHelper.insertEvent(
                        title = "Staycation - $shape",
                        date = finalTargetDate,
                        time = "2:00 PM",
                        agent = agentName,
                        unit = unitName,
                        shape = shape,
                        color = colorLong
                    )
                    dbHelper.updateUnitStatus(unitName, shape)

                    val eventMap = mapOf(
                        "id" to System.currentTimeMillis().toString(),
                        "title" to "Staycation - $shape",
                        "date" to finalTargetDate,
                        "time" to "2:00 PM",
                        "agent" to agentName,
                        "unit" to unitName,
                        "shape" to shape,
                        "color" to colorLong.toString()
                    )
                    FirebaseCloudSyncManager.pushEventToCloud(eventMap)
                    FirebaseCloudSyncManager.pushUnitStatusToCloud(unitName, shape)

                    refreshTrigger++
                    currentSubScreen = "calendar"
                    Toast.makeText(context, "Tag $shape applied to $unitName for $finalTargetDate!", Toast.LENGTH_SHORT).show()

                    EmailTemplateHelper.sendBookingEmail(
                        context = context,
                        ownerEmail = "owner.azure@gmail.com",
                        agentEmail = "${agentName.lowercase().replace(" ", ".")}@gmail.com",
                        agentName = agentName,
                        unitName = unitName
                    )
                }
            )
        }
        else -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                AzureCalendarView(
                    days = calendarDays,
                    selectedDayNumber = selectedDayNumber,
                    monthTitle = currentMonthText,
                    onMonthYearSelected = { mName, yr ->
                        activeMonthName = mName
                        activeYearNumber = yr
                    },
                    onDateRangeSelected = { cIn, cOut ->
                        selectedCheckInDay = cIn
                        selectedCheckOutDay = cOut
                    },
                    onDaySelected = { dayModel ->
                        selectedDayNumber = dayModel.dayNumber
                        selectedCheckInDay = dayModel.dayNumber
                        selectedCheckOutDay = dayModel.dayNumber
                        currentSubScreen = "calendar"
                    }
                )

                val selectedDayModel = calendarDays.find { it.dayNumber == selectedDayNumber }

                Spacer(modifier = Modifier.height(14.dp))

                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = CardBg),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        val shortM = activeMonthName.take(3)
                        Column {
                            Text(
                                text = "Units Status — $shortM $selectedDayNumber, $activeYearNumber",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextDark
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Status of all units on $shortM $selectedDayNumber, $activeYearNumber",
                                fontSize = 12.sp,
                                color = TextMuted
                            )
                        }

                        HorizontalDivider(color = Color(0xFFF3E5F5))

                        val defaultUnits = listOf("Unit 12A", "Unit 15B", "Unit 08C", "Unit 06D")
                        val dbUnits = dbUnitsState.value.mapNotNull { it["unitNumber"]?.toString() }
                        val unitList = (dbUnits + defaultUnits)
                            .distinctBy { it.replace("Unit", "", ignoreCase = true).trim() }
                        val eventsForSelectedDay = selectedDayModel?.events.orEmpty()

                        unitList.forEach { uName ->
                            val cleanU = uName.replace("Unit", "", ignoreCase = true).trim()
                            val matchingEvt = eventsForSelectedDay.find {
                                val evtUnit = it.unitNumber.replace("Unit", "", ignoreCase = true).trim()
                                evtUnit.equals(cleanU, ignoreCase = true) || it.unitNumber.contains(uName, ignoreCase = true)
                            }
                            val matchedUnitMap = dbUnitsState.value.find {
                                (it["unitNumber"]?.toString() ?: "").replace("Unit", "", ignoreCase = true).trim().equals(cleanU, ignoreCase = true)
                            }
                            val fallbackUnitStatus = matchedUnitMap?.get("status")?.toString() ?: "Available"
                            val rawStatus = matchingEvt?.description?.replace("Category: ", "")?.ifBlank { fallbackUnitStatus } ?: fallbackUnitStatus
                            val status = rawStatus.ifBlank { "Available" }
                            val (statusBg, statusTextCol, dotColor) = when {
                                status.contains("Available", ignoreCase = true) && !status.contains("Not", ignoreCase = true) -> Triple(Color(0xFFE8F5E9), Color(0xFF2E7D32), Color(0xFF00A86B))
                                status.contains("Check", ignoreCase = true) || status.contains("Heart", ignoreCase = true) -> Triple(Color(0xFFFFEBEE), Color(0xFFC2185B), Color(0xFFE91E63))
                                status.contains("Reserve", ignoreCase = true) || status.contains("Diamond", ignoreCase = true) -> Triple(Color(0xFFFFF3E0), Color(0xFFEF6C00), Color(0xFFF39C12))
                                status.contains("Maintenance", ignoreCase = true) || status.contains("Circle", ignoreCase = true) -> Triple(Color(0xFFE3F2FD), Color(0xFF1976D2), Color(0xFF2196F3))
                                else -> Triple(Color(0xFFFFEBEE), Color(0xFFC62828), Color(0xFFE74C3C))
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFFF8F5FE), RoundedCornerShape(12.dp))
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = uName,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextDark
                                )

                                Surface(
                                    shape = RoundedCornerShape(20.dp),
                                    color = statusBg
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
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
                                            text = status,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = statusTextCol
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
}

@Composable
private fun StatusSummaryCard(
    title: String,
    count: String,
    bgColor: Color,
    textColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = bgColor,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                fontSize = 12.sp,
                color = textColor,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = count,
                fontSize = 22.sp,
                color = textColor,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun ScheduleItemCard(
    time: String,
    unit: String,
    type: String,
    indicatorColor: Color
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(36.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(indicatorColor)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = time,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextDark
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = unit,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextDark
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = type,
                    fontSize = 12.sp,
                    color = TextMuted
                )
            }
        }
    }
}

@Composable
private fun AgentBottomNavigationBar(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit
) {
    Surface(
        color = CardBg,
        shadowElevation = 8.dp,
        modifier = Modifier.fillMaxWidth()
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

@Composable
private fun BottomNavItem(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp)
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
                tint = if (isSelected) PurplePrimary else Color.Gray,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) PurplePrimary else Color.Gray
        )
    }
}

@Preview(showBackground = true)
@Composable
fun AgentDashboardScreenPreview() {
    AzureCalendarTheme {
        AgentDashboardScreen(agentName = "Alex")
    }
}

fun saveSampledProfilePhoto(context: Context, uri: Uri, fileName: String): String? {
    return try {
        val inputStream = context.contentResolver.openInputStream(uri) ?: return null
        val originalBitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
        inputStream.close()
        if (originalBitmap == null) return null

        val maxDim = 512
        val width = originalBitmap.width
        val height = originalBitmap.height
        val scale = (maxDim.toFloat() / maxOf(width, height)).coerceAtMost(1.0f)

        val scaledWidth = (width * scale).toInt().coerceAtLeast(1)
        val scaledHeight = (height * scale).toInt().coerceAtLeast(1)

        val scaledBitmap = android.graphics.Bitmap.createScaledBitmap(originalBitmap, scaledWidth, scaledHeight, true)
        if (scaledBitmap != originalBitmap) {
            originalBitmap.recycle()
        }

        val file = java.io.File(context.filesDir, fileName)
        val outputStream = java.io.FileOutputStream(file)
        scaledBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, outputStream)
        outputStream.flush()
        outputStream.close()
        scaledBitmap.recycle()

        file.absolutePath
    } catch (_: Throwable) {
        null
    }
}

@Composable
fun ProfileTabContent(agentName: String, isOwnerMode: Boolean = false, onLogoutClick: () -> Unit) {
    val context = LocalContext.current
    val isPreview = LocalInspectionMode.current
    val prefs = remember { context.getSharedPreferences("azure_calendar_profile_prefs", Context.MODE_PRIVATE) }
    val infoPrefs = remember { context.getSharedPreferences("azure_calendar_personal_info", Context.MODE_PRIVATE) }

    val appearancePrefs = remember { context.getSharedPreferences("azure_calendar_appearance_prefs", Context.MODE_PRIVATE) }
    var showAppearanceDialog by remember { mutableStateOf(false) }

    var selectedColorHex by remember {
        mutableStateOf(if (isPreview) "0xFF6A1B9A" else appearancePrefs.getString("theme_color_$agentName", "0xFF6A1B9A") ?: "0xFF6A1B9A")
    }

    val securityPrefs = remember { context.getSharedPreferences("azure_calendar_security_info", Context.MODE_PRIVATE) }
    var showSecurityDialog by remember { mutableStateOf(false) }

    val notifPrefs = remember { context.getSharedPreferences("azure_calendar_notification_prefs", Context.MODE_PRIVATE) }
    var showNotifSettingsDialog by remember { mutableStateOf(false) }

    val emailPrefs = remember { context.getSharedPreferences("azure_calendar_email_prefs", Context.MODE_PRIVATE) }
    var showEmailSettingsDialog by remember { mutableStateOf(false) }

    var isBookingNotifEnabled by remember {
        mutableStateOf(if (isPreview) true else notifPrefs.getBoolean("notif_booking_$agentName", true))
    }
    var isChatNotifEnabled by remember {
        mutableStateOf(if (isPreview) true else notifPrefs.getBoolean("notif_chat_$agentName", true))
    }
    var isEventNotifEnabled by remember {
        mutableStateOf(if (isPreview) true else notifPrefs.getBoolean("notif_event_$agentName", true))
    }
    var isEmailAlertsEnabled by remember {
        mutableStateOf(if (isPreview) true else notifPrefs.getBoolean("notif_email_$agentName", true))
    }
    var isSoundVibrationEnabled by remember {
        mutableStateOf(if (isPreview) true else notifPrefs.getBoolean("notif_sound_$agentName", true))
    }

    @Suppress("UNUSED_VARIABLE")
    var is2FAEnabled by remember {
        mutableStateOf(if (isPreview) true else securityPrefs.getBoolean("2fa_$agentName", true))
    }
    var isBiometricEnabled by remember {
        mutableStateOf(if (isPreview) false else securityPrefs.getBoolean("biometric_$agentName", false))
    }
    @Suppress("UNUSED_VARIABLE")
    var showFingerprintScanDialog by remember { mutableStateOf(false) }
    var currentPasswordInput by remember { mutableStateOf("") }
    var newPasswordInput by remember { mutableStateOf("") }
    var confirmPasswordInput by remember { mutableStateOf("") }

    val defaultRole = if (agentName.contains("Admin", ignoreCase = true)) "Property Owner & Admin" else "Senior Leasing Agent"
    val defaultEmail = "${agentName.lowercase().replace(" ", "")}@gmail.com"

    var senderEmailInput by remember {
        mutableStateOf(if (isPreview) defaultEmail else emailPrefs.getString("sender_email_$agentName", defaultEmail) ?: defaultEmail)
    }
    var isAutoBookingEmailEnabled by remember {
        mutableStateOf(if (isPreview) true else emailPrefs.getBoolean("auto_booking_$agentName", true))
    }
    var isAutoInvoiceEmailEnabled by remember {
        mutableStateOf(if (isPreview) true else emailPrefs.getBoolean("auto_invoice_$agentName", true))
    }
    var isAutoScheduleEmailEnabled by remember {
        mutableStateOf(if (isPreview) true else emailPrefs.getBoolean("auto_schedule_$agentName", true))
    }

    var profilePhotoUri by remember {
        mutableStateOf(if (isPreview) "" else prefs.getString("profile_photo_$agentName", "") ?: "")
    }
    var showPersonalInfoDialog by remember { mutableStateOf(false) }

    var fullNameState by remember { mutableStateOf(if (isPreview) agentName else infoPrefs.getString("name_$agentName", agentName) ?: agentName) }
    var emailState by remember { mutableStateOf(if (isPreview) defaultEmail else infoPrefs.getString("email_$agentName", defaultEmail) ?: defaultEmail) }
    var phoneState by remember { mutableStateOf(if (isPreview) "+63 917 888 2468" else infoPrefs.getString("phone_$agentName", "+63 917 888 2468") ?: "+63 917 888 2468") }
    var roleState by remember { mutableStateOf(if (isPreview) defaultRole else infoPrefs.getString("role_$agentName", defaultRole) ?: defaultRole) }
    var genderState by remember { mutableStateOf(if (isPreview) "Female" else infoPrefs.getString("gender_$agentName", "Female") ?: "Female") }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val savedPath = saveSampledProfilePhoto(context, uri, "profile_photo_$agentName.jpg")
            if (savedPath != null) {
                profilePhotoUri = savedPath
                if (!isPreview) {
                    prefs.edit { putString("profile_photo_$agentName", savedPath) }
                }
                Toast.makeText(context, "Profile picture updated from Gallery!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .background(ScreenBg)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .background(
                    brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                        colors = listOf(Color(0xFFB39DDB), Color(0xFF6A1B9A))
                    )
                )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Profile",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .offset(y = (-30).dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = CardBg,
                shadowElevation = 3.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Start
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.BottomEnd,
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .clickable { galleryLauncher.launch("image/*") }
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = PurplePrimary,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    if (profilePhotoUri.isNotBlank()) {
                                        val fileObj = java.io.File(profilePhotoUri)
                                        val imageModel: Any = if (fileObj.exists()) fileObj else profilePhotoUri

                                        AsyncImage(
                                            model = imageModel,
                                            contentDescription = "Profile Picture",
                                            contentScale = ContentScale.Crop,
                                            onError = {
                                                profilePhotoUri = ""
                                                if (!isPreview) {
                                                    prefs.edit().remove("profile_photo_$agentName").apply()
                                                }
                                            },
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        Text(
                                            text = fullNameState.take(2).uppercase(),
                                            fontSize = 22.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                }
                            }

                            Surface(
                                shape = CircleShape,
                                color = Color.White,
                                border = BorderStroke(1.5.dp, PurplePrimary),
                                modifier = Modifier.size(22.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.CameraAlt,
                                        contentDescription = "Upload Photo",
                                        tint = PurplePrimary,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            }
                        }

                        Column {
                            Text(
                                text = fullNameState,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextDark
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = roleState,
                                fontSize = 12.sp,
                                color = TextMuted
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = emailState,
                                fontSize = 12.sp,
                                color = TextMuted
                            )
                        }
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = CardBg,
                shadowElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 8.dp)
                ) {
                    ProfileMenuItem(
                        icon = Icons.Default.Person,
                        title = "Personal Information",
                        onClick = { showPersonalInfoDialog = true }
                    )
                    ProfileMenuItem(
                        icon = Icons.Default.Security,
                        title = "Account Security",
                        onClick = { showSecurityDialog = true }
                    )
                    ProfileMenuItem(
                        icon = Icons.Default.Notifications,
                        title = "Notification Settings",
                        onClick = { showNotifSettingsDialog = true }
                    )
                    ProfileMenuItem(
                        icon = Icons.Default.Email,
                        title = "Email Settings",
                        onClick = { showEmailSettingsDialog = true }
                    )
                    if (isOwnerMode) {
                        ProfileMenuItem(
                            icon = Icons.Default.Palette,
                            title = "Appearance",
                            onClick = { showAppearanceDialog = true }
                        )
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = CardBg,
                shadowElevation = 2.dp,
                border = BorderStroke(1.dp, Color(0xFFFFCDD2)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onLogoutClick() }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                        contentDescription = "Logout",
                        tint = Color(0xFFE53935),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Logout",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFE53935)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (showPersonalInfoDialog) {
        var isEditMode by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = {
                isEditMode = false
                showPersonalInfoDialog = false
            },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isEditMode) "Edit Personal Info" else "Personal Information",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextDark
                    )

                    if (!isEditMode) {
                        IconButton(
                            onClick = { isEditMode = true },
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFF3EDFD))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit Personal Information",
                                tint = PurplePrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    if (!isEditMode) {
                        Text("Account details for $fullNameState:", fontSize = 12.sp, color = TextMuted)

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFF8F5FE),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                InfoDisplayRow(icon = Icons.Default.Person, label = "Full Name", value = fullNameState)
                                HorizontalDivider(color = Color(0xFFEFE6F7))
                                InfoDisplayRow(icon = Icons.Default.Email, label = "Email Address", value = emailState)
                                HorizontalDivider(color = Color(0xFFEFE6F7))
                                InfoDisplayRow(icon = Icons.Default.Phone, label = "Phone Number", value = phoneState)
                                HorizontalDivider(color = Color(0xFFEFE6F7))
                                InfoDisplayRow(icon = Icons.Default.Badge, label = "Role / Position", value = roleState)
                                HorizontalDivider(color = Color(0xFFEFE6F7))
                                InfoDisplayRow(icon = Icons.Default.Face, label = "Gender", value = genderState)
                            }
                        }
                    } else {
                        Text("Edit your account details below:", fontSize = 12.sp, color = TextMuted)

                        OutlinedTextField(
                            value = fullNameState,
                            onValueChange = { fullNameState = it },
                            label = { Text("Full Name") },
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = emailState,
                            onValueChange = { emailState = it },
                            label = { Text("Email Address") },
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = phoneState,
                            onValueChange = { phoneState = it },
                            label = { Text("Phone / Contact Number") },
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = roleState,
                            onValueChange = { roleState = it },
                            label = { Text("Role / Position") },
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = genderState,
                            onValueChange = { genderState = it },
                            label = { Text("Gender") },
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            confirmButton = {
                if (isEditMode) {
                    Button(
                        onClick = {
                            if (!isPreview) {
                                infoPrefs.edit()
                                    .putString("name_$agentName", fullNameState)
                                    .putString("email_$agentName", emailState)
                                    .putString("phone_$agentName", phoneState)
                                    .putString("role_$agentName", roleState)
                                    .putString("gender_$agentName", genderState)
                                    .apply()

                                if (isOwnerMode || agentName.contains("Admin", ignoreCase = true)) {
                                    infoPrefs.edit().putString("email_Admin", emailState).apply()
                                    FirebaseCloudSyncManager.pushAdminProfileToCloud(
                                        fullName = fullNameState,
                                        email = emailState,
                                        phone = phoneState,
                                        role = roleState,
                                        gender = genderState
                                    )
                                }

                                val sessionMgr = UserSessionManager(context)
                                val currentRole = if (isOwnerMode) "Owner" else "Agent"
                                sessionMgr.saveSession(fullNameState, currentRole)

                                if (emailState.isNotBlank()) {
                                    try {
                                        com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.updateEmail(emailState)
                                            ?.addOnCompleteListener { task ->
                                                if (task.isSuccessful) {
                                                    android.util.Log.d("FirebaseEmail", "Admin/Owner Email synced to Firebase Cloud!")
                                                }
                                            }
                                    } catch (_: Exception) {}
                                }
                            }
                            Toast.makeText(context, "Personal information saved and synced to Firebase!", Toast.LENGTH_SHORT).show()
                            isEditMode = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Save Info", fontWeight = FontWeight.Bold)
                    }
                } else {
                    Button(
                        onClick = { showPersonalInfoDialog = false },
                        colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Close", fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                if (isEditMode) {
                    TextButton(onClick = { isEditMode = false }) {
                        Text("Cancel", color = TextMuted)
                    }
                }
            },
            containerColor = Color.White,
            shape = RoundedCornerShape(20.dp)
        )
    }

    if (showSecurityDialog) {
        AlertDialog(
            onDismissRequest = { showSecurityDialog = false },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Security, contentDescription = null, tint = PurplePrimary, modifier = Modifier.size(22.dp))
                    Text(
                        text = "Account Security",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextDark
                    )
                }
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    Text("Manage authentication, password, and security preferences:", fontSize = 12.sp, color = TextMuted)

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFF8F5FE),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = Color(0xFF00A86B), modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Account Protection Active", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextDark)
                            }
                            Text("Active Session: Android Device • Current Session", fontSize = 11.sp, color = TextMuted)
                        }
                    }

                    HorizontalDivider(color = Color(0xFFF3E5F5))

                    Text("Security Features", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextDark)

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Biometric / Fingerprint Unlock", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextDark)
                                Text("Use fingerprint or Face ID to sign in", fontSize = 11.sp, color = TextMuted)
                            }
                            Switch(
                                checked = isBiometricEnabled,
                                onCheckedChange = { checked ->
                                    isBiometricEnabled = checked
                                    if (!isPreview) {
                                        securityPrefs.edit().putBoolean("biometric_$agentName", checked).apply()
                                    }
                                    if (checked) {
                                        val activity = context as? androidx.fragment.app.FragmentActivity
                                        if (activity != null) {
                                            BiometricAuthHelper.promptBiometricAuthentication(
                                                activity = activity,
                                                title = "Enroll Biometric Fingerprint",
                                                subtitle = "Scan fingerprint to confirm automatic login for $agentName",
                                                onSuccess = {
                                                    Toast.makeText(context, "Fingerprint enrolled and linked for automatic login!", Toast.LENGTH_SHORT).show()
                                                }
                                            )
                                        } else {
                                            BiometricAuthHelper.openSystemBiometricEnrollment(context)
                                        }
                                    }
                                },
                                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = PurplePrimary)
                            )
                        }

                        if (isBiometricEnabled) {
                            TextButton(
                                onClick = {
                                    val activity = context as? androidx.fragment.app.FragmentActivity
                                    if (activity != null) {
                                        BiometricAuthHelper.promptBiometricAuthentication(
                                            activity = activity,
                                            title = "Register Fingerprint for App Unlock",
                                            subtitle = "Touch fingerprint sensor to register and link your account to unlock app",
                                            onSuccess = {
                                                val userKey = if (isOwnerMode || agentName.contains("Admin", ignoreCase = true)) "Admin" else agentName.lowercase()
                                                securityPrefs.edit()
                                                    .putBoolean("biometric_$agentName", true)
                                                    .putString("biometric_user_key", userKey)
                                                    .apply()
                                                Toast.makeText(context, "Fingerprint registered and linked for $agentName!", Toast.LENGTH_SHORT).show()
                                            }
                                        )
                                    } else {
                                        BiometricAuthHelper.openSystemBiometricEnrollment(context)
                                    }
                                },
                                modifier = Modifier.padding(top = 2.dp)
                            ) {
                                Text("👆 Scan & Register Fingerprint to Unlock App", fontSize = 11.sp, color = PurplePrimary, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    HorizontalDivider(color = Color(0xFFF3E5F5))

                    Text("Change Password", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextDark)

                    OutlinedTextField(
                        value = currentPasswordInput,
                        onValueChange = { currentPasswordInput = it },
                        label = { Text("Current Password") },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    var newPassVisible by remember { mutableStateOf(false) }
                    var confirmPassVisible by remember { mutableStateOf(false) }

                    OutlinedTextField(
                        value = newPasswordInput,
                        onValueChange = { newPasswordInput = it },
                        label = { Text("New Password") },
                        singleLine = true,
                        visualTransformation = if (newPassVisible) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { newPassVisible = !newPassVisible }) {
                                Icon(
                                    imageVector = if (newPassVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = "Toggle Password Visibility",
                                    tint = PurplePrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = confirmPasswordInput,
                        onValueChange = { confirmPasswordInput = it },
                        label = { Text("Confirm New Password") },
                        singleLine = true,
                        visualTransformation = if (confirmPassVisible) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { confirmPassVisible = !confirmPassVisible }) {
                                Icon(
                                    imageVector = if (confirmPassVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = "Toggle Password Visibility",
                                    tint = PurplePrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newPasswordInput.isNotBlank() && newPasswordInput != confirmPasswordInput) {
                            Toast.makeText(context, "New passwords do not match!", Toast.LENGTH_SHORT).show()
                        } else {
                            if (newPasswordInput.isNotBlank()) {
                                if (!isPreview) {
                                    securityPrefs.edit().putString("password_$agentName", newPasswordInput).apply()
                                    try {
                                        com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.updatePassword(newPasswordInput)
                                            ?.addOnCompleteListener { task ->
                                                if (task.isSuccessful) {
                                                    android.util.Log.d("FirebasePass", "Cloud password updated across all devices!")
                                                }
                                            }
                                    } catch (_: Exception) {}
                                }
                                Toast.makeText(context, "Password updated across all devices!", Toast.LENGTH_SHORT).show()
                                currentPasswordInput = ""
                                newPasswordInput = ""
                                confirmPasswordInput = ""
                            } else {
                                Toast.makeText(context, "Security preferences saved!", Toast.LENGTH_SHORT).show()
                            }
                            showSecurityDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Save Settings", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSecurityDialog = false }) {
                    Text("Close", color = TextMuted)
                }
            },
            containerColor = Color.White,
            shape = RoundedCornerShape(20.dp)
        )
    }

    if (showNotifSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showNotifSettingsDialog = false },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Notifications, contentDescription = null, tint = PurplePrimary, modifier = Modifier.size(22.dp))
                    Text(
                        text = "Notification Settings",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextDark
                    )
                }
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    Text("Customize your alert and notification preferences:", fontSize = 12.sp, color = TextMuted)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Booking & Calendar Reminders", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextDark)
                            Text("Push alerts for new check-ins and bookings", fontSize = 11.sp, color = TextMuted)
                        }
                        Switch(
                            checked = isBookingNotifEnabled,
                            onCheckedChange = {
                                isBookingNotifEnabled = it
                                if (!isPreview) {
                                    notifPrefs.edit().putBoolean("notif_booking_$agentName", it).apply()
                                }
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = PurplePrimary)
                        )
                    }

                    HorizontalDivider(color = Color(0xFFF3E5F5))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Event Group Chat Alerts", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextDark)
                            Text("Alerts when new chat messages arrive", fontSize = 11.sp, color = TextMuted)
                        }
                        Switch(
                            checked = isChatNotifEnabled,
                            onCheckedChange = {
                                isChatNotifEnabled = it
                                if (!isPreview) {
                                    notifPrefs.edit().putBoolean("notif_chat_$agentName", it).apply()
                                }
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = PurplePrimary)
                        )
                    }

                    HorizontalDivider(color = Color(0xFFF3E5F5))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Owner Events & Announcements", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextDark)
                            Text("Notifications for new owner event invites", fontSize = 11.sp, color = TextMuted)
                        }
                        Switch(
                            checked = isEventNotifEnabled,
                            onCheckedChange = {
                                isEventNotifEnabled = it
                                if (!isPreview) {
                                    notifPrefs.edit().putBoolean("notif_event_$agentName", it).apply()
                                }
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = PurplePrimary)
                        )
                    }

                    HorizontalDivider(color = Color(0xFFF3E5F5))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Automated Email Confirmation", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextDark)
                            Text("Send Gmail confirmations to agent & owner", fontSize = 11.sp, color = TextMuted)
                        }
                        Switch(
                            checked = isEmailAlertsEnabled,
                            onCheckedChange = {
                                isEmailAlertsEnabled = it
                                if (!isPreview) {
                                    notifPrefs.edit().putBoolean("notif_email_$agentName", it).apply()
                                }
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = PurplePrimary)
                        )
                    }

                    HorizontalDivider(color = Color(0xFFF3E5F5))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Sound & Vibration", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextDark)
                            Text("Play ringtone and vibrate on urgent alerts", fontSize = 11.sp, color = TextMuted)
                        }
                        Switch(
                            checked = isSoundVibrationEnabled,
                            onCheckedChange = {
                                isSoundVibrationEnabled = it
                                if (!isPreview) {
                                    notifPrefs.edit().putBoolean("notif_sound_$agentName", it).apply()
                                }
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = PurplePrimary)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        Toast.makeText(context, "Notification preferences saved!", Toast.LENGTH_SHORT).show()
                        showNotifSettingsDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Save Preferences", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showNotifSettingsDialog = false }) {
                    Text("Close", color = TextMuted)
                }
            },
            containerColor = Color.White,
            shape = RoundedCornerShape(20.dp)
        )
    }

    if (showEmailSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showEmailSettingsDialog = false },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Email, contentDescription = null, tint = PurplePrimary, modifier = Modifier.size(22.dp))
                    Text(
                        text = "Email Settings",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextDark
                    )
                }
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    Text("Configure email delivery, registered Gmail address, and automated triggers:", fontSize = 12.sp, color = TextMuted)

                    OutlinedTextField(
                        value = senderEmailInput,
                        onValueChange = { senderEmailInput = it },
                        label = { Text("Registered Email Address") },
                        placeholder = { Text("agent@gmail.com") },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    HorizontalDivider(color = Color(0xFFF3E5F5))

                    Text("Automated Email Triggers", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextDark)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Booking Confirmation Email", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextDark)
                            Text("Send automated confirmation on new booking", fontSize = 11.sp, color = TextMuted)
                        }
                        Switch(
                            checked = isAutoBookingEmailEnabled,
                            onCheckedChange = {
                                isAutoBookingEmailEnabled = it
                                if (!isPreview) {
                                    emailPrefs.edit().putBoolean("auto_booking_$agentName", it).apply()
                                }
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = PurplePrimary)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Invoice Receipt Email", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextDark)
                            Text("Send receipt email when invoice is created", fontSize = 11.sp, color = TextMuted)
                        }
                        Switch(
                            checked = isAutoInvoiceEmailEnabled,
                            onCheckedChange = {
                                isAutoInvoiceEmailEnabled = it
                                if (!isPreview) {
                                    emailPrefs.edit().putBoolean("auto_invoice_$agentName", it).apply()
                                }
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = PurplePrimary)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Schedule Change Email", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextDark)
                            Text("Alert guests/agents when schedule changes", fontSize = 11.sp, color = TextMuted)
                        }
                        Switch(
                            checked = isAutoScheduleEmailEnabled,
                            onCheckedChange = {
                                isAutoScheduleEmailEnabled = it
                                if (!isPreview) {
                                    emailPrefs.edit().putBoolean("auto_schedule_$agentName", it).apply()
                                }
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = PurplePrimary)
                        )
                    }

                    HorizontalDivider(color = Color(0xFFF3E5F5))

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFFF8F5FE),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.MarkEmailRead, contentDescription = null, tint = Color(0xFF00A86B), modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("SMTP Protocol Status: Active", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextDark)
                                Text("TLS/SSL Encryption (Port 587) • Connected", fontSize = 10.sp, color = TextMuted)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (!isPreview) {
                            emailPrefs.edit().putString("sender_email_$agentName", senderEmailInput).apply()
                        }
                        Toast.makeText(context, "Email settings updated successfully!", Toast.LENGTH_SHORT).show()
                        showEmailSettingsDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Save Email Settings", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEmailSettingsDialog = false }) {
                    Text("Close", color = TextMuted)
                }
            },
            containerColor = Color.White,
            shape = RoundedCornerShape(20.dp)
        )
    }

    if (showAppearanceDialog) {
        val themeColors = listOf(
            Triple("Royal Purple", Color(0xFF6A1B9A), "0xFF6A1B9A"),
            Triple("Ocean Blue", Color(0xFF1976D2), "0xFF1976D2"),
            Triple("Rose Pink", Color(0xFFC2185B), "0xFFC2185B"),
            Triple("Emerald Green", Color(0xFF00897B), "0xFF00897B"),
            Triple("Sunset Amber", Color(0xFFE65100), "0xFFE65100"),
            Triple("Midnight Dark", Color(0xFF212121), "0xFF212121")
        )

        AlertDialog(
            onDismissRequest = { showAppearanceDialog = false },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Palette, contentDescription = null, tint = PurplePrimary, modifier = Modifier.size(22.dp))
                    Text(
                        text = "Dashboard Appearance",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextDark
                    )
                }
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Select your preferred theme color for the Owner Dashboard:", fontSize = 12.sp, color = TextMuted)

                    themeColors.forEach { (name, col, hex) ->
                        val isSelected = selectedColorHex == hex
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) Color(0xFFF3EDFD) else Color(0xFFF8F5FE),
                            border = if (isSelected) BorderStroke(1.5.dp, PurplePrimary) else null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    selectedColorHex = hex
                                    if (!isPreview) {
                                        appearancePrefs.edit().putString("theme_color_$agentName", hex).apply()
                                    }
                                    AzureCalendarColors.updateThemeColor(hex)
                                    Toast.makeText(context, "Dashboard theme updated to $name!", Toast.LENGTH_SHORT).show()
                                    showAppearanceDialog = false
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clip(CircleShape)
                                            .background(col)
                                    )
                                    Text(text = name, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextDark)
                                }

                                if (isSelected) {
                                    Text("✓ Active", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = PurplePrimary)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showAppearanceDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Done", fontWeight = FontWeight.Bold)
                }
            },
            containerColor = Color.White,
            shape = RoundedCornerShape(20.dp)
        )
    }
}

@Composable
private fun InfoDisplayRow(icon: ImageVector, label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = PurplePrimary,
            modifier = Modifier.size(20.dp)
        )
        Column {
            Text(text = label, fontSize = 11.sp, color = TextMuted)
            Text(text = value.ifBlank { "Not specified" }, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextDark)
        }
    }
}

@Composable
private fun UnitsTabContent(
    agentName: String = "Alex Reyes",
    onTabSelected: (Int) -> Unit
) {
    val context = LocalContext.current
    val dbHelper = remember { AzureDatabaseHelper(context) }
    var currentSubScreen by remember { mutableStateOf("directory") }
    var selectedUnitName by remember { mutableStateOf("Unit 12A") }
    val realTimeCal = remember { java.util.Calendar.getInstance() }
    val monthNamesList = listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")
    val currentRealMonth = monthNamesList.getOrElse(realTimeCal.get(java.util.Calendar.MONTH)) { "October" }
    val currentRealYear = realTimeCal.get(java.util.Calendar.YEAR)
    val currentRealDay = realTimeCal.get(java.util.Calendar.DAY_OF_MONTH)

    var selectedDayNum by remember { mutableIntStateOf(currentRealDay) }
    var activeMonthName by remember { mutableStateOf(currentRealMonth) }
    var activeYearNumber by remember { mutableIntStateOf(currentRealYear) }
    var refreshTrigger by remember { mutableIntStateOf(0) }
    var selectedImageUri by remember { mutableStateOf("") }
    var selectedStatus by remember { mutableStateOf("Available") }

    val shortM = activeMonthName.take(3)
    val defaultRangeStr = "$shortM $selectedDayNum - $shortM ${(selectedDayNum + 2).coerceAtMost(30)}, $activeYearNumber"

    when (currentSubScreen) {
        "directory" -> UnitsDirectoryScreen(
            initialDayNumber = selectedDayNum,
            isOwnerMode = false,
            onDateChange = { selectedDayNum = it },
            onViewScheduleClick = { unitNumber, _, _, status, imageUri ->
                selectedUnitName = unitNumber
                selectedStatus = status
                selectedImageUri = imageUri
                currentSubScreen = "details"
            }
        )
        "details" -> UnitDetailsScreen(
            unitName = selectedUnitName,
            unitStatus = selectedStatus,
            imageUri = selectedImageUri,
            initialCheckInDay = selectedDayNum,
            onBackClick = { currentSubScreen = "directory" },
            onEditUnitClick = { currentSubScreen = "choose_category" },
            onBookNowClick = { currentSubScreen = "choose_category" },
            onTabSelected = onTabSelected
        )
        "choose_category" -> ChooseCategoryScreen(
            selectedDateText = defaultRangeStr,
            initialUnitName = selectedUnitName,
            onBackClick = { currentSubScreen = "details" },
            onSaveClick = { shape, color, unitName, dateText ->
                val finalTargetDate = dateText.ifBlank { defaultRangeStr }
                val colorLong = color.value.toLong()
                dbHelper.deleteEventByDateAndUnit(finalTargetDate, unitName)
                dbHelper.deleteEventsForUnit(unitName)
                dbHelper.insertEvent(
                    title = "Staycation - $shape",
                    date = finalTargetDate,
                    time = "2:00 PM",
                    agent = agentName,
                    unit = unitName,
                    shape = shape,
                    color = colorLong
                )
                dbHelper.updateUnitStatus(unitName, shape)
                val eventMap = mapOf(
                    "id" to System.currentTimeMillis().toString(),
                    "title" to "Staycation - $shape",
                    "date" to finalTargetDate,
                    "time" to "2:00 PM",
                    "agent" to agentName,
                    "unit" to unitName,
                    "shape" to shape,
                    "color" to colorLong.toString()
                )
                FirebaseCloudSyncManager.pushEventToCloud(eventMap)
                FirebaseCloudSyncManager.pushUnitStatusToCloud(unitName, shape)
                refreshTrigger++
                Toast.makeText(context, "Tag $shape applied to $unitName for $finalTargetDate!", Toast.LENGTH_SHORT).show()
                currentSubScreen = "directory"

                val (_, calculatedPriceStr) = EmailTemplateHelper.calculateBookingPrice(finalTargetDate, 2500.0)
                EmailTemplateHelper.sendBookingEmail(
                    context = context,
                    ownerEmail = "owner.azure@gmail.com",
                    agentEmail = "${agentName.lowercase().replace(" ", ".")}@gmail.com",
                    agentName = agentName,
                    unitName = unitName,
                    statusLabel = shape,
                    dateText = finalTargetDate,
                    amountText = calculatedPriceStr
                )
            }
        )
        "event" -> BookingDetailsScreen(
            unitName = selectedUnitName,
            isOwnerMode = false,
            onBackClick = { currentSubScreen = "choose_category" },
            onViewUnitDetailsClick = { currentSubScreen = "details" },
            onEditTagClick = { currentSubScreen = "choose_category" },
            onDeleteBookingClick = {
                dbHelper.deleteEventByDateAndUnit(defaultRangeStr, selectedUnitName)
                FirebaseCloudSyncManager.deleteEventFromCloud(defaultRangeStr, selectedUnitName)
                FirebaseCloudSyncManager.pushUnitStatusToCloud(selectedUnitName, "Available")
                refreshTrigger++
                Toast.makeText(context, "Tag & booking deleted for $selectedUnitName!", Toast.LENGTH_SHORT).show()
                currentSubScreen = "directory"
            }
        )
    }
}

@Composable
private fun ProfileMenuItem(icon: ImageVector, title: String, onClick: () -> Unit = {}) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = PurplePrimary,
                modifier = Modifier.size(22.dp)
            )
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = TextDark
            )
        }
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = TextMuted,
            modifier = Modifier.size(20.dp)
        )
    }
}