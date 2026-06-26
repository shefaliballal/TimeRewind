package com.example.timerewind

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textview.MaterialTextView
import java.security.MessageDigest

class LoginActivity : AppCompatActivity() {

    private lateinit var emailInput: TextInputEditText
    private lateinit var passwordInput: TextInputEditText
    private lateinit var loginButton: MaterialButton
    private lateinit var forgotPasswordText: MaterialTextView
    private lateinit var signUpText: MaterialTextView
    private lateinit var databaseHelper: DatabaseHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        databaseHelper = DatabaseHelper(this)

        // Initialize views
        emailInput = findViewById(R.id.emailInput)
        passwordInput = findViewById(R.id.passwordInput)
        loginButton = findViewById(R.id.loginButton)
        forgotPasswordText = findViewById(R.id.forgotPasswordText)
        signUpText = findViewById(R.id.signUpText)

        // Check if user is already logged in
        if (isUserLoggedIn()) {
            startMainActivity()
            return
        }

        // Set up click listeners
        loginButton.setOnClickListener {
            performLogin()
        }

        forgotPasswordText.setOnClickListener {
            // TODO: Implement forgot password functionality
            Toast.makeText(this, "Forgot password feature coming soon!", Toast.LENGTH_SHORT).show()
        }

        signUpText.setOnClickListener {
            val intent = Intent(this, SignupActivity::class.java)
            startActivity(intent)
        }
    }

    private fun performLogin() {
        val email = emailInput.text.toString().trim()
        val password = passwordInput.text.toString().trim()

        // Validation
        if (email.isEmpty()) {
            emailInput.error = "Email is required"
            return
        }

        if (password.isEmpty()) {
            passwordInput.error = "Password is required"
            return
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailInput.error = "Please enter a valid email"
            return
        }

        // Show loading state
        loginButton.isEnabled = false
        loginButton.text = "Signing in..."

        // Hash password and authenticate
        val passwordHash = hashPassword(password)
        
        if (databaseHelper.authenticateUser(email, passwordHash)) {
            // Save login state
            saveLoginState(email)
            
            // Navigate to main activity
            startMainActivity()
        } else {
            Toast.makeText(this, "Invalid email or password", Toast.LENGTH_SHORT).show()
            loginButton.isEnabled = true
            loginButton.text = "Sign In"
        }
    }

    private fun hashPassword(password: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(password.toByteArray())
        return hash.fold("") { str, it -> str + "%02x".format(it) }
    }

    private fun isUserLoggedIn(): Boolean {
        val sharedPrefs = getSharedPreferences("TimeRewindPrefs", MODE_PRIVATE)
        return sharedPrefs.getBoolean("isLoggedIn", false)
    }

    private fun saveLoginState(email: String) {
        val sharedPrefs = getSharedPreferences("TimeRewindPrefs", MODE_PRIVATE)
        with(sharedPrefs.edit()) {
            putBoolean("isLoggedIn", true)
            putString("userEmail", email)
            apply()
        }
    }

    private fun startMainActivity() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }
} 