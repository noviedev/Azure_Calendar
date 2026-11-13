package com.example.azurecalendar

import android.widget.Toast
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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.azurecalendar.ui.theme.AzureCalendarTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterScreen(
    onBackClick: () -> Unit,
    onRegisterSubmit: (fullName: String, email: String, password: String, phoneNumber: String, gender: String) -> Unit,
    onNavigateToLogin: () -> Unit,
    @Suppress("UNUSED_PARAMETER") onFacebookSignUpClick: () -> Unit = {},
    onGoogleSignUpClick: () -> Unit = {}
) {
    val context = LocalContext.current
    var fullName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var age by remember { mutableStateOf("") }
    var selectedGender by remember { mutableStateOf("") }
    var phoneNumber by remember { mutableStateOf("") }

    var termsAccepted by remember { mutableStateOf(false) }
    var showTermsDialog by remember { mutableStateOf(false) }

    var passwordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }

    var genderExpanded by remember { mutableStateOf(false) }

    val genders = listOf("Male", "Female", "Prefer not to say")

    val primaryPurple = Color(0xFF4C279E)
    val fieldBackground = Color(0xFFE8DEF8)
    val screenBackground = Color(0xFFF3EDFD)

    val fieldColors = TextFieldDefaults.colors(
        focusedContainerColor = fieldBackground,
        unfocusedContainerColor = fieldBackground,
        focusedIndicatorColor = Color.Transparent,
        unfocusedIndicatorColor = Color.Transparent
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(screenBackground)
    ) {
        // Accent Cloud Decorations
        Image(
            painter = painterResource(id = R.drawable.clouds_1),
            contentDescription = null,
            modifier = Modifier
                .offset(x = (-20).dp, y = 40.dp)
                .size(70.dp)
        )
        Image(
            painter = painterResource(id = R.drawable.clouds_1),
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 20.dp, y = 80.dp)
                .size(80.dp)
        )
        Image(
            painter = painterResource(id = R.drawable.clouds_1),
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(x = (-15).dp, y = (-20).dp)
                .size(80.dp)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(top = 16.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Circular Custom Back Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color(0xFF7B2CBF),
                    border = BorderStroke(2.dp, Color(0xFFE0AAFF)),
                    shadowElevation = 6.dp,
                    modifier = Modifier
                        .size(46.dp)
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
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Title and Subtitle
            Text(
                text = "Create Your Account",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1E1045)
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Join TIME TREE today",
                fontSize = 14.sp,
                color = Color(0xFF6B4AA0)
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Full Name Field
            OutlinedTextField(
                value = fullName,
                onValueChange = { fullName = it },
                label = { Text("Full Name", fontSize = 12.sp, color = Color(0xFF6B4AA0)) },
                placeholder = { Text("Enter your full name", fontSize = 13.sp, color = Color.Gray) },
                leadingIcon = { Icon(Icons.Default.Person, "Name", tint = primaryPurple) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = fieldColors,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Email Field
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email", fontSize = 12.sp, color = Color(0xFF6B4AA0)) },
                placeholder = { Text("Enter your email", fontSize = 13.sp, color = Color.Gray) },
                leadingIcon = { Icon(Icons.Default.Email, "Email", tint = primaryPurple) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                shape = RoundedCornerShape(14.dp),
                colors = fieldColors,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Password Field
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password", fontSize = 12.sp, color = Color(0xFF6B4AA0)) },
                placeholder = { Text("Create a password", fontSize = 13.sp, color = Color.Gray) },
                leadingIcon = { Icon(Icons.Default.Lock, "Password", tint = primaryPurple) },
                trailingIcon = {
                    val icon = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(imageVector = icon, contentDescription = "Toggle", tint = Color.Gray)
                    }
                },
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                shape = RoundedCornerShape(14.dp),
                colors = fieldColors,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Confirm Password Field
            OutlinedTextField(
                value = confirmPassword,
                onValueChange = { confirmPassword = it },
                label = { Text("Confirm Password", fontSize = 12.sp, color = Color(0xFF6B4AA0)) },
                placeholder = { Text("Confirm your password", fontSize = 13.sp, color = Color.Gray) },
                leadingIcon = { Icon(Icons.Default.Lock, "Confirm Password", tint = primaryPurple) },
                trailingIcon = {
                    val icon = if (confirmPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff
                    IconButton(onClick = { confirmPasswordVisible = !confirmPasswordVisible }) {
                        Icon(imageVector = icon, contentDescription = "Toggle", tint = Color.Gray)
                    }
                },
                visualTransformation = if (confirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                shape = RoundedCornerShape(14.dp),
                colors = fieldColors,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Age & Gender (Two Columns)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Age Field
                OutlinedTextField(
                    value = age,
                    onValueChange = { if (it.all { char -> char.isDigit() }) age = it },
                    label = { Text("Age", fontSize = 12.sp, color = Color(0xFF6B4AA0)) },
                    placeholder = { Text("Enter age", fontSize = 13.sp, color = Color.Gray) },
                    leadingIcon = { Icon(Icons.Default.DateRange, "Age", tint = primaryPurple) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(14.dp),
                    colors = fieldColors,
                    modifier = Modifier.weight(1f)
                )

                // Gender Dropdown
                ExposedDropdownMenuBox(
                    expanded = genderExpanded,
                    onExpandedChange = { genderExpanded = !genderExpanded },
                    modifier = Modifier.weight(1f)
                ) {
                    OutlinedTextField(
                        value = selectedGender,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Gender", fontSize = 11.sp, maxLines = 1, color = Color(0xFF6B4AA0)) },
                        placeholder = { Text("Select", fontSize = 13.sp, color = Color.Gray) },
                        leadingIcon = { Icon(Icons.Default.Face, "Gender", tint = primaryPurple) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = genderExpanded) },
                        shape = RoundedCornerShape(14.dp),
                        colors = fieldColors,
                        modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true)
                    )
                    ExposedDropdownMenu(
                        expanded = genderExpanded,
                        onDismissRequest = { genderExpanded = false }
                    ) {
                        genders.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option) },
                                onClick = {
                                    selectedGender = option
                                    genderExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Mobile Number Field
            OutlinedTextField(
                value = phoneNumber,
                onValueChange = { phoneNumber = it },
                label = { Text("Mobile Number", fontSize = 12.sp, color = Color(0xFF6B4AA0)) },
                placeholder = { Text("+63 917 123 4567", fontSize = 13.sp, color = Color.Gray) },
                leadingIcon = { Icon(Icons.Default.Phone, "Mobile Number", tint = primaryPurple) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                shape = RoundedCornerShape(14.dp),
                colors = fieldColors,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Terms & Conditions and Privacy Policy Checkbox Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = termsAccepted,
                    onCheckedChange = { termsAccepted = it },
                    colors = CheckboxDefaults.colors(
                        checkedColor = primaryPurple,
                        uncheckedColor = Color(0xFF6B4AA0)
                    )
                )
                Spacer(modifier = Modifier.width(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { termsAccepted = !termsAccepted }
                ) {
                    Text(
                        text = "I agree to ",
                        fontSize = 11.sp,
                        color = Color(0xFF4A4A4A)
                    )
                    Text(
                        text = "Terms & Conditions",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = primaryPurple,
                        textDecoration = TextDecoration.Underline,
                        modifier = Modifier.clickable { showTermsDialog = true }
                    )
                    Text(
                        text = " & ",
                        fontSize = 11.sp,
                        color = Color(0xFF4A4A4A)
                    )
                    Text(
                        text = "Privacy Policy",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = primaryPurple,
                        textDecoration = TextDecoration.Underline,
                        modifier = Modifier.clickable { showTermsDialog = true }
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Register Account Action Button
            Button(
                onClick = {
                    if (!termsAccepted) {
                        Toast.makeText(context, "Please agree to the Terms & Conditions and Privacy Policy to proceed.", Toast.LENGTH_LONG).show()
                    } else if (email.isBlank() || password.isBlank()) {
                        Toast.makeText(context, "Please enter your email and password", Toast.LENGTH_SHORT).show()
                    } else if (password != confirmPassword && confirmPassword.isNotBlank()) {
                        Toast.makeText(context, "Passwords do not match", Toast.LENGTH_SHORT).show()
                    } else {
                        val targetPhone = phoneNumber.ifBlank { "+63 917 888 2468" }
                        val targetGender = selectedGender.ifBlank { "Female" }
                        onRegisterSubmit(
                            fullName.ifBlank { "New User" },
                            email.ifBlank { "user@azure.com" },
                            password.ifBlank { "Password123!" },
                            targetPhone,
                            targetGender
                        )
                    }
                },
                enabled = termsAccepted,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = primaryPurple,
                    disabledContainerColor = Color(0xFFCCC2DC),
                    disabledContentColor = Color.White
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Text(
                    text = "Register Account",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Divider Text
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HorizontalDivider(modifier = Modifier.weight(1f), color = Color(0xFFD6C7EE))
                Text(
                    text = "  or Sign Up with  ",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
                HorizontalDivider(modifier = Modifier.weight(1f), color = Color(0xFFD6C7EE))
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Social Sign-Up (Google)
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.White,
                    shadowElevation = 3.dp,
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onGoogleSignUpClick() }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_google),
                            contentDescription = "Google Sign Up",
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Footer Navigation to Login
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFF3EDFD))
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Already have an account? ", fontSize = 12.sp, color = Color(0xFF4A4A4A))
                Text(
                    text = "Log In",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = primaryPurple,
                    modifier = Modifier.clickable { onNavigateToLogin() }
                )
            }
        }
    }

    if (showTermsDialog) {
        AlertDialog(
            onDismissRequest = { showTermsDialog = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = primaryPurple, modifier = Modifier.size(24.dp))
                    Text(
                        text = "Terms & Privacy Policy",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1E1045)
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "1. Acceptance of Terms",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = primaryPurple
                    )
                    Text(
                        text = "By creating an account on Azure Calendar / TimeTree, you agree to comply with our platform policies, booking rules, and user conduct standards.",
                        fontSize = 12.sp,
                        color = Color(0xFF4A4A4A),
                        lineHeight = 16.sp
                    )

                    HorizontalDivider(color = Color(0xFFE8DEF8))

                    Text(
                        text = "2. Privacy & Data Protection",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = primaryPurple
                    )
                    Text(
                        text = "Your personal information (name, email, phone number, gender, and booking records) is securely stored and encrypted. We do not sell or share your personal data with third-party advertisers.",
                        fontSize = 12.sp,
                        color = Color(0xFF4A4A4A),
                        lineHeight = 16.sp
                    )

                    HorizontalDivider(color = Color(0xFFE8DEF8))

                    Text(
                        text = "3. Booking & Cancellation Rules",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = primaryPurple
                    )
                    Text(
                        text = "All unit staycation bookings, reservations, and tags created by owners or agents are subject to real-time status tracking and check-in date rules.",
                        fontSize = 12.sp,
                        color = Color(0xFF4A4A4A),
                        lineHeight = 16.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        termsAccepted = true
                        showTermsDialog = false
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = primaryPurple)
                ) {
                    Text("Accept & Close", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showTermsDialog = false }) {
                    Text("Close", color = Color.Gray)
                }
            },
            containerColor = Color.White,
            shape = RoundedCornerShape(20.dp)
        )
    }
}

@Preview(showBackground = true)
@Composable
fun RegisterScreenPreview() {
    AzureCalendarTheme {
        RegisterScreen(
            onBackClick = {},
            onRegisterSubmit = { _, _, _, _, _ -> },
            onNavigateToLogin = {},
            onFacebookSignUpClick = {},
            onGoogleSignUpClick = {}
        )
    }
}

fun startFirebasePhoneAuth(
    activity: android.app.Activity?,
    phoneNumber: String,
    onCodeSent: (verificationId: String) -> Unit,
    onError: (errorMsg: String) -> Unit
) {
    val formattedPhone = formatPhilippinePhone(phoneNumber)
    if (activity == null) {
        onCodeSent("simulated-ver-id")
        return
    }

    try {
        val options = com.google.firebase.auth.PhoneAuthOptions.newBuilder(com.google.firebase.auth.FirebaseAuth.getInstance())
            .setPhoneNumber(formattedPhone)
            .setTimeout(60L, java.util.concurrent.TimeUnit.SECONDS)
            .setActivity(activity)
            .setCallbacks(object : com.google.firebase.auth.PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                override fun onVerificationCompleted(credential: com.google.firebase.auth.PhoneAuthCredential) {
                    // Auto-verification if detected
                }

                override fun onVerificationFailed(e: com.google.firebase.FirebaseException) {
                    onError(e.message ?: "Firebase Phone Verification Failed")
                }

                override fun onCodeSent(
                    verificationId: String,
                    token: com.google.firebase.auth.PhoneAuthProvider.ForceResendingToken
                ) {
                    onCodeSent(verificationId)
                }
            })
            .build()

        com.google.firebase.auth.PhoneAuthProvider.verifyPhoneNumber(options)
    } catch (e: Exception) {
        onError(e.message ?: "Failed to initiate Firebase Phone Auth")
    }
}

fun formatPhilippinePhone(phone: String): String {
    val clean = phone.replace(" ", "").replace("-", "")
    return if (clean.startsWith("0")) "+63" + clean.substring(1)
    else if (!clean.startsWith("+")) "+63" + clean
    else clean
}

@android.annotation.SuppressLint("MissingPermission", "NotificationPermission")
fun sendRealtimeSmsOTP(context: android.content.Context, phoneNumber: String, otpCode: String) {
    val formattedPhone = formatPhilippinePhone(phoneNumber)
    val message = "Your Azure Residences OTP verification code is: $otpCode. Valid for 5 minutes."

    try {
        val smsManager = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            context.getSystemService(android.telephony.SmsManager::class.java)
        } else {
            @Suppress("DEPRECATION")
            android.telephony.SmsManager.getDefault()
        }
        smsManager?.sendTextMessage(formattedPhone, null, message, null, null)
    } catch (e: Exception) {
        android.util.Log.e("SmsManager", "SMS send error: ${e.message}")
    }

    try {
        val channelId = "azure_sms_otp_channel_v2"
        val notificationManager = context.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                channelId,
                "SMS OTP Notifications",
                android.app.NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Real-time SMS OTP Verification Messages"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 250, 250, 250)
                setBypassDnd(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val notification = androidx.core.app.NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle("💬 SMS Message to $formattedPhone")
            .setContentText("Your Azure OTP verification code is: $otpCode")
            .setStyle(androidx.core.app.NotificationCompat.BigTextStyle().bigText("Your Azure Residences SMS OTP code for $formattedPhone is: $otpCode\n\nValid for 5 minutes. Do not share with anyone."))
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_MAX)
            .setDefaults(androidx.core.app.NotificationCompat.DEFAULT_ALL)
            .setCategory(androidx.core.app.NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .build()

        try {
            notificationManager.notify((100..999).random(), notification)
        } catch (_: Exception) {}
    } catch (_: Exception) {}

    Toast.makeText(context, "💬 SMS Sent to $formattedPhone\nVerification Code: $otpCode", Toast.LENGTH_LONG).show()
}
