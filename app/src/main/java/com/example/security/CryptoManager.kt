package com.example.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class CryptoManager(private val context: Context) {

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val MASTER_KEY_ALIAS = "ScanFlowMasterKey"
        private const val GCM_TAG_LENGTH = 128
        private const val GCM_IV_LENGTH = 12
        private const val PBKDF2_ITERATIONS = 310000
    }

    private val keyStore: KeyStore by lazy {
        KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
    }

    private fun getOrCreateMasterKey(): SecretKey {
        if (!keyStore.containsAlias(MASTER_KEY_ALIAS)) {
            val keyGen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            val spec = KeyGenParameterSpec.Builder(
                MASTER_KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
            keyGen.init(spec)
            keyGen.generateKey()
        }
        return (keyStore.getEntry(MASTER_KEY_ALIAS, null) as KeyStore.SecretKeyEntry).secretKey
    }

    fun deriveKeyFromPassword(password: String, salt: ByteArray): SecretKey {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, 256)
        val tmp = factory.generateSecret(spec)
        return SecretKeySpec(tmp.encoded, "AES")
    }

    fun generateSalt(): ByteArray {
        val salt = ByteArray(16)
        SecureRandom().nextBytes(salt)
        return salt
    }

    suspend fun encryptFile(sourcePath: String, password: String? = null, salt: ByteArray? = null): String = withContext(Dispatchers.IO) {
        val sourceFile = File(sourcePath)
        if (!sourceFile.exists()) return@withContext sourcePath

        val secretKey = if (password != null && salt != null) {
            deriveKeyFromPassword(password, salt)
        } else {
            getOrCreateMasterKey()
        }

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val iv = cipher.iv

        val destPath = "$sourcePath.enc"
        val tempFile = File("$destPath.tmp")

        FileOutputStream(tempFile).use { out ->
            // Prepend IV
            out.write(iv)
            FileInputStream(sourceFile).use { inStream ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (inStream.read(buffer).also { bytesRead = it } != -1) {
                    val encryptedChunk = cipher.update(buffer, 0, bytesRead)
                    if (encryptedChunk != null) out.write(encryptedChunk)
                }
                val finalChunk = cipher.doFinal()
                if (finalChunk != null) out.write(finalChunk)
            }
        }

        // Atomic file rename
        val destFile = File(destPath)
        tempFile.renameTo(destFile)
        sourceFile.delete()

        destPath
    }

    suspend fun decryptFileToMemory(encPath: String, password: String? = null, salt: ByteArray? = null): ByteArray? = withContext(Dispatchers.IO) {
        val encFile = File(encPath)
        if (!encFile.exists()) return@withContext null

        val secretKey = if (password != null && salt != null) {
            deriveKeyFromPassword(password, salt)
        } else {
            getOrCreateMasterKey()
        }

        FileInputStream(encFile).use { inStream ->
            val iv = ByteArray(GCM_IV_LENGTH)
            val readIv = inStream.read(iv)
            if (readIv != GCM_IV_LENGTH) return@withContext null

            val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)

            val ciphertext = inStream.readBytes()
            cipher.doFinal(ciphertext)
        }
    }

    suspend fun decryptFileToDisk(encPath: String, password: String? = null, salt: ByteArray? = null): String = withContext(Dispatchers.IO) {
        val bytes = decryptFileToMemory(encPath, password, salt) ?: return@withContext encPath
        val plainPath = encPath.removeSuffix(".enc")
        val tempFile = File("$plainPath.tmp")

        FileOutputStream(tempFile).use { out ->
            out.write(bytes)
        }

        val plainFile = File(plainPath)
        tempFile.renameTo(plainFile)
        File(encPath).delete()

        plainPath
    }
}
