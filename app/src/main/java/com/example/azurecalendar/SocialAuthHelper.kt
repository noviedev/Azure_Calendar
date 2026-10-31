package com.example.azurecalendar

import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object SocialAuthHelper {

    // Replace with your Web Client ID from Google Cloud Console
    private const val GOOGLE_WEB_CLIENT_ID = "278906261627-4q5bqb5d03v9cque9c1f9vvbtj1tu6nl.apps.googleusercontent.com"

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
}
