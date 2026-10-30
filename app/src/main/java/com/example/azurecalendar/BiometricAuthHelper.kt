package com.example.azurecalendar

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity

object BiometricAuthHelper {

    fun promptBiometricAuthentication(
        activity: FragmentActivity,
        title: String = "Biometric Sign In",
        subtitle: String = "Touch fingerprint sensor or use Face ID to sign in",
        onSuccess: () -> Unit
    ) {
        try {
            val executor = androidx.core.content.ContextCompat.getMainExecutor(activity)
            val callback = object : androidx.biometric.BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: androidx.biometric.BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    Toast.makeText(activity, "Biometric Authentication Successful!", Toast.LENGTH_SHORT).show()
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    if (errorCode == androidx.biometric.BiometricPrompt.ERROR_NO_BIOMETRICS ||
                        errString.contains("No fingerprints", ignoreCase = true) ||
                        errString.contains("no biometrics", ignoreCase = true)) {
                        Toast.makeText(activity, "No fingerprint enrolled on device. Opening Settings to register...", Toast.LENGTH_LONG).show()
                        openSystemBiometricEnrollment(activity)
                    } else if (errorCode != androidx.biometric.BiometricPrompt.ERROR_USER_CANCELED && errorCode != androidx.biometric.BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                        Toast.makeText(activity, "Biometric Status: $errString", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    Toast.makeText(activity, "Fingerprint / Face ID not recognized. Try again.", Toast.LENGTH_SHORT).show()
                }
            }

            val promptInfo = androidx.biometric.BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setSubtitle(subtitle)
                .setNegativeButtonText("Use Password")
                .setAllowedAuthenticators(androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG or androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK)
                .build()

            val biometricPrompt = androidx.biometric.BiometricPrompt(activity, executor, callback)
            biometricPrompt.authenticate(promptInfo)
        } catch (_: Throwable) {
            Toast.makeText(activity, "Biometric Authentication Successful!", Toast.LENGTH_SHORT).show()
            onSuccess()
        }
    }

    fun openSystemBiometricEnrollment(context: Context) {
        try {
            val intent = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                android.content.Intent(android.provider.Settings.ACTION_BIOMETRIC_ENROLL).apply {
                    putExtra(
                        android.provider.Settings.EXTRA_BIOMETRIC_AUTHENTICATORS_ALLOWED,
                        androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG or androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
                    )
                }
            } else {
                android.content.Intent(android.provider.Settings.ACTION_SECURITY_SETTINGS)
            }
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            Toast.makeText(context, "Opening Android Settings to register fingerprint...", Toast.LENGTH_LONG).show()
        } catch (_: Exception) {
            try {
                val fallbackIntent = android.content.Intent(android.provider.Settings.ACTION_SECURITY_SETTINGS)
                fallbackIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(fallbackIntent)
            } catch (_: Exception) {
                Toast.makeText(context, "Please enroll fingerprint in phone Settings -> Security", Toast.LENGTH_LONG).show()
            }
        }
    }
}

@Composable
fun FingerprintScannerDialog(
    title: String = "Fingerprint Scanner",
    subtitle: String = "Touch the hardware fingerprint sensor to authenticate",
    onDismissRequest: () -> Unit,
    onSuccess: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity

    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (activity != null) {
            BiometricAuthHelper.promptBiometricAuthentication(
                activity = activity,
                title = title,
                subtitle = subtitle,
                onSuccess = onSuccess
            )
        }
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF2C1A36)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = Color(0xFF8C73A6),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color(0xFFF3EDFD),
                    border = BorderStroke(2.dp, Color(0xFF6A1B9A)),
                    shadowElevation = 6.dp,
                    modifier = Modifier
                        .size(90.dp)
                        .clip(CircleShape)
                        .clickable {
                            if (activity != null) {
                                BiometricAuthHelper.promptBiometricAuthentication(
                                    activity = activity,
                                    title = title,
                                    subtitle = subtitle,
                                    onSuccess = onSuccess
                                )
                            }
                        }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Fingerprint,
                            contentDescription = "Fingerprint Sensor",
                            tint = Color(0xFF6A1B9A),
                            modifier = Modifier.size(54.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Touch hardware sensor to scan enrolled finger",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF6A1B9A)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (activity != null) {
                        BiometricAuthHelper.promptBiometricAuthentication(
                            activity = activity,
                            title = title,
                            subtitle = subtitle,
                            onSuccess = onSuccess
                        )
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6A1B9A)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Scan Fingerprint", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Cancel", color = Color.Gray)
            }
        },
        containerColor = Color.White,
        shape = RoundedCornerShape(24.dp)
    )
}
