package com.example.timerewind

import android.content.Intent
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomnavigation.BottomNavigationView
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.*

class AssistantActivity : AppCompatActivity() {

    private lateinit var queryInput: EditText
    private lateinit var responseView: TextView
    private lateinit var askButton: Button
    private lateinit var tts: TextToSpeech
    private lateinit var dbHelper: DatabaseHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Check if user is logged in
        if (!isUserLoggedIn()) {
            startLoginActivity()
            return
        }
        
        setContentView(R.layout.activity_assistant)

        queryInput = findViewById(R.id.queryInput)
        responseView = findViewById(R.id.responseView)
        askButton = findViewById(R.id.askButton)
        dbHelper = DatabaseHelper(this)

        // Initialize Text-to-Speech
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts.language = Locale.US
                Log.d("AssistantActivity", "TTS initialized successfully")
            } else {
                Log.e("AssistantActivity", "TTS initialization failed")
            }
        }

        askButton.setOnClickListener {
            val query = queryInput.text.toString()
            if (query.isNotEmpty()) {
                askButton.isEnabled = false
                responseView.text = "🤔 Thinking..."
                
                // Get all memories from database
                val allMemories = dbHelper.getAllMemories()
                val contextData = allMemories.joinToString("\n") { memory ->
                    val timestamp = try {
                        val parsedTime = memory.timestamp.toLongOrNull()
                        if (parsedTime != null) {
                            java.text.SimpleDateFormat("MMM dd, yyyy 'at' hh:mm a", Locale.getDefault()).format(java.util.Date(parsedTime))
                        } else {
                            memory.timestamp
                        }
                    } catch (e: Exception) {
                        memory.timestamp
                    }
                    
                    val emotion = memory.emotion ?: "unknown"
                    val stress = memory.voice_stress?.stress_level ?: "0.0"
                    val transcript = memory.text ?: ""
                    
                    "$timestamp: [$emotion, stress=$stress] — $transcript"
                }

                sendToAssistantServer(query, contextData)
            }
        }

        // Bottom navigation setup
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNav)
        bottomNav.selectedItemId = R.id.nav_assistant
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    startActivity(Intent(this, MainActivity::class.java))
                    overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                    finish()
                    true
                }
                R.id.nav_timeline -> {
                    startActivity(Intent(this, TimelineActivity::class.java))
                    overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                    finish()
                    true
                }
                R.id.nav_search -> {
                    startActivity(Intent(this, MemorySearchActivity::class.java))
                    overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                    finish()
                    true
                }
                R.id.nav_assistant -> false // Already here
                else -> false
            }
        }
    }

    private fun sendToAssistantServer(question: String, context: String) {
        val retrofit = Retrofit.Builder()
            .baseUrl("http://192.168.1.13:5000/") // Using the same server as transcription
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        val api = retrofit.create(AssistantApi::class.java)

        val request = AssistantRequest(question, context)
        api.askQuestion(request).enqueue(object : Callback<AssistantResponse> {
            override fun onResponse(call: Call<AssistantResponse>, response: Response<AssistantResponse>) {
                askButton.isEnabled = true
                if (response.isSuccessful) {
                    val reply = response.body()?.reply ?: "No response received"
                    responseView.text = reply
                    // Speak the response
                    tts.speak(reply, TextToSpeech.QUEUE_FLUSH, null, null)
                } else {
                    responseView.text = "❌ Error from assistant: ${response.code()}"
                }
            }

            override fun onFailure(call: Call<AssistantResponse>, t: Throwable) {
                askButton.isEnabled = true
                responseView.text = "❌ Network error: ${t.message}"
                Log.e("AssistantActivity", "Network error", t)
            }
        })
    }

    private fun isUserLoggedIn(): Boolean {
        val sharedPrefs = getSharedPreferences("TimeRewindPrefs", MODE_PRIVATE)
        return sharedPrefs.getBoolean("isLoggedIn", false)
    }

    private fun startLoginActivity() {
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }

    override fun onDestroy() {
        tts.shutdown()
        super.onDestroy()
    }

    override fun onBackPressed() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        startActivity(intent)
        finish()
    }
} 