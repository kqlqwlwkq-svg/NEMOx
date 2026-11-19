package com.example.auth

import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.example.R
import com.example.ui.theme.findActivity
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { auth ->
        trySend(auth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

private fun formatAuthException(stage: String, e: Throwable): String {
    val className = e::class.java.simpleName.ifBlank { e::class.java.name }
    val parts = mutableListOf("[$stage] $className")
    when (e) {
        is GetCredentialException -> {
            if (e.type.isNotBlank()) {
                parts += "type=${e.type}"
            }
            val credMsg = e.errorMessage?.toString()?.trim().orEmpty()
            if (credMsg.isNotBlank()) {
                parts += "errorMessage=$credMsg"
            }
        }
        is FirebaseAuthException -> {
            if (e.errorCode.isNotBlank()) {
                parts += "errorCode=${e.errorCode}"
            }
        }
    }
    val rawMsg = e.message?.trim().orEmpty()
    val locMsg = e.localizedMessage?.trim().orEmpty()
    val msg = rawMsg.ifBlank { locMsg }
    if (msg.isNotBlank()) {
        parts += "message=$msg"
    }
    val cause = e.cause
    if (cause != null) {
        val causeName = cause::class.java.simpleName.ifBlank { cause::class.java.name }
        val causeMsg = cause.message?.trim().orEmpty().ifBlank { "no cause message" }
        parts += "cause=$causeName: $causeMsg"
    }
    if (parts.size == 1) {
        parts += "No error details provided"
    }
    return parts.joinToString(" | ")
}

fun attemptAutoSignIn(
    context: Context,
    credentialManager: CredentialManager,
    onAuthSuccess: () -> Unit,
    onUnauthenticated: () -> Unit,
    scope: CoroutineScope
) {
    if (runCatching { Firebase.auth.currentUser }.getOrNull() != null) {
        onAuthSuccess()
        return
    }
    val clientId = try {
        context.getString(R.string.default_web_client_id)
    } catch (_: Exception) {
        onUnauthenticated()
        return
    }
    val activity = context.findActivity()
    if (activity == null) {
        onUnauthenticated()
        return
    }

    val googleIdOption = GetGoogleIdOption.Builder()
        .setFilterByAuthorizedAccounts(true)
        .setServerClientId(clientId)
        .setAutoSelectEnabled(true)
        .build()
    val request = GetCredentialRequest.Builder()
        .addCredentialOption(googleIdOption)
        .build()

    scope.launch {
        try {
            val cred = credentialManager.getCredential(activity, request).credential
            if (cred is CustomCredential && cred.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val token = GoogleIdTokenCredential.createFrom(cred.data).idToken
                Firebase.auth.signInWithCredential(GoogleAuthProvider.getCredential(token, null)).await()
                onAuthSuccess()
            } else {
                onUnauthenticated()
            }
        } catch (e: Exception) {
            Log.w("Auth", "Auto sign-in skipped: ${formatAuthException("AutoSignIn", e)}", e)
            onUnauthenticated()
        }
    }
}

fun onGoogleSignInClicked(
    context: Context,
    credentialManager: CredentialManager,
    onAuthSuccess: () -> Unit,
    onAuthError: (String) -> Unit,
    scope: CoroutineScope,
    onAuthCancelled: () -> Unit = {}
) {
    val clientId = try {
        context.getString(R.string.default_web_client_id)
    } catch (e: Exception) {
        onAuthError(formatAuthException("Config.default_web_client_id", e))
        return
    }
    if (clientId.isBlank()) {
        onAuthError("[Config.default_web_client_id] Value is blank")
        return
    }
    val activity = context.findActivity()
    if (activity == null) {
        onAuthError("[ActivityLookup] Activity context unavailable for Google Sign-In")
        return
    }

    val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
    val request = GetCredentialRequest.Builder()
        .addCredentialOption(signInOption)
        .build()

    scope.launch {
        var stage = "CredentialManager.getCredential"
        try {
            val cred = credentialManager.getCredential(activity, request).credential
            stage = "GoogleIdTokenCredential.parse"
            if (cred is CustomCredential && cred.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val token = GoogleIdTokenCredential.createFrom(cred.data).idToken
                if (token.isBlank()) {
                    onAuthError("[$stage] Received blank Google ID token")
                    return@launch
                }
                stage = "FirebaseAuth.signInWithCredential"
                Firebase.auth.signInWithCredential(GoogleAuthProvider.getCredential(token, null)).await()
                onAuthSuccess()
            } else {
                onAuthError("[$stage] Unexpected credential: ${cred::class.java.simpleName} (type=${cred.type})")
            }
        } catch (e: GetCredentialCancellationException) {
            val details = formatAuthException(stage, e)
            Log.w("Auth", "Google Sign-In cancelled/aborted: $details", e)
            onAuthCancelled()
            onAuthError(details)
        } catch (e: GetCredentialException) {
            val details = formatAuthException(stage, e)
            Log.e("Auth", "Google Sign-In credential error: $details", e)
            onAuthError(details)
        } catch (e: FirebaseAuthException) {
            val details = formatAuthException(stage, e)
            Log.e("Auth", "Firebase Auth sign-in error: $details", e)
            onAuthError(details)
        } catch (e: Exception) {
            val details = formatAuthException(stage, e)
            Log.e("Auth", "Google Sign-In failed: $details", e)
            onAuthError(details)
        }
    }
}

fun signOut(
    credentialManager: CredentialManager,
    scope: CoroutineScope,
    onSignOutComplete: () -> Unit = {}
) {
    scope.launch {
        withContext(NonCancellable) {
            try {
                credentialManager.clearCredentialState(ClearCredentialStateRequest())
            } catch (e: Exception) {
                Log.w("Auth", "Failed to clear credential state: ${e.message}")
            } finally {
                Firebase.auth.signOut()
                onSignOutComplete()
            }
        }
    }
}
