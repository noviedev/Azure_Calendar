package com.example.azurecalendar

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.example.azurecalendar.ui.theme.AzureCalendarTheme

private val PurplePrimary get() = AzureCalendarColors.PurplePrimary
private val ScreenBg get() = AzureCalendarColors.ScreenBg
private val CardBg get() = AzureCalendarColors.CardBg
private val TextDark get() = AzureCalendarColors.TextDark
private val TextMuted get() = AzureCalendarColors.TextMuted
private val BorderPurple get() = AzureCalendarColors.BorderPurple

data class OwnerEventItem(
    val id: String,
    val title: String,
    val description: String,
    val date: String,
    val time: String,
    val createdBy: String = "Owner",
    val targetAgent: String = "All Agents",
    val eventLink: String = "https://azurecalendar.app/event/join?id=$id",
    var isJoined: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

data class GroupChatMessage(
    val id: String,
    val senderName: String,
    val senderRole: String, // "Owner" or "Agent"
    val messageText: String,
    val timestamp: String,
    val isSelf: Boolean,
    val createdTime: Long = System.currentTimeMillis()
)

object EventChatRepository {
    private val chatStore = mutableMapOf<String, androidx.compose.runtime.snapshots.SnapshotStateList<GroupChatMessage>>()

    fun getMessagesForEvent(eventId: String, eventTitle: String, targetAgent: String): androidx.compose.runtime.snapshots.SnapshotStateList<GroupChatMessage> {
        return chatStore.getOrPut(eventId) {
            mutableStateListOf(
                GroupChatMessage(
                    id = "m1",
                    senderName = "Admin (Owner)",
                    senderRole = "Owner",
                    messageText = "Welcome to the group chat for '$eventTitle'! All joined agents and admin can communicate here.",
                    timestamp = "10:00 AM",
                    isSelf = false,
                    createdTime = 1L
                )
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsCenterScreen(
    isOwnerMode: Boolean = false,
    agentName: String = "Alex Reyes",
    resetTrigger: Int = 0,
    @Suppress("UNUSED_PARAMETER") onBackClick: () -> Unit = {},
    @Suppress("UNUSED_PARAMETER") onNavigateToInvoice: (invoiceId: String) -> Unit = {},
    @Suppress("UNUSED_PARAMETER") onNavigateToTemplates: () -> Unit = {}
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val dbHelper = remember { AzureDatabaseHelper(context) }
    val isPreview = LocalInspectionMode.current

    var activeGroupChatEventId by rememberSaveable { mutableStateOf<String?>(null) }

    var showCreateDialog by remember { mutableStateOf(false) }
    var newDateState by remember { mutableStateOf("") }
    var linkInput by remember { mutableStateOf("") }
    val joinedPrefs = remember { context.getSharedPreferences("azure_calendar_joined_events", Context.MODE_PRIVATE) }
    val joinedEventIds = remember {
        mutableStateListOf<String>().apply {
            if (!isPreview) {
                addAll(joinedPrefs.getStringSet("joined_ids", emptySet()).orEmpty())
            }
        }
    }
    val prefs = remember { context.getSharedPreferences("azure_calendar_deleted_events", Context.MODE_PRIVATE) }
    val deletedEventIds = remember {
        mutableStateListOf<String>().apply {
            if (!isPreview) {
                addAll(prefs.getStringSet("deleted_ids", emptySet()).orEmpty())
            }
        }
    }

    var cloudOwnerEvents by remember { mutableStateOf<List<OwnerEventItem>>(emptyList()) }

    DisposableEffect(isPreview) {
        if (isPreview) {
            onDispose { }
        } else {
            val registration = FirebaseCloudSyncManager.listenToOwnerEvents(context) { updatedEvents ->
                cloudOwnerEvents = updatedEvents
            }
            onDispose {
                registration.remove()
            }
        }
    }

    // Load owner events from SQLite database (strictly filtering out Staycation unit bookings)
    val dbOwnerEvents = remember(showCreateDialog, isPreview, FirebaseCloudSyncManager.globalCloudSyncTrigger.intValue) {
        if (isPreview) emptyList() else {
            dbHelper.getAllEvents().filter { evt ->
                val title = evt["title"] ?: ""
                val unit = evt["unit"] ?: ""
                val shape = evt["shape"] ?: ""
                val isStaycation = title.startsWith("Staycation", ignoreCase = true) || shape.contains("Check", ignoreCase = true) || shape.contains("Reserve", ignoreCase = true)
                !isStaycation && (unit.contains("Event", ignoreCase = true) || shape.contains("Owner Event", ignoreCase = true))
            }.map { evt ->
                val evtId = evt["id"] ?: "0"
                val idNum = evtId.toLongOrNull() ?: 0L
                val targetAg = evt["agent"]?.ifBlank { "All Agents" } ?: "All Agents"
                OwnerEventItem(
                    id = evtId,
                    title = evt["title"] ?: "Owner Event",
                    description = "Created by Owner/Admin for $targetAg",
                    date = evt["date"] ?: "Sept 15, 2026",
                    time = evt["time"] ?: "2:00 PM",
                    createdBy = "Owner",
                    targetAgent = targetAg,
                    eventLink = "https://azurecalendar.app/event/join?id=$evtId",
                    createdAt = if (idNum > 1000000000L) idNum else System.currentTimeMillis()
                )
            }
        }
    }

    val combinedEvents = remember(dbOwnerEvents, cloudOwnerEvents, deletedEventIds.toList(), FirebaseCloudSyncManager.globalCloudSyncTrigger.intValue) {
        val merged = if (cloudOwnerEvents.isNotEmpty()) {
            cloudOwnerEvents.filter { !it.title.startsWith("Staycation", ignoreCase = true) }
        } else if (dbOwnerEvents.isNotEmpty()) {
            dbOwnerEvents.filter {
                it.id !in deletedEventIds &&
                it.title !in deletedEventIds &&
                !it.title.startsWith("Staycation", ignoreCase = true)
            }
        } else {
            emptyList()
        }
        merged.distinctBy { "${it.title.lowercase().trim()}_${it.date}_${it.time}" }
    }

    val visibleEvents = remember(combinedEvents, agentName, isOwnerMode, FirebaseCloudSyncManager.globalCloudSyncTrigger.intValue) {
        val sessionManager = UserSessionManager(context)
        val sessionName = sessionManager.getUsername().trim().lowercase()
        val cleanParamName = agentName.trim().lowercase()

        val infoPrefs = context.getSharedPreferences("azure_calendar_personal_info", Context.MODE_PRIVATE)
        val storedEmail = infoPrefs.getString("email_$agentName", "") ?: ""
        val emailPrefix = storedEmail.substringBefore("@").trim().lowercase()

        val filtered = if (isOwnerMode) {
            combinedEvents
        } else {
            combinedEvents.filter { event ->
                val target = event.targetAgent.trim()
                val targetClean = target.lowercase()

                // If target is blank or "All Agents", all agents join automatically.
                if (target.isBlank() || targetClean.contains("all agents") || targetClean == "all") {
                    true
                } else {
                    val userIdentifiers = listOf(
                        cleanParamName,
                        sessionName,
                        emailPrefix
                    ).filter { it.isNotBlank() }

                    val targetItems = targetClean.split(",", ";", "and").map { it.trim() }.filter { it.isNotBlank() }

                    val exactMatch = targetItems.any { item ->
                        userIdentifiers.any { user ->
                            user == item || user.contains(item) || item.contains(user)
                        }
                    }

                    val tokenMatch = userIdentifiers.any { user ->
                        val tokens = user.split(" ", ".", "_").filter { it.length >= 3 }
                        tokens.any { t -> targetClean.contains(t) }
                    }

                    exactMatch || tokenMatch || UserSessionManager.isSameCreator(target, agentName)
                }
            }
        }

        filtered.sortedByDescending { event ->
            val chatRoomId = FirebaseCloudSyncManager.getNormalizedEventId(event.id, event.title)
            val dbMsgs = dbHelper.getChatMessagesForRoom(chatRoomId)
            val memMsgs = EventChatRepository.getMessagesForEvent(chatRoomId, event.title, event.targetAgent)
            val lastMsgTime = maxOf(dbMsgs.lastOrNull()?.createdTime ?: 0L, memMsgs.lastOrNull()?.id?.toLongOrNull() ?: 0L)
            val idNum = event.id.replace(Regex("[^0-9]"), "").toLongOrNull() ?: 0L
            val createdStamp = if (event.createdAt > 0L) event.createdAt else idNum
            maxOf(lastMsgTime, createdStamp)
        }
    }

    LaunchedEffect(resetTrigger) {
        if (resetTrigger > 0) {
            if (visibleEvents.isNotEmpty()) {
                activeGroupChatEventId = visibleEvents.first().id
            }
        } else if (activeGroupChatEventId == null && visibleEvents.isNotEmpty()) {
            activeGroupChatEventId = visibleEvents.first().id
        }
    }

    val activeGroupChatEvent = remember(activeGroupChatEventId, combinedEvents) {
        if (activeGroupChatEventId == null || activeGroupChatEventId == "SHOW_LIST") null
        else combinedEvents.find { it.id == activeGroupChatEventId || it.title.equals(activeGroupChatEventId, ignoreCase = true) }
    }

    if (activeGroupChatEvent != null) {
        EventGroupChatView(
            event = activeGroupChatEvent!!,
            currentUserName = if (isOwnerMode) "Admin Owner" else agentName,
            isOwnerMode = isOwnerMode,
            onBackClick = { activeGroupChatEventId = "SHOW_LIST" }
        )
    } else {
        LazyColumn(
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
                    Column {
                        Text(
                            text = "Event",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextDark
                        )
                        Text(
                            text = if (isOwnerMode) "Create and Manage Owner Events" else "Owner Event Notifications & Group Chats",
                            fontSize = 12.sp,
                            color = TextMuted
                        )
                    }

                    if (isOwnerMode) {
                        Button(
                            onClick = { showCreateDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = "Create Event", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
            }

            // In Agent Mode: Push Notification Invites Section
            if (!isOwnerMode && visibleEvents.isEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = CardBg),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(24.dp)
                                .fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Default.EventAvailable, contentDescription = null, tint = PurplePrimary, modifier = Modifier.size(48.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("No Owner Events Assigned", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextDark)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Owner events created for you or All Agents will automatically appear here.",
                                fontSize = 12.sp,
                                color = TextMuted,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }

            items(visibleEvents) { event ->
                val joinedState = event.isJoined || event.id in joinedEventIds
                val chatRoomId = remember(event.id, event.title) {
                    FirebaseCloudSyncManager.getNormalizedEventId(event.id, event.title)
                }
                val totalRoomMessages = remember(chatRoomId, FirebaseCloudSyncManager.globalCloudSyncTrigger.intValue) {
                    val dbMsgs = dbHelper.getChatMessagesForRoom(chatRoomId)
                    val memMsgs = EventChatRepository.getMessagesForEvent(chatRoomId, event.title, event.targetAgent)
                    val combined = dbMsgs + memMsgs
                    combined.distinctBy { "${it.senderName.trim().lowercase()}_${it.messageText.trim()}_${it.timestamp}" }.size
                }
                val chatPrefs = remember { context.getSharedPreferences("azure_calendar_chat_reads", Context.MODE_PRIVATE) }
                val userNameKey = remember(isOwnerMode, agentName) { if (isOwnerMode) "Admin Owner" else agentName }
                val currentReadCount = remember(chatRoomId, totalRoomMessages, FirebaseCloudSyncManager.globalCloudSyncTrigger.intValue) {
                    chatPrefs.getInt("read_count_${chatRoomId}_$userNameKey", 0)
                }
                val unreadChatMsgCount = (totalRoomMessages - currentReadCount).coerceAtLeast(0)

                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = CardBg),
                    elevation = CardDefaults.cardElevation(defaultElevation = if (isOwnerMode) 1.dp else 1.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val userName = if (isOwnerMode) "Admin Owner" else agentName
                            val cPrefs = context.getSharedPreferences("azure_calendar_chat_reads", Context.MODE_PRIVATE)
                            val sPrefs = context.getSharedPreferences("azure_calendar_event_reads", Context.MODE_PRIVATE)

                            cPrefs.edit()
                                .putInt("read_count_${chatRoomId}_$userName", totalRoomMessages)
                                .apply()
                            sPrefs.edit()
                                .putBoolean("seen_event_${event.id}_$userName", true)
                                .putBoolean("seen_event_${event.title}_$userName", true)
                                .apply()
                            FirebaseCloudSyncManager.notifyCloudUpdate()

                            activeGroupChatEventId = event.id
                        }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = Color(0xFFF3E5F5)
                            ) {
                                Text(
                                    text = "Created by Owner",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = PurplePrimary,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }

                            if (joinedState) {
                                Surface(
                                    shape = RoundedCornerShape(20.dp),
                                    color = Color(0xFFE8F5E9)
                                ) {
                                    Text(
                                        text = "Joined",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF2E7D32),
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }

                        Text(
                            text = event.title,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextDark
                        )

                        Text(
                            text = event.description,
                            fontSize = 12.sp,
                            color = TextMuted,
                            lineHeight = 16.sp
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.DateRange, contentDescription = null, tint = PurplePrimary, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(text = "${event.date} at ${event.time}", fontSize = 11.sp, color = TextDark, fontWeight = FontWeight.Medium)
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Person, contentDescription = null, tint = TextMuted, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(text = "Target: ${event.targetAgent}", fontSize = 11.sp, color = TextMuted)
                            }
                        }

                        // Open Group Chat Button
                        Button(
                            onClick = {
                                val userName = if (isOwnerMode) "Admin Owner" else agentName
                                val cPrefs = context.getSharedPreferences("azure_calendar_chat_reads", Context.MODE_PRIVATE)
                                val sPrefs = context.getSharedPreferences("azure_calendar_event_reads", Context.MODE_PRIVATE)

                                cPrefs.edit()
                                    .putInt("read_count_${chatRoomId}_$userName", totalRoomMessages)
                                    .apply()
                                sPrefs.edit()
                                    .putBoolean("seen_event_${event.id}_$userName", true)
                                    .putBoolean("seen_event_${event.title}_$userName", true)
                                    .apply()
                                FirebaseCloudSyncManager.notifyCloudUpdate()

                                activeGroupChatEventId = event.id
                            },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(38.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(Icons.Default.ChatBubble, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Open Event Group Chat",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                if (unreadChatMsgCount > 0) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Surface(
                                        shape = CircleShape,
                                        color = Color.White,
                                        shadowElevation = 3.dp,
                                        border = BorderStroke(1.dp, Color(0xFFE53935)),
                                        modifier = Modifier.size(20.dp)
                                    ) {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier.fillMaxSize()
                                        ) {
                                            Text(
                                                text = unreadChatMsgCount.toString(),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = Color(0xFFE53935)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Delete Button for Owner
                        if (isOwnerMode) {
                            OutlinedButton(
                                onClick = {
                                    deletedEventIds.add(event.id)
                                    deletedEventIds.add(event.title)
                                    if (!isPreview) {
                                        prefs.edit().putStringSet("deleted_ids", deletedEventIds.toSet()).apply()
                                    }
                                    dbHelper.deleteEventByIdOrTitle(event.id)
                                    dbHelper.deleteEventByIdOrTitle(event.title)
                                    FirebaseCloudSyncManager.deleteOwnerEventFromCloud(event.id, event.title)
                                    Toast.makeText(context, "'${event.title}' deleted!", Toast.LENGTH_SHORT).show()
                                },
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, Color(0xFFE53935)),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFEBEE)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(38.dp)
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete Event", tint = Color(0xFFE53935), modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Delete Event",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFE53935)
                                )
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    var showDatePickerDialog by remember { mutableStateOf(false) }
    val realTimeCal = remember { java.util.Calendar.getInstance() }
    val monthsList = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sept", "Oct", "Nov", "Dec")
    var selectedMonth by remember { mutableStateOf(monthsList.getOrElse(realTimeCal.get(java.util.Calendar.MONTH)) { "Oct" }) }
    var selectedDay by remember { mutableIntStateOf(realTimeCal.get(java.util.Calendar.DAY_OF_MONTH)) }
    var selectedYear by remember { mutableIntStateOf(realTimeCal.get(java.util.Calendar.YEAR)) }

    if (showDatePickerDialog) {
        AlertDialog(
            onDismissRequest = { showDatePickerDialog = false },
            title = { Text("Select Event Date", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = TextDark) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Choose month, day, and year for the event:", fontSize = 12.sp, color = TextMuted)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        monthsList.forEach { m ->
                            FilterChip(
                                selected = selectedMonth == m,
                                onClick = { selectedMonth = m },
                                label = { Text(m, fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = PurplePrimary,
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }

                    val maxDaysForMonth = remember(selectedMonth, selectedYear) {
                        val mIdx = monthsList.indexOf(selectedMonth)
                        val cal = java.util.Calendar.getInstance().apply {
                            clear()
                            set(java.util.Calendar.YEAR, selectedYear)
                            set(java.util.Calendar.MONTH, if (mIdx != -1) mIdx else 9)
                            set(java.util.Calendar.DAY_OF_MONTH, 1)
                        }
                        cal.getActualMaximum(java.util.Calendar.DAY_OF_MONTH)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Day:", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { if (selectedDay > 1) selectedDay-- }) {
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = null, tint = PurplePrimary)
                            }
                            Text("$selectedDay", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = PurplePrimary)
                            IconButton(onClick = { if (selectedDay < maxDaysForMonth) selectedDay++ }) {
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = PurplePrimary)
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Year:", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
                            listOf(currentYear, currentYear + 1, currentYear + 2).forEach { y ->
                                FilterChip(
                                    selected = selectedYear == y,
                                    onClick = { selectedYear = y },
                                    label = { Text("$y", fontSize = 12.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = PurplePrimary,
                                        selectedLabelColor = Color.White
                                    )
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        newDateState = "$selectedMonth $selectedDay, $selectedYear"
                        showDatePickerDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary)
                ) {
                    Text("Confirm Date", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePickerDialog = false }) {
                    Text("Cancel", color = TextMuted)
                }
            },
            containerColor = CardBg,
            shape = RoundedCornerShape(20.dp)
        )
    }

    // Owner Create Event Dialog Popup with Multi-Agent Selection
    if (showCreateDialog) {
        val currentNowCal = java.util.Calendar.getInstance()
        val currentMonthStr = monthsList.getOrElse(currentNowCal.get(java.util.Calendar.MONTH)) { "Nov" }
        val currentDayNum = currentNowCal.get(java.util.Calendar.DAY_OF_MONTH)
        val currentYearNum = currentNowCal.get(java.util.Calendar.YEAR)
        val realTimeDefaultDate = "$currentMonthStr $currentDayNum, $currentYearNum"

        val currentHour24 = currentNowCal.get(java.util.Calendar.HOUR_OF_DAY)
        val isPmNow = currentHour24 >= 12
        val currentHour12 = when {
            currentHour24 == 0 -> 12
            currentHour24 > 12 -> currentHour24 - 12
            else -> currentHour24
        }
        val realTimeDefaultTime = "$currentHour12:00 ${if (isPmNow) "PM" else "AM"}"

        var newTitle by remember { mutableStateOf("") }
        var newDate by remember { mutableStateOf(realTimeDefaultDate) }
        var newTime by remember { mutableStateOf(realTimeDefaultTime) }
        var selectedTargetAgents by remember { mutableStateOf(setOf("All Agents")) }
        var agentDropdownExpanded by remember { mutableStateOf(false) }

        val availableAgentOptions = remember(showCreateDialog, FirebaseCloudSyncManager.globalCloudSyncTrigger.intValue) {
            val deletedPrefs = context.getSharedPreferences("azure_calendar_deleted_agents", Context.MODE_PRIVATE)
            val deletedSet = deletedPrefs.getStringSet("deleted_agent_keys", emptySet()).orEmpty()

            val registered = dbHelper.getAllAgents()
                .filter { agent ->
                    val aName = (agent["name"] ?: "").trim().lowercase()
                    val aEmail = (agent["email"] ?: "").trim().lowercase()
                    aName.isNotBlank() && aName !in deletedSet && aEmail !in deletedSet
                }
                .mapNotNull { it["name"] }

            val activeList = (listOf("All Agents") + registered).distinctBy { it.trim().lowercase() }
            if (activeList.isEmpty()) listOf("All Agents") else activeList
        }

        LaunchedEffect(newDateState) {
            if (newDateState.isNotBlank()) {
                newDate = newDateState
            }
        }

        var timeDropdownExpanded by remember { mutableStateOf(false) }
        val timeOptions = listOf(
            "8:00 AM", "9:00 AM", "10:00 AM", "11:00 AM", "12:00 PM",
            "1:00 PM", "2:00 PM", "3:00 PM", "4:00 PM", "5:00 PM",
            "6:00 PM", "7:00 PM", "8:00 PM", "9:00 PM", "10:00 PM"
        )

        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = {
                Text(
                    text = "Create Owner Event",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextDark
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = "Set title, date, time, and select agents who can join this event group chat:", fontSize = 12.sp, color = TextMuted)

                    OutlinedTextField(
                        value = newTitle,
                        onValueChange = { newTitle = it },
                        label = { Text("Event Title") },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Target Agents Multi-Select Dropdown
                    ExposedDropdownMenuBox(
                        expanded = agentDropdownExpanded,
                        onExpandedChange = { agentDropdownExpanded = !agentDropdownExpanded },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = if (selectedTargetAgents.contains("All Agents")) "All Agents" else selectedTargetAgents.joinToString(", "),
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Select Target Agents (Multi-Select)") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = agentDropdownExpanded) },
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true).fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = agentDropdownExpanded,
                            onDismissRequest = { agentDropdownExpanded = false }
                        ) {
                            availableAgentOptions.forEach { agentOpt ->
                                val isChecked = selectedTargetAgents.contains(agentOpt)
                                DropdownMenuItem(
                                    text = {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Checkbox(
                                                checked = isChecked,
                                                onCheckedChange = null
                                            )
                                            Text(agentOpt, fontWeight = FontWeight.Medium)
                                        }
                                    },
                                    onClick = {
                                        val updated = selectedTargetAgents.toMutableSet()
                                        if (agentOpt == "All Agents") {
                                            updated.clear()
                                            updated.add("All Agents")
                                        } else {
                                            updated.remove("All Agents")
                                            if (isChecked) updated.remove(agentOpt) else updated.add(agentOpt)
                                            if (updated.isEmpty()) updated.add("All Agents")
                                        }
                                        selectedTargetAgents = updated
                                    }
                                )
                            }

                            HorizontalDivider(color = Color(0xFFE8DEF8))

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                contentAlignment = Alignment.CenterEnd
                            ) {
                                Button(
                                    onClick = { agentDropdownExpanded = false },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary),
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
                                ) {
                                    Text("Okay", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Clickable Date Selector
                        Box(modifier = Modifier.weight(1f)) {
                            OutlinedTextField(
                                value = newDate,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Date") },
                                trailingIcon = {
                                    Icon(Icons.Default.DateRange, contentDescription = "Pick Date", tint = PurplePrimary)
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .clickable { showDatePickerDialog = true }
                            )
                        }

                        // Clickable Standard 12-Hour Time Selector
                        ExposedDropdownMenuBox(
                            expanded = timeDropdownExpanded,
                            onExpandedChange = { timeDropdownExpanded = !timeDropdownExpanded },
                            modifier = Modifier.weight(1f)
                        ) {
                            OutlinedTextField(
                                value = newTime,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Time") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = timeDropdownExpanded) },
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true).fillMaxWidth()
                            )
                            ExposedDropdownMenu(
                                expanded = timeDropdownExpanded,
                                onDismissRequest = { timeDropdownExpanded = false }
                            ) {
                                timeOptions.forEach { t ->
                                    DropdownMenuItem(
                                        text = { Text(t, fontWeight = FontWeight.Medium) },
                                        onClick = {
                                            newTime = t
                                            timeDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newTitle.isBlank()) {
                            Toast.makeText(context, "Please enter Event Title", Toast.LENGTH_SHORT).show()
                        } else {
                            val finalTargetAgents = if (selectedTargetAgents.contains("All Agents")) "All Agents" else selectedTargetAgents.joinToString(", ")

                            val insertedRowId = dbHelper.insertEvent(
                                title = newTitle,
                                date = newDate,
                                time = newTime,
                                agent = finalTargetAgents,
                                unit = "Event",
                                shape = "Owner Event",
                                color = 0L
                            )

                            val newId = if (insertedRowId > 0) insertedRowId.toString() else "evt-${System.currentTimeMillis()}"

                            val eventMap = mapOf<String, Any>(
                                "id" to newId,
                                "title" to newTitle,
                                "date" to newDate,
                                "time" to newTime,
                                "agent" to finalTargetAgents,
                                "unit" to "Event",
                                "shape" to "Owner Event",
                                "color" to 0L
                            )
                            deletedEventIds.remove(newId)
                            deletedEventIds.remove(newTitle)
                            if (!isPreview) {
                                prefs.edit().putStringSet("deleted_ids", deletedEventIds.toSet()).apply()
                            }

                            FirebaseCloudSyncManager.pushEventToCloud(eventMap)
                            FirebaseCloudSyncManager.pushOwnerEventToCloud(
                                id = newId,
                                title = newTitle,
                                description = "Owner event created for $finalTargetAgents.",
                                date = newDate,
                                time = newTime,
                                createdBy = "Owner",
                                targetAgent = finalTargetAgents,
                                eventLink = ""
                            )

                            Toast.makeText(context, "Event '$newTitle' created for $finalTargetAgents!", Toast.LENGTH_LONG).show()
                            showCreateDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Create Event", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text("Cancel", color = TextMuted)
                }
            },
            containerColor = CardBg,
            shape = RoundedCornerShape(20.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventGroupChatView(
    event: OwnerEventItem,
    currentUserName: String,
    isOwnerMode: Boolean,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val dbHelper = remember { AzureDatabaseHelper(context) }
    var messageInput by remember { mutableStateOf("") }
    var showExitMeetingDialog by remember { mutableStateOf(false) }

    // Intercept system back button/swipes during an active event meeting
    BackHandler(enabled = true) {
        showExitMeetingDialog = true
    }

    val chatRoomId = remember(event.id, event.title) {
        FirebaseCloudSyncManager.getNormalizedEventId(event.id, event.title)
    }

    val syncTrigger = FirebaseCloudSyncManager.globalCloudSyncTrigger.intValue

    // Load persisted chat history from local SQLite database first
    val dbMessages = remember(chatRoomId, syncTrigger) {
        dbHelper.getChatMessagesForRoom(chatRoomId)
    }

    val memoryMessages = remember(chatRoomId) {
        EventChatRepository.getMessagesForEvent(chatRoomId, event.title, event.targetAgent)
    }

    var cloudMessages by remember { mutableStateOf<List<GroupChatMessage>>(emptyList()) }

    DisposableEffect(chatRoomId) {
        val registration = FirebaseCloudSyncManager.listenToEventChatMessages(context, event.id, event.title) { updatedList ->
            cloudMessages = updatedList
        }
        onDispose {
            registration.remove()
        }
    }

    // Launcher for attaching photos in group chat
    val chatPhotoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val base64DataUri = FirebaseCloudSyncManager.prepareImageForCloud(context, uri.toString())
            if (base64DataUri.isNotBlank()) {
                val imagePayload = "[IMAGE]:$base64DataUri"
                val timeFormatted = java.text.SimpleDateFormat("h:mm a", java.util.Locale.US).format(java.util.Date())
                val msgId = "msg_${System.currentTimeMillis()}"

                val newMsg = GroupChatMessage(
                    id = msgId,
                    senderName = currentUserName,
                    senderRole = if (isOwnerMode) "Owner" else "Agent",
                    messageText = imagePayload,
                    timestamp = timeFormatted,
                    isSelf = true
                )
                memoryMessages.add(newMsg)
                dbHelper.insertChatMessage(
                    msgId = msgId,
                    roomId = chatRoomId,
                    senderName = currentUserName,
                    senderRole = if (isOwnerMode) "Owner" else "Agent",
                    text = imagePayload,
                    timestamp = timeFormatted
                )

                FirebaseCloudSyncManager.pushChatMessageToCloud(
                    msgId = msgId,
                    eventId = event.id,
                    eventTitle = event.title,
                    senderName = currentUserName,
                    senderRole = if (isOwnerMode) "Owner" else "Agent",
                    messageText = imagePayload,
                    timestamp = timeFormatted
                )

                FirebaseCloudSyncManager.notifyCloudUpdate()
                Toast.makeText(context, "Photo sent to event group chat!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val liveDbMsgs = dbHelper.getChatMessagesForRoom(chatRoomId)
    val displayMessages = (liveDbMsgs + dbMessages + memoryMessages + cloudMessages)
        .distinctBy { if (it.id.isNotBlank()) it.id else "${it.senderName.trim().lowercase()}_${it.messageText.trim()}_${it.createdTime}" }
        .sortedBy { if (it.createdTime > 0L) it.createdTime else it.id.replace(Regex("[^0-9]"), "").toLongOrNull() ?: 0L }

    val chatListState = androidx.compose.foundation.lazy.rememberLazyListState()

    LaunchedEffect(displayMessages.hashCode(), displayMessages.size, chatRoomId) {
        val userName = if (isOwnerMode) "Admin Owner" else currentUserName
        val chatPrefs = context.getSharedPreferences("azure_calendar_chat_reads", Context.MODE_PRIVATE)
        val seenPrefs = context.getSharedPreferences("azure_calendar_event_reads", Context.MODE_PRIVATE)

        chatPrefs.edit()
            .putInt("read_count_${chatRoomId}_$userName", displayMessages.size)
            .apply()
        seenPrefs.edit()
            .putBoolean("seen_event_${event.id}_$userName", true)
            .putBoolean("seen_event_${event.title}_$userName", true)
            .apply()

        if (displayMessages.isNotEmpty()) {
            chatListState.scrollToItem(displayMessages.size)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ScreenBg)
    ) {
        // Chat Header Bar
        Surface(
            color = CardBg,
            shadowElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
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
                        .clickable { showExitMeetingDialog = true }
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

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = event.title,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextDark,
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Group,
                            contentDescription = null,
                            tint = PurplePrimary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Admin, ${event.targetAgent}",
                            fontSize = 11.sp,
                            color = TextMuted,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        // Chat Message List
        LazyColumn(
            state = chatListState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFF3EDFD),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = PurplePrimary, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Group chat for event '${event.title}'. Only joined agents and admin have access.",
                            fontSize = 11.sp,
                            color = PurplePrimary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            items(items = displayMessages, key = { msg -> msg.id }) { msg ->
                val isMe = msg.senderName.equals(currentUserName, ignoreCase = true) ||
                           (isOwnerMode && msg.senderRole.equals("Owner", ignoreCase = true)) ||
                           (!isOwnerMode && msg.senderRole.equals("Agent", ignoreCase = true) && msg.senderName.contains(currentUserName, ignoreCase = true))

                val isImageMsg = msg.messageText.startsWith("[IMAGE]:", ignoreCase = true) ||
                                 msg.messageText.startsWith("data:image", ignoreCase = true) ||
                                 msg.messageText.startsWith("content://", ignoreCase = true) ||
                                 msg.messageText.startsWith("file://", ignoreCase = true) ||
                                 msg.messageText.startsWith("/data/", ignoreCase = true)

                val rawImagePayload = if (msg.messageText.startsWith("[IMAGE]:", ignoreCase = true)) {
                    msg.messageText.substringAfter("[IMAGE]:").trim()
                } else {
                    msg.messageText.trim()
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = if (isMe) Alignment.End else Alignment.Start
                ) {
                    Text(
                        text = msg.senderName,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (msg.senderRole == "Owner") PurplePrimary else Color(0xFF2E7D32)
                    )
                    Spacer(modifier = Modifier.height(2.dp))

                    if (isImageMsg) {

                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (isMe) PurplePrimary else CardBg,
                            border = if (isMe) null else BorderStroke(1.dp, BorderPurple),
                            shadowElevation = 2.dp,
                            modifier = Modifier.widthIn(max = 240.dp)
                        ) {
                            Column(modifier = Modifier.padding(6.dp)) {
                                ChatImageBubble(
                                    rawImagePayload = rawImagePayload,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(180.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = msg.timestamp,
                                    fontSize = 9.sp,
                                    color = if (isMe) Color.White.copy(alpha = 0.7f) else TextMuted,
                                    modifier = Modifier.align(Alignment.End).padding(end = 4.dp, bottom = 2.dp)
                                )
                            }
                        }
                    } else {
                        Surface(
                            shape = RoundedCornerShape(
                                topStart = 16.dp,
                                topEnd = 16.dp,
                                bottomStart = if (isMe) 16.dp else 4.dp,
                                bottomEnd = if (isMe) 4.dp else 16.dp
                            ),
                            color = if (isMe) PurplePrimary else CardBg,
                            border = if (isMe) null else BorderStroke(1.dp, BorderPurple),
                            shadowElevation = 1.dp
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                                Text(
                                    text = msg.messageText,
                                    fontSize = 13.sp,
                                    color = if (isMe) Color.White else TextDark,
                                    lineHeight = 17.sp
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = msg.timestamp,
                                    fontSize = 9.sp,
                                    color = if (isMe) Color.White.copy(alpha = 0.7f) else TextMuted,
                                    modifier = Modifier.align(Alignment.End)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Bottom Input Bar
        Surface(
            color = CardBg,
            shadowElevation = 8.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(
                    onClick = {
                        try {
                            chatPhotoLauncher.launch("image/*")
                        } catch (e: Exception) {
                            Toast.makeText(context, "Opening gallery...", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFF3EDFD))
                ) {
                    Icon(
                        imageVector = Icons.Default.AddPhotoAlternate,
                        contentDescription = "Attach Photo",
                        tint = PurplePrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                val inputBgColor = if (AzureCalendarColors.isDarkMode) Color(0xFF2A2335) else CardBg
                val inputTextColor = if (AzureCalendarColors.isDarkMode) Color(0xFFFFFFFF) else TextDark

                OutlinedTextField(
                    value = messageInput,
                    onValueChange = { messageInput = it },
                    placeholder = { Text("Type a message...", fontSize = 13.sp, color = TextMuted) },
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = inputTextColor,
                        unfocusedTextColor = inputTextColor,
                        focusedContainerColor = inputBgColor,
                        unfocusedContainerColor = inputBgColor,
                        focusedBorderColor = PurplePrimary,
                        unfocusedBorderColor = BorderPurple,
                        cursorColor = PurplePrimary
                    ),
                    modifier = Modifier.weight(1f)
                )

                IconButton(
                    onClick = {
                        val trimmedMsg = messageInput.trim()
                        if (trimmedMsg.isNotBlank()) {
                            val timeFormatted = java.text.SimpleDateFormat("h:mm a", java.util.Locale.US).format(java.util.Date())
                            val msgId = "msg_${System.currentTimeMillis()}"
                            val newMsg = GroupChatMessage(
                                id = msgId,
                                senderName = currentUserName,
                                senderRole = if (isOwnerMode) "Owner" else "Agent",
                                messageText = trimmedMsg,
                                timestamp = timeFormatted,
                                isSelf = true
                            )
                            memoryMessages.add(newMsg)
                            dbHelper.insertChatMessage(
                                msgId = msgId,
                                roomId = chatRoomId,
                                senderName = currentUserName,
                                senderRole = if (isOwnerMode) "Owner" else "Agent",
                                text = trimmedMsg,
                                timestamp = timeFormatted
                            )

                            FirebaseCloudSyncManager.pushChatMessageToCloud(
                                msgId = msgId,
                                eventId = event.id,
                                eventTitle = event.title,
                                senderName = currentUserName,
                                senderRole = if (isOwnerMode) "Owner" else "Agent",
                                messageText = trimmedMsg,
                                timestamp = timeFormatted
                            )

                            val userNameKey = if (isOwnerMode) "Admin Owner" else currentUserName
                            val chatPrefs = context.getSharedPreferences("azure_calendar_chat_reads", Context.MODE_PRIVATE)
                            val seenPrefs = context.getSharedPreferences("azure_calendar_event_reads", Context.MODE_PRIVATE)
                            val newTotal = displayMessages.size + 1
                            chatPrefs.edit()
                                .putInt("read_count_${chatRoomId}_$userNameKey", newTotal)
                                .putBoolean("read_all_${chatRoomId}_$userNameKey", true)
                                .apply()
                            seenPrefs.edit()
                                .putBoolean("seen_event_${event.id}_$userNameKey", true)
                                .putBoolean("seen_event_${event.title}_$userNameKey", true)
                                .apply()

                            val allUserKeys = listOf("Admin Owner", "Alex Reyes", "sungit", "novie", "Gilbert", "Agent")
                            val otherKeys = allUserKeys.filter { !it.equals(userNameKey, ignoreCase = true) }
                            val editor = chatPrefs.edit()
                            otherKeys.forEach { otherKey ->
                                editor.putBoolean("read_all_${chatRoomId}_$otherKey", false)
                            }
                            editor.apply()

                            FirebaseCloudSyncManager.notifyCloudUpdate()

                            messageInput = ""
                        }
                    },
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(PurplePrimary)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }

    if (showExitMeetingDialog) {
        AlertDialog(
            onDismissRequest = { showExitMeetingDialog = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFF39C12), modifier = Modifier.size(22.dp))
                    Text(
                        text = "Ongoing Event Meeting",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextDark
                    )
                }
            },
            text = {
                Text(
                    text = "You are currently inside an active event meeting session. Are you sure you want to leave this meeting?",
                    fontSize = 13.sp,
                    color = TextMuted
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showExitMeetingDialog = false
                        onBackClick()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Leave Meeting", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showExitMeetingDialog = false },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Stay in Meeting", color = PurplePrimary)
                }
            },
            containerColor = CardBg,
            shape = RoundedCornerShape(20.dp)
        )
    }
}

@Composable
fun ChatImageBubble(
    rawImagePayload: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val bitmap = remember(rawImagePayload) {
        val raw = rawImagePayload.trim()
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
            contentDescription = "Chat Photo Attachment",
            contentScale = ContentScale.Crop,
            modifier = modifier
        )
    } else {
        Box(
            modifier = modifier.background(Color.Gray.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Image,
                contentDescription = "Photo Placeholder",
                tint = Color.Gray,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun NotificationsCenterScreenPreview() {
    AzureCalendarTheme {
        NotificationsCenterScreen(isOwnerMode = false)
    }
}
