package com.example.timerewind

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.DefaultItemAnimator
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.airbnb.lottie.LottieAnimationView
import java.text.SimpleDateFormat
import java.util.*

class TimelineActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: TimelineAdapter
    private lateinit var spinner: Spinner

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("TimelineActivity", "onCreate started")
        
        // Check if user is logged in
        if (!isUserLoggedIn()) {
            startLoginActivity()
            return
        }
        
        setContentView(R.layout.activity_timeline)

        recyclerView = findViewById(R.id.memoryRecycler)
        spinner = findViewById(R.id.emotionFilter)
        val emptyLottie = findViewById<LottieAnimationView>(R.id.emptyLottie)

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.itemAnimator = DefaultItemAnimator().apply {
            addDuration = 350
            removeDuration = 200
        }

        val emotions = listOf("All", "joy", "sadness", "neutral", "anger", "fear", "surprise")
        val spinnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, emotions)
        spinner.adapter = spinnerAdapter

        // Initialize with all memories first
        loadMemories("All")

        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: android.view.View?, pos: Int, id: Long) {
                val selectedEmotion = parent.getItemAtPosition(pos).toString()
                Log.d("TimelineActivity", "Emotion selected: $selectedEmotion")
                loadMemories(selectedEmotion)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // Bottom navigation setup
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNav)
        bottomNav.selectedItemId = R.id.nav_timeline
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    startActivity(Intent(this, MainActivity::class.java))
                    overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                    finish()
                    true
                }
                R.id.nav_timeline -> false // Already here
                R.id.nav_search -> {
                    startActivity(Intent(this, MemorySearchActivity::class.java))
                    overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                    finish()
                    true
                }
                R.id.nav_assistant -> {
                    startActivity(Intent(this, AssistantActivity::class.java))
                    overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                    finish()
                    true
                }
                else -> false
            }
        }
        Log.d("TimelineActivity", "onCreate completed")
    }

    override fun onResume() {
        super.onResume()
        // Refresh the timeline when returning to this activity
        val selectedEmotion = spinner.selectedItem.toString()
        loadMemories(selectedEmotion)
    }

    private fun loadMemories(selectedEmotion: String) {
        try {
            Log.d("TimelineActivity", "Loading memories for emotion: $selectedEmotion")
            val dbHelper = DatabaseHelper(this@TimelineActivity)
            val allMemories = dbHelper.getAllMemories()
            Log.d("TimelineActivity", "Retrieved ${allMemories.size} memories from database")
            
            val allMoments = allMemories.mapNotNull { memory ->
                try {
                    val parsedTime = memory.timestamp.toLongOrNull()
                    val timeString = if (parsedTime != null) {
                        try {
                            SimpleDateFormat("MMM dd, yyyy 'at' hh:mm a", Locale.getDefault()).format(Date(parsedTime))
                        } catch (e: Exception) {
                            Log.w("TimelineActivity", "Error formatting time: ${e.message}")
                            memory.timestamp
                        }
                    } else {
                        memory.timestamp
                    }
                    
                    val stressLevel = try {
                        memory.voice_stress?.stress_level?.toFloat() ?: 0f
                    } catch (e: Exception) {
                        Log.w("TimelineActivity", "Error parsing stress level: ${e.message}")
                        0f
                    }
                    
                    MemoryMoment(
                        time = timeString,
                        transcript = memory.text ?: "",
                        emotion = memory.emotion ?: "",
                        stress = stressLevel
                    )
                } catch (e: Exception) {
                    Log.e("TimelineActivity", "Error processing memory: ${e.message}")
                    // Skip this memory if there's an error processing it
                    null
                }
            }
            
            val filtered = if (selectedEmotion == "All") {
                allMoments
            } else {
                allMoments.filter { it.emotion == selectedEmotion }
            }

            Log.d("TimelineActivity", "Created ${filtered.size} moments for display")
            adapter = TimelineAdapter(filtered)
            recyclerView.adapter = adapter

            val emptyLottie = findViewById<LottieAnimationView>(R.id.emptyLottie)
            if (filtered.isEmpty()) {
                recyclerView.visibility = View.GONE
                emptyLottie.visibility = View.VISIBLE
            } else {
                recyclerView.visibility = View.VISIBLE
                emptyLottie.visibility = View.GONE
            }
        } catch (e: Exception) {
            Log.e("TimelineActivity", "Error loading memories: ${e.message}")
            // Handle any database or processing errors
            adapter = TimelineAdapter(emptyList())
            recyclerView.adapter = adapter
        }
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

    override fun onBackPressed() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        startActivity(intent)
        finish()
    }
} 