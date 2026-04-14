package com.example.fintrack

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions ORDER BY date DESC")
    fun getAllTransactions(): Flow<List<TransactionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(transaction: TransactionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(transactions: List<TransactionEntity>)

    @Delete
    suspend fun delete(transaction: TransactionEntity)

    @Query("DELETE FROM transactions")
    suspend fun deleteAll()

    @Query("SELECT * FROM transactions WHERE amount = :amount AND merchant = :merchant AND date BETWEEN :startTime AND :endTime")
    suspend fun findSimilar(amount: Double, merchant: String, startTime: Long, endTime: Long): List<TransactionEntity>
}

@Entity(
    tableName = "transactions",
    indices = [Index(value = ["body", "date"], unique = true)]
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amount: Double,
    val merchant: String,
    val category: String,
    val date: Long,
    val body: String,
    val isDebit: Boolean,
    val isSavingGoal: Boolean = false
)
