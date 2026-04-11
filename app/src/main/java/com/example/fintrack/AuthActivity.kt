package com.example.fintrack

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.fintrack.databinding.ActivityAuthBinding
import java.security.MessageDigest

class AuthActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAuthBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAuthBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.tabLayout.addOnTabSelectedListener(object : com.google.android.material.tabs.TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: com.google.android.material.tabs.TabLayout.Tab?) {
                if (tab?.position == 0) {
                    binding.loginSection.visibility = android.view.View.VISIBLE
                    binding.registerSection.visibility = android.view.View.GONE
                } else {
                    binding.loginSection.visibility = android.view.View.GONE
                    binding.registerSection.visibility = android.view.View.VISIBLE
                }
            }
            override fun onTabUnselected(tab: com.google.android.material.tabs.TabLayout.Tab?) {}
            override fun onTabReselected(tab: com.google.android.material.tabs.TabLayout.Tab?) {}
        })

        binding.btnLogin.setOnClickListener {
            val email = binding.etEmail.text.toString()
            val password = binding.etPassword.text.toString()

            if (email.isNotEmpty() && password.isNotEmpty()) {
                val sharedPref = getSharedPreferences("FinTrackPrefs", Context.MODE_PRIVATE)
                val storedEmail = sharedPref.getString("user_email", "")
                val storedHash = sharedPref.getString("user_password_hash", "")
                val currentHash = hashPassword(password)

                if (email == storedEmail && currentHash == storedHash) {
                    sharedPref.edit().putBoolean("isLoggedIn", true).apply()
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                } else {
                    Toast.makeText(this, "Invalid Credentials", Toast.LENGTH_SHORT).show()
                }
            }
        }

        binding.btnRegister.setOnClickListener {
            val name = binding.etRegName.text.toString()
            val email = binding.etRegEmail.text.toString()
            val password = binding.etRegPassword.text.toString()
            val income = binding.etRegIncome.text.toString()

            if (name.isNotEmpty() && email.isNotEmpty() && password.isNotEmpty()) {
                val sharedPref = getSharedPreferences("FinTrackPrefs", Context.MODE_PRIVATE)
                with(sharedPref.edit()) {
                    putString("user_name", name)
                    putString("user_email", email)
                    putString("user_password_hash", hashPassword(password))
                    putFloat("monthly_income", income.toFloatOrNull() ?: 0f)
                    putBoolean("isLoggedIn", true)
                    apply()
                }
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            }
        }
    }

    private fun hashPassword(password: String): String {
        val bytes = password.toByteArray()
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(bytes)
        return digest.fold("") { str, it -> str + "%02x".format(it) }
    }
}
