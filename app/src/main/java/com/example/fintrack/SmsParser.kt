package com.example.fintrack

import java.util.regex.Pattern

object SmsParser {

    // 🏆 Strong patterns for transaction amounts. 
    // Requires a currency symbol (Rs, INR, ₹) to avoid picking up IDs or balances incorrectly.
    private val debitPatterns = listOf(
        // Matches "Debited Rs 100", "Paid INR 500", etc.
        Pattern.compile("(?i)(?:debited|spent|paid|payment|purchase|used|sent|withdrawal|vpa)\\s*(?:of|by|with|:| )?\\s*(?:rs\\.?|inr|₹)\\s*([\\d,]+\\.?\\d{0,2})"),
        // Matches "Rs 100 debited"
        Pattern.compile("(?i)(?:rs\\.?|inr|₹)\\s*([\\d,]+\\.?\\d{0,2})\\s*(?:debited|spent|paid|used|sent|purchase|withdrawal)")
    )

    fun parse(body: String, date: Long): Transaction? {
        val lowerBody = body.lowercase()

        // 🛑 STEP 1: Filter out system, security, failures, and MARKETING/OFFERS
        if (lowerBody.contains("otp") || lowerBody.contains("password") || 
            lowerBody.contains("verify") || lowerBody.contains("failed") || 
            lowerBody.contains("declined") || lowerBody.contains("insufficient") ||
            lowerBody.contains("rejected") || lowerBody.contains("cancelled") ||
            lowerBody.contains("reversed") || lowerBody.contains("unsuccessful") ||
            lowerBody.contains("login detected") || 
            // Marketing/Offers filtering
            lowerBody.contains("offer") || lowerBody.contains("discount") || 
            lowerBody.contains("cashback up to") || lowerBody.contains("win") ||
            lowerBody.contains("recharge now") || lowerBody.contains("valid till") ||
            lowerBody.contains("click to") || lowerBody.contains("get rs.") ||
            lowerBody.contains("avail") || lowerBody.contains("limited period") ||
            lowerBody.contains("exclusive") || lowerBody.contains("congratulations")) {
            return null
        }

        // 🏦 STEP 2: Identify Credit vs Debit
        val isExplicitCredit = lowerBody.contains("credited") || lowerBody.contains("received") || 
                              lowerBody.contains("refunded") || lowerBody.contains("deposited") ||
                              lowerBody.contains("salary") || lowerBody.contains("cashback") ||
                              lowerBody.contains("added to your account")
        
        val isExplicitDebit = lowerBody.contains("debited") || lowerBody.contains("spent") || 
                             lowerBody.contains("paid") || lowerBody.contains("sent") ||
                             lowerBody.contains("used") || lowerBody.contains("withdrawal") ||
                             lowerBody.contains("purchase") || lowerBody.contains("payment to")

        if (!isExplicitCredit && !isExplicitDebit) return null

        // 🛑 STEP 3: Extract Amount
        val amount = if (isExplicitCredit) extractCreditAmount(body) else extractDebitAmount(body)
        if (amount == null || amount < 1.0) return null 

        val isDebit = !isExplicitCredit
        val category = if (isDebit) determineCategory(body) else "Income 💰"
        val merchant = if (isDebit) extractMerchant(body) else extractSender(body)

        return Transaction(
            amount = amount,
            merchant = merchant,
            category = category,
            date = date,
            body = body,
            isDebit = isDebit
        )
    }

    private val creditPatterns = listOf(
        Pattern.compile("(?i)(?:credited|received|deposited|added)\\s*(?:of|by|with|:| )?\\s*(?:rs\\.?|inr|₹)\\s*([\\d,]+\\.?\\d{0,2})"),
        Pattern.compile("(?i)(?:rs\\.?|inr|₹)\\s*([\\d,]+\\.?\\d{0,2})\\s*(?:credited|received|deposited|added)")
    )

    private fun extractCreditAmount(body: String): Double? {
        for (pattern in creditPatterns) {
            val matcher = pattern.matcher(body)
            if (matcher.find()) {
                val amountStr = matcher.group(1)
                    ?.replace(",", "")
                    ?.replace(" ", "")
                return amountStr?.toDoubleOrNull()
            }
        }
        return null
    }

    private fun extractSender(body: String): String {
        val lower = body.lowercase()
        if (lower.contains("salary")) return "Salary"
        if (lower.contains("cashback")) return "Cashback"
        if (lower.contains("refund")) return "Refund"
        
        val patterns = listOf(
            Pattern.compile("(?i)from\\s+([^\\s,]+(?:\\s+[^\\s,]+)?)"),
            Pattern.compile("(?i)by\\s+([^\\s,]+(?:\\s+[^\\s,]+)?)")
        )

        for (pattern in patterns) {
            val matcher = pattern.matcher(body)
            if (matcher.find()) {
                var found = matcher.group(1)?.trim()
                if (found != null) {
                    found = found.split("(?i)using|via|on|ref|id|utr|date|link|at|for|from|to".toRegex())[0].trim()
                    val normalized = found.replace(Regex("[^a-zA-Z0-9\\s]"), "").trim()
                    if (normalized.length > 2 && !isCommonWord(normalized)) {
                        return normalized.split(" ").take(2).joinToString(" ").replaceFirstChar { it.uppercase() }
                    }
                }
            }
        }
        return "Sender"
    }

    private fun extractDebitAmount(body: String): Double? {
        for (pattern in debitPatterns) {
            val matcher = pattern.matcher(body)
            if (matcher.find()) {
                val amountStr = matcher.group(1)
                    ?.replace(",", "")
                    ?.replace(" ", "")
                return amountStr?.toDoubleOrNull()
            }
        }
        return null
    }

    private fun determineCategory(body: String): String {
        val lower = body.lowercase()
        return when {
            lower.contains("cash") || lower.contains("withdrawal") || lower.contains("atm") -> "Cash 💵"
            lower.contains("zomato") || lower.contains("swiggy") || lower.contains("food") || lower.contains("eat") || lower.contains("restaurant") || lower.contains("bakery") -> "Food 🍔"
            lower.contains("amazon") || lower.contains("flipkart") || lower.contains("shop") || lower.contains("blinkit") || lower.contains("zepto") || lower.contains("myntra") || lower.contains("meesho") -> "Shopping 🛍"
            lower.contains("recharge") || lower.contains("airtel") || lower.contains("jio") || lower.contains("bill") || lower.contains("electricity") || lower.contains("vi ") || lower.contains("broadband") -> "Bills 🧾"
            lower.contains("uber") || lower.contains("ola") || lower.contains("metro") || lower.contains("travel") || lower.contains("rapido") || lower.contains("train") || lower.contains("irctc") || lower.contains("bus") -> "Travel 🚗"
            lower.contains("netflix") || lower.contains("spotify") || lower.contains("movie") || lower.contains("pvr") || lower.contains("hotstar") || lower.contains("theatre") || lower.contains("entertainment") -> "Entertainment 🍿"
            lower.contains("transfer") || lower.contains("upi") || lower.contains("sent to") || lower.contains("phonepe") || lower.contains("gpay") || lower.contains("paytm") || lower.contains("vpa") -> "Transfers 💸"
            else -> "Others 📦"
        }
    }

    private fun extractMerchant(body: String): String {
        val lower = body.lowercase()
        if (lower.contains("atm") || lower.contains("cash withdrawal")) return "Cash Withdrawal"

        val knownMerchants = listOf(
            "zomato", "swiggy", "amazon", "flipkart", "airtel", "jio", "uber", "ola", 
            "paytm", "phonepe", "gpay", "netflix", "spotify", "bigbasket", "blinkit", 
            "zepto", "zudio", "dmart", "jiomart", "hotstar", "bookmyshow", "pvr", "myntra", "meesho"
        )
        for (m in knownMerchants) {
            if (lower.contains(m)) return m.replaceFirstChar { it.uppercase() }
        }

        val patterns = listOf(
            Pattern.compile("(?i)paid\\s+(?:Rs\\.?|inr|₹)?\\s*[\\d.,]+\\s+to\\s+([^\\s,]+(?:\\s+[^\\s,]+)?)"),
            Pattern.compile("(?i)spent\\s+(?:Rs\\.?|inr|₹)?\\s*[\\d.,]+\\s+at\\s+([^\\s,]+(?:\\s+[^\\s,]+)?)"),
            Pattern.compile("(?i)towards\\s+([^\\s,]+(?:\\s+[^\\s,]+)?)"),
            Pattern.compile("(?i)at\\s+([^\\s,]{3,}(?:\\s+[^\\s,]+)?)")
        )

        for (pattern in patterns) {
            val matcher = pattern.matcher(body)
            if (matcher.find()) {
                var found = matcher.group(1)?.trim()
                if (found != null) {
                    found = found.split("(?i)using|via|on|ref|id|utr|date|link|at|for|from|to".toRegex())[0].trim()
                    val normalized = found.replace(Regex("[^a-zA-Z0-9\\s]"), "").trim()
                    if (normalized.length > 2 && !isCommonWord(normalized)) {
                        return normalized.split(" ").take(2).joinToString(" ").replaceFirstChar { it.uppercase() }
                    }
                }
            }
        }
        return "Merchant"
    }

    private fun isCommonWord(word: String): Boolean {
        val lower = word.lowercase()
        val common = listOf("your", "rs", "inr", "amount", "account", "bank", "with", "from", "successfully", "payment", "at", "request", "towards", "vpa", "upi")
        if (lower.contains(":") || (lower.matches(".*\\d.*".toRegex()) && lower.length < 4)) return true
        return common.contains(lower) || lower.all { !it.isLetter() }
    }
}
