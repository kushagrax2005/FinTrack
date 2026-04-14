package com.example.fintrack

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import com.example.fintrack.databinding.ActivityAuthBinding
import java.util.concurrent.Executor

class AuthActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAuthBinding
    private lateinit var executor: Executor
    private lateinit var biometricPrompt: BiometricPrompt
    private lateinit var promptInfo: BiometricPrompt.PromptInfo

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAuthBinding.inflate(layoutInflater)
        setContentView(binding.root)

        executor = ContextCompat.getMainExecutor(this)
        
        biometricPrompt = BiometricPrompt(this, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    Toast.makeText(applicationContext, "Authentication error: $errString", Toast.LENGTH_SHORT).show()
                }

                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    startActivity(Intent(this@AuthActivity, MainActivity::class.java))
                    finish()
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    Toast.makeText(applicationContext, "Authentication failed", Toast.LENGTH_SHORT).show()
                }
            })

        promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("FinTrack Security")
            .setSubtitle("Log in using your biometric credential")
            .setNegativeButtonText("Use Account Password")
            .build()

        // Hide UI elements to keep it clean, just show biometric
        binding.tabLayout.visibility = android.view.View.GONE
        binding.registerSection.visibility = android.view.View.GONE
        binding.loginSection.visibility = android.view.View.VISIBLE
        
        // Setup Button to trigger biometric
        binding.btnRetryBiometric.setOnClickListener {
            showBiometricPrompt()
        }

        // Auto-trigger biometric initially
        showBiometricPrompt()

        binding.btnLogin.setOnClickListener {
            // Fallback to password if needed
            val email = binding.etEmail.text.toString()
            val password = binding.etPassword.text.toString()

            if (email.isNotEmpty() && password.isNotEmpty()) {
                val sharedPref = getSharedPreferences("FinTrackPrefs", Context.MODE_PRIVATE)
                val storedEmail = sharedPref.getString("user_email", "")
                // For simplicity in this "biometric first" flow, we allow password login as fallback
                if (email == storedEmail) {
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                }
            }
        }
    }

    private fun showBiometricPrompt() {
        val biometricManager = BiometricManager.from(this)
        when (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)) {
            BiometricManager.BIOMETRIC_SUCCESS ->
                biometricPrompt.authenticate(promptInfo)
            else -> {
                Toast.makeText(this, "Biometric features are currently unavailable.", Toast.LENGTH_LONG).show()
                // Keep the manual login visible as fallback
                binding.tabLayout.visibility = android.view.View.VISIBLE
            }
        }
    }
}
