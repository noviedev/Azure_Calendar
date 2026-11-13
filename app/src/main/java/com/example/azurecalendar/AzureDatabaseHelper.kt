package com.example.azurecalendar

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

class AzureDatabaseHelper(context: Context) : SQLiteOpenHelper(context.applicationContext, DATABASE_NAME, null, DATABASE_VERSION) {

    // Hold applicationContext so image files can be read, cached, and synced to Firestore
    private val appContext: Context = context.applicationContext

    companion object {
        private const val DATABASE_NAME = "azure_calendar.db"
        private const val DATABASE_VERSION = 7

        // Events Table
        const val TABLE_EVENTS = "events"
        const val COLUMN_ID = "id"
        const val COLUMN_TITLE = "title"
        const val COLUMN_DATE = "date"
        const val COLUMN_TIME = "time"
        const val COLUMN_AGENT = "agent"
        const val COLUMN_UNIT = "unit"
        const val COLUMN_SHAPE = "shape"
        const val COLUMN_COLOR = "color"

        // Units Table
        const val TABLE_UNITS = "units"
        const val COLUMN_UNIT_NUMBER = "unit_number"
        const val COLUMN_TOWER = "tower"
        const val COLUMN_STATUS = "status"
        const val COLUMN_OWNER = "owner"
        const val COLUMN_RATE = "rate"
        const val COLUMN_IMAGE = "image_uri"
        const val COLUMN_DESCRIPTION = "description"

        // Agents Table
        const val TABLE_AGENTS = "agents"
        const val COLUMN_AGENT_ID = "id"
        const val COLUMN_AGENT_NAME = "name"
        const val COLUMN_AGENT_EMAIL = "email"
        const val COLUMN_AGENT_GENDER = "gender"
        const val COLUMN_AGENT_POSITION = "position"
        const val COLUMN_AGENT_PASSWORD = "password"

        // Chat Messages Table
        const val TABLE_CHAT_MESSAGES = "chat_messages"
        const val COLUMN_CHAT_ID = "msg_id"
        const val COLUMN_CHAT_ROOM_ID = "room_id"
        const val COLUMN_CHAT_SENDER_NAME = "sender_name"
        const val COLUMN_CHAT_SENDER_ROLE = "sender_role"
        const val COLUMN_CHAT_TEXT = "message_text"
        const val COLUMN_CHAT_TIMESTAMP = "timestamp"
        const val COLUMN_CHAT_CREATED_TIME = "created_time"
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createEventsTable = ("CREATE TABLE $TABLE_EVENTS (" +
                "$COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "$COLUMN_TITLE TEXT, " +
                "$COLUMN_DATE TEXT, " +
                "$COLUMN_TIME TEXT, " +
                "$COLUMN_AGENT TEXT, " +
                "$COLUMN_UNIT TEXT, " +
                "$COLUMN_SHAPE TEXT, " +
                "$COLUMN_COLOR LONG)")

        val createUnitsTable = ("CREATE TABLE $TABLE_UNITS (" +
                "$COLUMN_UNIT_NUMBER TEXT PRIMARY KEY, " +
                "$COLUMN_TOWER TEXT, " +
                "$COLUMN_STATUS TEXT, " +
                "$COLUMN_OWNER TEXT, " +
                "$COLUMN_RATE REAL, " +
                "$COLUMN_IMAGE TEXT, " +
                "$COLUMN_DESCRIPTION TEXT)")

        val createAgentsTable = ("CREATE TABLE $TABLE_AGENTS (" +
                "$COLUMN_AGENT_ID INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "$COLUMN_AGENT_NAME TEXT, " +
                "$COLUMN_AGENT_EMAIL TEXT, " +
                "$COLUMN_AGENT_GENDER TEXT, " +
                "$COLUMN_AGENT_POSITION TEXT, " +
                "$COLUMN_AGENT_PASSWORD TEXT)")

        val createChatTable = ("CREATE TABLE IF NOT EXISTS $TABLE_CHAT_MESSAGES (" +
                "$COLUMN_CHAT_ID TEXT PRIMARY KEY, " +
                "$COLUMN_CHAT_ROOM_ID TEXT, " +
                "$COLUMN_CHAT_SENDER_NAME TEXT, " +
                "$COLUMN_CHAT_SENDER_ROLE TEXT, " +
                "$COLUMN_CHAT_TEXT TEXT, " +
                "$COLUMN_CHAT_TIMESTAMP TEXT, " +
                "$COLUMN_CHAT_CREATED_TIME LONG)")

        db.execSQL(createEventsTable)
        db.execSQL(createUnitsTable)
        db.execSQL(createAgentsTable)
        db.execSQL(createChatTable)

        seedDefaultUnits(db)
    }

    private fun seedDefaultUnits(db: SQLiteDatabase) {
        val defaultUnits = listOf(
            arrayOf("Unit 12A", "Tower 1", "Available", "Owner A", "2500.0", "", "1 Bedroom"),
            arrayOf("Unit 15B", "Azure Tower", "Available", "Owner B", "2500.0", "", "1 Bedroom"),
            arrayOf("Unit 08C", "Azure Tower", "Available", "Owner C", "2500.0", "", "1 Bedroom"),
            arrayOf("Unit 06D", "Azure Tower", "Available", "Owner D", "2500.0", "", "1 Bedroom")
        )
        for (u in defaultUnits) {
            val values = ContentValues().apply {
                put(COLUMN_UNIT_NUMBER, u[0])
                put(COLUMN_TOWER, u[1])
                put(COLUMN_STATUS, u[2])
                put(COLUMN_OWNER, u[3])
                put(COLUMN_RATE, u[4].toDoubleOrNull() ?: 2500.0)
                put(COLUMN_IMAGE, u[5])
                put(COLUMN_DESCRIPTION, u[6])
            }
            db.insertWithOnConflict(TABLE_UNITS, null, values, SQLiteDatabase.CONFLICT_IGNORE)
        }
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_EVENTS")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_UNITS")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_AGENTS")
        onCreate(db)
    }

    private fun persistLocalImageIfNeeded(cleanUnit: String, rawUri: String): String {
        if (rawUri.isBlank()) return ""
        if (rawUri.startsWith("file://") || rawUri.startsWith("/data/")) return rawUri

        if (rawUri.startsWith("data:image", ignoreCase = true)) {
            return try {
                val base64Part = rawUri.substringAfter("base64,", "")
                    .replace(" ", "")
                    .replace("\n", "")
                    .replace("\r", "")
                    .trim()
                if (base64Part.isBlank()) return rawUri

                val bytes = try {
                    android.util.Base64.decode(base64Part, android.util.Base64.DEFAULT)
                } catch (_: Exception) {
                    try {
                        android.util.Base64.decode(base64Part, android.util.Base64.NO_WRAP or android.util.Base64.URL_SAFE)
                    } catch (_: Exception) {
                        return rawUri
                    }
                }

                val dir = File(appContext.filesDir, "synced_unit_images")
                if (!dir.exists()) dir.mkdirs()

                val safeUnit = cleanUnit.replace(Regex("[^a-zA-Z0-9_\\-.]"), "")
                val outFile = File(dir, "unit_${safeUnit}_${base64Part.hashCode()}.jpg")
                if (!outFile.exists()) {
                    FileOutputStream(outFile).use { fos ->
                        fos.write(bytes)
                        fos.flush()
                    }
                }
                Uri.fromFile(outFile).toString()
            } catch (_: Exception) {
                rawUri
            }
        }

        if (!rawUri.startsWith("content://", ignoreCase = true)) return rawUri

        return try {
            val uri = Uri.parse(rawUri)
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            appContext.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            }

            var inSampleSize = 1
            val maxDim = 800
            if (options.outWidth > maxDim || options.outHeight > maxDim) {
                val halfWidth = options.outWidth / 2
                val halfHeight = options.outHeight / 2
                while (halfWidth / inSampleSize >= maxDim && halfHeight / inSampleSize >= maxDim) {
                    inSampleSize *= 2
                }
            }

            options.inSampleSize = inSampleSize
            options.inJustDecodeBounds = false

            val bitmap = appContext.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            } ?: return rawUri

            val dir = File(appContext.filesDir, "synced_unit_images")
            if (!dir.exists()) dir.mkdirs()

            val safeUnit = cleanUnit.replace(Regex("[^a-zA-Z0-9_\\-.]"), "")
            val outFile = File(dir, "owner_unit_${safeUnit}_${System.currentTimeMillis()}.jpg")
            FileOutputStream(outFile).use { fos ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 80, fos)
                fos.flush()
            }
            bitmap.recycle()
            Uri.fromFile(outFile).toString()
        } catch (_: Exception) {
            rawUri
        }
    }

    private fun ensurePasswordColumn(db: SQLiteDatabase) {
        try {
            db.execSQL("ALTER TABLE $TABLE_AGENTS ADD COLUMN $COLUMN_AGENT_PASSWORD TEXT")
        } catch (_: Exception) {}
    }

    // --- Agents CRUD ---
    fun insertAgent(
        name: String,
        email: String,
        phone: String = "",
        role: String = "Property Agent",
        gender: String = "Female",
        password: String = "",
        syncToCloud: Boolean = true
    ): Long {
        val db = this.writableDatabase
        ensurePasswordColumn(db)
        if (email.isNotBlank()) {
            db.delete(TABLE_AGENTS, "$COLUMN_AGENT_EMAIL = ?", arrayOf(email))
        }
        if (name.isNotBlank()) {
            db.delete(TABLE_AGENTS, "$COLUMN_AGENT_NAME = ?", arrayOf(name))
        }

        val values = ContentValues().apply {
            put(COLUMN_AGENT_NAME, name)
            put(COLUMN_AGENT_EMAIL, email)
            put(COLUMN_AGENT_GENDER, gender)
            put(COLUMN_AGENT_POSITION, role)
            if (password.isNotBlank()) {
                put(COLUMN_AGENT_PASSWORD, password)
            }
        }
        val rowId = db.insert(TABLE_AGENTS, null, values)

        if (syncToCloud && name.isNotBlank() && email.isNotBlank()) {
            FirebaseCloudSyncManager.pushAgentToCloud(name, email, phone, role, gender, password)
        }
        return rowId
    }

    fun updateAgentProfile(
        oldName: String,
        oldEmail: String,
        newName: String,
        newEmail: String,
        phone: String = "",
        role: String = "Property Agent",
        gender: String = "Female",
        password: String = "",
        syncToCloud: Boolean = true
    ): Long {
        val db = this.writableDatabase
        ensurePasswordColumn(db)
        if (oldEmail.isNotBlank()) db.delete(TABLE_AGENTS, "$COLUMN_AGENT_EMAIL = ?", arrayOf(oldEmail))
        if (oldName.isNotBlank()) db.delete(TABLE_AGENTS, "$COLUMN_AGENT_NAME = ?", arrayOf(oldName))
        if (newEmail.isNotBlank()) db.delete(TABLE_AGENTS, "$COLUMN_AGENT_EMAIL = ?", arrayOf(newEmail))
        if (newName.isNotBlank()) db.delete(TABLE_AGENTS, "$COLUMN_AGENT_NAME = ?", arrayOf(newName))

        if (syncToCloud && (oldName.isNotBlank() || oldEmail.isNotBlank()) && (oldName != newName || oldEmail != newEmail)) {
            FirebaseCloudSyncManager.deleteAgentFromCloud(name = oldName, email = oldEmail)
        }

        val values = ContentValues().apply {
            put(COLUMN_AGENT_NAME, newName)
            put(COLUMN_AGENT_EMAIL, newEmail)
            put(COLUMN_AGENT_GENDER, gender)
            put(COLUMN_AGENT_POSITION, role)
            if (password.isNotBlank()) {
                put(COLUMN_AGENT_PASSWORD, password)
            }
        }
        val rowId = db.insert(TABLE_AGENTS, null, values)

        if (syncToCloud && newName.isNotBlank() && newEmail.isNotBlank()) {
            FirebaseCloudSyncManager.pushAgentToCloud(newName, newEmail, phone, role, gender, password)
        }
        return rowId
    }

    fun updateAgentPassword(identifier: String, newPass: String, syncToCloud: Boolean = true): Boolean {
        val cleanId = identifier.trim()
        if (cleanId.isBlank() || newPass.isBlank()) return false
        val db = this.writableDatabase
        ensurePasswordColumn(db)

        val cleanPrefix = cleanId.substringBefore("@").lowercase()
        val values = ContentValues().apply {
            put(COLUMN_AGENT_PASSWORD, newPass)
        }

        val rows = db.update(
            TABLE_AGENTS,
            values,
            "LOWER($COLUMN_AGENT_EMAIL) = ? OR LOWER($COLUMN_AGENT_NAME) = ? OR LOWER($COLUMN_AGENT_EMAIL) LIKE ?",
            arrayOf(cleanId.lowercase(), cleanId.lowercase(), "$cleanPrefix@%")
        )

        val isAdmin = cleanId.contains("admin", ignoreCase = true) || cleanId.contains("owner", ignoreCase = true)
        val name = if (isAdmin) "Admin" else if (!cleanId.contains("@")) cleanId else cleanId.substringBefore("@")
        val email = if (cleanId.contains("@")) cleanId else "${cleanId.lowercase()}@gmail.com"
        val role = if (isAdmin) "Property Owner & Admin" else "Property Agent"

        if (rows == 0) {
            insertAgent(name = name, email = email, phone = "", role = role, gender = "Female", password = newPass, syncToCloud = syncToCloud)
        } else if (syncToCloud) {
            FirebaseCloudSyncManager.pushAgentPasswordToCloud(cleanId, newPass)
        }
        return true
    }

    fun getAgentPassword(identifier: String): String? {
        val cleanId = identifier.trim().lowercase()
        if (cleanId.isBlank()) return null
        val db = this.readableDatabase
        ensurePasswordColumn(db)
        val cleanPrefix = cleanId.substringBefore("@")

        val cursor = db.rawQuery(
            "SELECT $COLUMN_AGENT_PASSWORD FROM $TABLE_AGENTS WHERE LOWER($COLUMN_AGENT_EMAIL) = ? OR LOWER($COLUMN_AGENT_NAME) = ? OR LOWER($COLUMN_AGENT_EMAIL) LIKE ? LIMIT 1",
            arrayOf(cleanId, cleanId, "$cleanPrefix@%")
        )
        var pass: String? = null
        if (cursor.moveToFirst()) {
            val passIdx = cursor.getColumnIndex(COLUMN_AGENT_PASSWORD)
            if (passIdx != -1) {
                pass = cursor.getString(passIdx)
            }
        }
        cursor.close()
        return if (!pass.isNullOrBlank()) pass else null
    }

    fun getAllAgents(): List<Map<String, String>> {
        val list = mutableListOf<Map<String, String>>()
        val db = this.readableDatabase
        ensurePasswordColumn(db)
        val cursor = db.rawQuery("SELECT * FROM $TABLE_AGENTS", null)
        val sampleEmails = setOf("alex.reyes@azure.com", "apple.u@azure.com", "john.dc@azure.com", "maria.santos@azure.com", "elena.r@azure.com")

        if (cursor.moveToFirst()) {
            do {
                val idIdx = cursor.getColumnIndex(COLUMN_AGENT_ID)
                val nameIdx = cursor.getColumnIndex(COLUMN_AGENT_NAME)
                val emailIdx = cursor.getColumnIndex(COLUMN_AGENT_EMAIL)
                val genderIdx = cursor.getColumnIndex(COLUMN_AGENT_GENDER)
                val posIdx = cursor.getColumnIndex(COLUMN_AGENT_POSITION)
                val passIdx = cursor.getColumnIndex(COLUMN_AGENT_PASSWORD)

                val emailStr = if (emailIdx != -1) cursor.getString(emailIdx) ?: "" else ""
                if (emailStr.lowercase() in sampleEmails) continue

                val agent = mapOf(
                    "id" to if (idIdx != -1) cursor.getLong(idIdx).toString() else "0",
                    "name" to if (nameIdx != -1) cursor.getString(nameIdx) ?: "" else "",
                    "email" to emailStr,
                    "gender" to if (genderIdx != -1) cursor.getString(genderIdx) ?: "" else "",
                    "position" to if (posIdx != -1) cursor.getString(posIdx) ?: "" else "",
                    "password" to if (passIdx != -1) cursor.getString(passIdx) ?: "" else ""
                )
                list.add(agent)
            } while (cursor.moveToNext())
        }
        cursor.close()
        return list.distinctBy { it["email"]?.lowercase()?.ifBlank { it["name"]?.lowercase() } }
    }

    fun deleteAgent(id: Long): Int {
        val db = this.writableDatabase
        return db.delete(TABLE_AGENTS, "$COLUMN_AGENT_ID = ?", arrayOf(id.toString()))
    }

    fun deleteAgentByEmail(email: String, syncToCloud: Boolean = true): Int {
        val db = this.writableDatabase
        val rows = db.delete(TABLE_AGENTS, "$COLUMN_AGENT_EMAIL = ?", arrayOf(email))
        if (syncToCloud && email.isNotBlank()) {
            FirebaseCloudSyncManager.deleteAgentFromCloud("", email)
        }
        return rows
    }

    fun deleteAgentByNameOrEmail(name: String, email: String, syncToCloud: Boolean = true): Int {
        val db = this.writableDatabase
        val rows = db.delete(
            TABLE_AGENTS,
            "$COLUMN_AGENT_EMAIL = ? OR $COLUMN_AGENT_NAME = ?",
            arrayOf(email, name)
        )
        if (syncToCloud && (email.isNotBlank() || name.isNotBlank())) {
            FirebaseCloudSyncManager.deleteAgentFromCloud(name, email)
        }
        return rows
    }

    // --- Events CRUD ---
    fun insertEventRaw(
        docId: String,
        title: String,
        date: String,
        time: String,
        agent: String,
        unit: String,
        shape: String = "",
        color: Long = 0L
    ): Long {
        val db = this.writableDatabase
        val isGeneralEvent = unit.equals("Event", ignoreCase = true) || unit.contains("Meeting", ignoreCase = true) || shape.contains("Owner Event", ignoreCase = true)
        val cleanUnit = unit.replace("Unit", "", ignoreCase = true).trim()
        val normalizedUnit = if (isGeneralEvent) "Event" else "Unit $cleanUnit"

        if (!isGeneralEvent && cleanUnit.isNotBlank() && date.isNotBlank()) {
            db.delete(
                TABLE_EVENTS,
                "$COLUMN_DATE = ? AND ($COLUMN_UNIT LIKE ? OR $COLUMN_UNIT LIKE ? OR $COLUMN_UNIT = ?)",
                arrayOf(date, "%$cleanUnit%", "%$normalizedUnit%", unit)
            )
        } else if (isGeneralEvent) {
            db.delete(
                TABLE_EVENTS,
                "$COLUMN_TITLE = ? AND $COLUMN_DATE = ?",
                arrayOf(title, date)
            )
        }

        val values = ContentValues().apply {
            put(COLUMN_TITLE, title)
            put(COLUMN_DATE, date)
            put(COLUMN_TIME, time)
            put(COLUMN_AGENT, agent)
            put(COLUMN_UNIT, normalizedUnit)
            put(COLUMN_SHAPE, shape)
            put(COLUMN_COLOR, color)
        }
        return db.insert(TABLE_EVENTS, null, values)
    }

    fun insertEvent(
        title: String,
        date: String,
        time: String,
        agent: String,
        unit: String,
        shape: String = "",
        color: Long = 0L,
        syncToCloud: Boolean = true
    ): Long {
        val db = this.writableDatabase
        val isGeneralEvent = unit.equals("Event", ignoreCase = true) || unit.contains("Meeting", ignoreCase = true) || shape.contains("Owner Event", ignoreCase = true)
        val cleanUnit = unit.replace("Unit", "", ignoreCase = true).trim()
        val normalizedUnit = if (isGeneralEvent) "Event" else "Unit $cleanUnit"

        if (!isGeneralEvent) {
            db.delete(
                TABLE_EVENTS,
                "$COLUMN_DATE = ? AND ($COLUMN_UNIT LIKE ? OR $COLUMN_UNIT LIKE ? OR $COLUMN_UNIT = ?)",
                arrayOf(date, "%$cleanUnit%", "%$normalizedUnit%", unit)
            )
        } else {
            db.delete(
                TABLE_EVENTS,
                "$COLUMN_TITLE = ? AND $COLUMN_DATE = ?",
                arrayOf(title, date)
            )
        }

        val values = ContentValues().apply {
            put(COLUMN_TITLE, title)
            put(COLUMN_DATE, date)
            put(COLUMN_TIME, time)
            put(COLUMN_AGENT, agent)
            put(COLUMN_UNIT, normalizedUnit)
            put(COLUMN_SHAPE, shape)
            put(COLUMN_COLOR, color)
        }
        val rowId = db.insert(TABLE_EVENTS, null, values)

        if (syncToCloud && date.isNotBlank() && title != "TEMP") {
            FirebaseCloudSyncManager.pushEventToCloud(
                mapOf<String, Any>(
                    "id" to rowId.toString(),
                    "title" to title,
                    "date" to date,
                    "time" to time,
                    "agent" to agent,
                    "unit" to normalizedUnit,
                    "shape" to shape.ifBlank { if (isGeneralEvent) "Owner Event" else "Reserved" },
                    "color" to color
                )
            )
        }
        FirebaseCloudSyncManager.notifyCloudUpdate()
        return rowId
    }

    fun getAllEvents(): List<Map<String, String>> {
        autoReleaseExpiredUnits()
        val list = mutableListOf<Map<String, String>>()
        val db = this.readableDatabase
        val cursor = db.rawQuery("SELECT * FROM $TABLE_EVENTS ORDER BY $COLUMN_ID DESC", null)

        if (cursor.moveToFirst()) {
            do {
                val idIdx = cursor.getColumnIndex(COLUMN_ID)
                val titleIdx = cursor.getColumnIndex(COLUMN_TITLE)
                val dateIdx = cursor.getColumnIndex(COLUMN_DATE)
                val timeIdx = cursor.getColumnIndex(COLUMN_TIME)
                val agentIdx = cursor.getColumnIndex(COLUMN_AGENT)
                val unitIdx = cursor.getColumnIndex(COLUMN_UNIT)
                val shapeIdx = cursor.getColumnIndex(COLUMN_SHAPE)
                val colorIdx = cursor.getColumnIndex(COLUMN_COLOR)

                val event = mapOf(
                    "id" to if (idIdx != -1) cursor.getLong(idIdx).toString() else "0",
                    "title" to if (titleIdx != -1) cursor.getString(titleIdx) ?: "" else "",
                    "date" to if (dateIdx != -1) cursor.getString(dateIdx) ?: "" else "",
                    "time" to if (timeIdx != -1) cursor.getString(timeIdx) ?: "" else "",
                    "agent" to if (agentIdx != -1) cursor.getString(agentIdx) ?: "" else "",
                    "unit" to if (unitIdx != -1) cursor.getString(unitIdx) ?: "" else "",
                    "shape" to if (shapeIdx != -1) cursor.getString(shapeIdx) ?: "" else "",
                    "color" to if (colorIdx != -1) cursor.getLong(colorIdx).toString() else "0"
                )
                list.add(event)
            } while (cursor.moveToNext())
        }
        cursor.close()
        return list.distinctBy {
            val u = (it["unit"] ?: "").replace("Unit", "", ignoreCase = true).trim().lowercase()
            val d = (it["date"] ?: "").trim().lowercase()
            val s = (it["shape"] ?: "").trim().lowercase()
            val t = (it["title"] ?: "").trim().lowercase()
            if (u == "event" || s.contains("owner event")) "${t}_${d}_${s}" else "${u}_${d}"
        }
    }

    fun deleteEvent(id: Long) {
        val db = this.writableDatabase
        if (id != -999L) {
            val cursor = db.rawQuery("SELECT $COLUMN_DATE, $COLUMN_UNIT FROM $TABLE_EVENTS WHERE $COLUMN_ID = ?", arrayOf(id.toString()))
            if (cursor.moveToFirst()) {
                val dateStr = cursor.getString(0) ?: ""
                val unitStr = cursor.getString(1) ?: ""
                if (unitStr.isNotBlank()) {
                    FirebaseCloudSyncManager.deleteEventFromCloud(dateStr, unitStr)
                }
            }
            cursor.close()
        }
        db.delete(TABLE_EVENTS, "$COLUMN_ID = ?", arrayOf(id.toString()))
    }

    fun deleteEventByIdOrTitle(idOrTitle: String): Int {
        val db = this.writableDatabase
        val idVal = idOrTitle.replace("evt-", "").toLongOrNull()
        return if (idVal != null) {
            db.delete(TABLE_EVENTS, "$COLUMN_ID = ?", arrayOf(idVal.toString()))
        } else {
            db.delete(TABLE_EVENTS, "$COLUMN_TITLE LIKE ?", arrayOf("%$idOrTitle%"))
        }
    }

    fun deleteEventByDateAndUnit(date: String, unit: String, syncToCloud: Boolean = false): Int {
        val db = this.writableDatabase
        val cleanUnit = unit.replace("Unit", "", ignoreCase = true).trim()
        val normalizedUnit = "Unit $cleanUnit"
        val rows = db.delete(
            TABLE_EVENTS,
            "$COLUMN_DATE = ? AND ($COLUMN_UNIT LIKE ? OR $COLUMN_UNIT LIKE ? OR $COLUMN_UNIT = ?)",
            arrayOf(date, "%$cleanUnit%", "%$normalizedUnit%", unit)
        )
        if (syncToCloud && cleanUnit.isNotBlank()) {
            FirebaseCloudSyncManager.deleteEventFromCloud(date, normalizedUnit)
        }
        return rows
    }

    fun deleteEventsForUnit(unit: String, syncToCloud: Boolean = true): Int {
        val db = this.writableDatabase
        val cleanUnit = unit.replace("Unit", "", ignoreCase = true).trim()
        val normalizedUnit = "Unit $cleanUnit"
        val rows = db.delete(
            TABLE_EVENTS,
            "$COLUMN_UNIT LIKE ? OR $COLUMN_UNIT LIKE ? OR $COLUMN_UNIT = ?",
            arrayOf("%$cleanUnit%", "%$normalizedUnit%", unit)
        )
        if (syncToCloud && cleanUnit.isNotBlank()) {
            FirebaseCloudSyncManager.deleteEventFromCloud("", normalizedUnit)
        }
        return rows
    }

    fun clearAllEvents(): Int {
        val db = this.writableDatabase
        return db.delete(TABLE_EVENTS, null, null)
    }

    @Suppress("unused")
    fun updateEvent(id: Long, title: String, date: String, time: String, agent: String, unit: String, shape: String, color: Long): Int {
        val db = this.writableDatabase
        val cleanUnit = unit.replace("Unit", "", ignoreCase = true).trim()
        val normalizedUnit = "Unit $cleanUnit"
        val values = ContentValues().apply {
            put(COLUMN_TITLE, title)
            put(COLUMN_DATE, date)
            put(COLUMN_TIME, time)
            put(COLUMN_AGENT, agent)
            put(COLUMN_UNIT, normalizedUnit)
            put(COLUMN_SHAPE, shape)
            put(COLUMN_COLOR, color)
        }
        val rows = db.update(TABLE_EVENTS, values, "$COLUMN_ID = ?", arrayOf(id.toString()))
        if (cleanUnit.isNotBlank() && date.isNotBlank()) {
            FirebaseCloudSyncManager.pushEventToCloud(
                mapOf<String, Any>(
                    "id" to id.toString(),
                    "title" to title,
                    "date" to date,
                    "time" to time,
                    "agent" to agent,
                    "unit" to normalizedUnit,
                    "shape" to shape.ifBlank { "Reserved" },
                    "color" to color
                )
            )
        }
        return rows
    }

    fun insertChatMessage(
        msgId: String,
        roomId: String,
        senderName: String,
        senderRole: String,
        text: String,
        timestamp: String,
        createdTime: Long = System.currentTimeMillis()
    ): Long {
        val db = this.writableDatabase
        var existingRoomId = ""
        try {
            val cursor = db.rawQuery("SELECT $COLUMN_CHAT_ROOM_ID FROM $TABLE_CHAT_MESSAGES WHERE $COLUMN_CHAT_ID = ?", arrayOf(msgId))
            if (cursor.moveToFirst()) {
                existingRoomId = cursor.getString(0) ?: ""
            }
            cursor.close()
        } catch (_: Exception) {}

        val finalRoomId = if (existingRoomId.isNotBlank() && existingRoomId.startsWith("chat_") && !roomId.startsWith("chat_")) {
            existingRoomId
        } else {
            roomId
        }

        val values = ContentValues().apply {
            put(COLUMN_CHAT_ID, msgId)
            put(COLUMN_CHAT_ROOM_ID, finalRoomId)
            put(COLUMN_CHAT_SENDER_NAME, senderName)
            put(COLUMN_CHAT_SENDER_ROLE, senderRole)
            put(COLUMN_CHAT_TEXT, text)
            put(COLUMN_CHAT_TIMESTAMP, timestamp)
            put(COLUMN_CHAT_CREATED_TIME, createdTime)
        }
        return db.insertWithOnConflict(TABLE_CHAT_MESSAGES, null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun getChatMessagesForRoom(roomId: String): List<GroupChatMessage> {
        val list = mutableListOf<GroupChatMessage>()
        try {
            val db = this.readableDatabase
            val cleanKey = roomId.replace("chat_", "", ignoreCase = true).trim().lowercase()
            val cursor = db.rawQuery(
                "SELECT * FROM $TABLE_CHAT_MESSAGES WHERE $COLUMN_CHAT_ROOM_ID = ? OR $COLUMN_CHAT_ROOM_ID LIKE ? OR $COLUMN_CHAT_ROOM_ID LIKE ? ORDER BY $COLUMN_CHAT_CREATED_TIME ASC",
                arrayOf(roomId, "%$cleanKey%", "%$roomId%")
            )
            if (cursor.moveToFirst()) {
                do {
                    val id = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_CHAT_ID))
                    val senderName = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_CHAT_SENDER_NAME))
                    val senderRole = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_CHAT_SENDER_ROLE))
                    val text = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_CHAT_TEXT))
                    val timestamp = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_CHAT_TIMESTAMP))
                    val createdTime = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_CHAT_CREATED_TIME))
                    list.add(
                        GroupChatMessage(
                            id = id,
                            senderName = senderName,
                            senderRole = senderRole,
                            messageText = text,
                            timestamp = timestamp,
                            isSelf = false,
                            createdTime = createdTime
                        )
                    )
                } while (cursor.moveToNext())
            }
            cursor.close()
        } catch (_: Exception) {}
        return list.distinctBy { if (it.id.isNotBlank()) it.id else "${it.senderName}_${it.messageText}_${it.createdTime}" }
    }

    // --- Units CRUD ---
    fun insertUnit(
        unitNumber: String,
        tower: String,
        status: String,
        owner: String,
        rate: Double,
        imageUri: String = "",
        description: String = "1 Bedroom",
        syncToCloud: Boolean = true
    ): Long {
        val db = this.writableDatabase
        val cleanUnit = unitNumber.replace("Unit", "", ignoreCase = true).trim()
        val normalizedUnit = "Unit $cleanUnit"

        var existingImageUri = ""
        var existingStatus = ""
        val cursor = db.rawQuery(
            "SELECT $COLUMN_IMAGE, $COLUMN_STATUS FROM $TABLE_UNITS WHERE $COLUMN_UNIT_NUMBER = ? OR $COLUMN_UNIT_NUMBER = ? OR $COLUMN_UNIT_NUMBER = ?",
            arrayOf(normalizedUnit, unitNumber, cleanUnit)
        )
        if (cursor.moveToFirst()) {
            existingImageUri = cursor.getString(0) ?: ""
            existingStatus = cursor.getString(1) ?: ""
        }
        cursor.close()

        val rawFinalImage = imageUri.ifBlank { existingImageUri }
        val finalImageUri = if (rawFinalImage.isNotBlank()) rawFinalImage else ""

        val finalStatus = if (status.isNotBlank()) status else existingStatus.ifBlank { "Available" }

        db.delete(
            TABLE_UNITS,
            "$COLUMN_UNIT_NUMBER = ? OR $COLUMN_UNIT_NUMBER = ? OR $COLUMN_UNIT_NUMBER = ?",
            arrayOf(unitNumber, cleanUnit, "Unit$cleanUnit")
        )

        val values = ContentValues().apply {
            put(COLUMN_UNIT_NUMBER, normalizedUnit)
            put(COLUMN_TOWER, tower)
            put(COLUMN_STATUS, finalStatus)
            put(COLUMN_OWNER, owner)
            put(COLUMN_RATE, rate)
            put(COLUMN_IMAGE, finalImageUri)
            put(COLUMN_DESCRIPTION, description)
        }
        val rowId = db.insertWithOnConflict(TABLE_UNITS, null, values, SQLiteDatabase.CONFLICT_REPLACE)

        if (syncToCloud && cleanUnit.isNotBlank() && cleanUnit != "TEMP_UNIT") {
            FirebaseCloudSyncManager.pushFullUnitToCloud(
                unitNumber = normalizedUnit,
                tower = tower,
                description = description,
                status = finalStatus,
                owner = owner,
                rate = rate,
                imageUri = finalImageUri,
                context = appContext
            )
        }
        FirebaseCloudSyncManager.notifyCloudUpdate()
        return rowId
    }

    fun updateUnitStatus(unitNumber: String, status: String, syncToCloud: Boolean = true): Int {
        val db = this.writableDatabase
        val cleanUnit = unitNumber.replace("Unit", "", ignoreCase = true).trim()
        val normalizedUnit = "Unit $cleanUnit"

        var existingTower = "Azure Tower"
        var existingOwner = "Admin Owner"
        var existingRate = 2500.0
        var existingImage = ""
        var existingDesc = "1 Bedroom"

        try {
            val cursor = db.rawQuery(
                "SELECT $COLUMN_TOWER, $COLUMN_OWNER, $COLUMN_RATE, $COLUMN_IMAGE, $COLUMN_DESCRIPTION FROM $TABLE_UNITS WHERE $COLUMN_UNIT_NUMBER = ? OR $COLUMN_UNIT_NUMBER = ? OR $COLUMN_UNIT_NUMBER = ?",
                arrayOf(normalizedUnit, unitNumber, cleanUnit)
            )
            if (cursor.moveToFirst()) {
                existingTower = cursor.getString(0) ?: "Azure Tower"
                existingOwner = cursor.getString(1) ?: "Admin Owner"
                existingRate = cursor.getDouble(2)
                existingImage = cursor.getString(3) ?: ""
                existingDesc = cursor.getString(4) ?: "1 Bedroom"
            }
            cursor.close()
        } catch (_: Exception) {}

        val values = ContentValues().apply {
            put(COLUMN_STATUS, status)
        }
        val rows = db.update(
            TABLE_UNITS,
            values,
            "$COLUMN_UNIT_NUMBER = ? OR $COLUMN_UNIT_NUMBER = ? OR $COLUMN_UNIT_NUMBER = ?",
            arrayOf(normalizedUnit, unitNumber, cleanUnit)
        )

        if (rows <= 0) {
            insertUnit(
                unitNumber = normalizedUnit,
                tower = existingTower,
                status = status,
                owner = existingOwner,
                rate = existingRate,
                imageUri = existingImage,
                description = existingDesc,
                syncToCloud = syncToCloud
            )
        } else if (syncToCloud && cleanUnit.isNotBlank() && cleanUnit != "TEMP_UNIT") {
            FirebaseCloudSyncManager.pushUnitStatusToCloud(normalizedUnit, status)
        }
        FirebaseCloudSyncManager.notifyCloudUpdate()
        return rows
    }

    fun getAllUnits(): List<Map<String, Any>> {
        autoReleaseExpiredUnits()
        val list = mutableListOf<Map<String, Any>>()
        val db = this.writableDatabase
        var cursor = db.rawQuery("SELECT * FROM $TABLE_UNITS", null)

        if (!cursor.moveToFirst()) {
            cursor.close()
            seedDefaultUnits(db)
            cursor = db.rawQuery("SELECT * FROM $TABLE_UNITS", null)
        }

        if (cursor.moveToFirst()) {
            do {
                val numIdx = cursor.getColumnIndex(COLUMN_UNIT_NUMBER)
                val towIdx = cursor.getColumnIndex(COLUMN_TOWER)
                val statIdx = cursor.getColumnIndex(COLUMN_STATUS)
                val ownIdx = cursor.getColumnIndex(COLUMN_OWNER)
                val rateIdx = cursor.getColumnIndex(COLUMN_RATE)
                val imgIdx = cursor.getColumnIndex(COLUMN_IMAGE)
                val descIdx = cursor.getColumnIndex(COLUMN_DESCRIPTION)

                val uNum = if (numIdx != -1) cursor.getString(numIdx) ?: "" else ""
                val cleanImg = if (imgIdx != -1) cursor.getString(imgIdx) ?: "" else ""

                val unit = mapOf(
                    "unitNumber" to uNum,
                    "tower" to if (towIdx != -1) cursor.getString(towIdx) ?: "" else "",
                    "status" to if (statIdx != -1) cursor.getString(statIdx) ?: "" else "",
                    "owner" to if (ownIdx != -1) cursor.getString(ownIdx) ?: "" else "",
                    "rate" to if (rateIdx != -1) cursor.getDouble(rateIdx) ?: 0.0 else 0.0,
                    "image_uri" to cleanImg,
                    "description" to if (descIdx != -1) cursor.getString(descIdx) ?: "1 Bedroom" else "1 Bedroom"
                )
                list.add(unit)
            } while (cursor.moveToNext())
        }
        cursor.close()
        return list
    }

    fun deleteUnit(unitNumber: String): Int {
        val db = this.writableDatabase
        val cleanUnit = unitNumber.replace("Unit", "", ignoreCase = true).trim()
        val normalizedUnit = "Unit $cleanUnit"
        val count = db.delete(TABLE_UNITS, "$COLUMN_UNIT_NUMBER = ? OR $COLUMN_UNIT_NUMBER = ? OR $COLUMN_UNIT_NUMBER = ?", arrayOf(unitNumber, cleanUnit, normalizedUnit))
        FirebaseCloudSyncManager.notifyCloudUpdate()
        return count
    }

    fun autoReleaseExpiredUnits() {}
}