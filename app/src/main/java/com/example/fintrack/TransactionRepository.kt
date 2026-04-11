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
}
