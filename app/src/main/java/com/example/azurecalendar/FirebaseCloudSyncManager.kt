package com.example.azurecalendar

import android.content.Context
import android.util.Log
import android.widget.Toast
import com.google.firebase.firestore.*

object FirebaseCloudSyncManager {

    private const val TAG = "CloudSync"
    val globalCloudSyncTrigger = androidx.compose.runtime.mutableIntStateOf(0)

    fun notifyCloudUpdate() {
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            globalCloudSyncTrigger.intValue++
        }
    }

    private var isListenerActive = false
    private var eventsListener: ListenerRegistration? = null
    private var unitsListener: ListenerRegistration? = null
    private var unitsCatalogListener: ListenerRegistration? = null
    private var agentsListener: ListenerRegistration? = null
    private var deletedUnitsListener: ListenerRegistration? = null
    private var adminProfileListener: ListenerRegistration? = null

    private fun sanitizeDocId(rawId: String): String {
        return rawId.trim()
            .replace("/", "-")
            .replace("\\", "-")
            .replace(" ", "_")
            .replace(Regex("[^a-zA-Z0-9_\\-.]"), "")
            .ifBlank { System.currentTimeMillis().toString() }
    }

    private fun isSameUnit(unitA: String, unitB: String): Boolean {
        val cleanA = unitA.replace("Unit", "", ignoreCase = true).trim()
        val cleanB = unitB.replace("Unit", "", ignoreCase = true).trim()
        return cleanA.equals(cleanB, ignoreCase = true)
    }

    fun isEventDateExpired(dateStr: String, timeStr: String = "12:00 PM"): Boolean {
        try {
            if (dateStr.isBlank()) return false
            val realTimeCal = java.util.Calendar.getInstance()
            val currentYear = realTimeCal.get(java.util.Calendar.YEAR)
            val currentMonthIdx = realTimeCal.get(java.util.Calendar.MONTH) + 1
            val currentDay = realTimeCal.get(java.util.Calendar.DAY_OF_MONTH)
            val currentHour24 = realTimeCal.get(java.util.Calendar.HOUR_OF_DAY)

            val monthMap = mapOf(
                "jan" to 1, "feb" to 2, "mar" to 3, "apr" to 4, "may" to 5, "jun" to 6,
                "jul" to 7, "aug" to 8, "sep" to 9, "sept" to 9, "oct" to 10, "nov" to 11, "dec" to 12,
                "january" to 1, "february" to 2, "march" to 3, "april" to 4, "june" to 6,
                "july" to 7, "august" to 8, "september" to 9, "october" to 10, "november" to 11, "december" to 12
            )

            val yearFound = Regex("\\b20\\d{2}\\b").find(dateStr)?.value?.toIntOrNull() ?: currentYear
            val monthFound = monthMap.entries.find { dateStr.contains(it.key, ignoreCase = true) }?.value ?: currentMonthIdx

            val daysInDate = Regex("\\b([1-9]|[12][0-9]|3[01])\\b").findAll(dateStr).mapNotNull { it.value.toIntOrNull() }.toList()
            val checkOutDay = if (daysInDate.size >= 2) daysInDate[1] else if (daysInDate.isNotEmpty()) daysInDate[0] else currentDay

            val isPM = timeStr.contains("PM", ignoreCase = true)
            val rawHour = Regex("\\d+").find(timeStr)?.value?.toIntOrNull() ?: 12
            val checkOutHour24 = when {
                isPM && rawHour < 12 -> rawHour + 12
                !isPM && rawHour == 12 -> 0
                else -> rawHour
            }

            if (currentYear < yearFound) return false
            if (currentYear > yearFound) return true

            if (currentMonthIdx < monthFound) return false
            if (currentMonthIdx > monthFound) return true

            if (currentDay < checkOutDay) return false
            if (currentDay > checkOutDay) return true

            return currentHour24 > checkOutHour24
        } catch (_: Exception) {
            return false
        }
    }

    fun pushEventToCloud(eventData: Map<String, String>) {
        try {
            val db = FirebaseFirestore.getInstance()
            val rawUnit = eventData["unit"] ?: ""
            val cleanUnit = rawUnit.replace("Unit", "", ignoreCase = true).trim()
            val normalizedUnit = if (cleanUnit.isNotBlank()) "Unit $cleanUnit" else rawUnit
            val dateText = eventData["date"] ?: ""
            val titleText = eventData["title"] ?: "Booking"

            val rawDocId = if (cleanUnit.isNotBlank() && dateText.isNotBlank()) {
                "${cleanUnit}_${dateText}"
            } else {
                eventData["id"] ?: "${cleanUnit}_${titleText}_${System.currentTimeMillis()}"
            }
            val docId = sanitizeDocId(rawDocId)

            val enrichedData = eventData.toMutableMap().apply {
                put("unit", normalizedUnit)
                put("cleanUnit", cleanUnit)
                put("updatedAt", System.currentTimeMillis().toString())
            }

            db.collection("events").document(docId)
                .set(enrichedData, SetOptions.merge())
                .addOnSuccessListener {
                    Log.d(TAG, "Event $docId synced to Cloud Firestore!")
                    val shape = eventData["shape"] ?: ""
                    if (cleanUnit.isNotBlank() && shape.isNotBlank()) {
                        pushUnitStatusToCloud(normalizedUnit, shape)
                    }
                    notifyCloudUpdate()
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Failed to push event: ${e.message}")
                }
        } catch (e: Exception) {
            Log.e(TAG, "Cloud sync error: ${e.message}")
        }
    }

    fun syncAllLocalEventsToCloud(context: Context) {
        try {
            val dbHelper = AzureDatabaseHelper(context.applicationContext)
            val localEvents = dbHelper.getAllEvents()
            for (evt in localEvents) {
                val date = evt["date"]?.toString() ?: ""
                val time = evt["time"]?.toString() ?: "2:00 PM"
                val unit = evt["unit"]?.toString() ?: ""
                if (unit.isNotBlank() && date.isNotBlank() && !isEventDateExpired(date, time)) {
                    val eventMap = mapOf(
                        "title" to (evt["title"]?.toString() ?: "Booking"),
                        "date" to date,
                        "time" to time,
                        "agent" to (evt["agent"]?.toString() ?: "Agent"),
                        "unit" to unit,
                        "shape" to (evt["shape"]?.toString() ?: "Reserved"),
                        "color" to (evt["color"]?.toString() ?: "0")
                    )
                    pushEventToCloud(eventMap)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing local events to cloud: ${e.message}")
        }
    }

    fun syncAllLocalAgentsToCloud(context: Context) {
        try {
            val dbHelper = AzureDatabaseHelper(context.applicationContext)
            val localAgents = dbHelper.getAllAgents()
            for (agent in localAgents) {
                val name = agent["name"] ?: continue
                val email = agent["email"] ?: continue
                val phone = agent["phone"] ?: ""
                val role = agent["position"] ?: agent["role"] ?: "Agent"
                val gender = agent["gender"] ?: "Female"
                if (name.isNotBlank() && email.isNotBlank()) {
                    pushAgentToCloud(name, email, phone, role, gender)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing local agents to cloud: ${e.message}")
        }
    }

    fun deleteEventFromCloud(dateText: String, unitName: String) {
        try {
            val db = FirebaseFirestore.getInstance()
            val cleanUnit = unitName.replace("Unit", "", ignoreCase = true).trim()
            val directDocId = sanitizeDocId("${cleanUnit}_${dateText}")
            db.collection("events").document(directDocId).delete()

            db.collection("events")
                .get()
                .addOnSuccessListener { query ->
                    for (doc in query.documents) {
                        val uText = doc.getString("unit") ?: ""
                        val cleanU = uText.replace("Unit", "", ignoreCase = true).trim()
                        val dText = doc.getString("date") ?: ""
                        val matchesUnit = cleanU.equals(cleanUnit, ignoreCase = true) || uText.contains(cleanUnit, ignoreCase = true)
                        val matchesDate = dText.equals(dateText, ignoreCase = true) ||
                                dText.contains(dateText, ignoreCase = true) ||
                                dateText.contains(dText, ignoreCase = true) ||
                                dateText.isBlank()

                        if (matchesUnit && matchesDate) {
                            doc.reference.delete()
                        }
                    }
                    pushUnitStatusToCloud(unitName, "Available")
                    notifyCloudUpdate()
                }
        } catch (e: Exception) {
            Log.e(TAG, "Cloud delete error: ${e.message}")
        }
    }

    fun pushUnitStatusToCloud(unitName: String, status: String) {
        try {
            val db = FirebaseFirestore.getInstance()
            val cleanUnit = unitName.replace("Unit", "", ignoreCase = true).trim()
            if (cleanUnit.isBlank()) return
            val normalizedUnit = "Unit $cleanUnit"

            val unitData = mapOf(
                "unitNumber" to normalizedUnit,
                "cleanUnitNumber" to cleanUnit,
                "status" to status,
                "updatedAt" to System.currentTimeMillis()
            )
            db.collection("units").document(cleanUnit)
                .set(unitData, SetOptions.merge())
                .addOnSuccessListener {
                    Log.d(TAG, "Unit $normalizedUnit status ->$status synced to units!")
                    notifyCloudUpdate()
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Firestore units write blocked/failed: ${e.message}")
                }
            db.collection("units").document(cleanUnit.uppercase()).set(unitData, SetOptions.merge())
            db.collection("units").document(normalizedUnit).set(unitData, SetOptions.merge())

            db.collection("units_catalog").document(cleanUnit).set(unitData, SetOptions.merge())
            db.collection("units_catalog").document(cleanUnit.uppercase()).set(unitData, SetOptions.merge())
        } catch (e: Exception) {
            Log.e(TAG, "Unit status cloud sync error: ${e.message}")
        }
    }

    fun pushFullUnitToCloud(
        unitNumber: String,
        tower: String,
        description: String,
        status: String,
        owner: String = "Owner",
        rate: Double = 2500.0,
        imageUri: String = ""
    ) {
        try {
            val db = FirebaseFirestore.getInstance()
            val cleanUnit = unitNumber.replace("Unit", "", ignoreCase = true).trim()
            if (cleanUnit.isBlank()) return
            val normalizedUnit = "Unit $cleanUnit"

            val unitData = mapOf(
                "unitNumber" to normalizedUnit,
                "cleanUnitNumber" to cleanUnit,
                "tower" to tower.ifBlank { "Azure Tower" },
                "description" to description.ifBlank { "1 Bedroom" },
                "status" to status.ifBlank { "Available" },
                "owner" to owner.ifBlank { "Owner" },
                "rate" to rate,
                "image_uri" to imageUri,
                "updatedAt" to System.currentTimeMillis()
            )

            db.collection("units_catalog").document(cleanUnit).set(unitData, SetOptions.merge())
            db.collection("units_catalog").document(cleanUnit.uppercase()).set(unitData, SetOptions.merge())
            db.collection("units").document(cleanUnit).set(unitData, SetOptions.merge())
            db.collection("units").document(cleanUnit.uppercase()).set(unitData, SetOptions.merge())
            db.collection("deleted_units").document(cleanUnit).delete()
            db.collection("deleted_units").document(cleanUnit.uppercase()).delete()
            db.collection("deleted_units").document(normalizedUnit).delete()

            pushUnitStatusToCloud(normalizedUnit, status)
            notifyCloudUpdate()
        } catch (e: Exception) {
            Log.e(TAG, "Full unit cloud push error: ${e.message}")
        }
    }

    fun deleteUnitFromCloud(context: Context, unitNumber: String) {
        try {
            val db = FirebaseFirestore.getInstance()
            val cleanUnit = unitNumber.replace("Unit", "", ignoreCase = true).trim()
            if (cleanUnit.isBlank()) return

            db.collection("units_catalog").document(cleanUnit).delete()
            db.collection("units_catalog").document(cleanUnit.uppercase()).delete()
            db.collection("units").document(cleanUnit).delete()
            db.collection("units").document(cleanUnit.uppercase()).delete()
            db.collection("units").document("Unit $cleanUnit").delete()

            val deletedPrefs = context.getSharedPreferences("azure_calendar_deleted_units", Context.MODE_PRIVATE)
            val currentSet = deletedPrefs.getStringSet("deleted_unit_numbers", emptySet()).orEmpty().toMutableSet()
            currentSet.add(cleanUnit)
            currentSet.add("Unit $cleanUnit")
            deletedPrefs.edit().putStringSet("deleted_unit_numbers", currentSet).apply()

            db.collection("deleted_units").document(cleanUnit).set(mapOf("cleanUnitNumber" to cleanUnit))
            notifyCloudUpdate()
        } catch (e: Exception) {
            Log.e(TAG, "Delete unit cloud error: ${e.message}")
        }
    }

    fun syncAllLocalUnitsToCloud(context: Context) {
        try {
            val dbHelper = AzureDatabaseHelper(context.applicationContext)
            val deletedPrefs = context.getSharedPreferences("azure_calendar_deleted_units", Context.MODE_PRIVATE)
            val deletedSet = deletedPrefs.getStringSet("deleted_unit_numbers", emptySet()).orEmpty()
            val defaultUnits = listOf(
                mapOf("unitNumber" to "Unit 12A", "tower" to "Tower 1", "status" to "Available", "owner" to "Owner A", "rate" to 2500.0, "image_uri" to "", "description" to "1 Bedroom"),
                mapOf("unitNumber" to "Unit 15B", "tower" to "Azure Tower", "status" to "Available", "owner" to "Owner B", "rate" to 2500.0, "image_uri" to "", "description" to "1 Bedroom"),
                mapOf("unitNumber" to "Unit 08C", "tower" to "Azure Tower", "status" to "Available", "owner" to "Owner C", "rate" to 2500.0, "image_uri" to "", "description" to "1 Bedroom"),
                mapOf("unitNumber" to "Unit 06D", "tower" to "Azure Tower", "status" to "Available", "owner" to "Owner D", "rate" to 2500.0, "image_uri" to "", "description" to "1 Bedroom")
            )
            val allUnits = (dbHelper.getAllUnits() + defaultUnits)
                .distinctBy { (it["unitNumber"]?.toString() ?: "").replace("Unit", "", ignoreCase = true).trim() }
                .filter { u ->
                    val num = u["unitNumber"]?.toString() ?: ""
                    deletedSet.none { isSameUnit(it, num) }
                }

            val activeEvents = dbHelper.getAllEvents()
            for (u in allUnits) {
                val uNum = u["unitNumber"]?.toString() ?: continue
                val uTow = u["tower"]?.toString() ?: "Azure Tower"
                val uOwn = u["owner"]?.toString() ?: "Owner"
                val uRate = (u["rate"] as? Number)?.toDouble() ?: 2500.0
                val uImg = u["image_uri"]?.toString() ?: ""
                val uDesc = u["description"]?.toString() ?: "1 Bedroom"

                val matchingEvent = activeEvents.find { evt ->
                    val evtUnit = evt["unit"] ?: ""
                    isSameUnit(evtUnit, uNum)
                }
                val activeStatus = matchingEvent?.get("shape")?.ifBlank { null }
                val uStat = activeStatus ?: (u["status"]?.toString() ?: "Available")

                pushFullUnitToCloud(
                    unitNumber = uNum,
                    tower = uTow,
                    description = uDesc,
                    status = uStat,
                    owner = uOwn,
                    rate = uRate,
                    imageUri = uImg
                )
            }

            syncAllLocalEventsToCloud(context)
            syncAllLocalAgentsToCloud(context)
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing all local units: ${e.message}")
        }
    }

    fun pushAgentToCloud(name: String, email: String, phone: String, role: String, gender: String) {
        try {
            val db = FirebaseFirestore.getInstance()
            val agentData = mapOf(
                "name" to name,
                "email" to email,
                "phone" to phone,
                "role" to role,
                "gender" to gender,
                "updatedAt" to System.currentTimeMillis()
            )
            val docKey = sanitizeDocId(email.ifBlank { name }.lowercase())
            db.collection("agents").document(docKey).set(agentData, SetOptions.merge())
        } catch (e: Exception) {
            Log.e(TAG, "Agent push error: ${e.message}")
        }
    }

    fun deleteAgentFromCloud(email: String) {
        try {
            if (email.isBlank()) return
            val db = FirebaseFirestore.getInstance()
            val cleanEmail = email.trim().lowercase()
            db.collection("agents").get().addOnSuccessListener { query ->
                for (doc in query.documents) {
                    val docEmail = doc.getString("email") ?: ""
                    if (docEmail.trim().equals(cleanEmail, ignoreCase = true) || doc.id.equals(cleanEmail, ignoreCase = true)) {
                        doc.reference.delete()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Delete agent cloud error: ${e.message}")
        }
    }

    fun startRealtimeCloudListener(context: Context, forceRestart: Boolean = false, onSyncTriggered: () -> Unit = {}) {
        if (isListenerActive && !forceRestart) {
            Log.d(TAG, "Cloud listener is already active. Skipping duplicate setup.")
            return
        }
        stopCloudListener()
        isListenerActive = true
        try {
            val appContext = context.applicationContext
            val dbHelper = AzureDatabaseHelper(appContext)
            val db = FirebaseFirestore.getInstance()

            try {
                db.enableNetwork()
            } catch (_: Exception) {}

            val authUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
            val userLabel = authUser?.email ?: authUser?.uid ?: "Anonymous"

            syncAllLocalEventsToCloud(appContext)
            syncAllLocalAgentsToCloud(appContext)

            db.collection("_connection_test").document("test_probe")
                .set(mapOf("timestamp" to System.currentTimeMillis(), "user" to userLabel))
                .addOnSuccessListener {
                    Log.d(TAG, "FIRESTORE SYNC CONNECTED OK! User: $userLabel")
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        Toast.makeText(appContext, "Cloud Sync Active ($userLabel)", Toast.LENGTH_SHORT).show()
                    }
                }
                .addOnFailureListener { err: Exception ->
                    Log.e(TAG, "FIRESTORE CONNECTION BLOCKED: ${err.message}")
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        Toast.makeText(appContext, "Cloud Sync Error: ${err.message}", Toast.LENGTH_LONG).show()
                    }
                }

            // 1. Real-Time Events Listener
            eventsListener?.remove()
            eventsListener = db.collection("events")
                .addSnapshotListener { snapshots, e ->
                    if (e != null || snapshots == null) {
                        Log.e(TAG, "Events listener error: ${e?.message}")
                        try { db.enableNetwork() } catch (_: Exception) {}
                        return@addSnapshotListener
                    }
                    try {
                        for (change in snapshots.documentChanges) {
                            if (change.type == DocumentChange.Type.REMOVED) {
                                val removedId = change.document.id
                                val removedDate = change.document.getString("date") ?: ""
                                val removedUnit = change.document.getString("unit") ?: ""
                                if (removedId.isNotBlank()) {
                                    dbHelper.deleteEventByIdOrTitle(removedId)
                                }
                                if (removedDate.isNotBlank() && removedUnit.isNotBlank()) {
                                    dbHelper.deleteEventByDateAndUnit(removedDate, removedUnit, syncToCloud = false)
                                }
                            }
                        }

                        if (snapshots.documents.isEmpty()) {
                            val localCheck = dbHelper.getAllEvents()
                            if (localCheck.isNotEmpty()) {
                                syncAllLocalEventsToCloud(appContext)
                            }
                        } else {
                            dbHelper.clearAllEvents()
                            for (doc in snapshots.documents) {
                                val title = doc.getString("title") ?: "Booking"
                                val date = doc.getString("date") ?: ""
                                val time = doc.getString("time") ?: "2:00 PM"
                                val agent = doc.getString("agent") ?: "Agent"
                                val rawUnit = doc.getString("unit") ?: ""
                                val cleanUnit = rawUnit.replace("Unit", "", ignoreCase = true).trim()
                                val normalizedUnit = "Unit $cleanUnit"
                                val shape = doc.getString("shape") ?: "Available"

                                val rawColor = doc.get("color")
                                val colorLong = when (rawColor) {
                                    is Number -> rawColor.toLong()
                                    is String -> rawColor.toLongOrNull() ?: 0L
                                    else -> 0L
                                }

                                if (cleanUnit.isNotBlank() && date.isNotBlank()) {
                                    if (!isEventDateExpired(date, time)) {
                                        dbHelper.insertEvent(
                                            title = title,
                                            date = date,
                                            time = time,
                                            agent = agent,
                                            unit = normalizedUnit,
                                            shape = shape,
                                            color = colorLong,
                                            syncToCloud = false
                                        )
                                        dbHelper.updateUnitStatus(normalizedUnit, shape, syncToCloud = false)
                                        dbHelper.updateUnitStatus(cleanUnit, shape, syncToCloud = false)
                                    }
                                }
                            }
                        }
                    } catch (err: Exception) {
                        Log.e(TAG, "Events cloud listener error: ${err.message}")
                    }
                    notifyCloudUpdate()
                    onSyncTriggered()
                }

            // 2. Units Status Listener
            unitsListener?.remove()
            unitsListener = db.collection("units")
                .addSnapshotListener { snapshots, e ->
                    if (e != null || snapshots == null) {
                        Log.e(TAG, "Units listener error: ${e?.message}")
                        try { db.enableNetwork() } catch (_: Exception) {}
                        return@addSnapshotListener
                    }
                    for (doc in snapshots.documents) {
                        val uNumRaw = doc.getString("unitNumber") ?: doc.getString("cleanUnitNumber") ?: doc.id
                        val cleanNum = uNumRaw.replace("Unit", "", ignoreCase = true).trim()
                        val uNum = "Unit $cleanNum"
                        val uStat = doc.getString("status") ?: "Available"

                        if (cleanNum.isNotBlank()) {
                            dbHelper.updateUnitStatus(uNum, uStat, syncToCloud = false)
                            dbHelper.updateUnitStatus(cleanNum, uStat, syncToCloud = false)
                        }
                    }
                    notifyCloudUpdate()
                    onSyncTriggered()
                }

            // 3. Units Catalog Listener
            unitsCatalogListener?.remove()
            unitsCatalogListener = db.collection("units_catalog")
                .addSnapshotListener { snapshots, e ->
                    if (e != null || snapshots == null) {
                        Log.e(TAG, "Units catalog listener error: ${e?.message}")
                        try { db.enableNetwork() } catch (_: Exception) {}
                        return@addSnapshotListener
                    }

                    val cloudCleanNumbers = mutableSetOf<String>()
                    val deletedPrefs = appContext.getSharedPreferences("azure_calendar_deleted_units", Context.MODE_PRIVATE)
                    val currentDeletedSet = deletedPrefs.getStringSet("deleted_unit_numbers", emptySet()).orEmpty().toMutableSet()
                    var deletedSetChanged = false

                    for (doc in snapshots.documents) {
                        val uNumRaw = doc.getString("unitNumber") ?: doc.getString("cleanUnitNumber") ?: doc.id
                        val cleanNum = uNumRaw.replace("Unit", "", ignoreCase = true).trim()
                        val uNum = "Unit $cleanNum"
                        val uTow = doc.getString("tower") ?: "Azure Tower"
                        val uStat = doc.getString("status") ?: "Available"
                        val uOwn = doc.getString("owner") ?: "Owner"
                        val uRate = doc.getDouble("rate") ?: 2500.0
                        val uImg = doc.getString("image_uri") ?: ""
                        val uDesc = doc.getString("description") ?: "1 Bedroom"

                        if (cleanNum.isNotBlank()) {
                            val upperClean = cleanNum.uppercase()
                            cloudCleanNumbers.add(upperClean)
                            cloudCleanNumbers.add(cleanNum)
                            dbHelper.insertUnit(uNum, uTow, uStat, uOwn, uRate, uImg, uDesc, syncToCloud = false)
                            dbHelper.updateUnitStatus(uNum, uStat, syncToCloud = false)
                            dbHelper.updateUnitStatus(cleanNum, uStat, syncToCloud = false)

                            if (currentDeletedSet.removeAll { isSameUnit(it, cleanNum) || isSameUnit(it, uNum) }) {
                                deletedSetChanged = true
                            }
                        }
                    }

                    if (deletedSetChanged) {
                        deletedPrefs.edit().putStringSet("deleted_unit_numbers", currentDeletedSet).apply()
                    }

                    val activeEvents = dbHelper.getAllEvents()
                    for (evt in activeEvents) {
                        val evtUnit = evt["unit"] ?: ""
                        val evtShape = evt["shape"] ?: ""
                        val evtDate = evt["date"] ?: ""
                        if (evtUnit.isNotBlank() && evtShape.isNotBlank() && !isEventDateExpired(evtDate)) {
                            dbHelper.updateUnitStatus(evtUnit, evtShape, syncToCloud = false)
                        }
                    }

                    if (cloudCleanNumbers.isNotEmpty()) {
                        val localUnits = dbHelper.getAllUnits()
                        val defaultCleanNums = setOf("12A", "15B", "08C", "06D")
                        for (localUnit in localUnits) {
                            val localNumRaw = localUnit["unitNumber"]?.toString() ?: ""
                            val cleanLocal = localNumRaw.replace("Unit", "", ignoreCase = true).trim().uppercase()
                            if (cleanLocal.isNotBlank() && !cloudCleanNumbers.contains(cleanLocal) && !cloudCleanNumbers.contains(cleanLocal.lowercase()) && !defaultCleanNums.contains(cleanLocal)) {
                                dbHelper.deleteUnit(cleanLocal)
                                dbHelper.deleteUnit("Unit $cleanLocal")
                            }
                        }
                    }

                    notifyCloudUpdate()
                    onSyncTriggered()
                }

            // 4. Deleted Units Listener
            deletedUnitsListener?.remove()
            deletedUnitsListener = db.collection("deleted_units")
                .addSnapshotListener { snapshots, e ->
                    if (e != null) return@addSnapshotListener
                    if (snapshots != null && !snapshots.isEmpty) {
                        val deletedPrefs = appContext.getSharedPreferences("azure_calendar_deleted_units", Context.MODE_PRIVATE)
                        val currentSet = deletedPrefs.getStringSet("deleted_unit_numbers", emptySet()).orEmpty().toMutableSet()
                        for (doc in snapshots.documents) {
                            val cleanUnit = doc.getString("cleanUnitNumber") ?: doc.id
                            val cleanNum = cleanUnit.replace("Unit", "", ignoreCase = true).trim()
                            if (cleanNum.isNotBlank()) {
                                currentSet.add(cleanNum)
                                currentSet.add("Unit $cleanNum")
                                dbHelper.deleteUnit(cleanNum)
                                dbHelper.deleteUnit("Unit $cleanNum")
                            }
                        }
                        deletedPrefs.edit().putStringSet("deleted_unit_numbers", currentSet).apply()
                        notifyCloudUpdate()
                        onSyncTriggered()
                    }
                }

            // 5. Agents Roster Listener
            agentsListener?.remove()
            agentsListener = db.collection("agents")
                .addSnapshotListener { snapshots, e ->
                    if (e != null) return@addSnapshotListener
                    if (snapshots != null && !snapshots.isEmpty) {
                        for (doc in snapshots.documents) {
                            val aName = doc.getString("name") ?: ""
                            val aEmail = doc.getString("email") ?: ""
                            val aPhone = doc.getString("phone") ?: ""
                            val aRole = doc.getString("role") ?: "Property Agent"
                            val aGender = doc.getString("gender") ?: "Female"

                            if (aName.isNotBlank() && aEmail.isNotBlank()) {
                                dbHelper.insertAgent(aName, aEmail, aPhone, aRole, aGender, syncToCloud = false)
                            }
                        }
                        notifyCloudUpdate()
                        onSyncTriggered()
                    }
                }

            // 6. Single Admin Profile Listener
            adminProfileListener?.remove()
            adminProfileListener = db.collection("admin_profile").document("single_admin")
                .addSnapshotListener { snapshot, e ->
                    if (e != null || snapshot == null || !snapshot.exists()) return@addSnapshotListener
                    try {
                        val aName = snapshot.getString("name") ?: "Admin"
                        val aEmail = snapshot.getString("email") ?: "novie1413@gmail.com"
                        val aPhone = snapshot.getString("phone") ?: "+63 9926319519"
                        val aRole = snapshot.getString("role") ?: "Property Owner & Admin"
                        val aGender = snapshot.getString("gender") ?: "Female"

                        val infoPrefs = appContext.getSharedPreferences("azure_calendar_personal_info", Context.MODE_PRIVATE)
                        infoPrefs.edit()
                            .putString("name_Admin", aName)
                            .putString("email_Admin", aEmail)
                            .putString("phone_Admin", aPhone)
                            .putString("role_Admin", aRole)
                            .putString("gender_Admin", aGender)
                            .apply()

                        val sessionMgr = UserSessionManager(appContext)
                        if (sessionMgr.getRole() == "Owner" || sessionMgr.getUsername().contains("Admin", ignoreCase = true)) {
                            sessionMgr.saveSession(aName, "Owner")
                        }

                        notifyCloudUpdate()
                        onSyncTriggered()
                    } catch (err: Exception) {
                        Log.e(TAG, "Error syncing admin profile: ${err.message}")
                    }
                }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start Firestore cloud listener: ${e.message}")
        }
    }

    fun pushAdminProfileToCloud(
        fullName: String,
        email: String,
        phone: String,
        role: String = "Property Owner & Admin",
        gender: String = "Female"
    ) {
        try {
            val db = FirebaseFirestore.getInstance()
            val adminData = mapOf(
                "name" to fullName,
                "email" to email,
                "phone" to phone,
                "role" to role,
                "gender" to gender,
                "updatedAt" to System.currentTimeMillis()
            )
            db.collection("admin_profile").document("single_admin").set(adminData)
        } catch (e: Exception) {
            Log.e(TAG, "Admin profile push error: ${e.message}")
        }
    }

    fun stopCloudListener() {
        isListenerActive = false
        eventsListener?.remove()
        unitsListener?.remove()
        unitsCatalogListener?.remove()
        deletedUnitsListener?.remove()
        agentsListener?.remove()
        adminProfileListener?.remove()
        eventsListener = null
        unitsListener = null
        unitsCatalogListener = null
        deletedUnitsListener = null
        agentsListener = null
        adminProfileListener = null
    }
}
