package com.example.fintrack

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Bundle
import android.telephony.SmsManager
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.fintrack.databinding.ActivitySetupBinding
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Random

class SetupActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySetupBinding
    private val PREFS_NAME = "FinTrackPrefs"
    private var generatedOtp: String? = null
    private val SMS_PERMISSION_CODE = 101
    private var resendTimerJob: kotlinx.coroutines.Job? = null

    private val otpReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.example.fintrack.OTP_RECEIVED") {
                val otp = intent.getStringExtra("otp")
                if (otp != null && otp == generatedOtp && binding.cbAutoVerify.isChecked) {
                    binding.etOtp.setText(otp)
                    binding.txtVerificationStatus.text = "OTP $otp received! Verifying..."
                    binding.pbVerification.visibility = View.GONE
                    
                    lifecycleScope.launch {
                        delay(1000)
                        completeVerification(binding.etPhone.text.toString().trim())
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySetupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnNextName.setOnClickListener {
            val first = binding.etFirstName.text.toString().trim()
            val last = binding.etLastName.text.toString().trim()

            if (first.all { it.isLetter() } && last.all { it.isLetter() } && first.isNotEmpty() && last.isNotEmpty()) {
                binding.txtErrorName.visibility = View.GONE
                binding.layoutStepName.visibility = View.GONE
                binding.layoutStepAge.visibility = View.VISIBLE
            } else {
                binding.txtErrorName.visibility = View.VISIBLE
            }
        }

        binding.btnNextAge.setOnClickListener {
            val age = binding.etAge.text.toString().toIntOrNull() ?: 0
            if (age >= 18) {
                binding.layoutStepAge.visibility = View.GONE
                binding.layoutStepPhone.visibility = View.VISIBLE
            } else {
                AlertDialog.Builder(this)
                    .setTitle("Age Requirement")
                    .setMessage("To use FinTrack and its features, you must be 18 or older.")
                    .setPositiveButton("Close App") { _, _ ->
                        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().apply()
                        finishAffinity()
                    }
                    .setCancelable(false)
                    .show()
            }
        }

        binding.btnVerify.setOnClickListener {
            val phone = binding.etPhone.text.toString().trim()
            if (phone.length >= 10) {
                if (checkSmsPermission()) {
                    sendVerificationSms(phone)
                } else {
                    requestSmsPermission()
                }
            } else {
                Toast.makeText(this, "Enter a valid phone number", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnSubmitOtp.setOnClickListener {
            val enteredOtp = binding.etOtp.text.toString().trim()
            if (enteredOtp == generatedOtp) {
                completeVerification(binding.etPhone.text.toString().trim())
            } else {
                Toast.makeText(this, "Incorrect OTP. Please try again.", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnResend.setOnClickListener {
            sendVerificationSms(binding.etPhone.text.toString().trim())
        }

        binding.btnChangeNumber.setOnClickListener {
            binding.layoutStepOtp.visibility = View.GONE
            binding.layoutStepPhone.visibility = View.VISIBLE
            resendTimerJob?.cancel()
        }
    }

    private fun checkSmsPermission(): Boolean {
        val sendSms = ContextCompat.checkSelfPermission(this, android.Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED
        val receiveSms = ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
        val readSms = ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED
        return sendSms && receiveSms && readSms
    }

    private fun requestSmsPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(
                android.Manifest.permission.SEND_SMS,
                android.Manifest.permission.RECEIVE_SMS,
                android.Manifest.permission.READ_SMS
            ),
            SMS_PERMISSION_CODE
        )
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == SMS_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                val phone = binding.etPhone.text.toString().trim()
                if (phone.isNotEmpty()) sendVerificationSms(phone)
            } else {
                Toast.makeText(this, "SMS Permissions are required for real verification", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun sendVerificationSms(phone: String) {
        // Move to OTP step UI
        binding.layoutStepPhone.visibility = View.GONE
        binding.layoutStepOtp.visibility = View.VISIBLE
        binding.txtOtpSentTo.text = "Code sent to +91 $phone"
        binding.txtVerificationStatus.text = "Sending SMS..."
        binding.pbVerification.visibility = View.VISIBLE
        binding.etOtp.setText("")

        // Clear any previous received OTP
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().remove("received_otp").apply()

        val otp = (100000..999999).random().toString()
        generatedOtp = otp

        try {
            val smsManager = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                this.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }
            
            smsManager.sendTextMessage(phone, null, "Your FinTrack verification code is: $otp", null, null)
            binding.txtVerificationStatus.text = "Waiting for SMS to arrive..."
            
            startResendTimer()

            // Start listening for the SMS in real-time
            if (binding.cbAutoVerify.isChecked) {
                listenForIncomingSms(otp)
            } else {
                binding.pbVerification.visibility = View.GONE
                binding.txtVerificationStatus.text = "Please enter the OTP manually."
            }
            
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to send SMS: ${e.message}", Toast.LENGTH_LONG).show()
            binding.layoutStepOtp.visibility = View.GONE
            binding.layoutStepPhone.visibility = View.VISIBLE
        }
    }

    private fun startResendTimer() {
        resendTimerJob?.cancel()
        resendTimerJob = lifecycleScope.launch {
            binding.btnResend.isEnabled = false
            binding.btnResend.alpha = 0.5f
            for (i in 15 downTo 1) {
                binding.btnResend.text = "Resend OTP ($i)"
                delay(1000)
            }
            binding.btnResend.text = "Resend OTP"
            binding.btnResend.isEnabled = true
            binding.btnResend.alpha = 1.0f
        }
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter("com.example.fintrack.OTP_RECEIVED")
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(otpReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(otpReceiver, filter)
        }
    }

    override fun onStop() {
        super.onStop()
        unregisterReceiver(otpReceiver)
    }

    private fun listenForIncomingSms(expectedOtp: String) {
        lifecycleScope.launch {
            // Still have a timeout for the UI status, but logic is handled by Broadcast
            repeat(60) {
                delay(500)
            }
            if (binding.etOtp.text?.isEmpty() == true) {
                binding.txtVerificationStatus.text = "Auto-detection timed out. Please enter manually."
                binding.pbVerification.visibility = View.GONE
            }
        }
    }

    private fun completeVerification(phone: String) {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val age = binding.etAge.text.toString().toIntOrNull() ?: 25 // Default age for admin/fallback
        
        prefs.edit()
            .putString("user_name", "${binding.etFirstName.text} ${binding.etLastName.text}")
            .putInt("user_age", age)
            .putString("user_phone", phone)
            .putBoolean("user_verified", true)
            .apply()

        Toast.makeText(this, "Verification Successful!", Toast.LENGTH_SHORT).show()
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
