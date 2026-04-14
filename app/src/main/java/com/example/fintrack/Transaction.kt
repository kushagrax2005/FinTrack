package com.example.fintrack

data class Transaction(
    val id: Long = 0,
    val amount: Double,
    val merchant: String,
    val category: String,
    val date: Long,
    val body: String,
    val isDebit: Boolean = true
)
