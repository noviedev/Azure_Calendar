package com.example.azurecalendar

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.facebook.CallbackManager
import com.facebook.FacebookCallback
import com.facebook.FacebookException
import com.facebook.login.LoginManager
import com.facebook.login.LoginResult
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object SocialAuthHelper {

    // Replace with your Web Client ID from Google Cloud Console
    private const val GOOGLE_WEB_CLIENT_ID = "YOUR_WEB_CLIENT_ID.apps.googleusercontent.com"

    fun signInWithGoogle(
        context: Context,
        scope: CoroutineScope,
        onSuccess: (String, String) -> Unit, // email, displayName
        onError: (String) -> Unit
    ) {
        val credentialManager = CredentialManager.create(context)

        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(GOOGLE_WEB_CLIENT_ID)
            .setAutoSelectEnabled(false)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        scope.launch(Dispatchers.Main) {
            try {
                val result = credentialManager.getCredential(
                    request = request,
                    context = context
                )
                val credential = result.credential
                if (credential is androidx.credentials.CustomCredential &&
                    credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                ) {
                    val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                    val email = googleIdTokenCredential.id
                    val name = googleIdTokenCredential.displayName ?: "Google User"
                    onSuccess(email, name)
                }
            } catch (e: GetCredentialException) {
                Log.e("GOOGLE_AUTH", "Google Sign In Failed: ${e.message}")
                onError(e.message ?: "Google sign in failed")
            }
        }
    }

    fun signInWithFacebook(
        activity: Activity,
        callbackManager: CallbackManager,
        onSuccess: (String) -> Unit, // accessToken
        onError: (String) -> Unit
    ) {
        LoginManager.getInstance().registerCallback(
            callbackManager,
            object : FacebookCallback<LoginResult> {
                override fun onSuccess(result: LoginResult) {
                    val token = result.accessToken.token
                    onSuccess(token)
                }

                override fun onCancel() {
                    onError("Facebook login cancelled")
                }

                override fun onError(error: FacebookException) {
                    Log.e("FB_AUTH", "Facebook Sign In Error: ${error.message}")
                    onError(error.message ?: "Facebook login failed")
                }
            }
        )

        LoginManager.getInstance().logInWithReadPermissions(
            activity,
            listOf("email", "public_profile")
        )
    }
}