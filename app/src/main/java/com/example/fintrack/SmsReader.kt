package com.example.fintrack

import android.content.Context
import android.provider.Telephony

class SmsReader(private val context: Context) {

    fun readAllTransactions(): List<TransactionEntity> {
        val transactions = mutableListOf<TransactionEntity>()
        val cursor = context.contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            null,
            null,
            null,
            "date DESC"
        )

        cursor?.use {
            val bodyIndex = it.getColumnIndex(Telephony.Sms.BODY)
            val dateIndex = it.getColumnIndex(Telephony.Sms.DATE)
            val addressIndex = it.getColumnIndex(Telephony.Sms.ADDRESS)

            while (it.moveToNext()) {
                val body = it.getString(bodyIndex) ?: ""
                val date = it.getLong(dateIndex)
                val sender = it.getString(addressIndex) ?: ""

                // Use the existing SmsParser to determine if it's a transaction
                val parsed = SmsParser.parse(body, date)
                if (parsed != null) {
                    transactions.add(
                        TransactionEntity(
                            amount = parsed.amount,
                            merchant = parsed.merchant,
                            category = parsed.category,
                            date = parsed.date,
                            body = parsed.body,
                            isDebit = parsed.isDebit
                        )
                    )
                }
            }
        }
        return transactions
    }
}
