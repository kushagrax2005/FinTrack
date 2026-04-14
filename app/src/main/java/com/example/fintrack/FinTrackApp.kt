package com.example.fintrack

import android.app.Application

class FinTrackApp : Application() {
    val database by lazy { AppDatabase.getDatabase(this) }
    val repository by lazy { TransactionRepository(database.transactionDao()) }
}
