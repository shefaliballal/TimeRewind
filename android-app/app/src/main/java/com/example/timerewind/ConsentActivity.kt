package com.example.timerewind

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class ConsentActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_consent)

        val agreeBtn = findViewById<Button>(R.id.agreeBtn)
        agreeBtn.setOnClickListener {
            val prefs = getSharedPreferences("prefs", MODE_PRIVATE)
            prefs.edit().putBoolean("consentGiven", true).apply()

            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }
} 