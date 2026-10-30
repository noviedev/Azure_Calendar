package com.example.azurecalendar

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.userProfileChangeRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Log uncaught exceptions and pass to default handler
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("CrashProtection", "Uncaught exception in thread ${thread.name}: ${throwable.message}", throwable)
            defaultHandler?.uncaughtException(thread, throwable)
        }

        val dbHelper = AzureDatabaseHelper(this)
        try {
            val allUnits = dbHelper.getAllUnits()
            val allEvents = dbHelper.getAllEvents()
            Log.d("AzureDatabase", "Loaded ${allEvents.size} events and ${allUnits.size} units.")

            dbHelper.deleteEvent(-999L)
            dbHelper.deleteUnit("TEMP_UNIT")
        } catch (t: Throwable) {
            Log.e("MainActivity", "DB init catch: ${t.message}")
        }

        val sessionManager = UserSessionManager(this)
        val savedRole = sessionManager.getRole()
        val initialScreen = when {
            savedRole.equals("Owner", ignoreCase = true) || savedRole.equals("Admin", ignoreCase = true) -> "owner_dashboard"
            !savedRole.isNullOrBlank() -> "agent_dashboard"
            else -> "splash"
        }
        val initialUsername = sessionManager.getUsername()

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

            // Observe globalCloudSyncTrigger so Compose forces UI refresh when Firebase updates SQLite
            val cloudSyncTick by FirebaseCloudSyncManager.globalCloudSyncTrigger

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

            // 1. Unconditionally start real-time Firestore cloud listeners on app launch
            LaunchedEffect(Unit) {
                val auth = FirebaseAuth.getInstance()
                val startListeners = {
                    FirebaseCloudSyncManager.startRealtimeCloudListener(context, forceRestart = true)
                }

                if (auth.currentUser != null) {
                    startListeners()
                } else {
                    try {
                        auth.signInAnonymously()
                            .addOnSuccessListener {
                                Log.d("CloudSync", "Anonymous auth success, starting listeners")
                                startListeners()
                            }
                            .addOnFailureListener { e ->
                                Log.e("CloudSync", "Anonymous auth failed: ${e.message}")
                                startListeners()
                            }
                    } catch (e: Exception) {
                        Log.e("CloudSync", "Auth exception: ${e.message}")
                        startListeners()
                    }
                }
            }

            // 2. Sync full catalog when on owner dashboard
            LaunchedEffect(currentScreen) {
                if (currentScreen == "owner_dashboard") {
                    FirebaseCloudSyncManager.syncAllLocalUnitsToCloud(context)
                }
            }

            Surface {
                when (currentScreen) {
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
                            if (email.isNotBlank() && password.isNotBlank()) {
                                val securityPrefs = context.getSharedPreferences("azure_calendar_security_info", Context.MODE_PRIVATE)
                                val infoPrefs = context.getSharedPreferences("azure_calendar_personal_info", Context.MODE_PRIVATE)
                                val savedAdminEmail = infoPrefs.getString("email_Admin", "admin@azure.com") ?: "admin@azure.com"

                                val isAdminUser = email.equals(savedAdminEmail, ignoreCase = true) ||
                                        email.contains("admin", ignoreCase = true) ||
                                        email.contains("owner", ignoreCase = true)
                                val userKey = if (isAdminUser) "Admin" else email.substringBefore("@").lowercase()

                                val defaultPass = if (isAdminUser) "novie1109" else "123456"
                                val savedPassword = securityPrefs.getString("password_$userKey", defaultPass) ?: defaultPass

                                if (password != savedPassword) {
                                    Toast.makeText(context, "Incorrect password! Please enter your new password.", Toast.LENGTH_SHORT).show()
                                } else {
                                    val role = if (isAdminUser) "Owner" else "Agent"
                                    val dbAgents = dbHelper.getAllAgents()
                                    val matchedAgent = dbAgents.find { it["email"]?.equals(email, ignoreCase = true) == true }
                                    val name = matchedAgent?.get("name") ?: if (isAdminUser) "Admin" else email.substringBefore("@").replaceFirstChar { it.uppercase() }

                                    sessionManager.saveSession(name, role)
                                    registeredAgentName = name
                                    Toast.makeText(context, "Welcome back, $name!", Toast.LENGTH_SHORT).show()
                                    currentScreen = if (role == "Owner") "owner_dashboard" else "agent_dashboard"
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
                        onRegisterSubmit = { fullName, email, password, phoneNumber ->
                            val isOwner = email.contains("admin", ignoreCase = true) || email.contains("owner", ignoreCase = true)
                            val finalRole = if (isOwner) "Owner" else "Agent"

                            val securityPrefs = context.getSharedPreferences("azure_calendar_security_info", Context.MODE_PRIVATE)
                            val infoPrefs = context.getSharedPreferences("azure_calendar_personal_info", Context.MODE_PRIVATE)
                            val userKey = if (isOwner) "Admin" else email.substringBefore("@").lowercase()

                            securityPrefs.edit()
                                .putString("password_$userKey", password)
                                .putString("password_$fullName", password)
                                .apply()

                            infoPrefs.edit()
                                .putString("name_$userKey", fullName)
                                .putString("email_$userKey", email)
                                .putString("phone_$userKey", phoneNumber)
                                .apply()

                            dbHelper.insertAgent(fullName, email, phoneNumber, finalRole)
                            FirebaseCloudSyncManager.pushAgentToCloud(fullName, email, phoneNumber, finalRole, "Female")
                            if (isOwner) {
                                FirebaseCloudSyncManager.pushAdminProfileToCloud(fullName, email, phoneNumber, finalRole, "Female")
                            }
                            sessionManager.saveSession(fullName, finalRole)
                            registeredAgentName = fullName

                            try {
                                FirebaseAuth.getInstance().createUserWithEmailAndPassword(email, password)
                                    .addOnCompleteListener { task ->
                                        if (task.isSuccessful) {
                                            val user = task.result?.user
                                            val profileUpdates = userProfileChangeRequest {
                                                displayName = fullName
                                            }
                                            user?.updateProfile(profileUpdates)
                                        }
                                        FirebaseCloudSyncManager.startRealtimeCloudListener(context, forceRestart = true)
                                    }
                            } catch (e: Exception) {
                                Log.e("FirebaseAuth", "Auth error: ${e.message}")
                            }

                            Toast.makeText(context, "Welcome, $fullName!", Toast.LENGTH_SHORT).show()
                            currentScreen = if (isOwner) "owner_dashboard" else "agent_dashboard"
                        },
                        onNavigateToLogin = { currentScreen = "login" },
                        onGoogleSignUpClick = { currentScreen = "google_login" }
                    )

                    "google_login" -> GoogleLoginScreen(
                        onBackToLogin = { currentScreen = "login" },
                        onLoginSuccess = { userName ->
                            val role = "Agent"
                            val cleanEmail = "${userName.lowercase().replace(" ", ".")}@gmail.com"
                            dbHelper.insertAgent(userName, cleanEmail, "", "Google Agent", "Female")
                            FirebaseCloudSyncManager.pushAgentToCloud(userName, cleanEmail, "", "Google Agent", "Female")
                            sessionManager.saveSession(userName, role)
                            registeredAgentName = userName
                            Toast.makeText(context, "Google Login Successful for $userName!", Toast.LENGTH_SHORT).show()
                            currentScreen = "agent_dashboard"
                        }
                    )

                    "agent_dashboard" -> {
                        // key(cloudSyncTick) forces AgentDashboardScreen to reload SQLite units whenever Firebase updates!
                        key(cloudSyncTick) {
                            AgentDashboardScreen(
                                agentName = registeredAgentName,
                                onLogoutClick = {
                                    sessionManager.clearSession()
                                    currentScreen = "welcome"
                                }
                            )
                        }
                    }

                    "owner_dashboard" -> {
                        // key(cloudSyncTick) forces OwnerDashboardScreen to stay in sync with live updates!
                        key(cloudSyncTick) {
                            OwnerDashboardScreen(
                                onLogoutClick = {
                                    sessionManager.clearSession()
                                    currentScreen = "welcome"
                                }
                            )
                        }
                    }

                    "units_directory" -> {
                        key(cloudSyncTick) {
                            UnitsDirectoryScreen(
                                onBackClick = { currentScreen = "agent_dashboard" },
                                onViewScheduleClick = { _, _, _, _, _ -> currentScreen = "agent_dashboard" }
                            )
                        }
                    }

                    "add_edit_unit" -> AddEditUnitScreen(
                        onBackClick = { currentScreen = "owner_dashboard" },
                        onSaveSuccess = { currentScreen = "owner_dashboard" },
                        onDeleteUnit = { currentScreen = "owner_dashboard" }
                    )

                    "invoices" -> InvoiceBillingScreen(
                        onBackClick = { currentScreen = "owner_dashboard" }
                    )
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
                val userList = response.body()?.data
                if (response.isSuccessful && userList != null) {
                    for (user in userList) {
                        Log.d("XAMPP_SUCCESS", "Found User: ${user.name} | Role: ${user.role} | Email: ${user.email}")
                    }
                } else {
                    Log.e("XAMPP_ERROR", "Server returned code: ${response.code()}")
                }
            } catch (t: Throwable) {
                Log.e("XAMPP_ERROR", "Failed to connect: ${t.message}")
            }
        }
    }
}