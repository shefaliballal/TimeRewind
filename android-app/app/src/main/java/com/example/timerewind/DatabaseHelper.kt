package com.example.timerewind

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.content.ContentValues
import com.google.gson.Gson

class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, "memory.db", null, 3) {

    override fun onCreate(db: SQLiteDatabase) {
        // Create memory table
        val createMemoryTable = """
            CREATE TABLE memory (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                transcript TEXT,
                timestamp TEXT,
                emotion TEXT,
                confidence REAL,
                voice_stress TEXT
            );
        """.trimIndent()
        db.execSQL(createMemoryTable)
        
        // Create users table
        val createUsersTable = """
            CREATE TABLE users (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                email TEXT UNIQUE NOT NULL,
                password_hash TEXT NOT NULL,
                created_at TEXT DEFAULT CURRENT_TIMESTAMP
            );
        """.trimIndent()
        db.execSQL(createUsersTable)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE memory ADD COLUMN emotion TEXT;")
            db.execSQL("ALTER TABLE memory ADD COLUMN confidence REAL;")
            db.execSQL("ALTER TABLE memory ADD COLUMN voice_stress TEXT;")
        }
        if (oldVersion < 3) {
            // Create users table for authentication
            val createUsersTable = """
                CREATE TABLE users (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    email TEXT UNIQUE NOT NULL,
                    password_hash TEXT NOT NULL,
                    created_at TEXT DEFAULT CURRENT_TIMESTAMP
                );
            """.trimIndent()
            db.execSQL(createUsersTable)
        }
    }

    fun insertMemory(memory: MemoryItem) {
        val db = writableDatabase
        val gson = Gson()
        val values = ContentValues().apply {
            put("transcript", memory.text)
            put("timestamp", memory.timestamp)
            put("emotion", memory.emotion)
            put("confidence", memory.confidence)
            put("voice_stress", gson.toJson(memory.voice_stress))
        }
        db.insert("memory", null, values)
        db.close()
    }

    fun getAllMemories(): List<MemoryItem> {
        val db = readableDatabase
        val cursor = db.query("memory", null, null, null, null, null, "id DESC")
        val memories = mutableListOf<MemoryItem>()
        val gson = Gson()
        
        try {
            while (cursor.moveToNext()) {
                try {
                    val text = cursor.getString(cursor.getColumnIndexOrThrow("transcript")) ?: ""
                    val timestamp = cursor.getString(cursor.getColumnIndexOrThrow("timestamp")) ?: ""
                    val emotion = cursor.getString(cursor.getColumnIndexOrThrow("emotion"))
                    val confidence = if (cursor.isNull(cursor.getColumnIndexOrThrow("confidence"))) null else cursor.getDouble(cursor.getColumnIndexOrThrow("confidence"))
                    val voiceStressJson = cursor.getString(cursor.getColumnIndexOrThrow("voice_stress"))
                    
                    val voiceStress = try {
                        if (voiceStressJson != null && voiceStressJson.isNotEmpty()) {
                            gson.fromJson(voiceStressJson, VoiceStressData::class.java)
                        } else {
                            null
                        }
                    } catch (e: Exception) {
                        null
                    }
                    
                    memories.add(
                        MemoryItem(
                            text = text,
                            timestamp = timestamp,
                            emotion = emotion,
                            confidence = confidence,
                            voice_stress = voiceStress
                        )
                    )
                } catch (e: Exception) {
                    // Skip this row if there's an error processing it
                    continue
                }
            }
        } finally {
            cursor.close()
            db.close()
        }
        
        return memories
    }

    fun searchMemoriesLocal(query: String): List<MemoryItem> {
        val db = readableDatabase
        val memories = mutableListOf<MemoryItem>()
        val gson = Gson()
        val selection = "transcript LIKE ? OR emotion LIKE ?"
        val selectionArgs = arrayOf("%$query%", "%$query%")
        val cursor = db.query("memory", null, selection, selectionArgs, null, null, "id DESC")
        
        try {
            while (cursor.moveToNext()) {
                try {
                    val text = cursor.getString(cursor.getColumnIndexOrThrow("transcript")) ?: ""
                    val timestamp = cursor.getString(cursor.getColumnIndexOrThrow("timestamp")) ?: ""
                    val emotion = cursor.getString(cursor.getColumnIndexOrThrow("emotion"))
                    val confidence = if (cursor.isNull(cursor.getColumnIndexOrThrow("confidence"))) null else cursor.getDouble(cursor.getColumnIndexOrThrow("confidence"))
                    val voiceStressJson = cursor.getString(cursor.getColumnIndexOrThrow("voice_stress"))
                    
                    val voiceStress = try {
                        if (voiceStressJson != null && voiceStressJson.isNotEmpty()) {
                            gson.fromJson(voiceStressJson, VoiceStressData::class.java)
                        } else {
                            null
                        }
                    } catch (e: Exception) {
                        null
                    }
                    
                    memories.add(
                        MemoryItem(
                            text = text,
                            timestamp = timestamp,
                            emotion = emotion,
                            confidence = confidence,
                            voice_stress = voiceStress
                        )
                    )
                } catch (e: Exception) {
                    // Skip this row if there's an error processing it
                    continue
                }
            }
        } finally {
            cursor.close()
            db.close()
        }
        
        return memories
    }
    
    // Authentication methods
    fun registerUser(email: String, passwordHash: String): Boolean {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("email", email)
            put("password_hash", passwordHash)
        }
        
        return try {
            val result = db.insert("users", null, values)
            result != -1L
        } catch (e: Exception) {
            false
        } finally {
            db.close()
        }
    }
    
    fun authenticateUser(email: String, passwordHash: String): Boolean {
        val db = readableDatabase
        val cursor = db.query(
            "users",
            arrayOf("id"),
            "email = ? AND password_hash = ?",
            arrayOf(email, passwordHash),
            null, null, null
        )
        
        val isValid = cursor.count > 0
        cursor.close()
        db.close()
        return isValid
    }
    
    fun userExists(email: String): Boolean {
        val db = readableDatabase
        val cursor = db.query(
            "users",
            arrayOf("id"),
            "email = ?",
            arrayOf(email),
            null, null, null
        )
        
        val exists = cursor.count > 0
        cursor.close()
        db.close()
        return exists
    }
} 