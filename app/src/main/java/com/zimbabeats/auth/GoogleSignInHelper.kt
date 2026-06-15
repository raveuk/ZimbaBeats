package com.zimbabeats.auth

import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Helper class for Google Sign-In using Credential Manager API
 * Provides seamless one-tap sign-in experience
 */
class GoogleSignInHelper(private val context: Context) {

    private val credentialManager = CredentialManager.create(context)

    companion object {
        private const val TAG = "GoogleSignInHelper"
        // Web Client ID from google-services.json (client_type: 3)
        private const val WEB_CLIENT_ID = "897176107632-ufisep7q7j9kogeosocld7ee6nntgrut.apps.googleusercontent.com"
    }

    /**
     * Result of Google Sign-In attempt
     */
    sealed class SignInResult {
        data class Success(
            val idToken: String,
            val email: String,
            val displayName: String?,
            val profilePictureUrl: String?
        ) : SignInResult()

        data class Error(val message: String) : SignInResult()
        data object Cancelled : SignInResult()
        data object NoCredentials : SignInResult()
    }

    /**
     * Initiates Google Sign-In flow using Credential Manager
     * Shows the native Google account picker
     */
    suspend fun signIn(): SignInResult = withContext(Dispatchers.Main) {
        try {
            // Configure Google ID request
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false) // Show all accounts, not just previously used
                .setServerClientId(WEB_CLIENT_ID)
                .setAutoSelectEnabled(true) // Auto-select if only one account
                .setNonce(generateNonce()) // Security nonce
                .build()

            // Build the credential request
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            // Get credentials - this shows the Google account picker
            val result = credentialManager.getCredential(
                request = request,
                context = context as android.app.Activity
            )

            handleSignInResult(result)
        } catch (e: GetCredentialCancellationException) {
            Log.d(TAG, "Sign-in cancelled by user")
            SignInResult.Cancelled
        } catch (e: NoCredentialException) {
            Log.d(TAG, "No credentials available")
            SignInResult.NoCredentials
        } catch (e: GetCredentialException) {
            Log.e(TAG, "Sign-in failed", e)
            SignInResult.Error(e.message ?: "Sign-in failed")
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error during sign-in", e)
            SignInResult.Error(e.message ?: "Unexpected error")
        }
    }

    /**
     * Signs out and clears credential state
     */
    suspend fun signOut() = withContext(Dispatchers.IO) {
        try {
            credentialManager.clearCredentialState(ClearCredentialStateRequest())
            Log.d(TAG, "Signed out successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error signing out", e)
        }
    }

    private fun handleSignInResult(result: GetCredentialResponse): SignInResult {
        val credential = result.credential

        return when (credential) {
            is CustomCredential -> {
                if (credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    try {
                        val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                        Log.d(TAG, "Sign-in successful: ${googleIdTokenCredential.displayName}")

                        SignInResult.Success(
                            idToken = googleIdTokenCredential.idToken,
                            email = googleIdTokenCredential.id, // email is stored in id field
                            displayName = googleIdTokenCredential.displayName,
                            profilePictureUrl = googleIdTokenCredential.profilePictureUri?.toString()
                        )
                    } catch (e: GoogleIdTokenParsingException) {
                        Log.e(TAG, "Failed to parse Google ID token", e)
                        SignInResult.Error("Failed to parse credentials")
                    }
                } else {
                    Log.e(TAG, "Unexpected credential type: ${credential.type}")
                    SignInResult.Error("Unexpected credential type")
                }
            }
            else -> {
                Log.e(TAG, "Unexpected credential class: ${credential.javaClass}")
                SignInResult.Error("Unexpected credential")
            }
        }
    }

    private fun generateNonce(): String {
        // Generate a random nonce for security
        val bytes = ByteArray(32)
        java.security.SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
