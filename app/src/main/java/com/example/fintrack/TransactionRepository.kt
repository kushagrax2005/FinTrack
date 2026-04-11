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
        // Look for same amount and merchant within 15 minutes (900,000 ms)
        val startTime = date - 900_000
        val endTime = date + 900_000
        return transactionDao.findDuplicate(amount, merchant, startTime, endTime) != null
    }
}
