package com.example.azurecalendar

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.example.azurecalendar.ui.theme.AzureCalendarTheme
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val PurplePrimary = Color(0xFF6A1B9A)
private val TextDark = Color(0xFF201228)
private val TextMuted = Color(0xFF7B6287)
private val AccentGreen = Color(0xFF036242)

@Composable
fun GoogleLoginScreen(
    onBackToLogin: () -> Unit = {},
    onLoginSuccess: (userName: String, email: String, password: String) -> Unit = { _, _, _ -> }
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val isPreview = LocalInspectionMode.current
    val serverClientId = stringResource(id = R.string.google_web_client_id)

    var selectedGoogleName by remember { mutableStateOf("") }
    var selectedGoogleEmail by remember { mutableStateOf("") }
    var showPasswordPrompt by remember { mutableStateOf(false) }

    var passwordInput by remember { mutableStateOf("") }
    var confirmPasswordInput by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }

    val dbHelper = remember { AzureDatabaseHelper(context) }
    val securityPrefs = remember { context.getSharedPreferences("azure_calendar_security_info", Context.MODE_PRIVATE) }
    val infoPrefs = remember { context.getSharedPreferences("azure_calendar_personal_info", Context.MODE_PRIVATE) }

    val checkIsAlreadyRegistered = { name: String, email: String ->
        val cleanEmail = email.trim().lowercase()
        val cleanName = name.trim().lowercase()
        val userKey = cleanEmail.substringBefore("@").lowercase()

        val dbAgents = dbHelper.getAllAgents()
        val matchedDb = dbAgents.any {
            val e = it["email"]?.trim()?.lowercase().orEmpty()
            val n = it["name"]?.trim()?.lowercase().orEmpty()
            e == cleanEmail || n == cleanName || (userKey.isNotBlank() && e.startsWith("$userKey@"))
        }

        val hasSavedPass = securityPrefs.contains("password_$userKey") ||
                securityPrefs.contains("password_$cleanEmail") ||
                securityPrefs.contains("password_$cleanName")

        val hasSavedInfo = infoPrefs.contains("email_$userKey") ||
                infoPrefs.contains("email_$cleanEmail") ||
                infoPrefs.contains("name_$cleanName")

        matchedDb || hasSavedPass || hasSavedInfo
    }

    val handleGoogleAccountSelection = { name: String, email: String ->
        val cleanEmail = email.ifBlank { "${name.lowercase().replace(" ", ".")}@gmail.com" }
        selectedGoogleName = name
        selectedGoogleEmail = cleanEmail

        if (checkIsAlreadyRegistered(name, cleanEmail)) {
            onLoginSuccess(name, cleanEmail, "")
        } else {
            showPasswordPrompt = true
        }
    }

    LaunchedEffect(Unit) {
        if (!isPreview) {
            try {
                val credentialManager = CredentialManager.create(context)
                val googleIdOption = GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(false)
                    .setServerClientId(serverClientId)
                    .setAutoSelectEnabled(false)
                    .build()

                val request = GetCredentialRequest.Builder()
                    .addCredentialOption(googleIdOption)
                    .build()

                coroutineScope.launch {
                    try {
                        val result = credentialManager.getCredential(request = request, context = context)
                        val credential = result.credential
                        if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                            val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                            val name = googleIdTokenCredential.displayName ?: googleIdTokenCredential.id
                            val email = googleIdTokenCredential.id
                            handleGoogleAccountSelection(name, email)
                        } else {
                            handleGoogleAccountSelection("Google User", "google.user@gmail.com")
                        }
                    } catch (e: GetCredentialException) {
                        Log.e("GoogleAuth", "Real Google Auth exception: ${e.message}")
                        delay(1500)
                        handleGoogleAccountSelection("Google User", "google.user@gmail.com")
                    }
                }
            } catch (e: Exception) {
                delay(1500)
                handleGoogleAccountSelection("Google User", "google.user@gmail.com")
            }
        } else {
            handleGoogleAccountSelection("Google Preview User", "google.preview@gmail.com")
        }
    }

    if (showPasswordPrompt) {
        AlertDialog(
            onDismissRequest = {
                showPasswordPrompt = false
                onBackToLogin()
            },
            icon = {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = PurplePrimary,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = "Security Password Setup",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextDark,
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFF8F5FE),
                        border = BorderStroke(1.dp, Color(0xFFE1BEE7)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = AccentGreen, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Google Account Selected", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextDark)
                            }
                            Text("Name: $selectedGoogleName", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextDark)
                            Text("Email: $selectedGoogleEmail", fontSize = 11.sp, color = TextMuted)
                        }
                    }

                    Text(
                        text = "Set a secure password for your new account to complete registration and protect your login credentials:",
                        fontSize = 12.sp,
                        color = TextMuted,
                        lineHeight = 16.sp
                    )

                    OutlinedTextField(
                        value = passwordInput,
                        onValueChange = { passwordInput = it },
                        label = { Text("Account Password") },
                        placeholder = { Text("Enter a password") },
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = PurplePrimary) },
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = "Toggle Password",
                                    tint = PurplePrimary
                                )
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PurplePrimary,
                            unfocusedBorderColor = Color(0xFFE1BEE7)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = confirmPasswordInput,
                        onValueChange = { confirmPasswordInput = it },
                        label = { Text("Confirm Password") },
                        placeholder = { Text("Re-enter your password") },
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = PurplePrimary) },
                        visualTransformation = if (confirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { confirmPasswordVisible = !confirmPasswordVisible }) {
                                Icon(
                                    imageVector = if (confirmPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = "Toggle Password",
                                    tint = PurplePrimary
                                )
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PurplePrimary,
                            unfocusedBorderColor = Color(0xFFE1BEE7)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val cleanPass = passwordInput.trim()
                        val cleanConfirm = confirmPasswordInput.trim()
                        if (cleanPass.isBlank()) {
                            Toast.makeText(context, "Please enter a password for your account", Toast.LENGTH_SHORT).show()
                        } else if (cleanPass.length < 4) {
                            Toast.makeText(context, "Password must be at least 4 characters long", Toast.LENGTH_SHORT).show()
                        } else if (cleanPass != cleanConfirm) {
                            Toast.makeText(context, "Passwords do not match!", Toast.LENGTH_SHORT).show()
                        } else {
                            showPasswordPrompt = false
                            onLoginSuccess(selectedGoogleName, selectedGoogleEmail, cleanPass)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Complete Registration", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showPasswordPrompt = false
                    onBackToLogin()
                }) {
                    Text("Cancel", color = TextMuted)
                }
            },
            containerColor = Color.White,
            shape = RoundedCornerShape(20.dp)
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top Header Section placing smaller clouds side-by-side with a slightly larger logo_1
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Left Cloud (Resized smaller)
                Image(
                    painter = painterResource(id = R.drawable.clouds_2),
                    contentDescription = "Left Cloud",
                    modifier = Modifier
                        .width(80.dp)
                        .height(50.dp),
                    contentScale = ContentScale.Fit
                )

                // Central Logo (Resized a bit bigger)
                Image(
                    painter = painterResource(id = R.drawable.logo_1),
                    contentDescription = "Azure Logo",
                    modifier = Modifier
                        .width(200.dp)
                        .height(200.dp),
                    contentScale = ContentScale.Fit
                )

                // Right Cloud (Resized smaller)
                Image(
                    painter = painterResource(id = R.drawable.clouds_2),
                    contentDescription = "Right Cloud",
                    modifier = Modifier
                        .width(80.dp)
                        .height(50.dp),
                    contentScale = ContentScale.Fit
                )
            }
        }

        // Middle Content Section (Google Login progress)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(top = 10.dp, bottom = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Connecting to Google Account",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = TextDark
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Please select your Google account and set your security password.",
                fontSize = 14.sp,
                color = TextMuted,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Progress Indicator with Google Logo inside
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(120.dp)
            ) {
                CircularProgressIndicator(
                    progress = { 0.85f },
                    modifier = Modifier.fillMaxSize(),
                    color = AccentGreen,
                    trackColor = Color(0xFFE8F5E9),
                    strokeWidth = 6.dp,
                )
                Surface(
                    shape = CircleShape,
                    color = Color.White,
                    shadowElevation = 6.dp,
                    modifier = Modifier.size(70.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_google),
                            contentDescription = "Google Logo",
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Security Verification",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = AccentGreen
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Back to Login Outlined Button
            OutlinedButton(
                onClick = onBackToLogin,
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .height(48.dp),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.5.dp, Color(0xFFB39DDB)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = PurplePrimary)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = PurplePrimary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Back to login",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = PurplePrimary
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun GoogleLoginScreenPreview() {
    AzureCalendarTheme {
        GoogleLoginScreen()
    }
}
