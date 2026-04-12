package com.example.fintrack

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity

class SplashActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        Handler(Looper.getMainLooper()).postDelayed({
            // Force login every time as requested by setting isLoggedIn to false on every start
            val sharedPref = getSharedPreferences("FinTrackPrefs", Context.MODE_PRIVATE)
            
            // If you want it to ALWAYS show login, we route to AuthActivity
            // But we keep the user data (like budget/name) intact for when they log in.
            startActivity(Intent(this, AuthActivity::class.java))
            finish()
        }, 2000)
    }
}
