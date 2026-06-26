package com.example.timerewind

import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.switchmaterial.SwitchMaterial

class SettingsActivity : AppCompatActivity() {

    private lateinit var profileCard: MaterialCardView
    private lateinit var profileImage: ImageView
    private lateinit var profileName: TextView
    private lateinit var profileEmail: TextView
    private lateinit var darkModeSwitch: SwitchMaterial
    private lateinit var logoutButton: MaterialButton
    private lateinit var backButton: ImageView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        // Initialize views
        profileCard = findViewById(R.id.profileCard)
        profileImage = findViewById(R.id.profileImage)
        profileName = findViewById(R.id.profileName)
        profileEmail = findViewById(R.id.profileEmail)
        darkModeSwitch = findViewById(R.id.darkModeSwitch)
        logoutButton = findViewById(R.id.logoutButton)
        backButton = findViewById(R.id.backButton)

        // Load user data
        loadUserData()

        // Set up dark mode switch
        setupDarkModeSwitch()

        // Set up click listeners
        setupClickListeners()
    }

    private fun loadUserData() {
        val sharedPrefs = getSharedPreferences("TimeRewindPrefs", MODE_PRIVATE)
        val userEmail = sharedPrefs.getString("userEmail", "user@example.com") ?: "user@example.com"
        
        // Set profile data
        profileName.text = "Time Rewind User"
        profileEmail.text = userEmail
        
        // Set profile image (using app icon for now)
        profileImage.setImageResource(R.drawable.ic_baseline_history_24)
        profileImage.setColorFilter(ContextCompat.getColor(this, R.color.colorPrimary))
    }

    private fun setupDarkModeSwitch() {
        val sharedPrefs = getSharedPreferences("TimeRewindPrefs", MODE_PRIVATE)
        val isDarkMode = sharedPrefs.getBoolean("isDarkMode", false)
        
        darkModeSwitch.isChecked = isDarkMode
        
        darkModeSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            } else {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            }
            
            // Save preference
            sharedPrefs.edit().putBoolean("isDarkMode", isChecked).apply()
        }
    }

    private fun setupClickListeners() {
        backButton.setOnClickListener {
            finish()
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }

        profileCard.setOnClickListener {
            // TODO: Implement profile editing
            // For now, just show a toast
            android.widget.Toast.makeText(this, "Profile editing coming soon!", android.widget.Toast.LENGTH_SHORT).show()
        }

        logoutButton.setOnClickListener {
            performLogout()
        }
    }

    private fun performLogout() {
        // Clear login state
        val sharedPrefs = getSharedPreferences("TimeRewindPrefs", MODE_PRIVATE)
        with(sharedPrefs.edit()) {
            putBoolean("isLoggedIn", false)
            remove("userEmail")
            apply()
        }

        // Navigate to login activity
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }
} 