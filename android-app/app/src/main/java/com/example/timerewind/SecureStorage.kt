package com.example.timerewind

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.io.File
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.KeyStore
import java.util.*
import android.util.Base64

class SecureStorage(private val context: Context) {
    
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val encryptedPrefs = EncryptedSharedPreferences.create(
        context,
        "secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    // Save encrypted text data
    fun saveEncryptedText(key: String, value: String) {
        encryptedPrefs.edit().putString(key, value).apply()
    }

    // Read encrypted text data
    fun getEncryptedText(key: String, defaultValue: String? = null): String? {
        return encryptedPrefs.getString(key, defaultValue)
    }

    // Save encrypted file
    fun saveEncryptedFile(fileName: String, data: ByteArray) {
        try {
            val keyGenerator = KeyGenerator.getInstance("AES")
            keyGenerator.init(256)
            val secretKey = keyGenerator.generateKey()

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)

            val iv = cipher.iv
            val encryptedData = cipher.doFinal(data)

            // Save encrypted data
            val encryptedFile = File(context.filesDir, "${fileName}.enc")
            FileOutputStream(encryptedFile).use { fos ->
                fos.write(iv)
                fos.write(encryptedData)
            }

            // Save key reference (in production, use Android Keystore)
            saveEncryptedText("key_$fileName", Base64.encodeToString(secretKey.encoded, Base64.DEFAULT))
            
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Read encrypted file
    fun getEncryptedFile(fileName: String): ByteArray? {
        try {
            val encryptedFile = File(context.filesDir, "${fileName}.enc")
            if (!encryptedFile.exists()) return null

            val keyString = getEncryptedText("key_$fileName")
            if (keyString == null) return null

            val keyBytes = Base64.decode(keyString, Base64.DEFAULT)
            val keyGenerator = KeyGenerator.getInstance("AES")
            keyGenerator.init(256)
            val secretKey = keyGenerator.generateKey()

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey)

            FileInputStream(encryptedFile).use { fis ->
                val iv = ByteArray(12)
                fis.read(iv)
                
                val encryptedData = fis.readBytes()
                cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
                
                return cipher.doFinal(encryptedData)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    // Save transcript with encryption
    fun saveTranscript(transcript: String, timestamp: Long, emotion: String? = null) {
        val key = "transcript_$timestamp"
        val data = mapOf(
            "text" to transcript,
            "timestamp" to timestamp.toString(),
            "emotion" to (emotion ?: ""),
            "encrypted_at" to System.currentTimeMillis().toString()
        )
        
        saveEncryptedText(key, com.google.gson.Gson().toJson(data))
    }

    // Get all encrypted transcripts
    fun getAllTranscripts(): List<Map<String, String>> {
        val transcripts = mutableListOf<Map<String, String>>()
        val allPrefs = encryptedPrefs.all
        
        allPrefs.forEach { (key, value) ->
            if (key.startsWith("transcript_")) {
                try {
                    val data = com.google.gson.Gson().fromJson(value as String, Map::class.java)
                    transcripts.add(data as Map<String, String>)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        
        return transcripts.sortedByDescending { it["timestamp"]?.toLongOrNull() ?: 0L }
    }

    // Clear all encrypted data
    fun clearAllData() {
        encryptedPrefs.edit().clear().apply()
        
        // Clear encrypted files
        context.filesDir.listFiles()?.forEach { file ->
            if (file.name.endsWith(".enc")) {
                file.delete()
            }
        }
    }
} 