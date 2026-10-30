package com.example.azurecalendar

import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.azurecalendar.ui.theme.AzureCalendarTheme

private val PurplePrimary = Color(0xFF4C2B9E)
private val ScreenBg = Color(0xFFFAF5FC)
private val CardBg = Color(0xFFFFFFFF)
private val TextDark = Color(0xFF2C1A36)
private val TextMuted = Color(0xFF8C73A6)
private val BorderPurple = Color(0xFFE2D6F5)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditUnitScreen(
    unitId: String? = null,
    initialTowerName: String = "",
    initialUnitNumber: String = "",
    initialBrType: String = "",
    initialStatus: String = "Available",
    initialRate: String = "2500",
    initialImageUri: String = "",
    @Suppress("UNUSED_PARAMETER") initialAssignedAgent: String = "",
    onBackClick: () -> Unit = {},
    onSaveSuccess: () -> Unit = {},
    onDeleteUnit: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val isPreview = LocalInspectionMode.current
    val isEditMode = !unitId.isNullOrEmpty()

    var unitNumber by remember(initialUnitNumber) { mutableStateOf(initialUnitNumber) }
    var buildingName by remember(initialTowerName) { mutableStateOf(initialTowerName) }
    var selectedBrType by remember(initialBrType) { mutableStateOf(if (initialBrType.isNotBlank()) initialBrType else "1 Bedroom") }
    var selectedStatus by remember(initialStatus) { mutableStateOf(initialStatus) }
    var unitRate by remember(initialRate) { mutableStateOf(if (initialRate.isNotBlank()) initialRate else "2500") }
    var selectedImageUri by remember(initialImageUri) {
        mutableStateOf<Uri?>(if (initialImageUri.isNotBlank()) Uri.parse(initialImageUri) else null)
    }
    var savedImagePathStr by remember(initialImageUri) { mutableStateOf(initialImageUri) }
    var selectedBitmap by remember { mutableStateOf<ImageBitmap?>(null) }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedImageUri = uri
            val formattedUnit = if (unitNumber.startsWith("Unit", ignoreCase = true)) unitNumber else "Unit $unitNumber"
            val permanentPath = saveUnitPhotoToInternalStorage(context, uri, formattedUnit.ifBlank { "new_unit" })
            if (permanentPath != null) {
                savedImagePathStr = permanentPath
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val source = ImageDecoder.createSource(context.contentResolver, uri)
                    selectedBitmap = ImageDecoder.decodeBitmap(source).asImageBitmap()
                } else {
                    @Suppress("DEPRECATION")
                    val bitmap = MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                    selectedBitmap = bitmap.asImageBitmap()
                }
                Toast.makeText(context, "Photo uploaded & saved permanently!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Photo selected!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    var brDropdownExpanded by remember { mutableStateOf(false) }
    val brOptions = listOf("1 Bedroom", "2 Bedroom", "3 Bedroom", "Studio")

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor = CardBg,
        unfocusedContainerColor = CardBg,
        focusedBorderColor = BorderPurple,
        unfocusedBorderColor = BorderPurple,
        focusedLabelColor = PurplePrimary,
        unfocusedLabelColor = TextMuted
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ScreenBg)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(modifier = Modifier.height(4.dp))

        // Back Button & Header
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
                text = if (isEditMode) "Edit Unit" else "Add Unit",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = TextDark
            )
        }

        // Add Photo Container Box
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFFF8F5FE))
                .border(1.dp, BorderPurple, RoundedCornerShape(16.dp))
                .clickable {
                    if (!isPreview) {
                        try {
                            galleryLauncher.launch("image/*")
                        } catch (e: Exception) {
                            Toast.makeText(context, "Opening gallery...", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            if (selectedBitmap != null) {
                Image(
                    bitmap = selectedBitmap!!,
                    contentDescription = "Uploaded Unit Photo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = "Add Photo",
                        tint = PurplePrimary,
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Add Photo",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = PurplePrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Tap to upload or choose from gallery",
                        fontSize = 11.sp,
                        color = TextMuted
                    )
                }
            }
        }

        // Unit Number
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(text = "Unit Number", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextDark)
            OutlinedTextField(
                value = unitNumber,
                onValueChange = { unitNumber = it },
                placeholder = { Text("Enter unit number", fontSize = 14.sp, color = TextMuted) },
                shape = RoundedCornerShape(14.dp),
                colors = fieldColors,
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Building Name
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(text = "Building Name", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextDark)
            OutlinedTextField(
                value = buildingName,
                onValueChange = { buildingName = it },
                placeholder = { Text("Enter building name", fontSize = 14.sp, color = TextMuted) },
                shape = RoundedCornerShape(14.dp),
                colors = fieldColors,
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Type of Bedroom Dropdown
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(text = "Type of Bedroom", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextDark)
            ExposedDropdownMenuBox(
                expanded = brDropdownExpanded,
                onExpandedChange = { brDropdownExpanded = !brDropdownExpanded },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = selectedBrType,
                    onValueChange = {},
                    readOnly = true,
                    placeholder = { Text("Select type", fontSize = 14.sp, color = TextMuted) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = brDropdownExpanded) },
                    shape = RoundedCornerShape(14.dp),
                    colors = fieldColors,
                    modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true).fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = brDropdownExpanded,
                    onDismissRequest = { brDropdownExpanded = false }
                ) {
                    brOptions.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option) },
                            onClick = {
                                selectedBrType = option
                                brDropdownExpanded = false
                            }
                        )
                    }
                }
            }
        }

        // Unit Status / Remark Dropdown
        var statusDropdownExpanded by remember { mutableStateOf(false) }
        val statusOptions = listOf("Available", "Rented (6+ Months Lease)", "Under Maintenance", "Reserved")

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(text = "Unit Status / Remark", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextDark)
            ExposedDropdownMenuBox(
                expanded = statusDropdownExpanded,
                onExpandedChange = { statusDropdownExpanded = !statusDropdownExpanded },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = selectedStatus,
                    onValueChange = {},
                    readOnly = true,
                    placeholder = { Text("Select status remark", fontSize = 14.sp, color = TextMuted) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = statusDropdownExpanded) },
                    shape = RoundedCornerShape(14.dp),
                    colors = fieldColors,
                    modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true).fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = statusDropdownExpanded,
                    onDismissRequest = { statusDropdownExpanded = false }
                ) {
                    statusOptions.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option) },
                            onClick = {
                                selectedStatus = option
                                statusDropdownExpanded = false
                            }
                        )
                    }
                }
            }
        }

        // Billing Rate / Invoice Price Field
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(text = "Billing Rate / Price (₱)", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextDark)
            OutlinedTextField(
                value = unitRate,
                onValueChange = { if (it.all { c -> c.isDigit() || c == '.' }) unitRate = it },
                placeholder = { Text("Enter rate e.g. 2500", fontSize = 14.sp, color = TextMuted) },
                shape = RoundedCornerShape(14.dp),
                colors = fieldColors,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Save Changes / Add Unit Button
        // Replace your Save Changes / Add Unit Button onClick in AddEditUnitScreen.kt:
        Button(
            onClick = {
                if (unitNumber.isBlank() || buildingName.isBlank()) {
                    Toast.makeText(context, "Please enter Unit Number and Building Name", Toast.LENGTH_SHORT).show()
                } else {
                    val dbHelper = AzureDatabaseHelper(context)
                    val cleanNew = unitNumber.replace("Unit", "", ignoreCase = true).trim()
                    val formattedUnitNumber = "Unit $cleanNew"

                    val cleanOld = initialUnitNumber.replace("Unit", "", ignoreCase = true).trim()
                    val formattedOldUnitNumber = "Unit $cleanOld"

                    // If editing and the unit number was changed, delete the old unit first!
                    if (isEditMode && cleanOld.isNotBlank() && !cleanOld.equals(cleanNew, ignoreCase = true)) {
                        dbHelper.deleteUnit(formattedOldUnitNumber)
                        FirebaseCloudSyncManager.deleteUnitFromCloud(context, formattedOldUnitNumber)
                    }

                    val imgUriStr = if (savedImagePathStr.isNotBlank()) savedImagePathStr else (selectedImageUri?.toString() ?: "")
                    val rateVal = unitRate.toDoubleOrNull() ?: 2500.0

                    val deletedPrefs = context.getSharedPreferences("azure_calendar_deleted_units", android.content.Context.MODE_PRIVATE)
                    val currentDeletedSet = deletedPrefs.getStringSet("deleted_unit_numbers", emptySet()).orEmpty().toMutableSet()
                    if (currentDeletedSet.removeAll { isSameUnit(it, cleanNew) || isSameUnit(it, formattedUnitNumber) }) {
                        deletedPrefs.edit().putStringSet("deleted_unit_numbers", currentDeletedSet).apply()
                    }

                    // 1. Save locally in SQLite
                    dbHelper.insertUnit(
                        unitNumber = formattedUnitNumber,
                        tower = buildingName.ifBlank { "Azure Tower" },
                        status = selectedStatus.ifBlank { "Available" },
                        owner = "Owner",
                        rate = rateVal,
                        imageUri = imgUriStr,
                        description = selectedBrType.ifBlank { "1 Bedroom" }
                    )

                    // 2. Push to Cloud Firebase
                    FirebaseCloudSyncManager.pushFullUnitToCloud(
                        unitNumber = formattedUnitNumber,
                        tower = buildingName.ifBlank { "Azure Tower" },
                        description = selectedBrType.ifBlank { "1 Bedroom" },
                        status = selectedStatus.ifBlank { "Available" },
                        owner = "Owner",
                        rate = rateVal,
                        imageUri = imgUriStr
                    )

                    if (selectedStatus.contains("Available", ignoreCase = true) || selectedStatus.contains("Maintenance", ignoreCase = true)) {
                        dbHelper.deleteEventsForUnit(formattedUnitNumber)
                    }

                    Toast.makeText(
                        context,
                        if (isEditMode) "Unit Updated Successfully!" else "Unit $formattedUnitNumber Added Successfully!",
                        Toast.LENGTH_SHORT
                    ).show()
                    onSaveSuccess()
                }
            },
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            Text(
                text = if (isEditMode) "Save Changes" else "Add Unit",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }

        var showDeleteConfirmDialog by remember { mutableStateOf(false) }

        if (isEditMode) {
            Spacer(modifier = Modifier.height(10.dp))

            OutlinedButton(
                onClick = { showDeleteConfirmDialog = true },
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.5.dp, Color(0xFFE53935)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFE53935)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Icon(Icons.Default.Delete, contentDescription = "Delete Unit", modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Delete Unit", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }

        if (showDeleteConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirmDialog = false },
                title = { Text("Delete Unit $unitNumber", fontWeight = FontWeight.Bold, color = TextDark) },
                text = { Text("Are you sure you want to delete Unit $unitNumber? This action cannot be undone.", color = TextMuted) },
                confirmButton = {
                    Button(
                        onClick = {
                            val dbHelper = AzureDatabaseHelper(context)
                            val formattedUnitNumber = if (unitNumber.startsWith("Unit", ignoreCase = true)) unitNumber else "Unit $unitNumber"

                            // 1. Delete locally from SQLite
                            dbHelper.deleteUnit(formattedUnitNumber)

                            // 2. FIX: Delete from Firebase cloud so agents instantly purge it too!
                            FirebaseCloudSyncManager.deleteUnitFromCloud(context, formattedUnitNumber)

                            val deletedPrefs = context.getSharedPreferences("azure_calendar_deleted_units", android.content.Context.MODE_PRIVATE)
                            val currentSet = deletedPrefs.getStringSet("deleted_unit_numbers", emptySet()).orEmpty().toMutableSet()
                            currentSet.add(formattedUnitNumber)
                            currentSet.add(formattedUnitNumber.replace("Unit", "", ignoreCase = true).trim())
                            deletedPrefs.edit().putStringSet("deleted_unit_numbers", currentSet).apply()

                            Toast.makeText(context, "Unit $formattedUnitNumber deleted successfully!", Toast.LENGTH_SHORT).show()
                            showDeleteConfirmDialog = false
                            onDeleteUnit(formattedUnitNumber)
                            onSaveSuccess()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935))
                    ) {
                        Text("Delete", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirmDialog = false }) {
                        Text("Cancel", color = TextMuted)
                    }
                },
                containerColor = Color.White,
                shape = RoundedCornerShape(20.dp)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}

@Preview(showBackground = true)
@Composable
fun AddEditUnitScreenPreview() {
    AzureCalendarTheme {
        AddEditUnitScreen()
    }
}

fun saveUnitPhotoToInternalStorage(context: android.content.Context, uri: Uri, unitName: String): String? {
    return try {
        val fileName = "unit_photo_${unitName.replace(" ", "_").lowercase()}.jpg"
        val inputStream = context.contentResolver.openInputStream(uri) ?: return null
        val originalBitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
        inputStream.close()

        if (originalBitmap == null) return null

        val maxDim = 1024
        val width = originalBitmap.width
        val height = originalBitmap.height
        val scale = if (width > height) maxDim.toFloat() / width else maxDim.toFloat() / height
        val scaledWidth = (width * scale).toInt().coerceAtLeast(1)
        val scaledHeight = (height * scale).toInt().coerceAtLeast(1)

        val scaledBitmap = if (width > maxDim || height > maxDim) {
            android.graphics.Bitmap.createScaledBitmap(originalBitmap, scaledWidth, scaledHeight, true)
        } else {
            originalBitmap
        }

        val file = java.io.File(context.filesDir, fileName)
        val outputStream = java.io.FileOutputStream(file)
        scaledBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, outputStream)
        outputStream.flush()
        outputStream.close()

        if (scaledBitmap != originalBitmap) {
            scaledBitmap.recycle()
        }
        originalBitmap.recycle()

        file.absolutePath
    } catch (_: Throwable) {
        null
    }
}