package com.example.azurecalendar

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class AzureDatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "azure_calendar.db"
        private const val DATABASE_VERSION = 6

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
                "$COLUMN_AGENT_POSITION TEXT)")

        db.execSQL(createEventsTable)
        db.execSQL(createUnitsTable)
        db.execSQL(createAgentsTable)

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

    // --- Agents CRUD ---
    fun insertAgent(
        name: String,
        email: String,
        phone: String = "",
        role: String = "Property Agent",
        gender: String = "Female",
        syncToCloud: Boolean = true
    ): Long {
        val db = this.writableDatabase
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
        }
        val rowId = db.insert(TABLE_AGENTS, null, values)

        if (syncToCloud && name.isNotBlank() && email.isNotBlank()) {
            FirebaseCloudSyncManager.pushAgentToCloud(name, email, phone, role, gender)
        }
        return rowId
    }

    fun getAllAgents(): List<Map<String, String>> {
        val list = mutableListOf<Map<String, String>>()
        val db = this.readableDatabase
        val cursor = db.rawQuery("SELECT * FROM $TABLE_AGENTS", null)
        val sampleEmails = setOf("alex.reyes@azure.com", "apple.u@azure.com", "john.dc@azure.com", "maria.santos@azure.com", "elena.r@azure.com")

        if (cursor.moveToFirst()) {
            do {
                val idIdx = cursor.getColumnIndex(COLUMN_AGENT_ID)
                val nameIdx = cursor.getColumnIndex(COLUMN_AGENT_NAME)
                val emailIdx = cursor.getColumnIndex(COLUMN_AGENT_EMAIL)
                val genderIdx = cursor.getColumnIndex(COLUMN_AGENT_GENDER)
                val posIdx = cursor.getColumnIndex(COLUMN_AGENT_POSITION)

                val emailStr = if (emailIdx != -1) cursor.getString(emailIdx) ?: "" else ""
                if (emailStr.lowercase() in sampleEmails) continue

                val agent = mapOf(
                    "id" to if (idIdx != -1) cursor.getLong(idIdx).toString() else "0",
                    "name" to if (nameIdx != -1) cursor.getString(nameIdx) ?: "" else "",
                    "email" to emailStr,
                    "gender" to if (genderIdx != -1) cursor.getString(genderIdx) ?: "" else "",
                    "position" to if (posIdx != -1) cursor.getString(posIdx) ?: "" else ""
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
            FirebaseCloudSyncManager.deleteAgentFromCloud(email)
        }
        return rows
    }

    // --- Events CRUD ---
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
        val cleanUnit = unit.replace("Unit", "", ignoreCase = true).trim()
        val normalizedUnit = "Unit $cleanUnit"

        db.delete(
            TABLE_EVENTS,
            "$COLUMN_DATE = ? AND ($COLUMN_UNIT LIKE ? OR $COLUMN_UNIT LIKE ? OR $COLUMN_UNIT = ?)",
            arrayOf(date, "%$cleanUnit%", "%$normalizedUnit%", unit)
        )

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

        // Automatically push to Firebase Firestore whenever a user adds/updates an event!
        if (syncToCloud && cleanUnit.isNotBlank() && date.isNotBlank() && title != "TEMP") {
            FirebaseCloudSyncManager.pushEventToCloud(
                mapOf(
                    "title" to title,
                    "date" to date,
                    "time" to time,
                    "agent" to agent,
                    "unit" to normalizedUnit,
                    "shape" to shape.ifBlank { "Reserved" },
                    "color" to color.toString()
                )
            )
        }
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
        return list
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
                mapOf(
                    "title" to title,
                    "date" to date,
                    "time" to time,
                    "agent" to agent,
                    "unit" to normalizedUnit,
                    "shape" to shape.ifBlank { "Reserved" },
                    "color" to color.toString()
                )
            )
        }
        return rows
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

        db.delete(
            TABLE_UNITS,
            "$COLUMN_UNIT_NUMBER = ? OR $COLUMN_UNIT_NUMBER = ? OR $COLUMN_UNIT_NUMBER = ?",
            arrayOf(unitNumber, cleanUnit, "Unit$cleanUnit")
        )

        val values = ContentValues().apply {
            put(COLUMN_UNIT_NUMBER, normalizedUnit)
            put(COLUMN_TOWER, tower)
            put(COLUMN_STATUS, status)
            put(COLUMN_OWNER, owner)
            put(COLUMN_RATE, rate)
            put(COLUMN_IMAGE, imageUri)
            put(COLUMN_DESCRIPTION, description)
        }
        val rowId = db.insertWithOnConflict(TABLE_UNITS, null, values, SQLiteDatabase.CONFLICT_REPLACE)

        if (syncToCloud && cleanUnit.isNotBlank() && cleanUnit != "TEMP_UNIT") {
            FirebaseCloudSyncManager.pushFullUnitToCloud(
                unitNumber = normalizedUnit,
                tower = tower,
                description = description,
                status = status,
                owner = owner,
                rate = rate,
                imageUri = imageUri
            )
        }
        return rowId
    }

    fun updateUnitStatus(unitNumber: String, status: String, syncToCloud: Boolean = true): Int {
        val db = this.writableDatabase
        val cleanUnit = unitNumber.replace("Unit", "", ignoreCase = true).trim()
        val normalizedUnit = "Unit $cleanUnit"

        val values = ContentValues().apply {
            put(COLUMN_STATUS, status)
        }
        val rows = db.update(
            TABLE_UNITS,
            values,
            "$COLUMN_UNIT_NUMBER = ? OR $COLUMN_UNIT_NUMBER = ? OR $COLUMN_UNIT_NUMBER LIKE ?",
            arrayOf(unitNumber, normalizedUnit, "%$cleanUnit%")
        )

        if (rows <= 0) {
            insertUnit(
                unitNumber = normalizedUnit,
                tower = "Azure Tower",
                status = status,
                owner = "Admin Owner",
                rate = 2500.0,
                imageUri = "",
                description = "1 Bedroom",
                syncToCloud = syncToCloud
            )
        } else if (syncToCloud && cleanUnit.isNotBlank() && cleanUnit != "TEMP_UNIT") {
            FirebaseCloudSyncManager.pushUnitStatusToCloud(normalizedUnit, status)
        }
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

                val unit = mapOf(
                    "unitNumber" to if (numIdx != -1) cursor.getString(numIdx) ?: "" else "",
                    "tower" to if (towIdx != -1) cursor.getString(towIdx) ?: "" else "",
                    "status" to if (statIdx != -1) cursor.getString(statIdx) ?: "" else "",
                    "owner" to if (ownIdx != -1) cursor.getString(ownIdx) ?: "" else "",
                    "rate" to if (rateIdx != -1) cursor.getDouble(rateIdx) else 0.0,
                    "image_uri" to if (imgIdx != -1) cursor.getString(imgIdx) ?: "" else "",
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
        return db.delete(TABLE_UNITS, "$COLUMN_UNIT_NUMBER = ? OR $COLUMN_UNIT_NUMBER = ? OR $COLUMN_UNIT_NUMBER = ?", arrayOf(unitNumber, cleanUnit, normalizedUnit))
    }

    fun autoReleaseExpiredUnits() {
        // Safe query helper -- read-only queries should never delete database records automatically.
    }
}