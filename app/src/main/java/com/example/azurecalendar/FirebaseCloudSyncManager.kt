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

    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    private val webSocketManager = AppWebSocketManager { incomingMsg ->
        Log.d("CloudSync", "WebSocket incoming broadcast: $incomingMsg")
        notifyCloudUpdate()
    }

    fun initWebSocketConnection() {
        try {
            webSocketManager.connect("wss://socketsbay.com/wss/v2/1/demo/")
        } catch (e: Exception) {
            Log.e(TAG, "WebSocket connection init error: ${e.message}")
        }
    }

    fun broadcastWebSocketSync(payload: String) {
        webSocketManager.broadcastUpdate(payload)
    }

    fun notifyCloudUpdate() {
        broadcastWebSocketSync("{\"type\":\"SYNC\", \"timestamp\":${System.currentTimeMillis()}}")
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            globalCloudSyncTrigger.intValue++
        } else {
            mainHandler.post {
                globalCloudSyncTrigger.intValue++
            }
        }
    }

    private var isListenerActive = false
    private var eventsListener: ListenerRegistration? = null
    private var unitsListener: ListenerRegistration? = null
    private var unitsCatalogListener: ListenerRegistration? = null
    private var agentsListener: ListenerRegistration? = null
    private var deletedUnitsListener: ListenerRegistration? = null
    private var deletedEventsListener: ListenerRegistration? = null
    private var deletedAgentsListener: ListenerRegistration? = null
    private var allChatsListener: ListenerRegistration? = null
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
    - Converts a local device Uri/file path into a compressed Base64 data URI

    - so it can be stored directly inside Firestore and synced to all Agent phones.

     */
    fun prepareImageForCloud(context: Context?, rawImageUri: String): String {
        if (rawImageUri.isBlank()) return ""
        val trimmed = rawImageUri.trim()
        if (trimmed.startsWith("http", ignoreCase = true) ||
            trimmed.startsWith("data:image", ignoreCase = true)
        ) {
            return trimmed
        }

        return try {
            val bytes = when {
                trimmed.startsWith("content://", ignoreCase = true) -> {
                    context?.contentResolver?.openInputStream(Uri.parse(trimmed))?.use { it.readBytes() }
                }
                trimmed.startsWith("file://", ignoreCase = true) -> {
                    val filePath = Uri.parse(trimmed).path ?: trimmed.substringAfter("file://")
                    val file = File(filePath)
                    if (file.exists() && file.length() > 0) file.readBytes() else null
                }
                trimmed.startsWith("/") -> {
                    val file = File(trimmed)
                    if (file.exists() && file.length() > 0) file.readBytes() else null
                }
                else -> {
                    if (context != null) {
                        try {
                            context.contentResolver.openInputStream(Uri.parse(trimmed))?.use { it.readBytes() }
                        } catch (_: Exception) {
                            val file = File(trimmed)
                            if (file.exists() && file.length() > 0) file.readBytes() else null
                        }
                    } else null
                }
            }

            if (bytes == null || bytes.isEmpty()) return ""

            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)

            val maxDim = 450
            val width = options.outWidth
            val height = options.outHeight

            var inSampleSize = 1
            if (width > maxDim || height > maxDim) {
                val halfWidth = width / 2
                val halfHeight = height / 2
                while (halfWidth / inSampleSize >= maxDim && halfHeight / inSampleSize >= maxDim) {
                    inSampleSize *= 2
                }
            }

            options.inSampleSize = inSampleSize
            options.inJustDecodeBounds = false

            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return ""

            val currentWidth = bitmap.width
            val currentHeight = bitmap.height
            val scale = if (currentWidth > currentHeight && currentWidth > maxDim) {
                maxDim.toFloat() / currentWidth
            } else if (currentHeight >= currentWidth && currentHeight > maxDim) {
                maxDim.toFloat() / currentHeight
            } else {
                1f
            }

            val resizedBitmap = if (scale < 1f) {
                Bitmap.createScaledBitmap(
                    bitmap,
                    (currentWidth * scale).toInt().coerceAtLeast(1),
                    (currentHeight * scale).toInt().coerceAtLeast(1),
                    true
                ).also {
                    if (it != bitmap) bitmap.recycle()
                }
            } else {
                bitmap
            }

            val outputStream = ByteArrayOutputStream()
            resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 55, outputStream)
            resizedBitmap.recycle()
            val compressedBytes = outputStream.toByteArray()
            val base64Str = Base64.encodeToString(compressedBytes, Base64.NO_WRAP)
            "data:image/jpeg;base64,$base64Str"
        } catch (e: Exception) {
            Log.e(TAG, "Failed to encode image for cloud: ${e.message}")
            ""
        }
    }

    /**
    - Reconstructs a synced Base64 image from Firestore into a local cached .jpg file

    - on the Agent's device so Coil/BitmapFactory can display it seamlessly.

     */
    private fun resolveCloudImageToLocalFile(context: Context, cleanUnit: String, cloudImageUri: String): String {
        if (cloudImageUri.isBlank()) return ""
        if (!cloudImageUri.startsWith("data:image", ignoreCase = true)) {
            return cloudImageUri
        }
        return try {
            val base64Part = cloudImageUri.substringAfter("base64,", "")
                .replace(" ", "")
                .replace("\n", "")
                .replace("\r", "")
                .trim()
            if (base64Part.isBlank()) return cloudImageUri

            val bytes = try {
                Base64.decode(base64Part, Base64.DEFAULT)
            } catch (_: Exception) {
                try {
                    Base64.decode(base64Part, Base64.NO_WRAP or Base64.URL_SAFE)
                } catch (_: Exception) {
                    return cloudImageUri
                }
            }

            val cacheDir = File(context.filesDir, "synced_unit_images")
            if (!cacheDir.exists()) cacheDir.mkdirs()

            val safeUnit = sanitizeDocId(cleanUnit)
            val newHash = base64Part.hashCode()
            val imageFile = File(cacheDir, "unit_${safeUnit}_$newHash.jpg")
            if (!imageFile.exists()) {
                // Delete old cached photos for this unit so newly updated image takes effect immediately
                cacheDir.listFiles()?.filter { it.name.startsWith("unit_${safeUnit}_") && !it.name.endsWith("_$newHash.jpg") }?.forEach {
                    try { it.delete() } catch (_: Exception) {}
                }
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

    private var isNetworkCallbackRegistered = false

    fun registerNetworkCallback(context: Context) {
        if (isNetworkCallbackRegistered) return
        try {
            val connectivityManager = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
            if (connectivityManager != null) {
                val request = android.net.NetworkRequest.Builder()
                    .addCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .addTransportType(android.net.NetworkCapabilities.TRANSPORT_WIFI)
                    .addTransportType(android.net.NetworkCapabilities.TRANSPORT_CELLULAR)
                    .addTransportType(android.net.NetworkCapabilities.TRANSPORT_ETHERNET)
                    .build()

                connectivityManager.registerNetworkCallback(request, object : android.net.ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: android.net.Network) {
                        Log.d(TAG, "Network connected (USB Wire / Wi-Fi / Mobile Data). Re-enforcing Cloud Firestore network!")
                        try {
                            val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
                            val db = FirebaseFirestore.getInstance()
                            if (auth.currentUser == null) {
                                auth.signInAnonymously()
                                    .addOnSuccessListener {
                                        Log.d(TAG, "Reconnected: Anonymous auth success")
                                        db.enableNetwork().addOnSuccessListener { notifyCloudUpdate() }
                                    }
                                    .addOnFailureListener {
                                        db.enableNetwork().addOnSuccessListener { notifyCloudUpdate() }
                                    }
                            } else {
                                db.enableNetwork().addOnSuccessListener { notifyCloudUpdate() }
                            }
                        } catch (_: Exception) {}
                    }

                    override fun onLost(network: android.net.Network) {
                        Log.d(TAG, "Network disconnected.")
                    }
                })
                isNetworkCallbackRegistered = true
            }
        } catch (e: Exception) {
            Log.e(TAG, "NetworkCallback registration error: ${e.message}")
        }
    }

    private fun ensureAuthBeforeCloudAction(onAuthReady: () -> Unit) {
        val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
        if (auth.currentUser == null) {
            try {
                auth.signInAnonymously()
                    .addOnCompleteListener {
                        onAuthReady()
                    }
            } catch (_: Exception) {
                onAuthReady()
            }
        } else {
            onAuthReady()
        }
    }

    fun pushEventToCloud(eventData: Map<String, Any?>) {
        notifyCloudUpdate()

        try {
            val db = FirebaseFirestore.getInstance()
            val rawUnit = eventData["unit"]?.toString() ?: ""
            val cleanUnit = rawUnit.replace("Unit", "", ignoreCase = true).trim()
            val normalizedUnit = if (cleanUnit.isNotBlank()) "Unit $cleanUnit" else rawUnit
            val dateText = eventData["date"]?.toString() ?: ""
            val titleText = eventData["title"]?.toString() ?: "Booking"
            val shape = eventData["shape"]?.toString() ?: ""

            val isGeneralEvent = cleanUnit.equals("Event", ignoreCase = true) || cleanUnit.contains("Meeting", ignoreCase = true) || shape.contains("Owner Event", ignoreCase = true)

            val rawDocId = if (isGeneralEvent) {
                val eventId = eventData["id"]?.toString()?.takeIf { it.isNotBlank() && it != "0" }
                if (eventId != null) "event_${eventId}" else "event_${sanitizeDocId(titleText)}_${System.currentTimeMillis()}"
            } else if (cleanUnit.isNotBlank() && dateText.isNotBlank()) {
                "evt_${cleanUnit}_${sanitizeDocId(dateText)}"
            } else {
                val eventId = eventData["id"]?.toString()?.takeIf { it.isNotBlank() && it != "0" }
                if (eventId != null) "evt_${cleanUnit}_${eventId}" else "evt_${cleanUnit}_${System.currentTimeMillis()}"
            }
            val docId = sanitizeDocId(rawDocId)

            val rawColor = eventData["color"]
            val colorLong = when (rawColor) {
                is Number -> rawColor.toLong()
                is String -> rawColor.toLongOrNull() ?: 0L
                else -> 0L
            }

            val enrichedData = eventData.toMutableMap().apply {
                put("id", docId)
                put("unit", normalizedUnit)
                put("cleanUnit", cleanUnit)
                put("updatedAt", System.currentTimeMillis().toString())
                put("color", colorLong)
            }

            db.collection("events").document(docId)
                .set(enrichedData, SetOptions.merge())
                .addOnSuccessListener {
                    Log.d(TAG, "Event $docId synced to Cloud Firestore!")
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
                val date = evt["date"] ?: ""
                val time = evt["time"] ?: "2:00 PM"
                val unit = evt["unit"] ?: ""
                val rawColor = evt["color"]
                val colorLong = rawColor?.toLongOrNull() ?: 0L
                if (unit.isNotBlank() && date.isNotBlank()) {
                    val eventMap = mapOf<String, Any>(
                        "title" to (evt["title"] ?: "Booking"),
                        "date" to date,
                        "time" to time,
                        "agent" to (evt["agent"]?.toString() ?: "Agent"),
                        "unit" to unit,
                        "shape" to (evt["shape"]?.toString() ?: "Reserved"),
                        "color" to colorLong
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
        ensureAuthBeforeCloudAction {
            try {
                val db = FirebaseFirestore.getInstance()
                val cleanUnit = unitName.replace("Unit", "", ignoreCase = true).trim()
                if (cleanUnit.isBlank()) return@ensureAuthBeforeCloudAction

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
    }

    fun pushUnitStatusToCloud(unitName: String, status: String) {
        notifyCloudUpdate()

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

            val batch = db.batch()
            batch.set(db.collection("units_catalog").document(cleanUnit), unitData, SetOptions.merge())
            batch.set(db.collection("units_catalog").document(cleanUnit.uppercase()), unitData, SetOptions.merge())
            batch.set(db.collection("units_catalog").document(normalizedUnit), unitData, SetOptions.merge())

            batch.commit()
                .addOnSuccessListener {
                    Log.d(TAG, "Unit $normalizedUnit status -> $status synced to cloud!")
                    notifyCloudUpdate()
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Firestore unit status write failed: ${e.message}")
                }
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
        context: Context? = null,
        @Suppress("UNUSED_PARAMETER") syncStatus: Boolean = true
    ) {
        notifyCloudUpdate()

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
                "owner" to owner.ifBlank { "Owner" },
                "rate" to rate,
                "updatedAt" to System.currentTimeMillis()
            )

            if (status.isNotBlank()) {
                unitData["status"] = status
            }

            if (cloudReadyImage.isNotBlank()) {
                unitData["image_uri"] = cloudReadyImage
                unitData["imageUri"] = cloudReadyImage
                unitData["image"] = cloudReadyImage
            }

            val batch = db.batch()
            batch.set(db.collection("units_catalog").document(cleanUnit), unitData, SetOptions.merge())
            batch.set(db.collection("units_catalog").document(normalizedUnit), unitData, SetOptions.merge())

            batch.commit()
                .addOnSuccessListener {
                    Log.d(TAG, "Full unit $normalizedUnit successfully synced to cloud!")
                    notifyCloudUpdate()
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Firestore full unit write failed: ${e.message}")
                }

            db.collection("deleted_units").document(cleanUnit).delete()
            db.collection("deleted_units").document(cleanUnit.uppercase()).delete()
            db.collection("deleted_units").document(normalizedUnit).delete()
        } catch (e: Exception) {
            Log.e(TAG, "Unit full cloud sync error: ${e.message}")
        }
    }

    fun deleteUnitFromCloud(context: Context, unitNumber: String) {
        try {
            val db = FirebaseFirestore.getInstance()
            val cleanUnit = unitNumber.replace("Unit", "", ignoreCase = true).trim()
            if (cleanUnit.isBlank()) return

            val batch = db.batch()
            batch.delete(db.collection("units_catalog").document(cleanUnit))
            batch.delete(db.collection("units_catalog").document(cleanUnit.uppercase()))
            batch.delete(db.collection("units_catalog").document("Unit $cleanUnit"))
            batch.commit()

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
                val isCustomActiveTag = uStat != "Available"

                pushFullUnitToCloud(
                    unitNumber = uNum,
                    tower = uTow,
                    description = uDesc,
                    status = uStat,
                    owner = uOwn,
                    rate = uRate,
                    imageUri = uImg,
                    context = appContext,
                    syncStatus = isCustomActiveTag
                )
            }

            syncAllLocalEventsToCloud(appContext)
            syncAllLocalAgentsToCloud(appContext)
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing all local units: ${e.message}")
        }
    }

    fun pushAgentToCloud(name: String, email: String, phone: String, role: String, gender: String, password: String = "") {
        notifyCloudUpdate()

        try {
            val db = FirebaseFirestore.getInstance()
            val agentData = mutableMapOf<String, Any>(
                "name" to name,
                "email" to email,
                "phone" to phone,
                "role" to role,
                "gender" to gender,
                "updatedAt" to System.currentTimeMillis()
            )
            if (password.isNotBlank()) {
                agentData["password"] = password
            }
            val docKey = sanitizeDocId(email.ifBlank { name }.lowercase())
            val cleanEmail = email.trim().lowercase()

            // Delete stale deletion markers if active agent profile is pushed
            db.collection("deleted_agents").document(docKey).delete()
            if (cleanEmail.isNotBlank()) {
                db.collection("deleted_agents").document(cleanEmail).delete()
            }

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

    fun pushAgentPasswordToCloud(identifier: String, newPass: String) {
        if (identifier.isBlank() || newPass.isBlank()) return
        notifyCloudUpdate()

        try {
            val db = FirebaseFirestore.getInstance()
            val cleanId = identifier.trim().lowercase()
            val docKey = sanitizeDocId(cleanId)
            val passData = mapOf(
                "password" to newPass,
                "passwordUpdatedAt" to System.currentTimeMillis()
            )

            db.collection("agents").document(docKey)
                .set(passData, SetOptions.merge())
                .addOnSuccessListener {
                    Log.d(TAG, "Password for $identifier updated in Cloud Firestore!")
                    notifyCloudUpdate()
                }
        } catch (e: Exception) {
            Log.e(TAG, "Push password error: ${e.message}")
        }
    }

    fun deleteAgentFromCloud(name: String = "", email: String = "") {
        ensureAuthBeforeCloudAction {
            try {
                val db = FirebaseFirestore.getInstance()
                val cleanEmail = email.trim().lowercase()
                val cleanName = name.trim().lowercase()
                if (cleanEmail.isBlank() && cleanName.isBlank()) return@ensureAuthBeforeCloudAction

                db.collection("agents").get().addOnSuccessListener { query ->
                    for (doc in query.documents) {
                        val docEmail = doc.getString("email")?.trim()?.lowercase() ?: ""
                        val docName = doc.getString("name")?.trim()?.lowercase() ?: ""
                        if ((cleanEmail.isNotBlank() && docEmail == cleanEmail) ||
                            (cleanName.isNotBlank() && docName == cleanName) ||
                            doc.id.equals(cleanEmail, ignoreCase = true)
                        ) {
                            doc.reference.delete()
                        }
                    }
                    notifyCloudUpdate()
                }

                val docId = if (cleanEmail.isNotBlank()) cleanEmail else cleanName
                val delData = mapOf(
                    "name" to name,
                    "email" to email,
                    "deletedAt" to System.currentTimeMillis()
                )
                db.collection("deleted_agents").document(docId).set(delData)
                    .addOnSuccessListener {
                        notifyCloudUpdate()
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Delete agent cloud error: ${e.message}")
            }
        }
    }

    fun startRealtimeCloudListener(context: Context, forceRestart: Boolean = false, onSyncTriggered: () -> Unit = {}) {
        val appContext = context.applicationContext
        registerNetworkCallback(appContext)
        if (isListenerActive && !forceRestart) {
            Log.d(TAG, "Cloud listener is already active. Skipping duplicate setup.")
            return
        }
        stopCloudListener()
        isListenerActive = true
        initWebSocketConnection()

        try {
            val dbHelper = AzureDatabaseHelper(appContext)
            val db = FirebaseFirestore.getInstance()

            try {
                db.enableNetwork()
            } catch (_: Exception) {}

            // 1. Real-Time Events Listener
            eventsListener?.remove()
            eventsListener = db.collection("events")
                .addSnapshotListener(MetadataChanges.INCLUDE) { snapshots, e ->
                    if (e != null || snapshots == null) {
                        Log.e(TAG, "Events listener error: ${e?.message}")
                        try { db.enableNetwork() } catch (_: Exception) {}
                        return@addSnapshotListener
                    }
                    try {
                        for (change in snapshots.documentChanges) {
                            val doc = change.document
                            val docId = doc.id
                            val title = doc.getString("title") ?: "Booking"
                            val date = doc.getString("date") ?: ""
                            val time = doc.getString("time") ?: "2:00 PM"
                            val agent = doc.getString("agent") ?: doc.getString("targetAgent") ?: "Agent"
                            val rawUnit = doc.getString("unit") ?: "Event"
                            val cleanUnit = rawUnit.replace("Unit", "", ignoreCase = true).trim()
                            val shape = doc.getString("shape") ?: "Available"
                            val isGeneralEvent = cleanUnit.equals("Event", ignoreCase = true) || cleanUnit.contains("Meeting", ignoreCase = true) || shape.contains("Owner Event", ignoreCase = true)
                            val finalUnit = if (isGeneralEvent) "Event" else "Unit $cleanUnit"

                            val rawColor = doc.get("color")
                            val colorLong = when (rawColor) {
                                is Number -> rawColor.toLong()
                                is String -> rawColor.toLongOrNull() ?: 0L
                                else -> 0L
                            }

                            if (change.type == DocumentChange.Type.REMOVED) {
                                if (docId.isNotBlank()) {
                                    dbHelper.deleteEventByIdOrTitle(docId)
                                }
                                if (date.isNotBlank() && finalUnit.isNotBlank()) {
                                    dbHelper.deleteEventByDateAndUnit(date, finalUnit, syncToCloud = false)
                                }
                            } else { // ADDED or MODIFIED
                                if ((cleanUnit.isNotBlank() || isGeneralEvent) && date.isNotBlank()) {
                                    dbHelper.insertEventRaw(
                                        docId = docId,
                                        title = title,
                                        date = date,
                                        time = time,
                                        agent = agent,
                                        unit = finalUnit,
                                        shape = shape,
                                        color = colorLong
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

            // 2. Units Catalog Listener (Single unified collection for unit status, building, rates & photos!)
            unitsCatalogListener?.remove()
            unitsCatalogListener = db.collection("units_catalog")
                .addSnapshotListener(MetadataChanges.INCLUDE) { snapshots, e ->
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

                    val docsByUnit = snapshots.documents.groupBy { doc ->
                        val uNumRaw = doc.getString("unitNumber") ?: doc.getString("cleanUnitNumber") ?: doc.id
                        uNumRaw.replace("Unit", "", ignoreCase = true).trim().uppercase()
                    }.filterKeys { it.isNotBlank() }

                    for ((cleanNum, docsForUnit) in docsByUnit) {
                        val uNum = "Unit $cleanNum"
                        cloudCleanNumbers.add(cleanNum)
                        cloudCleanNumbers.add(cleanNum.lowercase())

                        val cloudImg = docsForUnit.mapNotNull { it.getString("image_uri") }
                            .firstOrNull { it.isNotBlank() } ?: ""

                        val uTow = docsForUnit.mapNotNull { it.getString("tower") }
                            .firstOrNull { it.isNotBlank() } ?: "Azure Tower"

                        val uStat = docsForUnit.mapNotNull { it.getString("status") }
                            .firstOrNull { it.isNotBlank() } ?: "Available"

                        val uOwn = docsForUnit.mapNotNull { it.getString("owner") }
                            .firstOrNull { it.isNotBlank() } ?: "Owner"

                        val uDesc = docsForUnit.mapNotNull { it.getString("description") }
                            .firstOrNull { it.isNotBlank() } ?: "1 Bedroom"

                        val uRate = docsForUnit.mapNotNull { doc ->
                            doc.get("rate")?.let {
                                when (it) {
                                    is Number -> it.toDouble()
                                    is String -> it.toDoubleOrNull()
                                    else -> null
                                }
                            }
                        }.firstOrNull() ?: 2500.0

                        // Preserve cloud image directly for 100% reliable native bitmap decoding
                        val resolvedImg = if (cloudImg.isNotBlank()) {
                            cloudImg
                        } else {
                            existingLocalUnits.find {
                                isSameUnit(it["unitNumber"]?.toString() ?: "", cleanNum)
                            }?.get("image_uri")?.toString() ?: ""
                        }

                        dbHelper.insertUnit(uNum, uTow, uStat, uOwn, uRate, resolvedImg, uDesc, syncToCloud = false)

                        if (currentDeletedSet.removeAll { isSameUnit(it, cleanNum) || isSameUnit(it, uNum) }) {
                            deletedSetChanged = true
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
                        val deletedPrefs = appContext.getSharedPreferences("azure_calendar_deleted_agents", Context.MODE_PRIVATE)
                        val currentDeletedSet = deletedPrefs.getStringSet("deleted_agent_keys", emptySet()).orEmpty().toMutableSet()
                        var deletedSetChanged = false

                        for (doc in snapshots.documents) {
                            val aName = doc.getString("name") ?: ""
                            val aEmail = doc.getString("email") ?: ""
                            val aPhone = doc.getString("phone") ?: ""
                            val aRole = doc.getString("role") ?: "Property Agent"
                            val aGender = doc.getString("gender") ?: "Female"
                            val aPassword = doc.getString("password") ?: ""

                            val cleanEmail = aEmail.trim().lowercase()
                            val cleanName = aName.trim().lowercase()
                            val cleanPrefix = cleanEmail.substringBefore("@")

                            if (aName.isNotBlank() && aEmail.isNotBlank()) {
                                if (currentDeletedSet.remove(cleanEmail) || currentDeletedSet.remove(cleanName)) {
                                    deletedSetChanged = true
                                }
                                dbHelper.insertAgent(aName, aEmail, aPhone, aRole, aGender, password = aPassword, syncToCloud = false)

                                if (aPassword.isNotBlank()) {
                                    val secPrefs = appContext.getSharedPreferences("azure_calendar_security_info", Context.MODE_PRIVATE)
                                    secPrefs.edit()
                                        .putString("password_$cleanEmail", aPassword)
                                        .putString("password_$cleanName", aPassword)
                                        .putString("password_$cleanPrefix", aPassword)
                                        .apply()
                                }
                            }
                        }

                        if (deletedSetChanged) {
                            deletedPrefs.edit().putStringSet("deleted_agent_keys", currentDeletedSet).apply()
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

            // 8. Deleted Agents Listener
            deletedAgentsListener?.remove()
            deletedAgentsListener = db.collection("deleted_agents")
                .addSnapshotListener { snapshots, e ->
                    if (e != null || snapshots == null || snapshots.isEmpty) return@addSnapshotListener
                    try {
                        val deletedPrefs = appContext.getSharedPreferences("azure_calendar_deleted_agents", Context.MODE_PRIVATE)
                        val currentSet = deletedPrefs.getStringSet("deleted_agent_keys", emptySet()).orEmpty().toMutableSet()
                        var changed = false

                        for (doc in snapshots.documents) {
                            val delName = doc.getString("name")?.trim()?.lowercase() ?: ""
                            val delEmail = doc.getString("email")?.trim()?.lowercase() ?: doc.id.trim().lowercase()

                            if (delName.isNotBlank() && currentSet.add(delName)) changed = true
                            if (delEmail.isNotBlank() && currentSet.add(delEmail)) changed = true

                            dbHelper.deleteAgentByEmail(delEmail, syncToCloud = false)
                        }

                        if (changed) {
                            deletedPrefs.edit().putStringSet("deleted_agent_keys", currentSet).apply()
                        }
                        notifyCloudUpdate()
                        onSyncTriggered()
                    } catch (err: Exception) {
                        Log.e(TAG, "Error syncing deleted agents: ${err.message}")
                    }
                }

            // 9. Global Event Chat Messages Listener (Background sync across devices)
            allChatsListener?.remove()
            allChatsListener = db.collectionGroup("messages")
                .addSnapshotListener(MetadataChanges.INCLUDE) { snapshots, e ->
                    if (e != null || snapshots == null) return@addSnapshotListener
                    try {
                        for (doc in snapshots.documents) {
                            val roomId = doc.reference.parent.parent?.id ?: continue
                            val id = doc.getString("id") ?: doc.id
                            val senderName = doc.getString("senderName") ?: "User"
                            val senderRole = doc.getString("senderRole") ?: "Agent"
                            val messageText = doc.getString("messageText") ?: ""
                            val timestamp = doc.getString("timestamp") ?: "12:00 PM"
                            val rawCreatedTime = doc.get("createdTime")
                            val createdTime = when (rawCreatedTime) {
                                is Number -> rawCreatedTime.toLong()
                                is String -> rawCreatedTime.toLongOrNull() ?: System.currentTimeMillis()
                                else -> System.currentTimeMillis()
                            }

                            if (messageText.isNotBlank()) {
                                dbHelper.insertChatMessage(
                                    msgId = id,
                                    roomId = roomId,
                                    senderName = senderName,
                                    senderRole = senderRole,
                                    text = messageText,
                                    timestamp = timestamp,
                                    createdTime = createdTime
                                )
                            }
                        }
                        notifyCloudUpdate()
                        onSyncTriggered()
                    } catch (err: Exception) {
                        Log.e(TAG, "Error syncing global chat messages: ${err.message}")
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
        ensureAuthBeforeCloudAction {
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
                    "agent" to targetAgent,
                    "unit" to "Event",
                    "shape" to "Owner Event",
                    "eventLink" to eventLink.ifBlank { "https://azurecalendar.app/event/join?id=$docId" },
                    "updatedAt" to System.currentTimeMillis()
                )

                val cleanEventRoomId = getNormalizedEventId(docId, title)
                val welcomeData = mapOf(
                    "id" to "m1",
                    "senderName" to "Admin (Owner)",
                    "senderRole" to "Owner",
                    "messageText" to "Welcome to the group chat for '$title'! All joined agents and admin can communicate here.",
                    "timestamp" to "10:00 AM",
                    "createdTime" to 1L
                )

                val batch = db.batch()
                batch.set(db.collection("owner_events").document(docId), data, SetOptions.merge())
                batch.set(db.collection("events").document(docId), data, SetOptions.merge())
                batch.set(db.collection("notifications").document(docId), data, SetOptions.merge())
                batch.set(db.collection("event_chats").document(cleanEventRoomId).collection("messages").document("m1"), welcomeData, SetOptions.merge())

                batch.commit().addOnSuccessListener {
                    Log.d(TAG, "Owner event $docId and group chat room $cleanEventRoomId pushed to Cloud Firestore!")
                    notifyCloudUpdate()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Push owner event error: ${e.message}")
            }
        }
    }

    fun listenToOwnerEvents(context: Context? = null, onEventsUpdated: (List<OwnerEventItem>) -> Unit): ListenerRegistration {
        val db = FirebaseFirestore.getInstance()
        val dbHelper = context?.applicationContext?.let { AzureDatabaseHelper(it) }

        return db.collection("owner_events")
            .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, e ->
                if (e != null || snapshot == null) return@addSnapshotListener

                val resultList = mutableListOf<OwnerEventItem>()
                for (doc in snapshot.documents) {
                    val id = doc.getString("id") ?: doc.id
                    val title = doc.getString("title") ?: "Owner Event"
                    val description = doc.getString("description") ?: "Event created by Owner"
                    val date = doc.getString("date") ?: "Nov 5, 2026"
                    val time = doc.getString("time") ?: "2:00 PM"
                    val createdBy = doc.getString("createdBy") ?: "Owner"
                    val targetAgent = doc.getString("targetAgent") ?: doc.getString("agent") ?: "All Agents"
                    val eventLink = doc.getString("eventLink") ?: "https://azurecalendar.app/event/join?id=$id"

                    val rawCreated = doc.get("updatedAt") ?: doc.get("createdAt")
                    val createdAt = when (rawCreated) {
                        is Number -> rawCreated.toLong()
                        is String -> rawCreated.toLongOrNull() ?: 0L
                        else -> 0L
                    }

                    if (title.isNotBlank() && !title.startsWith("Staycation", ignoreCase = true)) {
                        val idNum = id.replace(Regex("[^0-9]"), "").toLongOrNull() ?: 0L
                        val finalStamp = if (createdAt > 0L) createdAt else idNum

                        dbHelper?.insertEventRaw(
                            docId = id,
                            title = title,
                            date = date,
                            time = time,
                            agent = targetAgent,
                            unit = "Event",
                            shape = "Owner Event",
                            color = 0L
                        )

                        resultList.add(
                            OwnerEventItem(
                                id = id,
                                title = title,
                                description = description,
                                date = date,
                                time = time,
                                createdBy = createdBy,
                                targetAgent = targetAgent,
                                eventLink = eventLink,
                                createdAt = finalStamp
                            )
                        )
                    }
                }

                if (dbHelper != null) {
                    val activeTitles = resultList.map { it.title.lowercase().trim() }.filter { it.isNotBlank() }.toSet()
                    val localEvents = dbHelper.getAllEvents()
                    for (evt in localEvents) {
                        val unit = evt["unit"] ?: ""
                        val shape = evt["shape"] ?: ""
                        val isOwnerEvt = unit.equals("Event", ignoreCase = true) || shape.contains("Owner Event", ignoreCase = true)
                        if (isOwnerEvt) {
                            val evtTitle = evt["title"]?.lowercase()?.trim() ?: ""
                            if (evtTitle.isNotBlank() && evtTitle !in activeTitles) {
                                dbHelper.deleteEventByIdOrTitle(evtTitle)
                            }
                        }
                    }
                }

                onEventsUpdated(resultList)
                notifyCloudUpdate()
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
        val isGenericTitle = cleanTitle.isBlank() || cleanTitle == "owner_event" || cleanTitle == "event" || cleanTitle == "booking"

        return if (!isGenericTitle) {
            "chat_$cleanTitle"
        } else {
            val cleanId = eventId.lowercase().trim().replace(Regex("[^a-z0-9]"), "_")
            if (cleanId.isNotBlank()) "chat_$cleanId" else "chat_general_event"
        }
    }

    fun pushChatMessageToCloud(
        msgId: String = "",
        eventId: String,
        eventTitle: String = "",
        senderName: String,
        senderRole: String,
        messageText: String,
        timestamp: String
    ) {
        notifyCloudUpdate()

        try {
            val db = FirebaseFirestore.getInstance()
            val cleanEventRoomId = getNormalizedEventId(eventId, eventTitle)
            val finalMsgId = msgId.ifBlank { "msg_${System.currentTimeMillis()}" }

            val msgData = mapOf(
                "id" to finalMsgId,
                "senderName" to senderName,
                "senderRole" to senderRole,
                "messageText" to messageText,
                "timestamp" to timestamp,
                "createdTime" to System.currentTimeMillis()
            )

            val cleanTitle = eventTitle.lowercase().trim().replace(Regex("[^a-z0-9]"), "_")
            val cleanId = eventId.lowercase().trim().replace(Regex("[^a-z0-9]"), "_")

            val targetRooms = mutableSetOf(cleanEventRoomId)
            if (cleanTitle.isNotBlank() && cleanTitle != "owner_event" && cleanTitle != "event") {
                targetRooms.add("chat_$cleanTitle")
            }
            if (cleanId.isNotBlank()) {
                targetRooms.add("chat_$cleanId")
            }

            val batch = db.batch()
            for (rId in targetRooms) {
                val ref = db.collection("event_chats").document(rId).collection("messages").document(finalMsgId)
                batch.set(ref, msgData, SetOptions.merge())
            }

            batch.commit().addOnSuccessListener {
                Log.d(TAG, "Chat message pushed to Cloud Firestore across rooms: $targetRooms")
                notifyCloudUpdate()
            }.addOnFailureListener { e ->
                Log.e(TAG, "Chat cloud push failed: ${e.message}")
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
        val cleanTitle = eventTitle.lowercase().trim().replace(Regex("[^a-z0-9]"), "_")
        val cleanId = eventId.lowercase().trim().replace(Regex("[^a-z0-9]"), "_")

        val targetRooms = mutableSetOf(cleanEventRoomId)
        if (cleanTitle.isNotBlank() && cleanTitle != "owner_event" && cleanTitle != "event") {
            targetRooms.add("chat_$cleanTitle")
        }
        if (cleanId.isNotBlank()) {
            targetRooms.add("chat_$cleanId")
        }

        val db = FirebaseFirestore.getInstance()
        val dbHelper = AzureDatabaseHelper(context.applicationContext)
        val combinedMap = mutableMapOf<String, GroupChatMessage>()
        val registrations = mutableListOf<ListenerRegistration>()

        for (roomId in targetRooms) {
            val reg = db.collection("event_chats")
                .document(roomId)
                .collection("messages")
                .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, e ->
                    if (e != null || snapshot == null) return@addSnapshotListener

                    for (doc in snapshot.documents) {
                        val id = doc.getString("id") ?: doc.id
                        val senderName = doc.getString("senderName") ?: "User"
                        val senderRole = doc.getString("senderRole") ?: "Agent"
                        val messageText = doc.getString("messageText") ?: ""
                        val timestamp = doc.getString("timestamp") ?: "12:00 PM"
                        val rawCreatedTime = doc.get("createdTime")
                        val createdTime = when (rawCreatedTime) {
                            is Number -> rawCreatedTime.toLong()
                            is String -> rawCreatedTime.toLongOrNull() ?: System.currentTimeMillis()
                            else -> System.currentTimeMillis()
                        }

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
                            combinedMap[id] = GroupChatMessage(
                                id = id,
                                senderName = senderName,
                                senderRole = senderRole,
                                messageText = messageText,
                                timestamp = timestamp,
                                isSelf = false,
                                createdTime = createdTime
                            )

                            // Un-read marker for other users so unread badge shows
                            val chatPrefs = context.applicationContext.getSharedPreferences("azure_calendar_chat_reads", Context.MODE_PRIVATE)
                            val allUsers = listOf("Admin Owner", "Alex Reyes", "sungit", "novie", "Gilbert", "Agent", "Chione Udtohan", "Claire Delgado", "Cris")
                            val editor = chatPrefs.edit()
                            allUsers.filter { !it.equals(senderName, ignoreCase = true) }.forEach { otherUser ->
                                editor.putBoolean("read_all_${cleanEventRoomId}_$otherUser", false)
                            }
                            editor.apply()
                        }
                    }

                    val sorted = combinedMap.values.sortedBy {
                        if (it.createdTime > 0L) it.createdTime else it.id.replace(Regex("[^0-9]"), "").toLongOrNull() ?: 0L
                    }
                    onMessagesUpdated(sorted)
                    notifyCloudUpdate()
                }
            registrations.add(reg)
        }

        return object : ListenerRegistration {
            override fun remove() {
                registrations.forEach { it.remove() }
            }
        }
    }

    fun stopCloudListener() {
        isListenerActive = false
        eventsListener?.remove()
        unitsListener?.remove()
        unitsCatalogListener?.remove()
        deletedUnitsListener?.remove()
        deletedEventsListener?.remove()
        deletedAgentsListener?.remove()
        allChatsListener?.remove()
        agentsListener?.remove()
        adminProfileListener?.remove()
        eventsListener = null
        unitsListener = null
        unitsCatalogListener = null
        deletedUnitsListener = null
        deletedEventsListener = null
        deletedAgentsListener = null
        allChatsListener = null
        agentsListener = null
        adminProfileListener = null
    }
}