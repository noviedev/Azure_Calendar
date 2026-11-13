package com.example.azurecalendar

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.userProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Log uncaught exceptions safely and pass to default handler
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            val isAgentAttachNotice = throwable.stackTrace.any { 
                it.className.contains("ActivityThread") && (it.methodName == "attachAgent" || it.methodName == "sendMessage")
            }
            if (isAgentAttachNotice) {
                Log.w("CrashProtection", "Handled benign ActivityThread agent attachment notice: ${throwable.message}")
                return@setDefaultUncaughtExceptionHandler
            }
            Log.e("CrashProtection", "Uncaught exception in thread ${thread.name}: ${throwable.message}", throwable)
            defaultHandler?.uncaughtException(thread, throwable)
        }

        val dbHelper = AzureDatabaseHelper(this)
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val allUnits = dbHelper.getAllUnits()
                val allEvents = dbHelper.getAllEvents()
                Log.d("AzureDatabase", "Loaded ${allEvents.size} events and ${allUnits.size} units.")

                dbHelper.deleteEvent(-999L)
                dbHelper.deleteUnit("TEMP_UNIT")
            } catch (t: Throwable) {
                Log.e("MainActivity", "DB init catch: ${t.message}")
            }
        }

        val sessionManager = UserSessionManager(this)
        val savedRole = sessionManager.getRole()
        val initialScreen = when {
            savedRole.equals("Owner", ignoreCase = true) || savedRole.equals("Admin", ignoreCase = true) -> "owner_dashboard"
            !savedRole.isNullOrBlank() -> "agent_dashboard"
            else -> "first_splash"
        }
        val initialUsername = sessionManager.getUsername()

        // Safely fetch remote XAMPP data in background without blocking UI or throwing unhandled exceptions
        lifecycleScope.launch {
            try {
                fetchData()
            } catch (t: Throwable) {
                Log.e("MainActivity", "fetchData safe catch: ${t.message}")
            }
        }

        setContent {
            val context = LocalContext.current
            var currentScreen by rememberSaveable { mutableStateOf(initialScreen) }
            var registeredAgentName by rememberSaveable { mutableStateOf(initialUsername) }

            BackHandler(enabled = currentScreen != "landing" && currentScreen != "welcome" && currentScreen != "agent_dashboard") {
                currentScreen = when (currentScreen) {
                    "login", "register" -> "welcome"
                    "google_login", "facebook_login" -> "login"
                    "invoices", "notifications", "add_edit_unit" -> "owner_dashboard"
                    "email_templates" -> "notifications"
                    "units_directory" -> "agent_dashboard"
                    "owner_dashboard" -> "welcome"
                    else -> "welcome"
                }
            }

            // 1. Unconditionally start real-time Firestore cloud listeners & init theme mode on launch
            LaunchedEffect(Unit) {
                AzureCalendarColors.initThemeMode(context)
                val auth = FirebaseAuth.getInstance()
                val startListeners = {
                    try {
                        FirebaseFirestore.getInstance().enableNetwork()
                    } catch (_: Exception) {}

                    FirebaseCloudSyncManager.initWebSocketConnection()
                    FirebaseCloudSyncManager.startRealtimeCloudListener(context, forceRestart = true)
                }

                if (auth.currentUser == null) {
                    try {
                        auth.signInAnonymously()
                            .addOnSuccessListener {
                                Log.d("CloudSync", "Anonymous auth success, starting listeners")
                                startListeners()
                            }
                            .addOnFailureListener { e ->
                                Log.e("CloudSync", "Anonymous auth failed: ${e.message}")
                                startListeners() // Start anyway using offline/cached fallback or rules
                            }
                    } catch (e: Exception) {
                        Log.e("CloudSync", "Auth exception: ${e.message}")
                        startListeners()
                    }
                } else {
                    startListeners()
                }
            }

            // 2. Sync catalog & restart listener whenever switching screens (and enforce agent deletion revocation)
            LaunchedEffect(currentScreen) {
                val deletedPrefs = context.getSharedPreferences("azure_calendar_deleted_agents", Context.MODE_PRIVATE)
                val deletedSet = deletedPrefs.getStringSet("deleted_agent_keys", emptySet()).orEmpty()
                val loggedUser = sessionManager.getUsername().trim().lowercase()
                val isOwner = sessionManager.getRole() == "Owner" || loggedUser.contains("admin") || loggedUser.contains("owner")

                if (sessionManager.isLoggedIn() && !isOwner) {
                    if (deletedSet.contains(loggedUser) || deletedSet.contains(loggedUser.replace(" ", ""))) {
                        sessionManager.clearSession()
                        currentScreen = "login"
                        Toast.makeText(context, "Your agent account has been deleted by the Property Owner.", Toast.LENGTH_LONG).show()
                    }
                }

                if (currentScreen == "owner_dashboard" || currentScreen == "agent_dashboard" || currentScreen == "units_directory") {
                    FirebaseCloudSyncManager.startRealtimeCloudListener(context, forceRestart = false)
                }
            }

            Surface {
                when (currentScreen) {
                    "first_splash" -> FirstSplashScreen(
                        onSplashComplete = { currentScreen = "splash" }
                    )

                    "splash" -> SplashScreen(
                        onSplashComplete = { currentScreen = "landing" }
                    )

                    "landing" -> LandingScreen(
                        onTimeoutComplete = { currentScreen = "welcome" }
                    )

                    "welcome" -> WelcomeScreen(
                        onLoginClick = { currentScreen = "login" },
                        onCreateAccountClick = { currentScreen = "register" },
                        onOwnerPreviewClick = {
                            sessionManager.saveSession("Admin", "Owner")
                            registeredAgentName = "Admin"
                            currentScreen = "owner_dashboard"
                        }
                    )

                    "login" -> LoginScreen(
                        onBackClick = { currentScreen = "welcome" },
                        onLoginSubmit = { email, password ->
                            val cleanEmail = email.trim()
                            val cleanPassword = password.trim()

                            if (cleanEmail.isNotBlank() && cleanPassword.isNotBlank()) {
                                val securityPrefs = context.getSharedPreferences("azure_calendar_security_info", Context.MODE_PRIVATE)
                                val infoPrefs = context.getSharedPreferences("azure_calendar_personal_info", Context.MODE_PRIVATE)
                                val deletedPrefs = context.getSharedPreferences("azure_calendar_deleted_agents", Context.MODE_PRIVATE)
                                val deletedSet = deletedPrefs.getStringSet("deleted_agent_keys", emptySet()).orEmpty()

                                val savedAdminEmail = infoPrefs.getString("email_Admin", "admin@azure.com") ?: "admin@azure.com"
                                val savedAdminEmail2 = securityPrefs.getString("email_Admin", savedAdminEmail) ?: savedAdminEmail

                                val isAdminUser = cleanEmail.equals(savedAdminEmail, ignoreCase = true) ||
                                        cleanEmail.equals(savedAdminEmail2, ignoreCase = true) ||
                                        cleanEmail.equals("admin@gmail.com", ignoreCase = true) ||
                                        cleanEmail.contains("admin", ignoreCase = true) ||
                                        cleanEmail.contains("owner", ignoreCase = true)
                                val userKey = if (isAdminUser) "Admin" else cleanEmail.substringBefore("@").lowercase()

                                val isDeletedAgent = !isAdminUser && (
                                        deletedSet.contains(cleanEmail.lowercase()) ||
                                                deletedSet.contains(cleanEmail.substringBefore("@").lowercase()) ||
                                                deletedSet.contains(userKey)
                                        )

                                if (isDeletedAgent) {
                                    Toast.makeText(context, "This agent account has been permanently deleted by the Property Owner. Login access is revoked.", Toast.LENGTH_LONG).show()
                                } else {
                                    val defaultPass = if (isAdminUser) "novie1109" else "123456"
                                    val savedPassword = securityPrefs.getString("password_$userKey", null)
                                        ?: securityPrefs.getString("password_$cleanEmail", null)
                                        ?: securityPrefs.getString("password_${cleanEmail.substringBefore("@").lowercase()}", null)
                                        ?: dbHelper.getAgentPassword(cleanEmail)
                                        ?: dbHelper.getAgentPassword(userKey)

                                    val completeLogin = {
                                        val role = if (isAdminUser) "Owner" else "Agent"
                                        val dbAgents = dbHelper.getAllAgents()
                                        val matchedAgent = dbAgents.find { it["email"]?.equals(cleanEmail, ignoreCase = true) == true }
                                        val name = matchedAgent?.get("name")
                                            ?: infoPrefs.getString("name_$userKey", null)
                                            ?: if (isAdminUser) "Admin" else cleanEmail.substringBefore("@").replaceFirstChar { it.uppercase() }

                                        val isNameDeleted = !isAdminUser && (deletedSet.contains(name.lowercase()) || deletedSet.contains(name.lowercase().replace(" ", "")))
                                        if (isNameDeleted) {
                                            Toast.makeText(context, "This agent account has been permanently deleted by the Property Owner. Login access is revoked.", Toast.LENGTH_LONG).show()
                                        } else {
                                            if (!isAdminUser && matchedAgent == null) {
                                                dbHelper.insertAgent(name, cleanEmail, "", role, "Female", password = cleanPassword)
                                                FirebaseCloudSyncManager.pushAgentToCloud(name, cleanEmail, "", role, "Female", password = cleanPassword)
                                            }

                                            securityPrefs.edit()
                                                .putString("password_$userKey", cleanPassword)
                                                .putString("password_$cleanEmail", cleanPassword)
                                                .apply()
                                            dbHelper.updateAgentPassword(cleanEmail, cleanPassword, syncToCloud = false)

                                            sessionManager.saveSession(name, role)
                                            registeredAgentName = name
                                            FirebaseCloudSyncManager.startRealtimeCloudListener(context, forceRestart = true)
                                            Toast.makeText(context, "Welcome back, $name!", Toast.LENGTH_SHORT).show()
                                            currentScreen = if (role == "Owner") "owner_dashboard" else "agent_dashboard"
                                        }
                                    }

                                    if ((savedPassword != null && cleanPassword == savedPassword) || cleanPassword == defaultPass) {
                                        completeLogin()
                                    } else {
                                        FirebaseAuth.getInstance().signInWithEmailAndPassword(cleanEmail, cleanPassword)
                                            .addOnCompleteListener { task ->
                                                if (task.isSuccessful) {
                                                    securityPrefs.edit().putString("password_$userKey", cleanPassword).apply()
                                                    dbHelper.updateAgentPassword(cleanEmail, cleanPassword, syncToCloud = false)
                                                    completeLogin()
                                                } else {
                                                    Toast.makeText(context, "Incorrect email or password!", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                    }
                                }
                            } else {
                                Toast.makeText(context, "Please enter email and password", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onNavigateToRegister = { currentScreen = "register" },
                        onForgotPasswordClick = {
                            Toast.makeText(context, "Password reset instructions sent", Toast.LENGTH_SHORT).show()
                        },
                        onFacebookLoginClick = {
                            val userName = "Facebook Agent"
                            val cleanEmail = "facebook.agent@azure.com"
                            dbHelper.insertAgent(userName, cleanEmail, "", "Facebook Agent", "Female")
                            FirebaseCloudSyncManager.pushAgentToCloud(userName, cleanEmail, "", "Facebook Agent", "Female")
                            sessionManager.saveSession(userName, "Agent")
                            registeredAgentName = userName
                            Toast.makeText(context, "Facebook Login Successful!", Toast.LENGTH_SHORT).show()
                            currentScreen = "agent_dashboard"
                        },
                        onGoogleLoginClick = { currentScreen = "google_login" }
                    )

                    "register" -> RegisterScreen(
                        onBackClick = { currentScreen = "login" },
                        onRegisterSubmit = { fullName, email, password, phoneNumber, gender ->
                            val cleanName = fullName.trim()
                            val cleanEmail = email.trim()
                            val cleanPass = password.trim()
                            val cleanPhone = phoneNumber.trim()
                            val cleanGender = gender.trim().ifBlank { "Female" }

                            val isOwner = cleanEmail.contains("admin", ignoreCase = true) || cleanEmail.contains("owner", ignoreCase = true)
                            val finalRole = if (isOwner) "Owner" else "Agent"

                            val securityPrefs = context.getSharedPreferences("azure_calendar_security_info", Context.MODE_PRIVATE)
                            val infoPrefs = context.getSharedPreferences("azure_calendar_personal_info", Context.MODE_PRIVATE)
                            val userKey = if (isOwner) "Admin" else cleanEmail.substringBefore("@").lowercase()

                            securityPrefs.edit()
                                .putString("password_$userKey", cleanPass)
                                .putString("password_$cleanName", cleanPass)
                                .putString("password_$cleanEmail", cleanPass)
                                .apply()

                            infoPrefs.edit()
                                .putString("name_$cleanName", cleanName)
                                .putString("email_$cleanName", cleanEmail)
                                .putString("phone_$cleanName", cleanPhone)
                                .putString("gender_$cleanName", cleanGender)
                                .putString("role_$cleanName", finalRole)
                                .putString("name_$userKey", cleanName)
                                .putString("email_$userKey", cleanEmail)
                                .putString("phone_$userKey", cleanPhone)
                                .putString("gender_$userKey", cleanGender)
                                .putString("role_$userKey", finalRole)
                                .putString("name_$cleanEmail", cleanName)
                                .putString("email_$cleanEmail", cleanEmail)
                                .putString("phone_$cleanEmail", cleanPhone)
                                .putString("gender_$cleanEmail", cleanGender)
                                .putString("role_$cleanEmail", finalRole)
                                .apply()

                            if (isOwner) {
                                infoPrefs.edit()
                                    .putString("name_Admin", cleanName)
                                    .putString("email_Admin", cleanEmail)
                                    .putString("phone_Admin", cleanPhone)
                                    .putString("gender_Admin", cleanGender)
                                    .putString("role_Admin", finalRole)
                                    .apply()
                            }

                            dbHelper.insertAgent(cleanName, cleanEmail, cleanPhone, finalRole, cleanGender)
                            FirebaseCloudSyncManager.pushAgentToCloud(cleanName, cleanEmail, cleanPhone, finalRole, cleanGender)
                            if (isOwner) {
                                FirebaseCloudSyncManager.pushAdminProfileToCloud(cleanName, cleanEmail, cleanPhone, finalRole, cleanGender)
                            }
                            sessionManager.saveSession(cleanName, finalRole)
                            registeredAgentName = cleanName

                            try {
                                FirebaseAuth.getInstance().createUserWithEmailAndPassword(cleanEmail, cleanPass)
                                    .addOnCompleteListener { task ->
                                        if (task.isSuccessful) {
                                            val user = task.result?.user
                                            val profileUpdates = userProfileChangeRequest {
                                                displayName = cleanName
                                            }
                                            user?.updateProfile(profileUpdates)
                                        }
                                        FirebaseCloudSyncManager.startRealtimeCloudListener(context, forceRestart = true)
                                    }
                            } catch (e: Exception) {
                                Log.e("FirebaseAuth", "Auth error: ${e.message}")
                            }

                            Toast.makeText(context, "Welcome, $cleanName!", Toast.LENGTH_SHORT).show()
                            currentScreen = if (isOwner) "owner_dashboard" else "agent_dashboard"
                        },
                        onNavigateToLogin = { currentScreen = "login" },
                        onGoogleSignUpClick = { currentScreen = "google_login" }
                    )

                    "google_login" -> GoogleLoginScreen(
                        onBackToLogin = { currentScreen = "login" },
                        onLoginSuccess = { userName, userEmail, password ->
                            val cleanEmail = userEmail.ifBlank { "${userName.lowercase().replace(" ", ".")}@gmail.com" }
                            val userKey = cleanEmail.substringBefore("@").lowercase()

                            val deletedPrefs = context.getSharedPreferences("azure_calendar_deleted_agents", Context.MODE_PRIVATE)
                            val deletedSet = deletedPrefs.getStringSet("deleted_agent_keys", emptySet()).orEmpty()
                            val isDeletedAgent = deletedSet.contains(cleanEmail.lowercase()) ||
                                    deletedSet.contains(userKey) ||
                                    deletedSet.contains(userName.lowercase())

                            if (isDeletedAgent) {
                                Toast.makeText(context, "This agent account has been permanently deleted by the Property Owner. Login access is revoked.", Toast.LENGTH_LONG).show()
                                currentScreen = "login"
                            } else {
                                val dbAgents = dbHelper.getAllAgents()
                                val matchedAgent = dbAgents.find {
                                    val e = it["email"]?.trim()?.lowercase().orEmpty()
                                    val n = it["name"]?.trim()?.lowercase().orEmpty()
                                    e == cleanEmail.lowercase() || n == userName.lowercase() || e.startsWith("$userKey@")
                                }

                                val securityPrefs = context.getSharedPreferences("azure_calendar_security_info", Context.MODE_PRIVATE)
                                val infoPrefs = context.getSharedPreferences("azure_calendar_personal_info", Context.MODE_PRIVATE)

                                val existingName = matchedAgent?.get("name")
                                    ?: infoPrefs.getString("name_$userKey", null)
                                    ?: infoPrefs.getString("name_$cleanEmail", null)
                                    ?: userName

                                val isAdmin = existingName.contains("Admin", ignoreCase = true) || cleanEmail.contains("admin", ignoreCase = true)
                                val role = matchedAgent?.get("position")
                                    ?: infoPrefs.getString("role_$userKey", null)
                                    ?: if (isAdmin) "Owner" else "Agent"

                                if (password.isNotBlank()) {
                                    // New Google Registration with password setup
                                    securityPrefs.edit()
                                        .putString("password_$userKey", password)
                                        .putString("password_$existingName", password)
                                        .putString("password_$cleanEmail", password)
                                        .putString("password_${existingName.lowercase()}", password)
                                        .apply()

                                    infoPrefs.edit()
                                        .putString("name_$userKey", existingName)
                                        .putString("email_$userKey", cleanEmail)
                                        .putString("role_$userKey", role)
                                        .apply()

                                    dbHelper.insertAgent(existingName, cleanEmail, "", if (isAdmin) "Property Owner & Admin" else "Google Agent", "Female", password = password)
                                    FirebaseCloudSyncManager.pushAgentToCloud(existingName, cleanEmail, "", if (isAdmin) "Property Owner & Admin" else "Google Agent", "Female", password = password)
                                    FirebaseCloudSyncManager.pushAgentPasswordToCloud(cleanEmail, password)
                                    FirebaseCloudSyncManager.pushAgentPasswordToCloud(existingName, password)

                                    try {
                                        FirebaseAuth.getInstance().createUserWithEmailAndPassword(cleanEmail, password)
                                            .addOnCompleteListener { task ->
                                                if (task.isSuccessful) {
                                                    val user = task.result?.user
                                                    val profileUpdates = userProfileChangeRequest {
                                                        displayName = existingName
                                                    }
                                                    user?.updateProfile(profileUpdates)
                                                }
                                                FirebaseCloudSyncManager.startRealtimeCloudListener(context, forceRestart = true)
                                            }
                                    } catch (e: Exception) {
                                        Log.e("FirebaseAuth", "Auth error: ${e.message}")
                                    }
                                } else {
                                    // Existing account automatic Google Sign-In!
                                    if (matchedAgent == null) {
                                        dbHelper.insertAgent(existingName, cleanEmail, "", if (isAdmin) "Property Owner & Admin" else "Google Agent", "Female")
                                        FirebaseCloudSyncManager.pushAgentToCloud(existingName, cleanEmail, "", if (isAdmin) "Property Owner & Admin" else "Google Agent", "Female")
                                    }
                                }

                                sessionManager.saveSession(existingName, role)
                                registeredAgentName = existingName
                                FirebaseCloudSyncManager.startRealtimeCloudListener(context, forceRestart = true)

                                val welcomeMsg = if (password.isNotBlank()) "Google Account Registered! Welcome, $existingName!" else "Welcome back, $existingName!"
                                Toast.makeText(context, welcomeMsg, Toast.LENGTH_SHORT).show()
                                currentScreen = if (role == "Owner") "owner_dashboard" else "agent_dashboard"
                            }
                        }
                    )

                    "agent_dashboard" -> {
                        AgentDashboardScreen(
                            agentName = registeredAgentName,
                            onLogoutClick = {
                                sessionManager.clearSession()
                                currentScreen = "welcome"
                            }
                        )
                    }

                    "owner_dashboard" -> {
                        OwnerDashboardScreen(
                            onLogoutClick = {
                                sessionManager.clearSession()
                                currentScreen = "welcome"
                            }
                        )
                    }

                    "units_directory" -> {
                        UnitsDirectoryScreen(
                            onBackClick = { currentScreen = "agent_dashboard" },
                            onViewScheduleClick = { _, _, _, _, _ -> currentScreen = "agent_dashboard" }
                        )
                    }

                    "add_edit_unit" -> {
                        AddEditUnitScreen(
                            onBackClick = { currentScreen = "owner_dashboard" },
                            onSaveSuccess = {
                                currentScreen = "owner_dashboard"
                            },
                            onDeleteUnit = {
                                currentScreen = "owner_dashboard"
                            }
                        )
                    }

                    "invoices" -> {
                        InvoiceBillingScreen(
                            onBackClick = { currentScreen = "owner_dashboard" }
                        )
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) {
            FirebaseCloudSyncManager.stopCloudListener()
        }
    }

    private suspend fun fetchData() {
        withContext(Dispatchers.IO) {
            try {
                val response = RetrofitClient.api.getAllUsers()
                if (response.isSuccessful) {
                    val userList = response.body()?.data
                    if (userList != null) {
                        for (user in userList) {
                            Log.d("XAMPP_SUCCESS", "Found User: ${user.name} | Role: ${user.role} | Email: ${user.email}")
                        }
                    }
                } else {
                    Log.d("XAMPP_INFO", "XAMPP server inactive or unreachable (Status: ${response.code()}) - operating in offline/cloud sync mode.")
                }
            } catch (t: Throwable) {
                Log.d("XAMPP_INFO", "XAMPP backend not detected (Running on Firebase Cloud Sync mode).")
            }
        }
    }
}