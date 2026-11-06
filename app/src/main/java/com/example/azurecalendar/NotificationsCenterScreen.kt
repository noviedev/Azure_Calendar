package com.example.azurecalendar

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import com.example.azurecalendar.ui.theme.AzureCalendarTheme

private val PurplePrimary = Color(0xFF6A1B9A)
private val ScreenBg = Color(0xFFFAF5FC)
private val CardBg = Color(0xFFFFFFFF)
private val TextDark = Color(0xFF2C1A36)
private val TextMuted = Color(0xFF7B6287)
private val BorderPurple = Color(0xFFE0D0EC)

data class OwnerEventItem(
    val id: String,
    val title: String,
    val description: String,
    val date: String,
    val time: String,
    val createdBy: String = "Owner",
    val targetAgent: String = "All Agents",
    val eventLink: String = "https://azurecalendar.app/event/join?id=$id",
    var isJoined: Boolean = false
)

data class GroupChatMessage(
    val id: String,
    val senderName: String,
    val senderRole: String, // "Owner" or "Agent"
    val messageText: String,
    val timestamp: String,
    val isSelf: Boolean
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
                    isSelf = false
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
    @Suppress("UNUSED_PARAMETER") onBackClick: () -> Unit = {},
    @Suppress("UNUSED_PARAMETER") onNavigateToInvoice: (invoiceId: String) -> Unit = {},
    @Suppress("UNUSED_PARAMETER") onNavigateToTemplates: () -> Unit = {}
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val dbHelper = remember { AzureDatabaseHelper(context) }
    val isPreview = LocalInspectionMode.current

    var activeGroupChatEvent by remember { mutableStateOf<OwnerEventItem?>(null) }
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

    DisposableEffect(Unit) {
        val registration = FirebaseCloudSyncManager.listenToOwnerEvents { updatedEvents ->
            cloudOwnerEvents = updatedEvents
        }
        onDispose {
            registration.remove()
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
                !isStaycation && (unit.equals("Event", ignoreCase = true) || shape.contains("Owner Event", ignoreCase = true))
            }.map { evt ->
                val evtId = evt["id"] ?: "0"
                OwnerEventItem(
                    id = evtId,
                    title = evt["title"] ?: "Owner Event",
                    description = "Created by Owner/Admin for Agents",
                    date = evt["date"] ?: "Sept 15, 2026",
                    time = evt["time"] ?: "2:00 PM",
                    createdBy = "Owner",
                    targetAgent = "All Agents",
                    eventLink = "https://azurecalendar.app/event/join?id=$evtId"
                )
            }
        }
    }

    val combinedEvents = remember(dbOwnerEvents, cloudOwnerEvents, deletedEventIds.toList()) {
        val merged = (cloudOwnerEvents + dbOwnerEvents)
        merged.filter {
            it.id !in deletedEventIds &&
            it.title !in deletedEventIds &&
            !it.title.startsWith("Staycation", ignoreCase = true)
        }.distinctBy { "${it.title.lowercase().trim()}_${it.date}_${it.time}" }
    }

    // Separated lists for Agent mode: Pending Push Notification Invites vs. Joined Events
    val pendingInvites = remember(combinedEvents, joinedEventIds.toList(), isOwnerMode) {
        if (isOwnerMode) emptyList() else combinedEvents.filter { it.id !in joinedEventIds && !it.isJoined }
    }

    val joinedEvents = remember(combinedEvents, joinedEventIds.toList(), isOwnerMode) {
        if (isOwnerMode) combinedEvents else combinedEvents.filter { it.id in joinedEventIds || it.isJoined }
    }

    if (activeGroupChatEvent != null) {
        EventGroupChatView(
            event = activeGroupChatEvent!!,
            currentUserName = if (isOwnerMode) "Admin (Owner)" else agentName,
            isOwnerMode = isOwnerMode,
            onBackClick = { activeGroupChatEvent = null }
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
            if (!isOwnerMode && pendingInvites.isNotEmpty()) {
                item {
                    Text(
                        text = "🔔 New Event Notifications (Tap to Join)",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextDark
                    )
                }

                items(pendingInvites) { event ->
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF3EDFD)),
                        border = BorderStroke(1.5.dp, PurplePrimary),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (!joinedEventIds.contains(event.id)) {
                                    joinedEventIds.add(event.id)
                                    event.isJoined = true
                                    if (!isPreview) {
                                        joinedPrefs.edit().putStringSet("joined_ids", joinedEventIds.toSet()).apply()
                                    }
                                    Toast.makeText(context, "Successfully joined '${event.title}'!", Toast.LENGTH_SHORT).show()
                                }
                                activeGroupChatEvent = event
                            }
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(20.dp),
                                    color = PurplePrimary
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("NEW EVENT INVITE • TAP TO JOIN", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    }
                                }

                                Text("Created by Owner", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = PurplePrimary)
                            }

                            Text(text = event.title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextDark)
                            Text(text = event.description, fontSize = 12.sp, color = TextMuted, lineHeight = 16.sp)

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.DateRange, contentDescription = null, tint = PurplePrimary, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(text = "${event.date} at ${event.time}", fontSize = 12.sp, color = TextDark, fontWeight = FontWeight.Medium)
                                }

                                Button(
                                    onClick = {
                                        if (!joinedEventIds.contains(event.id)) {
                                            joinedEventIds.add(event.id)
                                            event.isJoined = true
                                            if (!isPreview) {
                                                joinedPrefs.edit().putStringSet("joined_ids", joinedEventIds.toSet()).apply()
                                            }
                                            Toast.makeText(context, "Successfully joined '${event.title}'!", Toast.LENGTH_SHORT).show()
                                        }
                                        activeGroupChatEvent = event
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Default.GroupAdd, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Tap to Join Event", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            if (!isOwnerMode) {
                item {
                    Text(
                        text = "Joined Events & Group Chats",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextDark
                    )
                }
            }

            if (!isOwnerMode && joinedEvents.isEmpty()) {
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
                            Text("No Joined Owner Events Yet", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextDark)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Tap any event push notification invite above to automatically join and register for owner events.",
                                fontSize = 12.sp,
                                color = TextMuted,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }

            items(joinedEvents) { event ->
                val joinedState = event.isJoined || event.id in joinedEventIds

                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = CardBg),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { activeGroupChatEvent = event }
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

                        // Automated Link Box
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFFF3EDFD),
                            border = BorderStroke(1.dp, BorderPurple),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Link, contentDescription = null, tint = PurplePrimary, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = event.eventLink,
                                        fontSize = 11.sp,
                                        color = PurplePrimary,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        clipboardManager.setText(AnnotatedString(event.eventLink))
                                        Toast.makeText(context, "Event link copied!", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy Link", tint = PurplePrimary, modifier = Modifier.size(16.dp))
                                }
                            }
                        }

                        val chatRoomId = remember(event.id, event.title) {
                            FirebaseCloudSyncManager.getNormalizedEventId(event.id, event.title)
                        }
                        val roomMessages = remember(chatRoomId, FirebaseCloudSyncManager.globalCloudSyncTrigger.intValue) {
                            dbHelper.getChatMessagesForRoom(chatRoomId)
                        }
                        val chatPrefs = remember { context.getSharedPreferences("azure_calendar_chat_reads", Context.MODE_PRIVATE) }
                        val currentReadCount = remember(chatRoomId, roomMessages.size) {
                            chatPrefs.getInt("read_count_${chatRoomId}_$agentName", 0)
                        }
                        val unreadChatMsgCount = (roomMessages.size - currentReadCount).coerceAtLeast(0)

                        // Open Group Chat Button
                        Button(
                            onClick = {
                                chatPrefs.edit().putInt("read_count_${chatRoomId}_$agentName", roomMessages.size).apply()
                                activeGroupChatEvent = event
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
                                    Badge(
                                        containerColor = Color(0xFFE53935),
                                        contentColor = Color.White
                                    ) {
                                        Text(
                                            text = "$unreadChatMsgCount New",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }

                        // Share / Delete for Owner
                        if (isOwnerMode) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        clipboardManager.setText(AnnotatedString(event.eventLink))
                                        Toast.makeText(context, "Link copied! Send to ${event.targetAgent} to join.", Toast.LENGTH_SHORT).show()
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, PurplePrimary),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(38.dp)
                                ) {
                                    Icon(Icons.Default.Share, contentDescription = null, tint = PurplePrimary, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Share Link",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = PurplePrimary
                                    )
                                }

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
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFE53935)),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(38.dp)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete Event", modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Delete Event",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
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
            containerColor = Color.White,
            shape = RoundedCornerShape(20.dp)
        )
    }

    // Owner Create Event Dialog Popup with Automated Link Generation
    if (showCreateDialog) {
        var newTitle by remember { mutableStateOf("") }
        var newDate by remember { mutableStateOf("Sept 15, 2026") }
        var newTime by remember { mutableStateOf("2:00 PM") }

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
                    Text(text = "An automated join link will be generated for this event. Anyone with the link can join.", fontSize = 12.sp, color = TextMuted)

                    OutlinedTextField(
                        value = newTitle,
                        onValueChange = { newTitle = it },
                        label = { Text("Event Title") },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )



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
                            val insertedRowId = dbHelper.insertEvent(
                                title = newTitle,
                                date = newDate,
                                time = newTime,
                                agent = "Owner",
                                unit = "Event",
                                shape = "Owner Event",
                                color = 0L
                            )

                            val newId = if (insertedRowId > 0) insertedRowId.toString() else "evt-${System.currentTimeMillis()}"
                            val generatedLink = "https://azurecalendar.app/event/join?id=$newId"

                            val eventMap = mapOf(
                                "id" to newId,
                                "title" to newTitle,
                                "date" to newDate,
                                "time" to newTime,
                                "agent" to "Owner",
                                "unit" to "Event",
                                "shape" to "Owner Event",
                                "color" to "0"
                            )
                            FirebaseCloudSyncManager.pushEventToCloud(eventMap)
                            FirebaseCloudSyncManager.pushOwnerEventToCloud(
                                id = newId,
                                title = newTitle,
                                description = "Event created by Owner for All Agents.",
                                date = newDate,
                                time = newTime,
                                createdBy = "Owner",
                                targetAgent = "All Agents",
                                eventLink = generatedLink
                            )

                            clipboardManager.setText(AnnotatedString(generatedLink))
                            Toast.makeText(
                                context,
                                "Event created! Link copied: $generatedLink. Send this link for agents to join!",
                                Toast.LENGTH_LONG
                            ).show()

                            showCreateDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Create & Generate Link", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text("Cancel", color = TextMuted)
                }
            },
            containerColor = Color.White,
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

    // Load persisted chat history from local SQLite database first
    val dbMessages = remember(chatRoomId) {
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

    val displayMessages = remember(dbMessages, memoryMessages.toList(), cloudMessages) {
        val combined = (dbMessages + memoryMessages + cloudMessages)
        combined.distinctBy { "${it.senderName.trim().lowercase()}_${it.messageText.trim()}_${it.timestamp}" }
            .sortedBy { it.id }
    }

    LaunchedEffect(displayMessages.size) {
        if (displayMessages.isNotEmpty()) {
            val chatPrefs = context.getSharedPreferences("azure_calendar_chat_reads", Context.MODE_PRIVATE)
            chatPrefs.edit().putInt("read_count_${chatRoomId}_$currentUserName", displayMessages.size).apply()
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
                            text = "Admin, ${event.targetAgent}, Alex Reyes",
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

            items(displayMessages) { msg ->
                val isMe = msg.senderName.equals(currentUserName, ignoreCase = true) ||
                           (isOwnerMode && msg.senderRole.equals("Owner", ignoreCase = true)) ||
                           (!isOwnerMode && msg.senderRole.equals("Agent", ignoreCase = true) && msg.senderName.contains(currentUserName, ignoreCase = true))

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
                OutlinedTextField(
                    value = messageInput,
                    onValueChange = { messageInput = it },
                    placeholder = { Text("Type a message...", fontSize = 13.sp, color = TextMuted) },
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PurplePrimary,
                        unfocusedBorderColor = BorderPurple
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
                                eventId = event.id,
                                eventTitle = event.title,
                                senderName = currentUserName,
                                senderRole = if (isOwnerMode) "Owner" else "Agent",
                                messageText = trimmedMsg,
                                timestamp = timeFormatted
                            )
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
            containerColor = Color.White,
            shape = RoundedCornerShape(20.dp)
        )
    }
}

@Preview(showBackground = true)
@Composable
fun NotificationsCenterScreenPreview() {
    AzureCalendarTheme {
        NotificationsCenterScreen(isOwnerMode = false)
    }
}
