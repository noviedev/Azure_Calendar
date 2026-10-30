package com.example.azurecalendar

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
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
                    FirebaseCloudSyncManager.syncAllLocalUnitsToCloud(context)
                }

                // Start listeners immediately so devices don't wait on auth timeout
                startListeners()

                if (auth.currentUser == null) {
                    try {
                        auth.signInAnonymously()
                            .addOnSuccessListener {
                                Log.d("CloudSync", "Anonymous auth success, restarting listeners")
                                startListeners()
                            }
                            .addOnFailureListener { e ->
                                Log.e("CloudSync", "Anonymous auth failed: ${e.message}")
                            }
                    } catch (e: Exception) {
                        Log.e("CloudSync", "Auth exception: ${e.message}")
                    }
                }
            }

            // 2. Sync catalog & restart listener whenever switching to Owner or Agent dashboard
            LaunchedEffect(currentScreen) {
                if (currentScreen == "owner_dashboard" || currentScreen == "agent_dashboard") {
                    FirebaseCloudSyncManager.startRealtimeCloudListener(context, forceRestart = false)
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
                            val cleanEmail = email.trim()
                            val cleanPassword = password.trim()

                            if (cleanEmail.isNotBlank() && cleanPassword.isNotBlank()) {
                                val securityPrefs = context.getSharedPreferences("azure_calendar_security_info", Context.MODE_PRIVATE)
                                val infoPrefs = context.getSharedPreferences("azure_calendar_personal_info", Context.MODE_PRIVATE)
                                val savedAdminEmail = infoPrefs.getString("email_Admin", "admin@azure.com") ?: "admin@azure.com"

                                val isAdminUser = cleanEmail.equals(savedAdminEmail, ignoreCase = true) ||
                                        cleanEmail.contains("admin", ignoreCase = true) ||
                                        cleanEmail.contains("owner", ignoreCase = true)
                                val userKey = if (isAdminUser) "Admin" else cleanEmail.substringBefore("@").lowercase()

                                val defaultPass = if (isAdminUser) "novie1109" else "123456"
                                val savedPassword = securityPrefs.getString("password_$userKey", null)

                                val completeLogin = {
                                    val role = if (isAdminUser) "Owner" else "Agent"
                                    val dbAgents = dbHelper.getAllAgents()
                                    val matchedAgent = dbAgents.find { it["email"]?.equals(cleanEmail, ignoreCase = true) == true }
                                    val name = matchedAgent?.get("name")
                                        ?: infoPrefs.getString("name_$userKey", null)
                                        ?: if (isAdminUser) "Admin" else cleanEmail.substringBefore("@").replaceFirstChar { it.uppercase() }

                                    // Ensure agent exists in local DB and Cloud so all devices stay in sync
                                    if (!isAdminUser && matchedAgent == null) {
                                        dbHelper.insertAgent(name, cleanEmail, "", role, "Female")
                                        FirebaseCloudSyncManager.pushAgentToCloud(name, cleanEmail, "", role, "Female")
                                    }

                                    sessionManager.saveSession(name, role)
                                    registeredAgentName = name
                                    FirebaseCloudSyncManager.startRealtimeCloudListener(context, forceRestart = true)
                                    Toast.makeText(context, "Welcome back, $name!", Toast.LENGTH_SHORT).show()
                                    currentScreen = if (role == "Owner") "owner_dashboard" else "agent_dashboard"
                                }

                                // 1. Check local password if saved on this device, or default password
                                if ((savedPassword != null && cleanPassword == savedPassword) || cleanPassword == defaultPass) {
                                    completeLogin()
                                } else {
                                    // 2. Fallback: Check Firebase Auth for accounts created on another phone!
                                    FirebaseAuth.getInstance().signInWithEmailAndPassword(cleanEmail, cleanPassword)
                                        .addOnCompleteListener { task ->
                                            if (task.isSuccessful) {
                                                // Cache password locally on this second device for next time
                                                securityPrefs.edit().putString("password_$userKey", cleanPassword).apply()
                                                completeLogin()
                                            } else {
                                                Toast.makeText(context, "Incorrect email or password!", Toast.LENGTH_SHORT).show()
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
                        onRegisterSubmit = { fullName, email, password, phoneNumber ->
                            val cleanName = fullName.trim()
                            val cleanEmail = email.trim()
                            val cleanPass = password.trim()
                            val cleanPhone = phoneNumber.trim()

                            val isOwner = cleanEmail.contains("admin", ignoreCase = true) || cleanEmail.contains("owner", ignoreCase = true)
                            val finalRole = if (isOwner) "Owner" else "Agent"

                            val securityPrefs = context.getSharedPreferences("azure_calendar_security_info", Context.MODE_PRIVATE)
                            val infoPrefs = context.getSharedPreferences("azure_calendar_personal_info", Context.MODE_PRIVATE)
                            val userKey = if (isOwner) "Admin" else cleanEmail.substringBefore("@").lowercase()

                            securityPrefs.edit()
                                .putString("password_$userKey", cleanPass)
                                .putString("password_$cleanName", cleanPass)
                                .apply()

                            infoPrefs.edit()
                                .putString("name_$userKey", cleanName)
                                .putString("email_$userKey", cleanEmail)
                                .putString("phone_$userKey", cleanPhone)
                                .apply()

                            dbHelper.insertAgent(cleanName, cleanEmail, cleanPhone, finalRole)
                            FirebaseCloudSyncManager.pushAgentToCloud(cleanName, cleanEmail, cleanPhone, finalRole, "Female")
                            if (isOwner) {
                                FirebaseCloudSyncManager.pushAdminProfileToCloud(cleanName, cleanEmail, cleanPhone, finalRole, "Female")
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
                        onSaveSuccess = {
                            FirebaseCloudSyncManager.syncAllLocalUnitsToCloud(context)
                            currentScreen = "owner_dashboard"
                        },
                        onDeleteUnit = {
                            FirebaseCloudSyncManager.syncAllLocalUnitsToCloud(context)
                            currentScreen = "owner_dashboard"
                        }
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