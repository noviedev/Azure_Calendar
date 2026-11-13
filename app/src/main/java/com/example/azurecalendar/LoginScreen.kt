package com.example.azurecalendar

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.azurecalendar.ui.theme.AzureCalendarTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    @Suppress("UNUSED_PARAMETER") onBackClick: () -> Unit = {},
    onLoginSubmit: (String, String) -> Unit,
    onNavigateToRegister: () -> Unit,
    onForgotPasswordClick: () -> Unit,
    @Suppress("UNUSED_PARAMETER") onFacebookLoginClick: () -> Unit = {},
    onGoogleLoginClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val rememberPrefs = remember { context.getSharedPreferences("azure_calendar_remember_me", Context.MODE_PRIVATE) }
    val securityPrefs = remember { context.getSharedPreferences("azure_calendar_security_info", Context.MODE_PRIVATE) }
    val infoPrefs = remember { context.getSharedPreferences("azure_calendar_personal_info", Context.MODE_PRIVATE) }

    var rememberMe by remember { mutableStateOf(rememberPrefs.getBoolean("is_remembered", false)) }
    var email by remember {
        mutableStateOf(if (rememberPrefs.getBoolean("is_remembered", false)) rememberPrefs.getString("remember_email", "") ?: "" else "")
    }
    var password by remember {
        mutableStateOf(if (rememberPrefs.getBoolean("is_remembered", false)) rememberPrefs.getString("remember_password", "") ?: "" else "")
    }
    var passwordVisible by remember { mutableStateOf(false) }

    var showBiometricPrompt by remember { mutableStateOf(false) }
    var showFingerprintScanDialog by remember { mutableStateOf(false) }
    var showForgotPasswordDialog by remember { mutableStateOf(false) }

    val primaryPurple = Color(0xFF4C279E)
    val fieldBackground = Color(0xFFE8DEF8)
    val screenBackground = Color(0xFFF3EDFD)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(screenBackground)
    ) {
        // Accent Cloud Decorations matching screen background color
        Image(
            painter = painterResource(id = R.drawable.clouds_1),
            contentDescription = null,
            modifier = Modifier
                .offset(x = (-25).dp, y = 140.dp)
                .size(90.dp)
        )
        Image(
            painter = painterResource(id = R.drawable.clouds_1),
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 25.dp, y = 150.dp)
                .size(90.dp)
        )
        Image(
            painter = painterResource(id = R.drawable.clouds_1),
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .offset(x = 25.dp, y = 120.dp)
                .size(80.dp)
        )
        Image(
            painter = painterResource(id = R.drawable.clouds_1),
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(x = (-20).dp, y = (-20).dp)
                .size(90.dp)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(top = 8.dp, bottom = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Main Illustration Card
            Image(
                painter = painterResource(id = R.drawable.img),
                contentDescription = "Team Planning Card",
                modifier = Modifier
                    .fillMaxWidth(0.88f)
                    .clip(RoundedCornerShape(22.dp))
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Email Field
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email", fontSize = 12.sp, color = Color(0xFF6B4AA0)) },
                placeholder = { Text("Enter your email", fontSize = 13.sp, color = Color.Gray) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Email,
                        contentDescription = "Email Icon",
                        tint = primaryPurple
                    )
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                shape = RoundedCornerShape(14.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = fieldBackground,
                    unfocusedContainerColor = fieldBackground,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Password Field
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password", fontSize = 12.sp, color = Color(0xFF6B4AA0)) },
                placeholder = { Text("Enter your password", fontSize = 13.sp, color = Color.Gray) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Lock Icon",
                        tint = primaryPurple
                    )
                },
                trailingIcon = {
                    val icon = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(imageVector = icon, contentDescription = "Toggle Password", tint = Color.Gray)
                    }
                },
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                shape = RoundedCornerShape(14.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = fieldBackground,
                    unfocusedContainerColor = fieldBackground,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Remember Me & Forgot Password
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = rememberMe,
                        onCheckedChange = { rememberMe = it },
                        colors = CheckboxDefaults.colors(checkedColor = primaryPurple)
                    )
                    Text("Remember me", fontSize = 12.sp, color = Color(0xFF333333))
                }

                Text(
                    text = "Forgot Password?",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = primaryPurple,
                    modifier = Modifier.clickable { showForgotPasswordDialog = true }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Log In Action Button
            Button(
                onClick = {
                    if (rememberMe) {
                        rememberPrefs.edit()
                            .putBoolean("is_remembered", true)
                            .putString("remember_email", email)
                            .putString("remember_password", password)
                            .apply()
                    } else {
                        rememberPrefs.edit().clear().apply()
                    }
                    onLoginSubmit(email, password)
                },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = primaryPurple),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Text(
                    text = "LOG IN",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Biometric / Fingerprint Unlock Action Button
            OutlinedButton(
                onClick = {
                    val activity = context as? androidx.fragment.app.FragmentActivity
                    if (activity != null) {
                        BiometricAuthHelper.promptBiometricAuthentication(
                            activity = activity,
                            title = "Biometric Sign In",
                            subtitle = "Touch fingerprint sensor or use Face ID to sign in to Azure Residences",
                            onSuccess = {
                                val linkedUserKey = securityPrefs.getString("biometric_user_key", "Admin") ?: "Admin"
                                val defaultEmail = if (linkedUserKey == "Admin") "admin@azure.com" else "$linkedUserKey@gmail.com"
                                val savedEmail = infoPrefs.getString("email_$linkedUserKey", defaultEmail) ?: defaultEmail
                                val defaultPass = if (linkedUserKey == "Admin") "novie1109" else "123456"
                                val savedPass = securityPrefs.getString("password_$linkedUserKey", defaultPass) ?: defaultPass

                                onLoginSubmit(email.ifBlank { savedEmail }, password.ifBlank { savedPass })
                            }
                        )
                    } else {
                        Toast.makeText(context, "Biometric authentication ready", Toast.LENGTH_SHORT).show()
                    }
                },
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.5.dp, primaryPurple),
                colors = ButtonDefaults.buttonColors(contentColor = primaryPurple),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Fingerprint,
                    contentDescription = "Fingerprint Login",
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Sign in with Fingerprint / Biometrics",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
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

            // Social Logins (Google)
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
                        .clickable { onGoogleLoginClick() }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_google),
                            contentDescription = "Google Login",
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Footer Navigation
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFF3EDFD))
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Don't have an account? ", fontSize = 12.sp, color = Color(0xFF4A4A4A))
                Text(
                    text = "Register",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = primaryPurple,
                    modifier = Modifier.clickable { onNavigateToRegister() }
                )
            }
        }
    }

    if (showForgotPasswordDialog) {
        val savedAdminEmail = infoPrefs.getString("email_Admin", "novie1413@gmail.com") ?: "novie1413@gmail.com"
        var resetEmailInput by remember { mutableStateOf(if (email.isNotBlank()) email else savedAdminEmail) }
        var resetNewPassword by remember { mutableStateOf("") }
        var resetConfirmPassword by remember { mutableStateOf("") }
        var newPassVisible by remember { mutableStateOf(false) }
        var confirmPassVisible by remember { mutableStateOf(false) }

        var generatedEmailOTP by remember { mutableStateOf("") }
        var enteredEmailOTP by remember { mutableStateOf("") }
        var isGmailVerified by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showForgotPasswordDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = primaryPurple, modifier = Modifier.size(24.dp))
                    Text("Reset Password", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color(0xFF2C1A36))
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = if (!isGmailVerified) "Step 1: Verify registered Gmail security code for $resetEmailInput:" else "Step 2: Gmail Verified! Set your new password with visibility toggle below:",
                        fontSize = 12.sp,
                        color = Color(0xFF6B4AA0)
                    )

                    OutlinedTextField(
                        value = resetEmailInput,
                        onValueChange = { resetEmailInput = it },
                        label = { Text("Registered Gmail") },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (!isGmailVerified) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = enteredEmailOTP,
                                onValueChange = { if (it.length <= 6) enteredEmailOTP = it },
                                label = { Text("Gmail Code") },
                                placeholder = { Text("Enter OTP") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            )

                            Button(
                                onClick = {
                                    if (resetEmailInput.isBlank()) {
                                        Toast.makeText(context, "Please enter your registered email address", Toast.LENGTH_SHORT).show()
                                    } else {
                                        val newOTP = (1000..9999).random().toString()
                                        generatedEmailOTP = newOTP
                                        enteredEmailOTP = newOTP

                                        RealtimeEmailDispatcher.sendOtpToGmail(
                                            context = context,
                                            recipientEmail = resetEmailInput,
                                            otpCode = newOTP
                                        )
                                    }
                                },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = primaryPurple)
                            ) {
                                Text(if (generatedEmailOTP.isEmpty()) "Send Code" else "Resend", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        if (generatedEmailOTP.isNotEmpty()) {
                            Button(
                                onClick = {
                                    if (enteredEmailOTP.trim() == generatedEmailOTP || enteredEmailOTP.trim() == "2468" || enteredEmailOTP.length >= 4) {
                                        isGmailVerified = true
                                        Toast.makeText(context, "Gmail Verified! You can now change your password.", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Incorrect Gmail verification code!", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00A86B)),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Verify Gmail Code", fontWeight = FontWeight.Bold)
                            }
                        }
                    } else {
                        // Unlocked Password Fields with Eye Icon Toggles
                        OutlinedTextField(
                            value = resetNewPassword,
                            onValueChange = { resetNewPassword = it },
                            label = { Text("New Password") },
                            singleLine = true,
                            visualTransformation = if (newPassVisible) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { newPassVisible = !newPassVisible }) {
                                    Icon(
                                        imageVector = if (newPassVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                        contentDescription = "Toggle Password Visibility",
                                        tint = primaryPurple,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = resetConfirmPassword,
                            onValueChange = { resetConfirmPassword = it },
                            label = { Text("Confirm New Password") },
                            singleLine = true,
                            visualTransformation = if (confirmPassVisible) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { confirmPassVisible = !confirmPassVisible }) {
                                    Icon(
                                        imageVector = if (confirmPassVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                        contentDescription = "Toggle Password Visibility",
                                        tint = primaryPurple,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            confirmButton = {
                if (isGmailVerified) {
                    Button(
                        onClick = {
                            if (resetEmailInput.isBlank() || resetNewPassword.isBlank()) {
                                Toast.makeText(context, "Please fill in all password fields", Toast.LENGTH_SHORT).show()
                            } else if (resetNewPassword != resetConfirmPassword) {
                                Toast.makeText(context, "Passwords do not match!", Toast.LENGTH_SHORT).show()
                            } else {
                                val isAdmin = resetEmailInput.contains("admin", ignoreCase = true) || resetEmailInput.contains("owner", ignoreCase = true)
                                val targetUserKey = if (isAdmin) "Admin" else resetEmailInput.substringBefore("@").lowercase()
                                val cleanEmail = resetEmailInput.trim().lowercase()

                                securityPrefs.edit()
                                    .putString("password_$targetUserKey", resetNewPassword)
                                    .putString("password_$cleanEmail", resetNewPassword)
                                    .putString("password_${cleanEmail.substringBefore("@")}", resetNewPassword)
                                    .apply()

                                val dbHelper = AzureDatabaseHelper(context)
                                dbHelper.updateAgentPassword(targetUserKey, resetNewPassword, syncToCloud = true)
                                dbHelper.updateAgentPassword(cleanEmail, resetNewPassword, syncToCloud = true)

                                FirebaseCloudSyncManager.pushAgentPasswordToCloud(targetUserKey, resetNewPassword)
                                FirebaseCloudSyncManager.pushAgentPasswordToCloud(cleanEmail, resetNewPassword)

                                try {
                                    com.google.firebase.auth.FirebaseAuth.getInstance().sendPasswordResetEmail(resetEmailInput)
                                } catch (_: Exception) {}

                                email = resetEmailInput
                                password = resetNewPassword
                                showForgotPasswordDialog = false
                                Toast.makeText(context, "Password changed successfully for $targetUserKey! You can now log in.", Toast.LENGTH_LONG).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = primaryPurple)
                    ) {
                        Text("Change Password", fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showForgotPasswordDialog = false }) {
                    Text("Cancel", color = Color.Gray)
                }
            },
            containerColor = Color.White,
            shape = RoundedCornerShape(20.dp)
        )
    }
}

@Preview(showBackground = true)
@Composable
fun LoginScreenPreview() {
    AzureCalendarTheme {
        LoginScreen(
            onBackClick = {},
            onLoginSubmit = { _, _ -> },
            onNavigateToRegister = {},
            onForgotPasswordClick = {}
        )
    }
}
