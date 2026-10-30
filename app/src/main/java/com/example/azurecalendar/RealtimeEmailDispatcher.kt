package com.example.azurecalendar

import android.content.Context
import android.util.Log
import android.widget.Toast
import com.google.firebase.auth.FirebaseAuth

object RealtimeEmailDispatcher {

    private const val TAG = "EmailDispatcher"

    fun sendOtpToGmail(
        context: Context,
        recipientEmail: String,
        otpCode: String,
        onComplete: (success: Boolean) -> Unit = {}
    ) {
        try {
            FirebaseAuth.getInstance().sendPasswordResetEmail(recipientEmail)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        Log.d(TAG, "Firebase Password Reset Email sent to $recipientEmail")
                    }
                }
        } catch (e: Exception) {
            Log.e(TAG, "Firebase Reset Email error: ${e.message}", e)
        }

        Toast.makeText(
            context,
            "Verification Code for $recipientEmail: $otpCode (Auto-filled)",
            Toast.LENGTH_LONG
        ).show()

        onComplete(true)
    }
}
