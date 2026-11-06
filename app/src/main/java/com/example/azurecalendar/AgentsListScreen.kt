package com.example.azurecalendar

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentsListScreen(
    onBackClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val isPreview = LocalInspectionMode.current
    var searchQuery by remember { mutableStateOf("") }
    var agentToDelete by remember { mutableStateOf<Map<String, String>?>(null) }
    val deletedPrefs = remember { context.getSharedPreferences("azure_calendar_deleted_agents", Context.MODE_PRIVATE) }

    val registeredAgents = remember {
        mutableStateOf<List<Map<String, String>>>(emptyList())
    }

    LaunchedEffect(Unit, FirebaseCloudSyncManager.globalCloudSyncTrigger.intValue) {
        try {
            val dbHelper = AzureDatabaseHelper(context)
            val deletedSet = deletedPrefs.getStringSet("deleted_agent_keys", emptySet()).orEmpty()
            val localList = dbHelper.getAllAgents().filter { agent ->
                val name = (agent["name"] ?: "").trim().lowercase()
                val email = (agent["email"] ?: "").trim().lowercase()
                name !in deletedSet && email !in deletedSet
            }
            registeredAgents.value = localList.distinctBy {
                it["email"]?.lowercase()?.ifBlank { it["name"]?.lowercase() }
            }

            com.google.firebase.firestore.FirebaseFirestore.getInstance().collection("agents")
                .get()
                .addOnSuccessListener { query ->
                    val deletedSetCloud = deletedPrefs.getStringSet("deleted_agent_keys", emptySet()).orEmpty()
                    for (doc in query.documents) {
                        val aName = doc.getString("name") ?: ""
                        val aEmail = doc.getString("email") ?: ""
                        val aPhone = doc.getString("phone") ?: ""
                        val aRole = doc.getString("role") ?: "Property Agent"
                        val aGender = doc.getString("gender") ?: "Female"

                        val isDeleted = deletedSetCloud.contains(aEmail.trim().lowercase()) ||
                                        deletedSetCloud.contains(aName.trim().lowercase())

                        if (aName.isNotBlank() && aEmail.isNotBlank() && !isDeleted) {
                            dbHelper.insertAgent(aName, aEmail, aPhone, aRole, aGender, syncToCloud = false)
                        }
                    }
                    val updatedList = dbHelper.getAllAgents().filter { agent ->
                        val name = (agent["name"] ?: "").trim().lowercase()
                        val email = (agent["email"] ?: "").trim().lowercase()
                        name !in deletedSetCloud && email !in deletedSetCloud
                    }
                    registeredAgents.value = updatedList.distinctBy {
                        it["email"]?.lowercase()?.ifBlank { it["name"]?.lowercase() }
                    }
                }
        } catch (_: Exception) {}
    }

    val filteredAgents = remember(searchQuery, registeredAgents.value) {
        registeredAgents.value.filter { agent ->
            val name = agent["name"] ?: ""
            val email = agent["email"] ?: ""
            val pos = agent["position"] ?: ""
            name.contains(searchQuery, ignoreCase = true) ||
                    email.contains(searchQuery, ignoreCase = true) ||
                    pos.contains(searchQuery, ignoreCase = true)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ScreenBg)
            .statusBarsPadding()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        // Top Navigation Header with Glossy Circular Back Button
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
            Text(
                text = "Registered Agents",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = TextDark
            )
        }

        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search agents by name or email...", color = TextMuted, fontSize = 14.sp) },
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
                focusedBorderColor = Color(0xFFE0D0EC),
                unfocusedBorderColor = Color(0xFFE0D0EC)
            ),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        // Agent List
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(filteredAgents) { agent ->
                val name = agent["name"] ?: "Agent"
                val email = agent["email"] ?: "agent@azure.com"
                val position = agent["position"] ?: "Agent"

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
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            // Avatar Icon
                            Surface(
                                shape = CircleShape,
                                color = Color(0xFFF3EDFD),
                                modifier = Modifier.size(48.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = name,
                                        tint = PurplePrimary,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = name,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextDark
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = position,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = PurplePrimary
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = email,
                                    fontSize = 12.sp,
                                    color = TextMuted
                                )
                            }
                        }

                        // Delete Agent Button
                        IconButton(
                            onClick = {
                                agentToDelete = agent
                            }
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = Color(0xFFFFEBEE),
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete Agent",
                                        tint = Color(0xFFE53935),
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
    }

    if (agentToDelete != null) {
        val targetName = agentToDelete?.get("name") ?: "Agent"
        val targetEmail = agentToDelete?.get("email") ?: ""
        val targetId = agentToDelete?.get("id")?.toLongOrNull()

        AlertDialog(
            onDismissRequest = { agentToDelete = null },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFE53935), modifier = Modifier.size(22.dp))
                    Text(
                        text = "Permanently Delete Agent",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextDark
                    )
                }
            },
            text = {
                Text(
                    text = "Are you sure you want to permanently delete agent '$targetName'? This action cannot be undone.",
                    fontSize = 13.sp,
                    color = TextMuted
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val dbHelper = AzureDatabaseHelper(context)
                        val deletedSet = deletedPrefs.getStringSet("deleted_agent_keys", emptySet()).orEmpty().toMutableSet()
                        if (targetName.isNotBlank()) deletedSet.add(targetName.trim().lowercase())
                        if (targetEmail.isNotBlank()) deletedSet.add(targetEmail.trim().lowercase())
                        deletedPrefs.edit().putStringSet("deleted_agent_keys", deletedSet).apply()

                        if (targetId != null && targetId != 0L) {
                            dbHelper.deleteAgent(targetId)
                        }
                        dbHelper.deleteAgentByNameOrEmail(targetName, targetEmail)
                        FirebaseCloudSyncManager.deleteAgentFromCloud(targetName, targetEmail)

                        registeredAgents.value = registeredAgents.value.filter {
                            !it["name"].equals(targetName, ignoreCase = true) && !it["email"].equals(targetEmail, ignoreCase = true)
                        }
                        Toast.makeText(context, "Agent $targetName permanently deleted", Toast.LENGTH_SHORT).show()
                        agentToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Permanently Delete", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { agentToDelete = null },
                    shape = RoundedCornerShape(10.dp)
                ) {
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
fun AgentsListScreenPreview() {
    AzureCalendarTheme {
        AgentsListScreen()
    }
}
