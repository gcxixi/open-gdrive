package dev.opengdrive.auth

import android.app.Activity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope

class DriveAuthorization(private val activity: Activity) {
    private val client = Identity.getAuthorizationClient(activity)
    private val request = AuthorizationRequest.builder()
        .setRequestedScopes(listOf(Scope(DRIVE_SCOPE)))
        .build()

    fun authorize(
        resolutionLauncher: ActivityResultLauncher<IntentSenderRequest>,
        onToken: (String) -> Unit,
        onError: (Throwable) -> Unit,
    ) {
        client.authorize(request)
            .addOnSuccessListener { result -> handleResult(result, resolutionLauncher, onToken, onError) }
            .addOnFailureListener(onError)
    }

    fun consumeResolution(data: android.content.Intent?, onToken: (String) -> Unit, onError: (Throwable) -> Unit) {
        if (data == null) {
            onError(IllegalStateException("Google authorization was cancelled"))
            return
        }
        runCatching { client.getAuthorizationResultFromIntent(data) }
            .onSuccess { result -> result.accessToken?.let(onToken) ?: onError(missingToken()) }
            .onFailure(onError)
    }

    private fun handleResult(
        result: AuthorizationResult,
        launcher: ActivityResultLauncher<IntentSenderRequest>,
        onToken: (String) -> Unit,
        onError: (Throwable) -> Unit,
    ) {
        when {
            result.hasResolution() -> launcher.launch(
                IntentSenderRequest.Builder(result.pendingIntent!!.intentSender).build(),
            )
            result.accessToken != null -> onToken(result.accessToken!!)
            else -> onError(missingToken())
        }
    }

    private fun missingToken() = IllegalStateException("Google authorization returned no access token")

    companion object {
        const val DRIVE_SCOPE = "https://www.googleapis.com/auth/drive"
    }
}
