package com.example.azurecalendar

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import coil.compose.AsyncImage
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OwnerDashboardScreen(
    @Suppress("UNUSED_PARAMETER") onLogoutClick: () -> Unit = {},
    @Suppress("UNUSED_PARAMETER") onNavigateToaddUnit: () -> Unit = {},
    @Suppress("UNUSED_PARAMETER") onNavigateToInvoices: () -> Unit = {},
    @Suppress("UNUSED_PARAMETER") onNavigateToAgents: () -> Unit = {}
) {
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Home, 1: Calendar, 2: Units, 3: Records, 4: Profile
    var currentSubScreen by remember { mutableStateOf("home") } // "home", "add_unit", "invoices", "agents"

    // Back closes a sub-screen first, then returns to the Home tab, before leaving the dashboard
    BackHandler(enabled = currentSubScreen != "home" || selectedTab != 0) {
        if (currentSubScreen != "home") currentSubScreen = "home" else selectedTab = 0
    }

    val context = LocalContext.current
    val isPreview = androidx.compose.ui.platform.LocalInspectionMode.current
    val appearancePrefs = remember { context.getSharedPreferences("azure_calendar_appearance_prefs", Context.MODE_PRIVATE) }
    val currentThemeHex = remember(selectedTab, currentSubScreen) {
        if (isPreview) "0xFF6A1B9A" else appearancePrefs.getString("theme_color_Admin", "0xFF6A1B9A") ?: "0xFF6A1B9A"
    }
    LaunchedEffect(currentThemeHex) {
        AzureCalendarColors.updateThemeColor(currentThemeHex)
    }

    Scaffold(
        bottomBar = {
            CalendarBottomNavigationBar(
                selectedTab = selectedTab,
                onTabSelected = {
                    selectedTab = it
                    currentSubScreen = "home"
                }
            )
        },
        containerColor = ScreenBg
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Sub-screens (Add Unit / Invoices / Agents) are shown on top of whatever tab is active
            when (currentSubScreen) {
                "add_unit" -> AddEditUnitScreen(
                    onBackClick = { currentSubScreen = "home" },
                    onSaveSuccess = { currentSubScreen = "home" }
                )
                "invoices" -> InvoiceBillingScreen(
                    onBackClick = { currentSubScreen = "home" }
                )
                "agents" -> AgentsListScreen(
                    onBackClick = { currentSubScreen = "home" }
                )
                else -> when (selectedTab) {
                    1 -> OwnerCalendarTabContent(onNavigateToUnits = { selectedTab = 2 })
                    2 -> OwnerUnitsTabContent(onTabSelected = { selectedTab = it })
                    3 -> NotificationsCenterScreen(isOwnerMode = true, agentName = "Admin Owner")
                    4 -> ProfileTabContent(agentName = "Admin", isOwnerMode = true, onLogoutClick = onLogoutClick)
                    else -> OwnerDashboardHomeContent(
                        onNavigateToaddUnit = { currentSubScreen = "add_unit" },
                        onNavigateToInvoices = { currentSubScreen = "invoices" },
                        onNavigateToAgents = { currentSubScreen = "agents" },
                        onNavigateToUnits = { selectedTab = 2 },
                        onNavigateToNotifications = { selectedTab = 3 }
                    )
                }
            }
        }
    }
}

@Composable
private fun OwnerCalendarTabContent(
    @Suppress("UNUSED_PARAMETER") onNavigateToUnits: () -> Unit = {}
) {
    val context = LocalContext.current
    val dbHelper = remember { AzureDatabaseHelper(context) }
    val deletedPrefs = remember { context.getSharedPreferences("azure_calendar_deleted_units", Context.MODE_PRIVATE) }

    val realTimeCal = remember { java.util.Calendar.getInstance() }
    val monthNamesList = listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")
    val currentRealMonth = monthNamesList.getOrElse(realTimeCal.get(java.util.Calendar.MONTH)) { "October" }
    val currentRealYear = realTimeCal.get(java.util.Calendar.YEAR)
    val currentRealDay = realTimeCal.get(java.util.Calendar.DAY_OF_MONTH)

    var selectedDayNumber by remember { mutableIntStateOf(currentRealDay) }
    var selectedCheckInDay by remember { mutableIntStateOf(currentRealDay) }
    var selectedCheckOutDay by remember { mutableIntStateOf((currentRealDay + 2).coerceAtMost(30)) }
    var activeMonthName by remember { mutableStateOf(currentRealMonth) }
    var activeYearNumber by remember { mutableIntStateOf(currentRealYear) }
    var currentSubScreen by remember { mutableStateOf("calendar") } // "calendar", "units_view", "choose_category", "booking_details"
    var selectedUnitName by remember { mutableStateOf("Unit 12A") }
    var refreshTrigger by remember { mutableIntStateOf(0) }

    val currentMonthText = remember(activeMonthName, activeYearNumber) {
        "$activeMonthName $activeYearNumber"
    }

    val dbEventsState = remember { mutableStateOf<List<Map<String, String>>>(emptyList()) }
    val dbUnitsState = remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }

    LaunchedEffect(currentSubScreen, refreshTrigger, activeMonthName, activeYearNumber, FirebaseCloudSyncManager.globalCloudSyncTrigger.intValue) {
        dbEventsState.value = dbHelper.getAllEvents()
        dbUnitsState.value = dbHelper.getAllUnits()
    }

    // Load events from SQLite database in real-time
    val calendarDays = remember(activeMonthName, activeYearNumber, currentSubScreen, refreshTrigger, dbEventsState.value) {
        createSampleCalendarMonth("Admin").map { day ->
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
                            description = evt["shape"] ?: "",
                            assignedAgent = evt["agent"] ?: "Admin",
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

    val selectedDayModel = calendarDays.find { it.dayNumber == selectedDayNumber }

    when (currentSubScreen) {
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
                        agent = "Admin",
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
                        "agent" to "Admin",
                        "unit" to unitName,
                        "shape" to shape,
                        "color" to colorLong.toString()
                    )
                    FirebaseCloudSyncManager.pushEventToCloud(eventMap)
                    FirebaseCloudSyncManager.pushUnitStatusToCloud(unitName, shape)
                    refreshTrigger++
                    currentSubScreen = "calendar"
                    Toast.makeText(context, "Tag $shape applied to $unitName for $finalTargetDate!", Toast.LENGTH_SHORT).show()

                    val (_, calculatedPriceStr) = EmailTemplateHelper.calculateBookingPrice(finalTargetDate, 2500.0)
                    EmailTemplateHelper.sendBookingEmail(
                        context = context,
                        ownerEmail = "owner.azure@gmail.com",
                        agentEmail = "admin@azure.com",
                        agentName = "Admin Owner",
                        unitName = unitName,
                        statusLabel = shape,
                        dateText = finalTargetDate,
                        amountText = calculatedPriceStr
                    )
                }
            )
        }
        "booking_details" -> {
            val currentEvt = selectedDayModel?.events?.firstOrNull()
            val unitForDisplay = currentEvt?.unitNumber ?: selectedUnitName
            BookingDetailsScreen(
                unitName = unitForDisplay,
                isOwnerMode = true,
                onBackClick = { currentSubScreen = "calendar" },
                onViewUnitDetailsClick = { currentSubScreen = "units_view" },
                onEditTagClick = { currentSubScreen = "choose_category" },
                onDeleteBookingClick = {
                    val evtId = currentEvt?.id?.toLongOrNull()
                    if (evtId != null) {
                        dbHelper.deleteEvent(evtId)
                    }
                    val targetDate = "Sept $selectedDayNumber, 2026"
                    dbHelper.deleteEventByDateAndUnit(targetDate, unitForDisplay)
                    FirebaseCloudSyncManager.deleteEventFromCloud(targetDate, unitForDisplay)
                    FirebaseCloudSyncManager.pushUnitStatusToCloud(unitForDisplay, "Available")
                    refreshTrigger++
                    currentSubScreen = "calendar"
                    Toast.makeText(context, "Tag & booking deleted for Sept $selectedDayNumber!", Toast.LENGTH_SHORT).show()
                }
            )
        }
        else -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
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

                // Units Status List Card for Specific Selected Date
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

                        val deletedSet = deletedPrefs.getStringSet("deleted_unit_numbers", emptySet()).orEmpty()
                        val dbUnits = dbUnitsState.value.mapNotNull { it["unitNumber"]?.toString() }
                        val defaultUnits = listOf("Unit 12A", "Unit 15B", "Unit 08C", "Unit 06D")
                        val unitList = (dbUnits + defaultUnits)
                            .distinctBy { it.replace("Unit", "", ignoreCase = true).trim() }
                            .filter { uNum -> deletedSet.none { del -> isSameUnit(del, uNum) } }
                        val eventsForSelectedDay = selectedDayModel?.events.orEmpty()

                        unitList.forEach { uName ->
                            val cleanU = uName.replace("Unit", "", ignoreCase = true).trim()
                            val matchingEvt = eventsForSelectedDay.find {
                                val evtUnit = it.unitNumber.replace("Unit", "", ignoreCase = true).trim()
                                evtUnit.equals(cleanU, ignoreCase = true) || it.unitNumber.contains(uName, ignoreCase = true)
                            }
                            val rawStatus = matchingEvt?.description?.replace("Category: ", "")?.ifBlank { "Available" } ?: "Available"
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
private fun OwnerDashboardHomeContent(
    onNavigateToaddUnit: () -> Unit,
    onNavigateToInvoices: () -> Unit,
    onNavigateToAgents: () -> Unit,
    @Suppress("UNUSED_PARAMETER") onNavigateToUnits: () -> Unit = {},
    @Suppress("UNUSED_PARAMETER") onNavigateToNotifications: () -> Unit = {}
) {
    val context = LocalContext.current
    val isPreview = androidx.compose.ui.platform.LocalInspectionMode.current
    val prefs = remember { context.getSharedPreferences("azure_calendar_profile_prefs", Context.MODE_PRIVATE) }
    val infoPrefs = remember { context.getSharedPreferences("azure_calendar_personal_info", Context.MODE_PRIVATE) }
    val dbHelper = remember { AzureDatabaseHelper(context) }
    val deletedPrefs = remember { context.getSharedPreferences("azure_calendar_deleted_units", Context.MODE_PRIVATE) }
    val dbEventsState = remember { mutableStateOf<List<Map<String, String>>>(emptyList()) }
    val dbUnitsState = remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }

    LaunchedEffect(Unit, FirebaseCloudSyncManager.globalCloudSyncTrigger.intValue) {
        dbEventsState.value = dbHelper.getAllEvents()
        dbUnitsState.value = dbHelper.getAllUnits()
    }

    val (totalUnitsVal, availVal, resVal, occVal, rentalIncomeVal) = remember(dbEventsState.value, dbUnitsState.value, isPreview) {
        if (isPreview) {
            listOf("20", "8", "7", "5", "₱45,000")
        } else {
            val deletedSet = deletedPrefs.getStringSet("deleted_unit_numbers", emptySet()).orEmpty()
            val dbUnits = dbUnitsState.value.filter { unitMap ->
                val num = unitMap["unitNumber"]?.toString() ?: ""
                deletedSet.none { isSameUnit(it, num) }
            }
            val dbEvents = dbEventsState.value

            val defaultUnits = listOf("Unit 12A", "Unit 15B", "Unit 08C", "Unit 06D")
                .filter { defNum -> deletedSet.none { isSameUnit(it, defNum) } }

            val customUnitNums = dbUnits.mapNotNull { it["unitNumber"]?.toString() }
            val allUnitNums = (customUnitNums + defaultUnits).distinctBy { it.replace("Unit", "", ignoreCase = true).trim() }

            val totalUnits = allUnitNums.size

            var occCount = 0
            var resCount = 0
            var availCount = 0

            allUnitNums.forEach { uNum ->
                val matchedUnitMap = dbUnits.find { (it["unitNumber"]?.toString() ?: "").replace("Unit", "", ignoreCase = true).trim() == uNum.replace("Unit", "", ignoreCase = true).trim() }
                val status = (matchedUnitMap?.get("status")?.toString() ?: "Available").lowercase()

                when {
                    status.contains("check") || status.contains("heart") || status.contains("occup") -> occCount++
                    status.contains("reserve") || status.contains("diamond") -> resCount++
                    else -> availCount++
                }
            }

            val dbTotalRate = dbUnits.sumOf { (it["rate"] as? Double) ?: 2500.0 }
            val baseRateSum = (defaultUnits.size * 2500.0) + dbTotalRate

            var totalCalculatedIncome = 0.0
            dbEvents.forEach { evt ->
                val dateStr = evt["date"] ?: ""
                val unitStr = evt["unit"] ?: ""
                val shape = (evt["shape"] ?: "").lowercase()

                val matchedUnitMap = dbUnits.find { (it["unitNumber"]?.toString() ?: "").contains(unitStr, ignoreCase = true) }
                val unitDailyRate = (matchedUnitMap?.get("rate") as? Double) ?: 2500.0

                val (calcAmount, _) = EmailTemplateHelper.calculateBookingPrice(dateStr, unitDailyRate)
                if (!shape.contains("available", ignoreCase = true)) {
                    totalCalculatedIncome += calcAmount
                }
            }

            val finalRevenue = if (totalCalculatedIncome > 0) totalCalculatedIncome else baseRateSum
            val formattedIncome = "₱" + String.format("%,.0f", finalRevenue)

            listOf(totalUnits.toString(), availCount.toString(), resCount.toString(), occCount.toString(), formattedIncome)
        }
    }

    val displayAdminName = remember(isPreview) {
        if (isPreview) "Admin"
        else infoPrefs.getString("name_Admin", "Admin") ?: "Admin"
    }

    var profilePhotoUri by remember {
        mutableStateOf(if (isPreview) "" else prefs.getString("profile_photo_Admin", "") ?: "")
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val savedPath = saveSampledProfilePhoto(context, uri, "profile_photo_Admin.jpg")
            if (savedPath != null) {
                profilePhotoUri = savedPath
                if (!isPreview) {
                    prefs.edit().putString("profile_photo_Admin", savedPath).apply()
                }
                Toast.makeText(context, "Profile picture updated from Gallery!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(12.dp))
            // Top Header Banner with Gradient
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = PurplePrimary,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Clickable Owner Avatar (Direct to Gallery)
                        Box(
                            contentAlignment = Alignment.BottomEnd,
                            modifier = Modifier
                                .size(50.dp)
                                .clip(CircleShape)
                                .clickable { galleryLauncher.launch("image/*") }
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = Color.White.copy(alpha = 0.25f),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    if (profilePhotoUri.isNotBlank()) {
                                        val fileObj = java.io.File(profilePhotoUri)
                                        val imageModel: Any = if (fileObj.exists()) fileObj else profilePhotoUri

                                        AsyncImage(
                                            model = imageModel,
                                            contentDescription = "Owner Profile Photo",
                                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                            onError = {
                                                profilePhotoUri = ""
                                                if (!isPreview) {
                                                    prefs.edit().remove("profile_photo_Admin").apply()
                                                }
                                            },
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        Text(
                                            text = "👑",
                                            fontSize = 22.sp
                                        )
                                    }
                                }
                            }

                            // Camera Badge
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
                                in 5..11 -> "Good morning"
                                in 12..16 -> "Good afternoon"
                                else -> "Good evening"
                            }
                        }

                        Column {
                            Text(
                                text = "$realTimeGreeting, $displayAdminName 👑",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Admin Calendar",
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.85f)
                            )
                        }
                    }
                }
            }
        }

        // 2x2 Metric Cards Grid
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    MetricCard(
                        title = "Total Units",
                        value = totalUnitsVal,
                        icon = Icons.Default.Home,
                        modifier = Modifier.weight(1f)
                    )
                    MetricCard(
                        title = "Available",
                        value = availVal,
                        icon = Icons.Default.CheckCircle,
                        iconTint = Color(0xFF00A86B),
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    MetricCard(
                        title = "Reserved",
                        value = resVal,
                        icon = Icons.Default.Bookmark,
                        iconTint = Color(0xFFF39C12),
                        modifier = Modifier.weight(1f)
                    )
                    MetricCard(
                        title = "Occupied",
                        value = occVal,
                        icon = Icons.Default.Person,
                        iconTint = Color(0xFFE74C3C),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Rental Overview Section
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = CardBg),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderPurple),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Rental Overview",
                            fontSize = 13.sp,
                            color = TextMuted,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = rentalIncomeVal,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextDark
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "This Month",
                            fontSize = 12.sp,
                            color = TextMuted
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFFE8F5E9),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.BarChart,
                                    contentDescription = null,
                                    tint = Color(0xFF00A86B),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "▲ 12%",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF00A86B)
                        )
                    }
                }
            }
        }

        // Quick Actions Section
        item {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Quick Actions",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = TextDark
            )
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                QuickActionCard(
                    title = "Add Unit",
                    icon = Icons.Default.Add,
                    onClick = onNavigateToaddUnit,
                    modifier = Modifier.weight(1f)
                )
                QuickActionCard(
                    title = "Invoices",
                    icon = Icons.Default.ReceiptLong,
                    onClick = onNavigateToInvoices,
                    modifier = Modifier.weight(1f)
                )
                QuickActionCard(
                    title = "Agents",
                    icon = Icons.Default.Group,
                    onClick = onNavigateToAgents,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun MetricCard(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color = PurplePrimary,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderPurple),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(iconTint.copy(alpha = 0.12f))
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = iconTint,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Text(
                    text = title,
                    fontSize = 12.sp,
                    color = TextMuted,
                    fontWeight = FontWeight.Medium
                )
            }
            Text(
                text = value,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = TextDark
            )
        }
    }
}

@Composable
private fun QuickActionCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderPurple),
        modifier = modifier.clickable { onClick() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFF3EDFD))
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = PurplePrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = TextDark
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun OwnerDashboardScreenPreview() {
    AzureCalendarTheme {
        OwnerDashboardScreen()
    }
}

@Composable
private fun OwnerUnitsTabContent(onTabSelected: (Int) -> Unit) {
    val context = LocalContext.current
    val dbHelper = remember { AzureDatabaseHelper(context) }
    var currentSubScreen by remember { mutableStateOf("directory") }
    var selectedUnitName by remember { mutableStateOf("Unit 12A") }
    var selectedTowerName by remember { mutableStateOf("Azure Tower") }
    var selectedBrType by remember { mutableStateOf("1 Bedroom") }
    var selectedStatus by remember { mutableStateOf("Available") }
    var selectedImageUri by remember { mutableStateOf("") }
    val realTimeCal = remember { java.util.Calendar.getInstance() }
    val monthNamesList = listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")
    val currentRealMonth = monthNamesList.getOrElse(realTimeCal.get(java.util.Calendar.MONTH)) { "October" }
    val currentRealYear = realTimeCal.get(java.util.Calendar.YEAR)
    val currentRealDay = realTimeCal.get(java.util.Calendar.DAY_OF_MONTH)

    var selectedDayNum by remember { mutableIntStateOf(currentRealDay) }
    var activeMonthName by remember { mutableStateOf(currentRealMonth) }
    var activeYearNumber by remember { mutableIntStateOf(currentRealYear) }
    var refreshTrigger by remember { mutableIntStateOf(0) }

    val shortM = activeMonthName.take(3)
    val defaultRangeStr = "$shortM $selectedDayNum - $shortM ${(selectedDayNum + 2).coerceAtMost(30)}, $activeYearNumber"

    when (currentSubScreen) {
        "directory" -> UnitsDirectoryScreen(
            initialDayNumber = selectedDayNum,
            onDateChange = { selectedDayNum = it },
            onViewScheduleClick = { unitNumber, tower, description, status, imageUri ->
                selectedUnitName = unitNumber
                selectedTowerName = tower
                selectedBrType = description
                selectedStatus = status
                selectedImageUri = imageUri
                currentSubScreen = "details"
            },
            onEditUnitClick = { unitNumber, tower, description, status, imageUri ->
                selectedUnitName = unitNumber
                selectedTowerName = tower
                selectedBrType = description
                selectedStatus = status
                selectedImageUri = imageUri
                currentSubScreen = "edit_unit"
            },
            isOwnerMode = true
        )
        "edit_unit" -> AddEditUnitScreen(
            unitId = selectedUnitName,
            initialUnitNumber = selectedUnitName,
            initialTowerName = selectedTowerName,
            initialBrType = selectedBrType,
            initialStatus = selectedStatus,
            initialImageUri = selectedImageUri,
            onBackClick = { currentSubScreen = "directory" },
            onSaveSuccess = { currentSubScreen = "directory" }
        )
        "details" -> UnitDetailsScreen(
            unitName = selectedUnitName,
            towerName = selectedTowerName,
            unitStatus = selectedStatus,
            imageUri = selectedImageUri,
            initialCheckInDay = selectedDayNum,
            onBackClick = { currentSubScreen = "directory" },
            onEditUnitClick = { currentSubScreen = "edit_unit" },
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
                    agent = "Admin",
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
                    "agent" to "Admin",
                    "unit" to unitName,
                    "shape" to shape,
                    "color" to colorLong.toString()
                )
                FirebaseCloudSyncManager.pushEventToCloud(eventMap)
                FirebaseCloudSyncManager.pushUnitStatusToCloud(unitName, shape)
                refreshTrigger++
                currentSubScreen = "directory"
                Toast.makeText(context, "Tag $shape applied to $unitName for $finalTargetDate!", Toast.LENGTH_SHORT).show()

                val (_, calculatedPriceStr) = EmailTemplateHelper.calculateBookingPrice(finalTargetDate, 2500.0)
                EmailTemplateHelper.sendBookingEmail(
                    context = context,
                    ownerEmail = "owner.azure@gmail.com",
                    agentEmail = "admin@azure.com",
                    agentName = "Admin Owner",
                    unitName = unitName,
                    statusLabel = shape,
                    dateText = finalTargetDate,
                    amountText = calculatedPriceStr
                )
            }
        )
        "event" -> BookingDetailsScreen(
            unitName = selectedUnitName,
            isOwnerMode = true,
            onBackClick = { currentSubScreen = "choose_category" },
            onViewUnitDetailsClick = { currentSubScreen = "details" },
            onEditTagClick = { currentSubScreen = "choose_category" },
            onDeleteBookingClick = {
                dbHelper.deleteEventByDateAndUnit(defaultRangeStr, selectedUnitName)
                Toast.makeText(context, "Tag & booking deleted for $selectedUnitName!", Toast.LENGTH_SHORT).show()
                currentSubScreen = "directory"
            }
        )
    }
}