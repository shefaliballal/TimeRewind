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

class SignupActivity : AppCompatActivity() {

    private lateinit var emailInput: TextInputEditText
    private lateinit var passwordInput: TextInputEditText
    private lateinit var confirmPasswordInput: TextInputEditText
    private lateinit var signupButton: MaterialButton
    private lateinit var loginText: MaterialTextView
    private lateinit var databaseHelper: DatabaseHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_signup)

        databaseHelper = DatabaseHelper(this)

        // Initialize views
        emailInput = findViewById(R.id.emailInput)
        passwordInput = findViewById(R.id.passwordInput)
        confirmPasswordInput = findViewById(R.id.confirmPasswordInput)
        signupButton = findViewById(R.id.signupButton)
        loginText = findViewById(R.id.loginText)

        // Set up click listeners
        signupButton.setOnClickListener {
            performSignup()
        }

        loginText.setOnClickListener {
            finish() // Go back to login
        }
    }

    private fun performSignup() {
        val email = emailInput.text.toString().trim()
        val password = passwordInput.text.toString().trim()
        val confirmPassword = confirmPasswordInput.text.toString().trim()

        // Validation
        if (email.isEmpty()) {
            emailInput.error = "Email is required"
            return
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailInput.error = "Please enter a valid email"
            return
        }

        if (password.isEmpty()) {
            passwordInput.error = "Password is required"
            return
        }

        if (password.length < 6) {
            passwordInput.error = "Password must be at least 6 characters"
            return
        }

        if (confirmPassword.isEmpty()) {
            confirmPasswordInput.error = "Please confirm your password"
            return
        }

        if (password != confirmPassword) {
            confirmPasswordInput.error = "Passwords do not match"
            return
        }

        // Check if user already exists
        if (databaseHelper.userExists(email)) {
            emailInput.error = "User with this email already exists"
            return
        }

        // Show loading state
        signupButton.isEnabled = false
        signupButton.text = "Creating account..."

        // Hash password and register user
        val passwordHash = hashPassword(password)
        
        if (databaseHelper.registerUser(email, passwordHash)) {
            Toast.makeText(this, "Account created successfully!", Toast.LENGTH_SHORT).show()
            
            // Save login state
            saveLoginState(email)
            
            // Navigate to main activity
            startMainActivity()
        } else {
            Toast.makeText(this, "Failed to create account. Please try again.", Toast.LENGTH_SHORT).show()
            signupButton.isEnabled = true
            signupButton.text = "Sign Up"
        }
    }

    private fun hashPassword(password: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(password.toByteArray())
        return hash.fold("") { str, it -> str + "%02x".format(it) }
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