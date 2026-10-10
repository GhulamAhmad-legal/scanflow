package com.example.security

import android.app.Activity
import android.content.Context
import android.view.WindowManager
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class LockTimeoutOption(val label: String, val millis: Long) {
    IMMEDIATELY("Immediately", 0L),
    ONE_MINUTE("1 Minute", 60_000L),
    FIVE_MINUTES("5 Minutes", 300_000L)
}

class SecurityManager(private val context: Context) {

    private val _unlockedDocumentIds = MutableStateFlow<Set<Long>>(emptySet())
    val unlockedDocumentIds: StateFlow<Set<Long>> = _unlockedDocumentIds.asStateFlow()

    private val _unlockedFolderIds = MutableStateFlow<Set<Long>>(emptySet())
    val unlockedFolderIds: StateFlow<Set<Long>> = _unlockedFolderIds.asStateFlow()

    private var lastActiveTimestamp: Long = System.currentTimeMillis()
    var lockTimeout: LockTimeoutOption = LockTimeoutOption.IMMEDIATELY

    fun isDocumentUnlocked(docId: Long): Boolean {
        checkTimeout()
        return _unlockedDocumentIds.value.contains(docId)
    }

    fun isFolderUnlocked(folderId: Long): Boolean {
        checkTimeout()
        return _unlockedFolderIds.value.contains(folderId)
    }

    fun markDocumentUnlocked(docId: Long) {
        lastActiveTimestamp = System.currentTimeMillis()
        _unlockedDocumentIds.value = _unlockedDocumentIds.value + docId
    }

    fun markFolderUnlocked(folderId: Long) {
        lastActiveTimestamp = System.currentTimeMillis()
        _unlockedFolderIds.value = _unlockedFolderIds.value + folderId
    }

    fun lockAll() {
        _unlockedDocumentIds.value = emptySet()
        _unlockedFolderIds.value = emptySet()
    }

    fun checkTimeout() {
        if (lockTimeout.millis > 0) {
            val elapsed = System.currentTimeMillis() - lastActiveTimestamp
            if (elapsed > lockTimeout.millis) {
                lockAll()
            }
        }
    }

    fun authenticateBiometric(
        activity: FragmentActivity,
        title: String = "Unlock Document",
        subtitle: String = "Authenticate to access encrypted files",
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    onError(errString.toString())
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    onError("Authentication failed")
                }
            }
        )

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
            .build()

        prompt.authenticate(promptInfo)
    }

    fun setFlagSecure(activity: Activity, secure: Boolean) {
        if (secure) {
            activity.window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
            )
        } else {
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
}
