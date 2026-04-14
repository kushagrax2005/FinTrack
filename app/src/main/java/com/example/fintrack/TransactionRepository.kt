package com.example.fintrack

import kotlinx.coroutines.flow.Flow

class TransactionRepository(private val transactionDao: TransactionDao) {
    val allTransactions: Flow<List<TransactionEntity>> = transactionDao.getAllTransactions()

    suspend fun insert(transaction: TransactionEntity) {
        transactionDao.insert(transaction)
    }

    suspend fun insertAll(transactions: List<TransactionEntity>) {
        transactionDao.insertAll(transactions)
    }

    suspend fun deleteAll() {
        transactionDao.deleteAll()
    }

    suspend fun delete(transaction: TransactionEntity) {
        transactionDao.delete(transaction)
    }

    suspend fun isDuplicate(amount: Double, merchant: String, date: Long): Boolean {
        // Look for same amount and merchant within 5 minutes (300,000 ms)
        val startTime = date - 300_000
        val endTime = date + 300_000
        return transactionDao.findSimilar(amount, merchant, startTime, endTime).isNotEmpty()
    }
}
