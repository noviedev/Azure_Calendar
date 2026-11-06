package com.example.azurecalendar

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.Log
import android.widget.Toast
import com.google.firebase.firestore.*
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

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
    private var deletedEventsListener: ListenerRegistration? = null
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
        if (unitA.isBlank() || unitB.isBlank()) return false
        val isGeneral1 = unitA.equals("Event", ignoreCase = true) || unitA.contains("Meeting", ignoreCase = true) || unitA.contains("Owner Event", ignoreCase = true)
        val isGeneral2 = unitB.equals("Event", ignoreCase = true) || unitB.contains("Meeting", ignoreCase = true) || unitB.contains("Owner Event", ignoreCase = true)
        if (isGeneral1 || isGeneral2) return false

        val cleanA = unitA.replace("Unit", "", ignoreCase = true).trim()
        val cleanB = unitB.replace("Unit", "", ignoreCase = true).trim()
        if (cleanA.isBlank() || cleanB.isBlank()) return false
        return cleanA.equals(cleanB, ignoreCase = true)
    }

    /**
     * Converts a local device Uri/file path into a compressed Base64 data URI
     * so it can be stored directly inside Firestore and synced to all Agent phones.
     */
    fun prepareImageForCloud(context: Context?, rawImageUri: String): String {
        if (rawImageUri.isBlank()) return ""
        // Already a cloud URL or Base64 data URI
        if (rawImageUri.startsWith("http", ignoreCase = true) ||
            rawImageUri.startsWith("data:image", ignoreCase = true)
        ) {
            return rawImageUri
        }
        if (context == null) return rawImageUri

        return try {
            val inputStream = when {
                rawImageUri.startsWith("content://") || rawImageUri.startsWith("file://") -> {
                    context.contentResolver.openInputStream(Uri.parse(rawImageUri))
                }
                else -> {
                    val file = File(rawImageUri)
                    if (file.exists()) FileInputStream(file) else null
                }
            } ?: return rawImageUri

            val originalBitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()
            if (originalBitmap == null) return rawImageUri

            // Scale down to max 600px on longest side so it easily fits inside Firestore's 1MB doc limit
            val maxDim = 600
            val width = originalBitmap.width
            val height = originalBitmap.height
            val scale = if (width > height && width > maxDim) {
                maxDim.toFloat() / width
            } else if (height >= width && height > maxDim) {
                maxDim.toFloat() / height
            } else {
                1f
            }

            val resizedBitmap = if (scale < 1f) {
                Bitmap.createScaledBitmap(
                    originalBitmap,
                    (width * scale).toInt().coerceAtLeast(1),
                    (height * scale).toInt().coerceAtLeast(1),
                    true
                )
            } else {
                originalBitmap
            }

            val outputStream = ByteArrayOutputStream()
            resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 70, outputStream)
            val byteArray = outputStream.toByteArray()
            val base64Str = Base64.encodeToString(byteArray, Base64.NO_WRAP)
            "data:image/jpeg;base64,$base64Str"
        } catch (e: Exception) {
            Log.e(TAG, "Failed to encode image for cloud: ${e.message}")
            rawImageUri
        }
    }

    /**
     * Reconstructs a synced Base64 image from Firestore into a local cached .jpg file
     * on the Agent's device so Coil/BitmapFactory can display it seamlessly.
     */
    private fun resolveCloudImageToLocalFile(context: Context, cleanUnit: String, cloudImageUri: String): String {
        if (cloudImageUri.isBlank()) return ""
        if (!cloudImageUri.startsWith("data:image", ignoreCase = true)) {
            return cloudImageUri
        }
        return try {
            val base64Part = cloudImageUri.substringAfter("base64,", "")
            if (base64Part.isBlank()) return cloudImageUri

            val bytes = Base64.decode(base64Part, Base64.DEFAULT)
            val cacheDir = File(context.filesDir, "synced_unit_images")
            if (!cacheDir.exists()) cacheDir.mkdirs()

            // Use hashCode in filename so Coil automatically refreshes when the Owner changes the photo
            val safeUnit = sanitizeDocId(cleanUnit)
            val imageFile = File(cacheDir, "unit_${safeUnit}_${base64Part.hashCode()}.jpg")
            if (!imageFile.exists()) {
                FileOutputStream(imageFile).use { fos ->
                    fos.write(bytes)
                    fos.flush()
                }
            }
            Uri.fromFile(imageFile).toString()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decode cloud image to local file: ${e.message}")
            cloudImageUri
        }
    }

    fun isEventDateExpired(dateStr: String, timeStr: String = "12:00 PM"): Boolean {
        try {
            if (dateStr.isBlank()) return false
            val realTimeCal = java.util.Calendar.getInstance()

            val monthMap = mapOf(
                "jan" to 1, "feb" to 2, "mar" to 3, "apr" to 4, "may" to 5, "jun" to 6,
                "jul" to 7, "aug" to 8, "sep" to 9, "sept" to 9, "oct" to 10, "nov" to 11, "dec" to 12,
                "january" to 1, "february" to 2, "march" to 3, "april" to 4, "june" to 6,
                "july" to 7, "august" to 8, "september" to 9, "october" to 10, "november" to 11, "december" to 12
            )

            val isRange = dateStr.contains("-") || dateStr.contains("to", ignoreCase = true)
            val checkOutPart = if (isRange) {
                val parts = dateStr.split("-", "to", ignoreCase = true)
                parts.getOrNull(1)?.trim() ?: dateStr
            } else {
                dateStr
            }

            val yearFound = Regex("\\b20\\d{2}\\b").find(checkOutPart)?.value?.toIntOrNull()
                ?: Regex("\\b20\\d{2}\\b").find(dateStr)?.value?.toIntOrNull()
                ?: realTimeCal.get(java.util.Calendar.YEAR)

            val monthFound = monthMap.entries.find { checkOutPart.contains(it.key, ignoreCase = true) }?.value
                ?: monthMap.entries.find { dateStr.contains(it.key, ignoreCase = true) }?.value
                ?: (realTimeCal.get(java.util.Calendar.MONTH) + 1)

            val daysInDate = Regex("\\b([1-9]|[12][0-9]|3[01])\\b").findAll(checkOutPart).mapNotNull { it.value.toIntOrNull() }.toList()
            val checkOutDay = if (daysInDate.isNotEmpty()) daysInDate[0] else {
                val allDays = Regex("\\b([1-9]|[12][0-9]|3[01])\\b").findAll(dateStr).mapNotNull { it.value.toIntOrNull() }.toList()
                allDays.lastOrNull() ?: realTimeCal.get(java.util.Calendar.DAY_OF_MONTH)
            }

            val isPM = timeStr.contains("PM", ignoreCase = true)
            val timeNumbers = Regex("\\d+").findAll(timeStr).mapNotNull { it.value.toIntOrNull() }.toList()
            val rawHour = timeNumbers.getOrNull(0) ?: 12
            val rawMinute = timeNumbers.getOrNull(1) ?: 0

            val checkOutHour24 = when {
                isPM && rawHour < 12 -> rawHour + 12
                !isPM && rawHour == 12 -> 0
                else -> rawHour
            }

            val checkOutCal = java.util.Calendar.getInstance().apply {
                clear()
                set(java.util.Calendar.YEAR, yearFound)
                set(java.util.Calendar.MONTH, monthFound - 1)
                set(java.util.Calendar.DAY_OF_MONTH, checkOutDay)
                set(java.util.Calendar.HOUR_OF_DAY, checkOutHour24)
                set(java.util.Calendar.MINUTE, rawMinute)
            }

            return !realTimeCal.before(checkOutCal)
        } catch (_: Exception) {
            return false
        }
    }

    fun isDateInPast(dateStr: String): Boolean {
        try {
            if (dateStr.isBlank()) return false
            val monthMap = mapOf(
                "jan" to 1, "feb" to 2, "mar" to 3, "apr" to 4, "may" to 5, "jun" to 6,
                "jul" to 7, "aug" to 8, "sep" to 9, "sept" to 9, "oct" to 10, "nov" to 11, "dec" to 12,
                "january" to 1, "february" to 2, "march" to 3, "april" to 4, "june" to 6,
                "july" to 7, "august" to 8, "september" to 9, "october" to 10, "november" to 11, "december" to 12
            )

            val realTimeCal = java.util.Calendar.getInstance().apply {
                set(java.util.Calendar.HOUR_OF_DAY, 0)
                set(java.util.Calendar.MINUTE, 0)
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }

            val yearFound = Regex("\\b20\\d{2}\\b").find(dateStr)?.value?.toIntOrNull()
                ?: realTimeCal.get(java.util.Calendar.YEAR)

            val monthFound = monthMap.entries.find { dateStr.contains(it.key, ignoreCase = true) }?.value
                ?: (realTimeCal.get(java.util.Calendar.MONTH) + 1)

            val dayFound = Regex("\\b([1-9]|[12][0-9]|3[01])\\b").find(dateStr)?.value?.toIntOrNull()
                ?: realTimeCal.get(java.util.Calendar.DAY_OF_MONTH)

            val checkCal = java.util.Calendar.getInstance().apply {
                clear()
                set(java.util.Calendar.YEAR, yearFound)
                set(java.util.Calendar.MONTH, monthFound - 1)
                set(java.util.Calendar.DAY_OF_MONTH, dayFound)
            }

            return checkCal.before(realTimeCal)
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
                    val isGeneralEvent = cleanUnit.equals("Event", ignoreCase = true) || cleanUnit.contains("Meeting", ignoreCase = true) || shape.contains("Owner Event", ignoreCase = true)
                    if (cleanUnit.isNotBlank() && shape.isNotBlank() && !isGeneralEvent) {
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
                if (unit.isNotBlank() && date.isNotBlank()) {
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
            if (cleanUnit.isBlank()) return

            db.collection("events")
                .get()
                .addOnSuccessListener { query ->
                    for (doc in query.documents) {
                        val uText = doc.getString("unit") ?: ""
                        val cleanU = uText.replace("Unit", "", ignoreCase = true).trim()
                        val dText = doc.getString("date") ?: ""
                        val matchesUnit = cleanU.equals(cleanUnit, ignoreCase = true) || uText.contains(cleanUnit, ignoreCase = true) || doc.id.contains(cleanUnit, ignoreCase = true)
                        val matchesDate = dateText.isBlank() || dText.equals(dateText, ignoreCase = true) ||
                                dText.contains(dateText, ignoreCase = true) ||
                                dateText.contains(dText, ignoreCase = true)

                        if (matchesUnit && matchesDate) {
                            doc.reference.delete()
                        }
                    }
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
            val isGeneralEvent = cleanUnit.equals("Event", ignoreCase = true) || cleanUnit.contains("Meeting", ignoreCase = true) || status.contains("Owner Event", ignoreCase = true)
            if (cleanUnit.isBlank() || isGeneralEvent) return
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
                    Log.d(TAG, "Unit $normalizedUnit status -> $status synced to units!")
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
        imageUri: String = "",
        context: Context? = null
    ) {
        try {
            val db = FirebaseFirestore.getInstance()
            val cleanUnit = unitNumber.replace("Unit", "", ignoreCase = true).trim()
            if (cleanUnit.isBlank()) return
            val normalizedUnit = "Unit $cleanUnit"

            // Convert local device URI to portable Base64 string if needed
            val cloudReadyImage = prepareImageForCloud(context, imageUri)

            val unitData = mutableMapOf<String, Any>(
                "unitNumber" to normalizedUnit,
                "cleanUnitNumber" to cleanUnit,
                "tower" to tower.ifBlank { "Azure Tower" },
                "description" to description.ifBlank { "1 Bedroom" },
                "status" to status.ifBlank { "Available" },
                "owner" to owner.ifBlank { "Owner" },
                "rate" to rate,
                "updatedAt" to System.currentTimeMillis()
            )

            // Only overwrite image_uri in Firestore if we actually have a valid cloud/base64 image
            // This prevents an Agent device with empty "" from erasing the Owner's uploaded picture!
            if (cloudReadyImage.isNotBlank() &&
                (cloudReadyImage.startsWith("data:image", ignoreCase = true) ||
                        cloudReadyImage.startsWith("http", ignoreCase = true))
            ) {
                unitData["image_uri"] = cloudReadyImage
            }

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
            val appContext = context.applicationContext
            val dbHelper = AzureDatabaseHelper(appContext)
            val deletedPrefs = appContext.getSharedPreferences("azure_calendar_deleted_units", Context.MODE_PRIVATE)
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

            for (u in allUnits) {
                val uNum = u["unitNumber"]?.toString() ?: continue
                val uTow = u["tower"]?.toString() ?: "Azure Tower"
                val uOwn = u["owner"]?.toString() ?: "Owner"
                val uRate = (u["rate"] as? Number)?.toDouble() ?: 2500.0
                val uImg = u["image_uri"]?.toString() ?: ""
                val uDesc = u["description"]?.toString() ?: "1 Bedroom"

                val uStat = u["status"]?.toString()?.ifBlank { "Available" } ?: "Available"

                pushFullUnitToCloud(
                    unitNumber = uNum,
                    tower = uTow,
                    description = uDesc,
                    status = uStat,
                    owner = uOwn,
                    rate = uRate,
                    imageUri = uImg,
                    context = appContext
                )
            }

            syncAllLocalEventsToCloud(appContext)
            syncAllLocalAgentsToCloud(appContext)
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
            db.collection("agents").document(docKey)
                .set(agentData, SetOptions.merge())
                .addOnSuccessListener {
                    Log.d(TAG, "Agent $name ($email) synced to Cloud Firestore!")
                    notifyCloudUpdate()
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Agent cloud push failed: ${e.message}")
                }
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

            // 3. Units Catalog Listener (Reconstructs Cloud Base64 Images to Local Files for Agent!)
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

                    val existingLocalUnits = dbHelper.getAllUnits()

                    for (doc in snapshots.documents) {
                        val uNumRaw = doc.getString("unitNumber") ?: doc.getString("cleanUnitNumber") ?: doc.id
                        val cleanNum = uNumRaw.replace("Unit", "", ignoreCase = true).trim()
                        val uNum = "Unit $cleanNum"
                        val uTow = doc.getString("tower") ?: "Azure Tower"
                        val uStat = doc.getString("status") ?: "Available"
                        val uOwn = doc.getString("owner") ?: "Owner"
                        val uRate = doc.getDouble("rate") ?: 2500.0
                        val cloudImg = doc.getString("image_uri") ?: ""
                        val uDesc = doc.getString("description") ?: "1 Bedroom"

                        if (cleanNum.isNotBlank()) {
                            val upperClean = cleanNum.uppercase()
                            cloudCleanNumbers.add(upperClean)
                            cloudCleanNumbers.add(cleanNum)

                            // Convert Base64 cloud image to a local file URI on the Agent's device
                            val resolvedImg = if (cloudImg.isNotBlank()) {
                                resolveCloudImageToLocalFile(appContext, cleanNum, cloudImg)
                            } else {
                                // Preserve existing local image if cloud doc didn't have one yet
                                existingLocalUnits.find {
                                    isSameUnit(it["unitNumber"]?.toString() ?: "", cleanNum)
                                }?.get("image_uri")?.toString() ?: ""
                            }

                            dbHelper.insertUnit(uNum, uTow, uStat, uOwn, uRate, resolvedImg, uDesc, syncToCloud = false)
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

            // 7. Deleted Owner Events Listener
            deletedEventsListener?.remove()
            deletedEventsListener = db.collection("deleted_events")
                .addSnapshotListener { snapshots, e ->
                    if (e != null || snapshots == null) return@addSnapshotListener
                    try {
                        val deletedPrefs = appContext.getSharedPreferences("azure_calendar_deleted_events", Context.MODE_PRIVATE)
                        val currentSet = deletedPrefs.getStringSet("deleted_ids", emptySet()).orEmpty().toMutableSet()
                        var changed = false

                        for (doc in snapshots.documents) {
                            val delId = doc.getString("eventId") ?: doc.id
                            val delTitle = doc.getString("title") ?: ""
                            if (delId.isNotBlank() && currentSet.add(delId)) changed = true
                            if (delTitle.isNotBlank() && currentSet.add(delTitle)) changed = true

                            dbHelper.deleteEventByIdOrTitle(delId)
                            if (delTitle.isNotBlank()) dbHelper.deleteEventByIdOrTitle(delTitle)
                        }

                        if (changed) {
                            deletedPrefs.edit().putStringSet("deleted_ids", currentSet).apply()
                        }
                        notifyCloudUpdate()
                        onSyncTriggered()
                    } catch (err: Exception) {
                        Log.e(TAG, "Error syncing deleted events: ${err.message}")
                    }
                }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start Firestore cloud listener: ${e.message}")
        }
    }

    fun pushOwnerEventToCloud(
        id: String,
        title: String,
        description: String,
        date: String,
        time: String,
        createdBy: String = "Owner",
        targetAgent: String = "All Agents",
        eventLink: String = ""
    ) {
        try {
            val db = FirebaseFirestore.getInstance()
            val docId = id.ifBlank { "evt_${System.currentTimeMillis()}" }

            val data = mapOf(
                "id" to docId,
                "title" to title,
                "description" to description,
                "date" to date,
                "time" to time,
                "createdBy" to createdBy,
                "targetAgent" to targetAgent,
                "eventLink" to eventLink.ifBlank { "https://azurecalendar.app/event/join?id=$docId" },
                "updatedAt" to System.currentTimeMillis()
            )

            db.collection("owner_events").document(docId).set(data)
                .addOnSuccessListener {
                    Log.d(TAG, "Owner event $docId pushed to Cloud Firestore!")
                    notifyCloudUpdate()
                }
        } catch (e: Exception) {
            Log.e(TAG, "Push owner event error: ${e.message}")
        }
    }

    fun listenToOwnerEvents(onEventsUpdated: (List<OwnerEventItem>) -> Unit): ListenerRegistration {
        val db = FirebaseFirestore.getInstance()

        return db.collection("owner_events")
            .addSnapshotListener { snapshot, e ->
                if (e != null || snapshot == null) return@addSnapshotListener

                val resultList = mutableListOf<OwnerEventItem>()
                for (doc in snapshot.documents) {
                    val id = doc.getString("id") ?: doc.id
                    val title = doc.getString("title") ?: "Owner Event"
                    val description = doc.getString("description") ?: "Event created by Owner"
                    val date = doc.getString("date") ?: "Nov 5, 2026"
                    val time = doc.getString("time") ?: "2:00 PM"
                    val createdBy = doc.getString("createdBy") ?: "Owner"
                    val targetAgent = doc.getString("targetAgent") ?: "All Agents"
                    val eventLink = doc.getString("eventLink") ?: "https://azurecalendar.app/event/join?id=$id"

                    if (title.isNotBlank() && !title.startsWith("Staycation", ignoreCase = true)) {
                        resultList.add(
                            OwnerEventItem(
                                id = id,
                                title = title,
                                description = description,
                                date = date,
                                time = time,
                                createdBy = createdBy,
                                targetAgent = targetAgent,
                                eventLink = eventLink
                            )
                        )
                    }
                }
                onEventsUpdated(resultList)
            }
    }

    fun deleteOwnerEventFromCloud(eventId: String, title: String = "") {
        try {
            val db = FirebaseFirestore.getInstance()
            val cleanTitle = title.lowercase().trim()

            if (eventId.isNotBlank()) {
                db.collection("owner_events").document(eventId).delete()
            }

            db.collection("owner_events")
                .get()
                .addOnSuccessListener { query ->
                    for (doc in query.documents) {
                        val docId = doc.id
                        val docTitle = doc.getString("title")?.lowercase()?.trim() ?: ""
                        if (docId.equals(eventId, ignoreCase = true) || (cleanTitle.isNotBlank() && docTitle.contains(cleanTitle))) {
                            doc.reference.delete()
                        }
                    }
                    notifyCloudUpdate()
                }

            db.collection("events")
                .get()
                .addOnSuccessListener { query ->
                    for (doc in query.documents) {
                        val docId = doc.id
                        val docTitle = doc.getString("title")?.lowercase()?.trim() ?: ""

                        val matchesId = docId.equals(eventId, ignoreCase = true) || docId.contains(eventId, ignoreCase = true)
                        val matchesTitle = cleanTitle.isNotBlank() && docTitle.contains(cleanTitle, ignoreCase = true)

                        if (matchesId || matchesTitle) {
                            doc.reference.delete()
                        }
                    }
                    notifyCloudUpdate()
                }

            val delData = mapOf(
                "eventId" to eventId,
                "title" to title,
                "deletedAt" to System.currentTimeMillis()
            )
            val docName = if (eventId.isNotBlank()) eventId else "del_${System.currentTimeMillis()}"
            db.collection("deleted_events")
                .document(docName)
                .set(delData)
                .addOnSuccessListener {
                    notifyCloudUpdate()
                }
        } catch (e: Exception) {
            Log.e(TAG, "Cloud owner event delete error: ${e.message}")
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

    fun getNormalizedEventId(eventId: String, title: String = ""): String {
        val cleanTitle = title.lowercase().trim().replace(Regex("[^a-z0-9]"), "_")
        if (cleanTitle.isNotBlank()) return "chat_$cleanTitle"
        val cleanId = eventId.lowercase().trim().replace(Regex("[^a-z0-9]"), "_")
        return if (cleanId.isNotBlank()) "chat_$cleanId" else "chat_general_event"
    }

    fun pushChatMessageToCloud(
        eventId: String,
        eventTitle: String = "",
        senderName: String,
        senderRole: String,
        messageText: String,
        timestamp: String
    ) {
        try {
            val db = FirebaseFirestore.getInstance()
            val cleanEventRoomId = getNormalizedEventId(eventId, eventTitle)
            val msgId = "msg_${System.currentTimeMillis()}"

            val msgData = mapOf(
                "id" to msgId,
                "senderName" to senderName,
                "senderRole" to senderRole,
                "messageText" to messageText,
                "timestamp" to timestamp,
                "createdTime" to System.currentTimeMillis()
            )

            db.collection("event_chats")
                .document(cleanEventRoomId)
                .collection("messages")
                .document(msgId)
                .set(msgData)
                .addOnSuccessListener {
                    Log.d(TAG, "Chat message pushed to Cloud Firestore for room $cleanEventRoomId")
                    notifyCloudUpdate()
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Failed to push chat message: ${e.message}")
                }
        } catch (e: Exception) {
            Log.e(TAG, "Chat cloud push error: ${e.message}")
        }
    }

    fun listenToEventChatMessages(
        context: Context,
        eventId: String,
        eventTitle: String = "",
        onMessagesUpdated: (List<GroupChatMessage>) -> Unit
    ): ListenerRegistration {
        val cleanEventRoomId = getNormalizedEventId(eventId, eventTitle)
        val db = FirebaseFirestore.getInstance()
        val dbHelper = AzureDatabaseHelper(context.applicationContext)

        return db.collection("event_chats")
            .document(cleanEventRoomId)
            .collection("messages")
            .orderBy("createdTime", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, e ->
                if (e != null || snapshot == null) return@addSnapshotListener

                val resultList = mutableListOf<GroupChatMessage>()
                for (doc in snapshot.documents) {
                    val id = doc.getString("id") ?: doc.id
                    val senderName = doc.getString("senderName") ?: "User"
                    val senderRole = doc.getString("senderRole") ?: "Agent"
                    val messageText = doc.getString("messageText") ?: ""
                    val timestamp = doc.getString("timestamp") ?: "12:00 PM"
                    val createdTime = doc.getLong("createdTime") ?: System.currentTimeMillis()

                    if (messageText.isNotBlank()) {
                        dbHelper.insertChatMessage(
                            msgId = id,
                            roomId = cleanEventRoomId,
                            senderName = senderName,
                            senderRole = senderRole,
                            text = messageText,
                            timestamp = timestamp,
                            createdTime = createdTime
                        )
                        resultList.add(
                            GroupChatMessage(
                                id = id,
                                senderName = senderName,
                                senderRole = senderRole,
                                messageText = messageText,
                                timestamp = timestamp,
                                isSelf = false
                            )
                        )
                    }
                }
                onMessagesUpdated(resultList)
            }
    }

    fun stopCloudListener() {
        isListenerActive = false
        eventsListener?.remove()
        unitsListener?.remove()
        unitsCatalogListener?.remove()
        deletedUnitsListener?.remove()
        deletedEventsListener?.remove()
        agentsListener?.remove()
        adminProfileListener?.remove()
        eventsListener = null
        unitsListener = null
        unitsCatalogListener = null
        deletedUnitsListener = null
        deletedEventsListener = null
        agentsListener = null
        adminProfileListener = null
    }
}