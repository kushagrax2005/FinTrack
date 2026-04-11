package com.example.fintrack

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.example.fintrack.NotificationHelper

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            for (msg in messages) {
                val sender = msg.displayOriginatingAddress ?: ""
                val body = msg.messageBody ?: ""
                val date = msg.timestampMillis

                // 🔍 Step 1: Check if it's a Bank/Transaction SMS
                if (isBankSender(sender) || isTransactionBody(body)) {
                    Log.d("SmsReceiver", "Processing SMS from: $sender Body: $body")
                    val transaction = SmsParser.parse(body, date)
                    if (transaction != null) {
                        Log.d("SmsReceiver", "Transaction Detected: ${transaction.amount} at ${transaction.merchant}")
                        
                        // Save to Room Database
                        val db = AppDatabase.getDatabase(context)
                        val repository = TransactionRepository(db.transactionDao())
                        
                        CoroutineScope(Dispatchers.IO).launch {
                            val entity = TransactionEntity(
                                amount = transaction.amount,
                                merchant = transaction.merchant,
                                category = transaction.category,
                                date = transaction.date,
                                body = transaction.body,
                                isDebit = transaction.isDebit
                            )
                            
                            // 🛑 PREVENT DUPLICATE ENTRIES: Check if same amount and merchant exists within 15 mins
                            if (!repository.isDuplicate(entity.amount, entity.merchant, entity.date)) {
                                repository.insert(entity)
                                NotificationHelper.showNotification(context, transaction.amount, transaction.merchant)
                            } else {
                                Log.d("SmsReceiver", "Skipping duplicate transaction: ${entity.amount} at ${entity.merchant}")
                            }
                        }
                    }
                }
            }
        }
    }

    private fun isBankSender(sender: String): Boolean {
        // Bank headers are usually 6 chars and alphanumeric (e.g., AD-HDFCBK)
        val cleanSender = sender.uppercase()
        return cleanSender.length >= 6 && (cleanSender.contains("BK") || cleanSender.contains("BNK") || cleanSender.any { it.isLetter() })
    }

    private fun isTransactionBody(body: String): Boolean {
        val lower = body.lowercase()
        return lower.contains("debited") || lower.contains("credited") || 
               lower.contains("spent") || lower.contains("vpa") || 
               lower.contains("a/c") || lower.contains("paid") ||
               lower.contains("txn")
    }
}
