package com.example.timerewind

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.timerewind.TranscriptionResponse
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.asRequestBody
import retrofit2.*
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.util.concurrent.TimeUnit
import com.google.android.material.bottomnavigation.BottomNavigationView
import androidx.appcompat.app.AppCompatDelegate

class MainActivity : AppCompatActivity() {

    private lateinit var recordButton: Button
    private lateinit var recordingStatus: TextView
    private lateinit var transcriptView: TextView
    private var recorder: MediaRecorder? = null
    private var isRecording = false
    private lateinit var audioFile: File
    private var recordingStartTime: Long = 0
    private val handler = Handler(Looper.getMainLooper())
    private var recordingRunnable: Runnable? = null

    private lateinit var apiService: ApiService
    private lateinit var dbHelper: DatabaseHelper
    private lateinit var settingsButton: ImageView
    private lateinit var secureStorage: SecureStorage

    private val PERMISSION_REQUEST_CODE = 100
    private val transcriptHint = "(Transcripts will appear here)"

    override fun onCreate(savedInstanceState: Bundle?) {
        // Check for first-time consent
        val prefs = getSharedPreferences("prefs", MODE_PRIVATE)
        val hasConsent = prefs.getBoolean("consentGiven", false)
        if (!hasConsent) {
            startActivity(Intent(this, ConsentActivity::class.java))
            finish()
            return
        }

        // Set night mode based on user preference before anything else
        val sharedPrefs = getSharedPreferences("TimeRewindPrefs", MODE_PRIVATE)
        val isDarkMode = sharedPrefs.getBoolean("isDarkMode", false)
        if (isDarkMode) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        }
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Connect UI elements
        recordButton = findViewById(R.id.recordButton)
        recordingStatus = findViewById(R.id.recordingStatus)
        transcriptView = findViewById(R.id.transcriptView)
        showTranscriptHintIfEmpty()

        // Initialize database helper
        dbHelper = DatabaseHelper(this)

        // Initialize secure storage
        secureStorage = SecureStorage(this)

        // Initialize Retrofit + ApiService with increased timeouts
        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl("http://192.168.1.13:5000/") // Use "10.0.2.2" if using Android emulator for localhost
            .addConverterFactory(GsonConverterFactory.create())
            .client(client)
            .build()

        apiService = retrofit.create(ApiService::class.java)

        // Request permissions
        checkAndRequestPermissions()

        // Handle record button
        recordButton.setOnClickListener {
            if (!hasPermissions()) {
                checkAndRequestPermissions()
                return@setOnClickListener
            }

            if (isRecording) stopRecording() else startRecording()
        }

        // Handle background recording toggle
        var isBackgroundRecording = false
        findViewById<Button>(R.id.toggleRecord).setOnClickListener {
            val serviceIntent = Intent(this, BackgroundAudioService::class.java)
            if (!isBackgroundRecording) {
                startService(serviceIntent)
                it as Button
                it.text = "Stop Background Recording"
            } else {
                stopService(serviceIntent)
                it as Button
                it.text = "Start Background Recording"
            }
            isBackgroundRecording = !isBackgroundRecording
        }

        // Bottom navigation setup
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNav)
        bottomNav.selectedItemId = R.id.nav_home
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> false // Already here
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
                R.id.nav_assistant -> {
                    startActivity(Intent(this, AssistantActivity::class.java))
                    overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                    finish()
                    true
                }
                else -> false
            }
        }
        settingsButton = findViewById(R.id.settingsButton)
        settingsButton.setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }
    }

    private fun hasPermissions(): Boolean {
        val audioPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
        return audioPermission == PackageManager.PERMISSION_GRANTED
    }

    private fun checkAndRequestPermissions() {
        val permissionsNeeded = mutableListOf<String>()
        if (!hasPermissions()) permissionsNeeded.add(Manifest.permission.RECORD_AUDIO)

        if (permissionsNeeded.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsNeeded.toTypedArray(), PERMISSION_REQUEST_CODE)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                // Permissions granted
            }
        }
    }

    private fun startRecording() {
        try {
            val fileName = "recorded_${System.currentTimeMillis()}.m4a"
            audioFile = File(getExternalFilesDir(null), fileName)

            recorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(audioFile.absolutePath)
                prepare()
                start()
            }

            isRecording = true
            recordingStartTime = System.currentTimeMillis()
            recordingStatus.text = "🎙 Recording... 00:00"
            recordButton.text = "🛑 Stop Recording"

            // Start recording timer
            startRecordingTimer()

        } catch (e: Exception) {
            recordingStatus.text = "Error starting recording"
        }
    }

    private fun startRecordingTimer() {
        recordingRunnable = object : Runnable {
            override fun run() {
                if (isRecording) {
                    val elapsedTime = System.currentTimeMillis() - recordingStartTime
                    val seconds = (elapsedTime / 1000).toInt()
                    val minutes = seconds / 60
                    val remainingSeconds = seconds % 60
                    recordingStatus.text = "🎙 Recording... ${String.format("%02d:%02d", minutes, remainingSeconds)}"
                    
                    // Stop recording after 60 seconds
                    if (seconds >= 60) {
                        stopRecording()
                        return
                    }
                    
                    handler.postDelayed(this, 1000)
                }
            }
        }
        handler.post(recordingRunnable!!)
    }

    private fun stopRecording() {
        try {
            // Stop the timer
            recordingRunnable?.let { handler.removeCallbacks(it) }
            
            recorder?.apply {
                stop()
                release()
            }
            recorder = null
            isRecording = false

            recordingStatus.text = "Not Recording"
            recordButton.text = "🎙 Start Recording"
            transcriptView.append("📤 File saved: ${audioFile.name}\n")

            // Send to server
            sendAudioToServer(audioFile)

            showTranscriptHintIfEmpty()

        } catch (e: Exception) {
            recordingStatus.text = "Error stopping recording"
        }
    }

    private fun sendAudioToServer(file: File) {
        recordingStatus.text = "🔄 Processing audio..."
        val requestFile = file.asRequestBody("audio/m4a".toMediaTypeOrNull())
        val body = MultipartBody.Part.createFormData("audio", file.name, requestFile)
        val call = apiService.transcribeAudio(body)
        call.enqueue(object : Callback<TranscriptionResponse> {
            override fun onResponse(
                call: Call<TranscriptionResponse>,
                response: Response<TranscriptionResponse>
            ) {
                if (response.isSuccessful) {
                    val responseBody = response.body()
                    val transcript = responseBody?.transcript ?: "No transcript received."
                    val emotion = responseBody?.emotion
                    runOnUiThread {
                        recordingStatus.text = "Not Recording"
                        setTranscriptText("\uD83D\uDCDD $transcript\n" + (if (emotion != null) "😊 Mood: $emotion\n\n" else ""))
                    }
                    // Save transcript to server for search with emotion and stress data
                    val saveBody = HashMap<String, String>()
                    saveBody["text"] = transcript
                    saveBody["timestamp"] = System.currentTimeMillis().toString()
                    if (emotion != null) {
                        saveBody["emotion"] = emotion
                    }
                    val confidence = responseBody?.confidence
                    if (confidence != null) {
                        saveBody["confidence"] = confidence.toString()
                    }
                    val voiceStress = responseBody?.voice_stress
                    if (voiceStress != null) {
                        saveBody["stress_level"] = voiceStress.stress_level.toString()
                        saveBody["stress_category"] = voiceStress.stress_category
                    }
                    apiService.saveTranscript(saveBody).enqueue(object : Callback<Any> {
                        override fun onResponse(call: Call<Any>, response: Response<Any>) {}
                        override fun onFailure(call: Call<Any>, t: Throwable) {}
                    })
                    
                    // Save to secure storage
                    secureStorage.saveTranscript(transcript, System.currentTimeMillis(), emotion)
                    
                    val memoryItem = MemoryItem(
                        text = transcript,
                        timestamp = System.currentTimeMillis().toString(),
                        emotion = emotion,
                        confidence = confidence,
                        voice_stress = voiceStress
                    )
                    dbHelper.insertMemory(memoryItem)
                } else {
                    runOnUiThread {
                        recordingStatus.text = "Not Recording"
                        val errorMsg = response.errorBody()?.string() ?: "Unknown error"
                        setTranscriptText("\u274C Server error: ${response.code()}\n$errorMsg\n")
                    }
                }
            }
            override fun onFailure(call: Call<TranscriptionResponse>, t: Throwable) {
                runOnUiThread {
                    recordingStatus.text = "Not Recording"
                    setTranscriptText("❌ Network error: ${t.message}\n")
                }
            }
        })
    }

    private fun showTranscriptHintIfEmpty() {
        if (transcriptView.text.isNullOrBlank()) {
            transcriptView.text = transcriptHint
            transcriptView.setAlpha(0.5f)
        } else if (transcriptView.text == transcriptHint) {
            transcriptView.setAlpha(0.5f)
        } else {
            transcriptView.setAlpha(1.0f)
        }
    }

    private fun setTranscriptText(text: String) {
        if (text.isBlank()) {
            transcriptView.text = transcriptHint
            transcriptView.setAlpha(0.5f)
        } else {
            transcriptView.text = text
            transcriptView.setAlpha(1.0f)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        recordingRunnable?.let { handler.removeCallbacks(it) }
        recorder?.release()
    }
}
